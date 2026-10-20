'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const { loadServedIds, loadServedOwners, resolveIdentities } = require('./episode-identity');
const { stableUUID } = require('./qdrant');
const { recipeId } = require('./episode-recipe-id');
const book = require('./episode-book');

const SID = 3391039;
const ep = (id, guid, extra = {}) => ({ id, guid, enclosureUrl: `https://cdn.example/${id}.mp3`, title: `Ep ${id}`, datePublished: 1790000000 + id, ...extra });

describe('resolveIdentities', () => {
    it('names a new episode by its PI ID and queues its book row', () => {
        const r = resolveIdentities(SID, [ep(61, ' g-61 ')], new Map());
        assert.deepEqual(r.items[0].stamps, { eid: recipeId(SID, 'g-61'), catalog_key: 'g-61' });
        assert.equal(r.items[0].servedId, 61);
        assert.equal(r.items[0].uuid, stableUUID(61));
        assert.deepEqual(r.newRows.map((x) => [x.key, x.servedId, x.piId]), [['g-61', 61, 61]]);
    });

    it('keeps the served name when Podcast Index changed the ID, and records the new ID as an alias', () => {
        const r = resolveIdentities(SID, [ep(90, 'g-61')], new Map([['g-61', 61]]));
        assert.equal(r.items[0].servedId, 61);
        assert.equal(r.items[0].uuid, stableUUID(61), 'the existing point is found, nothing is re-embedded');
        assert.deepEqual(r.newRows, []);
        assert.deepEqual(r.aliases, [{ key: 'g-61', eid: recipeId(SID, 'g-61'), piId: 90 }]);
        assert.equal(r.renamed, 1);
    });

    it('keeps the first of two items with one key, and marks keyless items', () => {
        const r = resolveIdentities(SID, [ep(1, 'same'), ep(2, 'same'), { id: 3, guid: ' ', enclosureUrl: '' }], new Map());
        assert.deepEqual(r.items.map((i) => i.piId), [1, 3]);
        assert.equal(r.duplicates, 1);
        assert.deepEqual(r.items[1].stamps, { eid_missing: true });
        assert.equal(r.items[1].uuid, stableUUID(3), 'keyless items keep today\'s naming');
    });
});

describe('a changed GUID under the same Podcast Index ID', () => {
    it('defers the new key instead of taking a served ID from another episode', () => {
        const owners = new Map([[61, `${SID}\u0000old-guid`]]);
        const r = resolveIdentities(SID, [ep(61, 'new-guid'), ep(62, 'other')], new Map(), owners);
        assert.equal(r.deferred, 1);
        assert.deepEqual(r.items[0], { raw: r.items[0].raw, piId: 61, servedId: 61, uuid: stableUUID(61), stamps: { eid_shared: SID }, deferred: true });
        assert.deepEqual(r.newRows.map((x) => x.key), ['other'], 'the rest of the show is still recorded');
    });

    it('marks an episode a duplicate feed already holds with the owning show', () => {
        const owners = new Map([[61, `7650363\u0000g`]]);
        const r = resolveIdentities(5893516, [ep(61, 'g')], new Map(), owners);
        assert.equal(r.deferred, 1);
        assert.deepEqual(r.items[0].stamps, { eid_shared: 7650363 });
        assert.deepEqual(r.newRows, []);
    });

    it('is not a clash when the ID is held by the same key', () => {
        const owners = new Map([[61, `${SID}\u0000g`]]);
        const r = resolveIdentities(SID, [ep(61, 'g')], new Map([['g', 61]]), owners);
        assert.equal(r.deferred, 0);
        assert.deepEqual(r.items[0].stamps, { eid: recipeId(SID, 'g'), catalog_key: 'g' });
    });
});

describe('loadServedIds', () => {
    it('reads served IDs for the given keys, digit-only keys included', async () => {
        const raw = new DatabaseSync(':memory:');
        raw.exec('CREATE TABLE podcasts (id INTEGER PRIMARY KEY); CREATE TABLE ep_vec_tip_queue (podcast_id INTEGER PRIMARY KEY)');
        const unwrap = (args = []) => args.map((a) => (a && typeof a === 'object' && 'value' in a ? a.value : a));
        const db = {
            text: (v) => ({ value: String(v) }),
            query: async (sql, args) => {
                const st = raw.prepare(sql);
                if (!st.columns().length) { st.run(...unwrap(args)); return []; }
                return st.all(...unwrap(args)).map((r) => Object.values(r));
            },
            atomic: async (stmts) => {
                raw.exec('BEGIN');
                try { for (const s of stmts) raw.prepare(s.sql).run(...unwrap(s.args)); raw.exec('COMMIT'); return { ok: true, error: null }; } catch (e) { raw.exec('ROLLBACK'); return { ok: false, error: e.message }; }
            },
        };
        await book.ensureBookSchema(db);
        await book.bootstrapShow(db, SID, [
            { key: '00123', eid: recipeId(SID, '00123'), piId: 61, servedId: 61 },
            { key: 'b', eid: recipeId(SID, 'b'), piId: 62, servedId: 62 },
        ], { now: 1, source: 'pi' });
        const m = await loadServedIds(db, SID, ['00123', 'b', 'missing']);
        assert.deepEqual([...m.entries()].sort(), [['00123', 61], ['b', 62]]);
        const [[source]] = await db.query('SELECT source FROM episode_book LIMIT 1');
        assert.equal(source, 'pi');
        const owners = await loadServedOwners(db, [61, 62, 99]);
        assert.deepEqual([...owners.entries()].sort(), [[61, `${SID}\u000000123`], [62, `${SID}\u0000b`]]);
    });
});
