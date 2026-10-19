'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const turso = require('./turso');

describe('turso.mapArgType', () => {
    it('binds scalars by type', () => {
        assert.deepEqual(turso.mapArgType(42), { type: 'integer', value: '42' });
        assert.deepEqual(turso.mapArgType(1.5), { type: 'float', value: '1.5' });
        assert.deepEqual(turso.mapArgType('123'), { type: 'integer', value: '123' });
        assert.deepEqual(turso.mapArgType('Show'), { type: 'text', value: 'Show' });
        assert.deepEqual(turso.mapArgType(''), { type: 'null', value: null });
        assert.deepEqual(turso.mapArgType(null), { type: 'null', value: null });
        assert.deepEqual(turso.mapArgType(undefined), { type: 'null', value: null });
    });

    it('binds deliberately serialized JSON as text', () => {
        const persons = JSON.stringify([{ name: 'Host', role: 'host' }]);
        assert.deepEqual(turso.mapArgType(persons), { type: 'text', value: persons });
    });

    it('refuses objects instead of storing "[object Object]"', () => {
        assert.throws(() => turso.mapArgType({ url: 'https://example.com/ch.json' }), /refusing to bind a Object object/);
        assert.throws(() => turso.mapArgType([{ name: 'Host' }]), /refusing to bind an array/);
        assert.throws(() => turso.mapArgType(new Date(0)), /refusing to bind a Date object/);
        assert.throws(() => turso.mapArgType(Object.create(null)), /refusing to bind a plain object/);
        assert.throws(() => turso.mapArgType(() => 1), /refusing to bind/);
    });

    it('refuses an object anywhere in a batch before anything is sent', async () => {
        const realFetch = global.fetch;
        let sent = 0;
        global.fetch = async () => {
            sent++;
            throw new Error('should not be called');
        };
        try {
            await assert.rejects(
                turso.batch([
                    { sql: 'UPDATE podcasts SET title = ? WHERE id = ?', args: ['Fine', 1] },
                    { sql: 'UPDATE podcasts SET latest_ep_persons = ? WHERE id = ?', args: [{ name: 'Host' }, 2] },
                ]),
                /refusing to bind/,
            );
            assert.equal(sent, 0);
        } finally {
            global.fetch = realFetch;
        }
    });
});
