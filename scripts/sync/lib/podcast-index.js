'use strict';

/**
 * Shared Podcast Index API client for every sync stage: generous by default, gentle as soon as
 * Podcast Index pushes back, and able to pause the whole run (own-episode-IDs P1b, 21 Oct: very
 * lenient limits, with cooling off and long backoffs, including suspending the full run).
 *
 * - Rate: up to PI_MAX_RPS (default 10) request starts per second, process-wide.
 * - Trouble (403, 429, any 5xx including Cloudflare's 52x, timeouts, network errors, a
 *   `status: false` answer): the rate halves (down to PI_MIN_RPS) and every worker cools off
 *   together, for Retry-After when given, else 15 s doubling with each trouble in a row, at most
 *   10 minutes. Every 50 clean answers raise the rate by a quarter, back up to the maximum.
 * - Suspension: after PI_SUSPEND_AFTER failed attempts in a row (default 12), or more than
 *   PI_SUSPEND_COOLING_MS (15 min) spent cooling off in one process, the client stops calling
 *   Podcast Index. The pause is saved to PI_PAUSE_FILE, so later stages and runs skip it too:
 *   30 minutes the first time, doubling up to 6 hours. When it ends, one probe call decides: an
 *   answer clears the pause, trouble extends it. While paused, calls throw PiSuspendedError at once,
 *   and stages treat that as "try again next run", not as an error.
 * - 400/404 and other answers are not trouble: the call fails, the rate is untouched.
 */

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const log = require('./log');

const API_BASE = 'https://api.podcastindex.org/api/1.0';

const num = (v, d) => {
    const n = Number(v);
    return Number.isFinite(n) && n > 0 ? n : d;
};

const DEFAULTS = Object.freeze({
    maxRps: num(process.env.PI_MAX_RPS, 10),
    minRps: num(process.env.PI_MIN_RPS, 0.5),
    timeoutMs: num(process.env.PI_TIMEOUT_MS, 30_000),
    maxAttempts: 5,
    coolBaseMs: 15_000,
    coolCapMs: 10 * 60_000,
    recoverAfter: 50,
    suspendAfter: num(process.env.PI_SUSPEND_AFTER, 12),
    suspendCoolingMs: num(process.env.PI_SUSPEND_COOLING_MS, 15 * 60_000),
    pauseBaseMs: 30 * 60_000,
    pauseCapMs: 6 * 3600_000,
    pauseFile: process.env.PI_PAUSE_FILE || path.resolve(__dirname, '../../data/pi-pause.json'),
});

class PiSuspendedError extends Error {
    constructor(until, reason) {
        super(`Podcast Index paused until ${new Date(until).toISOString()} (${reason})`);
        this.code = 'PI_SUSPENDED';
        this.until = until;
    }
}

const isSuspended = (e) => e?.code === 'PI_SUSPENDED';

/** Trouble statuses: throttling and server-side failure (Cloudflare 52x included). */
function isTransientHttp(status) {
    return status === 403 || status === 429 || (status >= 500 && status <= 599);
}

function isTransientNetwork(message) {
    return /fetch failed|socket|timeout|aborted|UND_ERR|ECONNRESET|ETIMEDOUT|EAI_AGAIN|ENOTFOUND/i.test(String(message || ''));
}

/**
 * Retry-After (seconds or HTTP date) as a delay, at least `floorMs`, at most `capMs`.
 */
function parseRetryAfterMs(headerVal, floorMs, capMs = DEFAULTS.coolCapMs, now = Date.now()) {
    const floor = Math.max(0, Number(floorMs) || 0);
    const clamp = (ms) => Math.min(Math.max(ms, floor), capMs);
    if (headerVal == null || headerVal === '') return clamp(floor);
    const s = String(headerVal).trim();
    const secs = Number(s);
    if (Number.isFinite(secs) && secs >= 0) return clamp(secs * 1000);
    const when = Date.parse(s);
    return Number.isFinite(when) ? clamp(when - now) : clamp(floor);
}

function authHeaders(key, secret, nowMs) {
    const t = Math.floor(nowMs / 1000);
    return {
        'User-Agent': 'BoxLore/1.0 (github.com/boxcreate/boxlore; podcast-sync)',
        'X-Auth-Key': key,
        'X-Auth-Date': String(t),
        Authorization: crypto.createHash('sha1').update(key + secret + t).digest('hex'),
    };
}

