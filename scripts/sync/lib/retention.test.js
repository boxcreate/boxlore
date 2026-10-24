'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { DAY_S, retentionSteps, archiveSplit, expiryFilter, archiveShows, enforce, summary } = require('./retention');

const NOW = 1_792_800_000;

test('retention steps: the configured days, then 60 and 30 when shorter', () => {
    assert.deepEqual(retentionSteps(90), [90, 60, 30]);
    assert.deepEqual(retentionSteps(60), [60, 30]);
    assert.deepEqual(retentionSteps(45), [45, 30]);
    assert.deepEqual(retentionSteps(30), [30]);
});

test('archiveSplit keeps the newest episodes by published date; undated ones go first', () => {
    const pts = [{ id: 'a', date: 10 }, { id: 'b', date: 30 }, { id: 'c' }, { id: 'd', date: 20 }];
    assert.deepEqual(archiveSplit(pts, 2), { keep: ['b', 'd'], drop: ['a', 'c'] });
    assert.deepEqual(archiveSplit(pts, 9), { keep: ['b', 'd', 'a', 'c'], drop: [] });
    assert.deepEqual(archiveSplit(null, 5), { keep: [], drop: [] });
});

test('expiryFilter matches only dated points last charted before the cutoff', () => {
    assert.deepEqual(expiryFilter(NOW - 90 * DAY_S), { must: [{ key: 'charted_at', range: { lt: NOW - 90 * DAY_S } }] });
});

function fakeQdrant({ points = [], counts = {}, totals = [] } = {}) {
    const calls = [];
    let totalIdx = 0;
    return {
        calls,
        scrollAll: async (c, filter, fields, page) => { calls.push(['scroll', c, filter, fields, page]); return points; },
        deleteByIds: async (c, ids, wait) => { calls.push(['deleteIds', c, ids, wait]); },
        deleteByFilter: async (c, filter, wait) => { calls.push(['deleteFilter', c, filter.must[0].range.lt, wait]); },
        countPoints: async (c, filter) => {
            if (!filter) return totals[Math.min(totalIdx++, totals.length - 1)] ?? 0;
            const days = Math.round((NOW - filter.must[0].range.lt) / DAY_S);
            return counts[`${c}:${days}`] ?? 0;
        },
    };
}

test('archiveShows deletes all but each show\'s newest episodes, and reports per show', async () => {
    const pt = (id, pid, date, eid) => ({ id, payload: { podcast_id: pid, published_date: date, id: eid } });
    const q = fakeQdrant({ points: [pt('p1', 7, 100, 1), pt('p2', 7, 300, 2), pt('p3', 7, 200, 3), pt('p4', 8, 50, 4), pt('x', 99, 1, 5)] });
    const out = await archiveShows(q, [7, 8, 9], { keep: 2, collection: 'episodes' });
    assert.deepEqual(q.calls[0].slice(1), ['episodes', { must: [{ key: 'podcast_id', match: { any: [7, 8, 9] } }] }, ['podcast_id', 'published_date', 'id'], 1000]);
    assert.deepEqual(q.calls.filter((c) => c[0] === 'deleteIds').map((c) => c.slice(1)), [['episodes', ['p1'], true]]);
    assert.deepEqual(out.get('7'), { kept: 2, dropped: [{ id: 'p1', date: 100, eid: 1 }] });
    assert.deepEqual(out.get('8'), { kept: 1, dropped: [] });
    assert.deepEqual(out.get('9'), { kept: 0, dropped: [] });
    assert.equal(out.has('99'), false, 'points of other shows are never touched');

    const dry = fakeQdrant({ points: [pt('p1', 7, 1, 1), pt('p2', 7, 2, 2)] });
    await archiveShows(dry, [7], { keep: 1, dry: true });
    assert.equal(dry.calls.filter((c) => c[0] === 'deleteIds').length, 0);
    assert.equal((await archiveShows(dry, [], { keep: 1 })).size, 0);
});

test('enforce, on: expired points deleted from both collections; within the ceiling it stops at 90 days', async () => {
    const q = fakeQdrant({ counts: { 'episodes:90': 40, 'podcasts:90': 3 }, totals: [1_500_000] });
    const expired = [];
    const r = await enforce(q, {
        mode: 'on', nowSec: NOW, steps: [90, 60, 30], ceiling: 2_000_000, alert: 1_800_000,
        collections: ['episodes', 'podcasts'], onExpired: async (c) => { expired.push(c); },
    });
    assert.deepEqual(r, { mode: 'on', retentionDays: 90, expired: { episodes: 40, podcasts: 3 }, total: 1_500_000, overAlert: false, overCeiling: false });
    assert.deepEqual(q.calls.map((c) => c.slice(0, 3)), [['deleteFilter', 'episodes', NOW - 90 * DAY_S], ['deleteFilter', 'podcasts', NOW - 90 * DAY_S]]);
    assert.deepEqual(expired, ['episodes', 'podcasts'], 'attribution sees what expires before it goes');
});

