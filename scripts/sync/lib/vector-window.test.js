'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { planWindow, dueForDepth, clearDepth, pruneIsSafe, handoffList, DAY_MS } = require('./vector-window');
const cfg = require('./config');
const vectorSpool = require('./vector-spool');
const { rememberEarnedCaps, effectiveCap } = require('./episode-caps');

const opts = { now: 1_000 * DAY_MS, newestEpisodes: 5, depthDays: 7 };

describe('newest first for shows with no vectors', () => {
    it('starts a newest-first phase for a show with no vectors and keeps it until the depth date', () => {
        const rec = {};
        assert.deepEqual(planWindow(rec, 0, 50, opts), { cap: 5, phase: 'newest' });
        assert.equal(rec.d, opts.now + 7 * DAY_MS);
        // Later runs inside the phase don't need the point count.
        assert.deepEqual(planWindow(rec, -1, 50, { ...opts, now: opts.now + 6 * DAY_MS }), { cap: 5, phase: 'newest' });
        assert.deepEqual(planWindow(rec, -1, 50, { ...opts, now: opts.now + 7 * DAY_MS }), { cap: 50, phase: 'depth' });
    });

    it('leaves shows that already have vectors, unknown counts and small caps alone', () => {
        assert.deepEqual(planWindow({}, 12, 20, opts), { cap: 20, phase: 'normal' });
        assert.deepEqual(planWindow({}, -1, 20, opts), { cap: 20, phase: 'normal' });
        assert.deepEqual(planWindow({}, 0, 5, opts), { cap: 5, phase: 'normal' });
        assert.deepEqual(planWindow({}, 0, 20, { ...opts, depthDays: 0 }), { cap: 20, phase: 'normal' });
    });

    it('flags each show for its full window once, when its depth date has passed', () => {
        const st = { shows: { a: { d: opts.now - 1 }, b: { d: opts.now + DAY_MS }, c: { k: 50 } } };
        assert.deepEqual(dueForDepth(st, opts.now), ['a']);
        clearDepth(st, ['a']);
        assert.equal('d' in st.shows.a, false);
        assert.equal(st.shows.b.d, opts.now + DAY_MS);
        assert.deepEqual(dueForDepth(st, opts.now), []);
    });

    it('keeps the depth date until the flag is stored, so a failed UPDATE retries next run', () => {
        const st = { shows: { a: { d: opts.now - 1 } } };
        assert.deepEqual(dueForDepth(st, opts.now), ['a']);
        // The UPDATE failed: nothing was cleared, and the saved state still has the date.
        assert.equal(st.shows.a.d, opts.now - 1);
        assert.deepEqual(dueForDepth(st, opts.now), ['a']);
    });
});

describe('never prune against a partial window', () => {
    it('prunes only a full window', () => {
        assert.equal(pruneIsSafe(50, 50), true);
        assert.equal(pruneIsSafe(1, 50), false);
        assert.equal(pruneIsSafe(5, 5), true);
    });

    it('never hands stage 4 the one-item tip as the window', () => {
        const full = [{ id: 1 }, { id: 2 }];
        assert.equal(handoffList(full, 20), full);
        assert.equal(handoffList([], 20), null, 'empty full fetch: no handoff, stage 4 fetches itself');
        assert.equal(handoffList(null, 20), null);
    });
});

describe('retention-aware caps', () => {
    it('keeps the highest cap a show earned while its vectors are kept', () => {
        const st = { shows: { 7: {} } };
        assert.equal(rememberEarnedCaps(st, new Map([['7', 50], ['8', 20]])), 2);
        assert.equal(st.shows['7'].k, 50);
        // Off the US/GB charts (cap 20 today) or off every chart: still 50.
        assert.equal(effectiveCap('7', new Map([['7', 20]]), st), 50);
        assert.equal(effectiveCap('7', new Map(), st), 50);
        assert.equal(rememberEarnedCaps(st, new Map([['7', 20]])), 0);
        assert.equal(st.shows['7'].k, 50);
        // Never seen on a full-tier chart: the default.
        assert.equal(effectiveCap('9', new Map(), st), 20);
        assert.equal(effectiveCap('9', new Map(), null), 20);
    });
});

describe('vector spool', () => {
    const point = (id) => ({ id: `p${id}`, vector: [0.1, 0.2], payload: { id } });

    it('re-uploads a failed batch first, then removes it', async () => {
        const dir = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'spool-')), 'nested');
        assert.ok(vectorSpool.spool([point(1), point(2)], [{ pid: '7', ep: { id: 1 } }], dir));
        assert.equal(fs.statSync(dir).mode & 0o777, 0o700);
        const uploaded = [];
        const stored = [];
        const out = await vectorSpool.replay(async (pts) => uploaded.push(...pts), (metas) => stored.push(...metas), { dir });
        assert.deepEqual(out, { batches: 1, points: 2, dropped: 0, left: 0 });
        assert.deepEqual(uploaded.map((p) => p.id), ['p1', 'p2']);
        assert.deepEqual(stored, [{ pid: '7', ep: { id: 1 } }]);
        assert.deepEqual(fs.readdirSync(dir), []);
    });

    it('keeps batches when the upload fails again, and drops old or broken ones', async () => {
        const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'spool-'));
        vectorSpool.spool([point(1)], [], dir);
        vectorSpool.spool([point(2)], [], dir);
        const failed = await vectorSpool.replay(async () => { throw new Error('qdrant down'); }, null, { dir });
        assert.deepEqual(failed, { batches: 0, points: 0, dropped: 0, left: 2 });
        assert.equal(fs.readdirSync(dir).length, 2);

        // Older than both batches, so the replay reaches it before the first failed upload.
        fs.writeFileSync(path.join(dir, `${Date.now() - 60_000}-deadbeef.json`), '{not json');
        const broken = await vectorSpool.replay(async () => { throw new Error('qdrant down'); }, null, { dir });
        assert.equal(broken.dropped, 1, 'a broken file is dropped before any upload');
        assert.equal(fs.readdirSync(dir).length, 2);

        const now = Date.now() + (vectorSpool.MAX_AGE_DAYS + 1) * DAY_MS;
        const old = await vectorSpool.replay(async () => {}, null, { dir, now });
        assert.equal(old.dropped, 2);
        assert.deepEqual(fs.readdirSync(dir), []);
    });

    it('uses one spool folder whatever the working directory', () => {
        if (process.env.SYNC_SPOOL_DIR) return;
        assert.equal(cfg.VECTOR_SPOOL_DIR, path.resolve(__dirname, '../../data/vector-spool'));
    });

    it('does nothing for an empty batch or a missing folder', async () => {
        assert.equal(vectorSpool.spool([], [], os.tmpdir()), null);
        const out = await vectorSpool.replay(async () => {}, null, { dir: path.join(os.tmpdir(), 'no-such-spool-dir') });
        assert.deepEqual(out, { batches: 0, points: 0, dropped: 0, left: 0 });
    });
});
