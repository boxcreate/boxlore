'use strict';

const { describe, it, beforeEach, afterEach, mock } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const qdrant = require('./qdrant');
const state = require('./state');
const turso = require('./turso');
const { trimEpisodeCaps } = require('./trim-episode-caps');

function points(podcastId, n) {
    return Array.from({ length: n }, (_, i) => ({
        id: `${podcastId}-${i}`,
        payload: { podcast_id: podcastId, published_date: 1_000_000 - i, id: podcastId * 1000 + i },
    }));
}

describe('nightly cap trim', () => {
    let deleted;
    let saved;
    let stateNow;

    beforeEach(() => {
        process.env.SYNC_ATTRIBUTION_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'attr-trim-'));
        deleted = [];
        saved = null;
        stateNow = { version: 2, shows: { 1: { k: 50 } } };
        // Show 1 earned 50 and is off the US/GB charts today; show 2 charts at 20 with 25 points.
        mock.method(qdrant, 'scrollAll', async () => [...points(1, 50), ...points(2, 25)]);
        mock.method(qdrant, 'payloadsByIds', async () => new Map());
        mock.method(qdrant, 'deleteByIds', async (_c, ids) => deleted.push(...ids));
        mock.method(state, 'load', () => stateNow);
        mock.method(state, 'save', (st) => { saved = JSON.parse(JSON.stringify(st)); });
        mock.method(turso, 'assertEnv', () => {});
    });

    afterEach(() => {
        mock.restoreAll();
        fs.rmSync(process.env.SYNC_ATTRIBUTION_DIR, { recursive: true, force: true });
        delete process.env.SYNC_ATTRIBUTION_DIR;
    });

    it('keeps an earned cap and trims everyone else to their chart cap', async () => {
        const out = await trimEpisodeCaps({ loadCaps: async () => new Map([['1', 20], ['2', 20]]) });
        assert.equal(out.trimmedShows, 1);
        assert.deepEqual(out.trimmedShowIds, ['2'], 'remediate-qdrant re-flags these');
        assert.equal(out.deletedPoints, 5);
        assert.equal(out.keptEarned, 1);
        assert.ok(deleted.every((id) => id.startsWith('2-')), 'only show 2 loses points');
        assert.deepEqual(deleted, ['2-20', '2-21', '2-22', '2-23', '2-24'], 'the oldest go first');
        assert.equal(saved.shows['2'].k, 20, 'today\'s chart cap is remembered');
        assert.equal(saved.shows['1'].k, 50);
    });

    it('trims nothing when the cap lookup fails (it used to cut every show to 20)', async () => {
        const out = await trimEpisodeCaps({ loadCaps: async () => { throw new Error('sqld down'); } });
        assert.equal(out.failed, true);
        assert.deepEqual(out.trimmedShowIds, []);
        assert.equal(out.deletedPoints, 0);
        assert.deepEqual(deleted, []);
        assert.equal(saved, null);
    });

    it('a dry run deletes and saves nothing but reports the excess', async () => {
        const out = await trimEpisodeCaps({ dryRun: true, loadCaps: async () => new Map([['2', 20]]) });
        assert.equal(out.excessPoints, 5);
        assert.equal(out.deletedPoints, 0);
        assert.deepEqual(deleted, []);
        assert.equal(saved, null);
    });
});

describe('typed text binding', () => {
    it('keeps digit-only catalog keys as text when asked', () => {
        assert.deepEqual(turso.mapArgType('00123'), { type: 'integer', value: '00123' }, 'unchanged default');
        assert.deepEqual(turso.mapArgType(turso.text('00123')), { type: 'text', value: '00123' });
        assert.deepEqual(turso.mapArgType(turso.text('')), { type: 'text', value: '' });
        assert.deepEqual(turso.mapArgType(turso.text(null)), { type: 'null', value: null });
        assert.deepEqual(turso.mapArgType(turso.text(42)), { type: 'text', value: '42' });
    });
});
