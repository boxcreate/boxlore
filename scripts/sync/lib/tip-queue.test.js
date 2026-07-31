'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const tipQueue = require('./tip-queue');

describe('tip-queue', () => {
    it('exports table name and API', () => {
        assert.equal(tipQueue.TABLE, 'ep_vec_tip_queue');
        assert.equal(typeof tipQueue.ensureTable, 'function');
        assert.equal(typeof tipQueue.upsert, 'function');
        assert.equal(typeof tipQueue.listIds, 'function');
        assert.equal(typeof tipQueue.remove, 'function');
        assert.equal(typeof tipQueue.removeMany, 'function');
    });

    it('upsert/remove ignore invalid ids (no turso call)', async () => {
        let called = 0;
        const fake = {
            execute: async () => {
                called++;
                return { rows: [] };
            },
            rows: (r) => r.rows || [],
        };
        await tipQueue.upsert(fake, 'nope', 'ep');
        await tipQueue.remove(fake, 'x');
        await tipQueue.removeMany(fake, ['bad', 0, -1]);
        assert.equal(called, 0);
    });

    it('listIds maps rows to strings', async () => {
        const fake = {
            execute: async () => ({ rows: [[10], [20]] }),
            rows: (r) => r.rows,
        };
        const ids = await tipQueue.listIds(fake);
        assert.deepEqual(ids, ['10', '20']);
    });

    it('removeMany chunks valid ids', async () => {
        const seen = [];
        const fake = {
            execute: async (sql, args) => {
                seen.push({ sql, args });
                return { rows: [] };
            },
            rows: () => [],
        };
        await tipQueue.removeMany(fake, ['1', '1', 2, 'x']);
        assert.equal(seen.length, 1);
        assert.match(seen[0].sql, /DELETE FROM ep_vec_tip_queue/);
        assert.deepEqual(seen[0].args, [1, 2]);
    });
});
