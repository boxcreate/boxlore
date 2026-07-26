'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { fetchAllPaged } = require('./turso-page');

describe('fetchAllPaged', () => {
    it('pages until empty and concatenates', async () => {
        const calls = [];
        const data = [
            [[1, 'a'], [2, 'b']],
            [[3, 'c']],
            [],
        ];
        let i = 0;
        const out = await fetchAllPaged(
            {
                execute: async (sql, args) => {
                    calls.push({ sql, args });
                    return { page: i };
                },
                rows: () => data[i++] || [],
            },
            {
                pageSize: 2,
                buildPage: (after, limit) => ({
                    sql: 'SELECT id FROM t WHERE id > ? LIMIT ?',
                    args: [after ?? 0, limit],
                }),
                rowId: (row) => row[0],
            },
        );
        assert.deepEqual(out, [[1, 'a'], [2, 'b'], [3, 'c']]);
        assert.equal(calls.length, 2); // third empty not needed when last page < limit
        assert.deepEqual(calls[0].args, [0, 2]);
        assert.deepEqual(calls[1].args, [2, 2]);
    });

    it('honors maxRows across pages', async () => {
        const pages = {
            '0:3': [[1], [2], [3]],
            '3:1': [[4], [5]], // server returns extra; client must slice to limit 1
        };
        const out = await fetchAllPaged(
            {
                execute: async (_sql, args) => ({ args }),
                rows: (res) => {
                    const [after, limit] = res.args;
                    return pages[`${after}:${limit}`] || [];
                },
            },
            {
                pageSize: 3,
                maxRows: 4,
                buildPage: (after, limit) => ({
                    sql: 'x',
                    args: [after == null ? 0 : after, limit],
                }),
                rowId: (row) => row[0],
            },
        );
        assert.deepEqual(out, [[1], [2], [3], [4]]);
    });

    it('stops on first empty page', async () => {
        const out = await fetchAllPaged(
            {
                execute: async () => ({}),
                rows: () => [],
            },
            {
                pageSize: 10,
                buildPage: () => ({ sql: 'x', args: [] }),
                rowId: (row) => row[0],
            },
        );
        assert.deepEqual(out, []);
    });
});