test('enforce, on: over the ceiling it steps down to 60 then 30 days, and says when it still does not fit', async () => {
    const q = fakeQdrant({ counts: { 'episodes:90': 1, 'episodes:60': 500, 'episodes:30': 900 }, totals: [2_100_000, 2_050_000, 2_010_000] });
    const r = await enforce(q, { mode: 'on', nowSec: NOW, steps: [90, 60, 30], ceiling: 2_000_000, alert: 1_800_000, collections: ['episodes', 'podcasts'] });
    assert.deepEqual([r.retentionDays, r.expired.episodes, r.total, r.overCeiling, r.overAlert], [30, 1401, 2_010_000, true, true]);
    assert.deepEqual(q.calls.filter((c) => c[1] === 'episodes').map((c) => Math.round((NOW - c[2]) / DAY_S)), [90, 60, 30]);
});

test('enforce, count: counts what would expire, deletes nothing, never steps down', async () => {
    const q = fakeQdrant({ counts: { 'episodes:90': 12 }, totals: [2_500_000] });
    const r = await enforce(q, { mode: 'count', nowSec: NOW, steps: [90, 60, 30], ceiling: 2_000_000, alert: 1_800_000, collections: ['episodes', 'podcasts'] });
    assert.deepEqual(q.calls, []);
    assert.deepEqual([r.retentionDays, r.expired.episodes, r.overCeiling], [90, 12, true]);
});

test('summary: one report line per mode', () => {
    const r = { retentionDays: 90, expired: { episodes: 0, podcasts: 0 }, total: 860_000, overAlert: false, overCeiling: false };
    assert.equal(summary('count', { archived: 2300, kept: 9000, trimmed: 26000 }, r),
        'retention count: would keep 2300 departed shows (9000 episodes; 26000 older ones would go) · past 90 d: episodes 0, podcasts 0 · 860000 episode points');
    assert.equal(summary('on', { archived: 1, kept: 5, trimmed: 15 }, { ...r, total: 1_900_000, overAlert: true }),
        'retention on: kept 1 departed shows (5 episodes; 15 older ones deleted) · expired 90 d: episodes 0, podcasts 0 · 1900000 episode points (over the alert level)');
    assert.equal(summary('off'), 'retention off');
});

test('enforce records expired points only after the delete is confirmed', async () => {
    const order = [];
    const q = fakeQdrant({ counts: { 'episodes:90': 4 }, totals: [10] });
    const del = q.deleteByFilter;
    q.deleteByFilter = async (...a) => { order.push('delete'); return del(...a); };
    await enforce(q, {
        mode: 'on', nowSec: NOW, steps: [90], ceiling: 100, alert: 50, collections: ['episodes', 'podcasts'],
        onExpired: async () => { order.push('count'); return () => order.push('record'); },
    });
    assert.deepEqual(order, ['count', 'delete', 'record']);

    const failing = fakeQdrant({ counts: { 'episodes:90': 4 }, totals: [10] });
    failing.deleteByFilter = async () => { throw new Error('qdrant down'); };
    let recorded = false;
    await assert.rejects(enforce(failing, {
        mode: 'on', nowSec: NOW, steps: [90], ceiling: 100, alert: 50, collections: ['episodes'],
        onExpired: async () => () => { recorded = true; },
    }), /qdrant down/);
    assert.equal(recorded, false, 'a failed delete records nothing');
});

test('summary says when expiry was held back', () => {
    const r = { retentionDays: 90, expired: { episodes: 3 }, total: 9, overAlert: false, overCeiling: false };
    assert.match(summary('on', {}, r, '2 date stamp calls failed'), / · expiry held: 2 date stamp calls failed$/);
});

test('retention settings accept only whole numbers; anything else keeps the default', () => {
    const keys = ['RETENTION_DAYS', 'ARCHIVE_EPISODES', 'QDRANT_POINTS_CEILING', 'QDRANT_POINTS_ALERT', 'CHARTED_AT_REFRESH_DAYS'];
    const saved = Object.fromEntries(keys.map((k) => [k, process.env[k]]));
    const fresh = () => {
        delete require.cache[require.resolve('./config')];
        return require('./config');
    };
    try {
        Object.assign(process.env, { RETENTION_DAYS: '1e3', ARCHIVE_EPISODES: '7', QDRANT_POINTS_CEILING: '2e6', QDRANT_POINTS_ALERT: ' 1500000 ', CHARTED_AT_REFRESH_DAYS: '-3' });
        let c = fresh();
        assert.deepEqual([c.RETENTION_DAYS, c.ARCHIVE_EPISODES, c.QDRANT_POINTS_CEILING, c.QDRANT_POINTS_ALERT, c.CHARTED_AT_REFRESH_DAYS], [90, 7, 2_000_000, 1_500_000, 7]);
        Object.assign(process.env, { RETENTION_DAYS: '10', ARCHIVE_EPISODES: '0' });
        c = fresh();
        assert.deepEqual([c.RETENTION_DAYS, c.ARCHIVE_EPISODES], [30, 1], 'minimums still apply');
    } finally {
        for (const k of keys) {
            if (saved[k] === undefined) delete process.env[k];
            else process.env[k] = saved[k];
        }
        fresh();
    }
});
