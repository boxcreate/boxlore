'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { FIELD, COLLECTIONS, DAY_S, dayStartSec, planBatches, staleFilter, stamp, summary } = require('./charted-at');

const DAY = 1_792_627_200; // a UTC midnight, seconds
const ms = (s) => s * 1000;

test('dayStartSec: the UTC day a time falls in', () => {
    assert.equal(dayStartSec(ms(DAY)), DAY);
    assert.equal(dayStartSec(ms(DAY + DAY_S - 1)), DAY);
    assert.equal(dayStartSec(ms(DAY + DAY_S)), DAY + DAY_S);
});

test('planBatches: shows grouped by their last chart day, newest first, in bounded chunks', () => {
    const shows = {
        1: { s: ms(DAY + 3600) },
        2: { s: ms(DAY + 7200) },
        3: { s: ms(DAY - 2 * DAY_S) },
        4: {},
        5: { s: ms(DAY + 10) },
    };
    const pods = [{ id: '5' }, { id: '1' }, { id: '3' }, { id: '4' }, { id: '2' }, { id: 'x' }, { id: '0' }];
    assert.deepEqual(planBatches(pods, shows, 2), [
        { value: DAY, ids: [1, 2] },
        { value: DAY, ids: [5] },
        { value: DAY - 2 * DAY_S, ids: [3] },
    ], 'shows without a chart day or a valid ID are skipped');
    assert.deepEqual(planBatches(null, shows), []);
});

test('staleFilter: only points of these shows with no date, or one older than the refresh window', () => {
    assert.deepEqual(staleFilter('podcast_id', [7, 8], DAY, 7 * DAY_S), {
        must: [{ key: 'podcast_id', match: { any: [7, 8] } }],
        should: [{ is_empty: { key: FIELD } }, { key: FIELD, range: { lt: DAY - 7 * DAY_S } }],
    });
});

test('both collections are stamped, episodes by podcast_id and shows by id', () => {
    assert.deepEqual(COLLECTIONS.map((c) => c.showKey), ['podcast_id', 'id']);
});

function fakeQdrant(counts, failOn = null) {
    const calls = [];
    return {
        calls,
        countPoints: async (name, filter) => {
            calls.push(['count', name, filter]);
            if (failOn && failOn(name, filter)) throw new Error('boom');
            return counts[name] ?? 0;
        },
        setPayloadByFilter: async (name, payload, filter, wait) => {
            calls.push(['set', name, payload, filter, wait]);
        },
    };
}

const COLS = [{ name: 'episodes', showKey: 'podcast_id' }, { name: 'podcasts', showKey: 'id' }];

test('stamp: counts, then writes the day with wait; skips writes where nothing is stale', async () => {
    const q = fakeQdrant({ episodes: 40, podcasts: 0 });
    const r = await stamp(q, [{ value: DAY, ids: [1, 2] }], { collections: COLS, refreshSec: 0 });
    assert.deepEqual(r, { batches: 1, failed: 0, points: { episodes: 40, podcasts: 0 }, dry: false });
    const sets = q.calls.filter((c) => c[0] === 'set');
    assert.equal(sets.length, 1, 'no write for a collection with nothing to stamp');
    assert.deepEqual(sets[0].slice(1, 3), ['episodes', { [FIELD]: DAY }]);
    assert.deepEqual(sets[0][3], staleFilter('podcast_id', [1, 2], DAY, 0));
    assert.equal(sets[0][4], true);
    assert.equal(summary(r), 'charted_at stamped: episodes 40, podcasts 0 points in 1 batches');
});

test('stamp: a dry run only counts; a failed call is counted and the rest still run', async () => {
    const dry = fakeQdrant({ episodes: 5, podcasts: 1 });
    const r = await stamp(dry, [{ value: DAY, ids: [1] }], { collections: COLS, refreshSec: 7 * DAY_S, dry: true });
    assert.equal(dry.calls.filter((c) => c[0] === 'set').length, 0);
    assert.match(summary(r), /^charted_at dry run: episodes 5, podcasts 1 points/);

    const warned = [];
    const q = fakeQdrant({ episodes: 3, podcasts: 1 }, (name, f) => name === 'episodes' && f.must[0].match.any[0] === 1);
    const out = await stamp(q, [{ value: DAY, ids: [1] }, { value: DAY, ids: [2] }], {
        collections: COLS, refreshSec: 0, warn: (m) => warned.push(m),
    });
    assert.deepEqual([out.failed, out.points], [1, { episodes: 3, podcasts: 2 }]);
    assert.equal(q.calls.filter((c) => c[0] === 'set').length, 3);
    assert.match(warned[0], /episodes: 1 shows at \d{4}-\d{2}-\d{2} not stamped \(boom\)/);
    assert.match(summary(out), /, 1 failed calls$/);
});