/**
 * A client with its own rate, cooling and pause state. Stages use the default one below.
 * @param {{ key?: string, secret?: string, fetchImpl?: typeof fetch, now?: () => number,
 *   sleep?: (ms: number) => Promise<void>, config?: Partial<typeof DEFAULTS> }} [opts]
 */
function createClient(opts = {}) {
    const conf = { ...DEFAULTS, ...(opts.config || {}) };
    const key = opts.key ?? process.env.PODCAST_INDEX_API_KEY;
    const secret = opts.secret ?? process.env.PODCAST_INDEX_API_SECRET;
    const fetchImpl = opts.fetchImpl || ((...a) => fetch(...a));
    const now = opts.now || Date.now;
    const sleep = opts.sleep || ((ms) => new Promise((r) => setTimeout(r, ms)));

    const s = {
        calls: 0,
        rps: conf.maxRps,
        nextSlotAt: 0,
        coolUntil: 0,
        failStreak: 0,
        okStreak: 0,
        coolingMs: 0,
        pause: undefined, // { until, level, reason } once read; null when none
        probe: null,
        throttled: 0,
    };

    function readPause() {
        if (s.pause !== undefined) return s.pause;
        try {
            const p = JSON.parse(fs.readFileSync(conf.pauseFile, 'utf8'));
            s.pause = Number.isFinite(p?.until) ? p : null;
        } catch {
            s.pause = null;
        }
        return s.pause;
    }

    function writePause(p) {
        s.pause = p;
        try {
            if (p) {
                fs.mkdirSync(path.dirname(conf.pauseFile), { recursive: true });
                fs.writeFileSync(`${conf.pauseFile}.tmp`, `${JSON.stringify(p)}\n`);
                fs.renameSync(`${conf.pauseFile}.tmp`, conf.pauseFile);
            } else {
                fs.rmSync(conf.pauseFile, { force: true });
            }
        } catch (e) {
            log.warn(`PI pause file not written: ${e.message}`);
        }
    }

    function suspend(reason) {
        const prev = readPause();
        // Several workers can fail at once: one pause per trouble episode, never one level each.
        if (prev && prev.until > now()) return new PiSuspendedError(prev.until, prev.reason || reason);
        const level = prev ? Number(prev.level || 0) + 1 : 0;
        const until = now() + Math.min(conf.pauseBaseMs * 2 ** level, conf.pauseCapMs);
        writePause({ until, level, reason, since: prev?.since || now() });
        log.warn(`Podcast Index suspended until ${new Date(until).toISOString()} (${reason}); stages skip it until then`);
        return new PiSuspendedError(until, reason);
    }

    async function acquireSlot() {
        const t = now();
        const slot = Math.max(s.nextSlotAt, s.coolUntil, t);
        s.nextSlotAt = slot + 1000 / s.rps;
        if (slot > t) await sleep(slot - t);
    }

    /** One trouble signal: slow down, cool everyone off, maybe suspend. Returns the cooling delay. */
    function trouble(retryAfter) {
        s.failStreak++;
        s.okStreak = 0;
        s.throttled++;
        s.rps = Math.max(conf.minRps, s.rps / 2);
        const exp = Math.min(conf.coolBaseMs * 2 ** (s.failStreak - 1), conf.coolCapMs);
        const delay = retryAfter !== undefined ? parseRetryAfterMs(retryAfter, conf.coolBaseMs, conf.coolCapMs, now()) : exp;
        s.coolUntil = Math.max(s.coolUntil, now() + delay);
        s.coolingMs += delay;
        if (s.failStreak >= conf.suspendAfter) throw suspend(`${s.failStreak} failed attempts in a row`);
        if (s.coolingMs > conf.suspendCoolingMs) throw suspend(`${Math.round(s.coolingMs / 60_000)} min of cooling off`);
        return delay;
    }

    function clean() {
        s.failStreak = 0;
        if (++s.okStreak >= conf.recoverAfter && s.rps < conf.maxRps) {
            s.rps = Math.min(conf.maxRps, s.rps * 1.25);
            s.okStreak = 0;
        }
    }

    /**
     * One HTTP attempt. Returns { data } or { trouble: string, retryAfter? }; throws on a hard error.
     * The pause is checked after waiting for the slot, right before the request: another worker may
     * have paused PI meanwhile, or a pause may have ended (then the probe runs first).
     */
    async function attempt(p, { probe = false } = {}) {
        await acquireSlot();
        if (!probe) await gate();
        s.calls++;
        let res;
        try {
            res = await fetchImpl(`${API_BASE}${p}`, { headers: authHeaders(key, secret, now()), signal: AbortSignal.timeout(conf.timeoutMs) });
        } catch (e) {
            if (isTransientNetwork(e.message) || e.name === 'TimeoutError' || e.name === 'AbortError') return { trouble: e.message || e.name };
            throw e;
        }
        if (isTransientHttp(res.status)) {
            await res.body?.cancel?.().catch?.(() => {});
            return { trouble: `HTTP ${res.status}`, retryAfter: res.headers?.get?.('retry-after') ?? undefined };
        }
        if (!res.ok) throw new Error(`PI API error: ${res.status}`);
        let data;
        try {
            data = await res.json();
        } catch {
            return { trouble: 'invalid JSON body' };
        }
        if (data && (data.status === 'false' || data.status === false)) return { trouble: data.description || data.error || 'status=false' };
        return { data };
    }

    /** After a pause has run out, one call at a time decides whether Podcast Index is back. */
    async function gate() {
        const p = readPause();
        if (!p) return;
        if (p.until > now()) throw new PiSuspendedError(p.until, p.reason || 'paused');
        if (s.probe) return s.probe;
        s.probe = (async () => {
            const r = await attempt('/stats/current', { probe: true });
            if (r.data) {
                log.info(`Podcast Index answers again; pause (level ${p.level}) cleared`);
                writePause(null);
                return;
            }
            throw suspend(`probe after pause: ${r.trouble}`);
        })().finally(() => { s.probe = null; });
        return s.probe;
    }

    async function apiGet(p) {
        for (let n = 1; n <= conf.maxAttempts; n++) {
            const r = await attempt(p); // checks the pause before every request
            if (r.data) {
                clean();
                return r.data;
            }
            const delay = trouble(r.retryAfter); // may throw PiSuspendedError
            if (n === conf.maxAttempts) throw new Error(`PI API ${r.trouble} on ${p} after ${n} attempts`);
            log.warn(`PI API ${r.trouble} on ${p}; cooling off ${Math.round(delay / 1000)}s at ${s.rps.toFixed(2)} req/s (attempt ${n}/${conf.maxAttempts})`);
        }
        throw new Error(`PI API exhausted retries for ${p}`);
    }

    return {
        apiGet,
        getApiCallCount: () => s.calls,
        stats: () => ({ calls: s.calls, rps: s.rps, throttled: s.throttled, coolingMs: s.coolingMs, paused: readPause() }),
        async episodesByFeedId(feedId, max) {
            const data = await apiGet(`/episodes/byfeedid?id=${feedId}&max=${max}`);
            return data.items || [];
        },
        async podcastByFeedId(feedId) {
            const data = await apiGet(`/podcasts/byfeedid?id=${feedId}`);
            const feed = data.feed;
            return !feed || (Array.isArray(feed) && feed.length === 0) || !feed.id ? null : feed;
        },
        async podcastByItunesId(itunesId) {
            const data = await apiGet(`/podcasts/byitunesid?id=${itunesId}`);
            const feed = data.feed;
            return !feed || (Array.isArray(feed) && feed.length === 0) || !feed.id ? null : feed;
        },
    };
}

