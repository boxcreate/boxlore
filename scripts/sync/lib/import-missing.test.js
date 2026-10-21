'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
    existingItunesAmong,
    lookupItunesIds,
    reuseMissingIdsFile,
    EXISTENCE_IN_CHUNK,
} = require('./import-missing');

describe('reuseMissingIdsFile', () => {
    it('returns null for empty file', () => {
        assert.equal(reuseMissingIdsFile([]), null);
        assert.equal(reuseMissingIdsFile(null), null);
    });

    it('reuses non-empty fresh file', () => {
        assert.deepEqual(
            reuseMissingIdsFile(['1', '2'], { mtimeMs: 1000, nowMs: 2000, maxAgeMs: 5000 }),
            ['1', '2'],
        );
    });

    it('rejects stale file', () => {
        assert.equal(
            reuseMissingIdsFile(['1'], { mtimeMs: 0, nowMs: 10_000_000, maxAgeMs: 1000 }),
            null,
        );
    });
});

describe('existingItunesAmong', () => {
    it('chunks IN queries and normalizes ids', async () => {
        const calls = [];
        const fake = {
            execute: async (sql, args) => {
                calls.push(args);
                return { rows: [[args[0]]] };
            },
            rows: (r) => r.rows,
        };
        const ids = Array.from({ length: EXISTENCE_IN_CHUNK + 3 }, (_, i) => String(i + 1));
        const set = await existingItunesAmong(fake, ids);
        assert.equal(calls.length, 2);
        assert.ok(set.has('1'));
        assert.equal(typeof calls[0][0], 'number');
    });
});

describe('lookupItunesIds', () => {
    it('separates found feeds, "no such feed" and failed lookups', async () => {
        const errors = [];
        let ticks = 0;
        const out = await lookupItunesIds(['1', '2', '3'], async (id) => {
            if (id === '1') return { id: 501, title: 'Found' };
            if (id === '2') return null;
            throw new Error('PI API status=521');
        }, { onError: (id, e) => errors.push([id, e.message]), onEach: () => ticks++ });
        assert.deepEqual(out.found.map((f) => [f.itunesId, f.feed.id]), [['1', 501]]);
        assert.deepEqual(out.notFoundIds, ['2']);
        assert.deepEqual(out.failedIds, ['3'], 'an outage is retried, never treated as not found');
        assert.deepEqual(errors, [['3', 'PI API status=521']]);
        assert.equal(ticks, 3);
    });
});

describe('lookupItunesIds onFound', () => {
    it('hands each feed over as it arrives, before the pass ends', async () => {
        const order = [];
        await lookupItunesIds(['1', '2', '3'], async (id) => {
            order.push(`lookup ${id}`);
            return id === '2' ? null : { id: Number(id) };
        }, { onFound: async ({ itunesId }) => { order.push(`write ${itunesId}`); } });
        assert.deepEqual(order, ['lookup 1', 'write 1', 'lookup 2', 'lookup 3', 'write 3']);
    });
});
