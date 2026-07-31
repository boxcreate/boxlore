'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
    existingItunesAmong,
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