const client = createClient();

function assertEnv() {
    if (!process.env.PODCAST_INDEX_API_KEY || !process.env.PODCAST_INDEX_API_SECRET) {
        log.error('Missing PODCAST_INDEX_API_KEY or PODCAST_INDEX_API_SECRET');
        process.exit(1);
    }
}

/** One line for run reports: calls, final rate, cooling, and any pause. */
function summary() {
    const st = client.stats();
    const pause = st.paused && st.paused.until > Date.now() ? ` · paused until ${new Date(st.paused.until).toISOString().slice(11, 16)} UTC` : '';
    return `PI ${st.calls} calls · ${st.rps.toFixed(1)} req/s · ${st.throttled} throttled · ${Math.round(st.coolingMs / 1000)}s cooling${pause}`;
}

module.exports = {
    assertEnv,
    getApiCallCount: client.getApiCallCount,
    episodesByFeedId: client.episodesByFeedId,
    podcastByFeedId: client.podcastByFeedId,
    podcastByItunesId: client.podcastByItunesId,
    stats: client.stats,
    summary,
    createClient,
    PiSuspendedError,
    isSuspended,
    // Exported for unit tests / ops tooling
    isTransientHttp,
    isTransientNetwork,
    parseRetryAfterMs,
    DEFAULTS,
    RATE_LIMIT_RPS: DEFAULTS.maxRps,
    MAX_RETRIES: DEFAULTS.maxAttempts,
};
