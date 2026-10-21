'use strict';

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const pi = require('./podcast-index');

describe('podcast-index failsafes', () => {
    it('parseRetryAfterMs honors numeric Retry-After seconds', () => {
        assert.equal(pi.parseRetryAfterMs('12', 1000), 12_000);
    });

    it('parseRetryAfterMs keeps the floor and the cap', () => {
        assert.equal(pi.parseRetryAfterMs(null, 15_000), 15_000);
        assert.equal(pi.parseRetryAfterMs('1', 15_000), 15_000);
        assert.equal(pi.parseRetryAfterMs('99999', 15_000), pi.DEFAULTS.coolCapMs);
    });

    it('isTransientHttp: throttling and every 5xx, including Cloudflare 52x', () => {
        for (const s of [403, 429, 500, 502, 503, 504, 521, 522, 524]) assert.equal(pi.isTransientHttp(s), true, String(s));
        assert.equal(pi.isTransientHttp(404), false);
        assert.equal(pi.isTransientHttp(400), false);
    });
});

/** A client on a fake clock: sleeping advances time instantly. */
function harness(responses, config = {}) {
    let t = 1_800_000_000_000;
    const calls = [];
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pi-pause-'));
    const pauseFile = path.join(dir, 'pi-pause.json');
    const make = () => pi.createClient({
        key: 'k',
        secret: 's',
        now: () => t,
        sleep: async (ms) => { t += ms; },
        fetchImpl: async (url) => {
            calls.push({ url: String(url).replace('https://api.podcastindex.org/api/1.0', ''), at: t });
            const r = typeof responses === 'function' ? responses(String(url), calls.length) : responses.shift();
            if (r instanceof Error) throw r;
            return {
                status: r.status ?? 200,
                ok: (r.status ?? 200) < 400,
                headers: { get: (h) => (h.toLowerCase() === 'retry-after' ? r.retryAfter ?? null : null) },
                json: async () => r.body ?? { status: 'true', items: [] },
                body: { cancel: async () => {} },
            };
        },
        config: { pauseFile, ...config },
    });
    return { make, calls, pauseFile, now: () => t, advance: (ms) => { t += ms; }, cleanup: () => fs.rmSync(dir, { recursive: true, force: true }) };
}

describe('podcast-index client: lenient rate, cooling off, suspension', () => {
    let h;
    afterEach(() => h?.cleanup());

    it('runs at the generous rate while answers are clean', async () => {
        h = harness(() => ({ body: { status: 'true', items: [{ id: 1 }] } }));
        const c = h.make();
        const t0 = h.now();
        for (let i = 0; i < 20; i++) await c.episodesByFeedId(1, 1);
        assert.equal(c.stats().calls, 20);
        assert.ok(h.now() - t0 <= 2000, '20 calls at 10 req/s take about 2 s');
        assert.equal(c.stats().throttled, 0);
    });

    it('a 429 honours Retry-After, halves the rate for everyone, and recovers after clean answers', async () => {
        h = harness((url, n) => (n === 1 ? { status: 429, retryAfter: '40' } : { body: { status: 'true', items: [] } }), { recoverAfter: 5 });
        const c = h.make();
        const t0 = h.now();
        await c.episodesByFeedId(1, 1);
        assert.ok(h.calls[1].at - t0 >= 40_000, 'waited out Retry-After');
        assert.equal(c.stats().rps, 5);
        for (let i = 0; i < 5; i++) await c.episodesByFeedId(1, 1);
        assert.equal(c.stats().rps, 6.25, 'a quarter faster after 5 clean answers');
    });

    it('cooling off grows with trouble in a row, and a timeout counts as trouble', async () => {
        const timeout = Object.assign(new Error('The operation was aborted due to timeout'), { name: 'TimeoutError' });
        h = harness([{ status: 503 }, timeout, { status: 521 }, { body: { status: 'true', items: [] } }]);
        const c = h.make();
        await c.episodesByFeedId(1, 1);
        const gaps = h.calls.slice(1).map((x, i) => x.at - h.calls[i].at);
        assert.ok(gaps[0] >= 15_000 && gaps[1] >= 30_000 && gaps[2] >= 60_000, `gaps ${gaps}`);
        assert.equal(c.stats().throttled, 3);
    });

    it('404 is an answer, not trouble: the call fails and the rate stays', async () => {
        h = harness([{ status: 404 }]);
        const c = h.make();
        await assert.rejects(c.podcastByItunesId(5), /PI API error: 404/);
        assert.equal(c.stats().rps, pi.DEFAULTS.maxRps);
    });

    it('trouble in a row suspends the run, saves the pause, and later calls never reach Podcast Index', async () => {
        h = harness(() => ({ status: 503 }), { suspendAfter: 4 });
        const c = h.make();
        await assert.rejects(c.episodesByFeedId(1, 1), (e) => pi.isSuspended(e));
        assert.equal(h.calls.length, 4);
        const pause = JSON.parse(fs.readFileSync(h.pauseFile, 'utf8'));
        assert.equal(pause.level, 0);
        assert.equal(pause.until - h.now(), 30 * 60_000, 'first pause: 30 minutes');
        await assert.rejects(c.episodesByFeedId(2, 1), (e) => pi.isSuspended(e));
        // The next stage is another process: it reads the pause and skips Podcast Index too.
        await assert.rejects(h.make().podcastByItunesId(9), (e) => pi.isSuspended(e));
        assert.equal(h.calls.length, 4, 'no call while paused');
    });

    it('too much time cooling off also suspends', async () => {
        h = harness(() => ({ status: 429, retryAfter: '600' }), { suspendCoolingMs: 15 * 60_000, suspendAfter: 99 });
        await assert.rejects(h.make().episodesByFeedId(1, 1), (e) => pi.isSuspended(e) && /cooling off/.test(e.message));
    });

    it('after the pause one probe decides: an answer clears it, trouble doubles it', async () => {
        let up = false;
        h = harness(() => (up ? { body: { status: 'true', items: [{ id: 7 }] } } : { status: 503 }), { suspendAfter: 2 });
        await assert.rejects(h.make().episodesByFeedId(1, 1), (e) => pi.isSuspended(e));
        h.advance(31 * 60_000);
        await assert.rejects(h.make().episodesByFeedId(1, 1), (e) => pi.isSuspended(e) && /probe/.test(e.message));
        const second = JSON.parse(fs.readFileSync(h.pauseFile, 'utf8'));
        assert.deepEqual([second.level, second.until - h.now()], [1, 60 * 60_000], 'second pause: an hour');
        assert.equal(h.calls.at(-1).url, '/stats/current', 'the probe is one cheap call');

        h.advance(61 * 60_000);
        up = true;
        const c = h.make();
        assert.deepEqual((await c.episodesByFeedId(1, 1)).map((x) => x.id), [7]);
        assert.equal(fs.existsSync(h.pauseFile), false, 'pause cleared');
    });
});
