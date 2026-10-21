'use strict';

const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const book = require('./episode-book');
const tipQueue = require('./tip-queue');
const { recipeId, catalogKey } = require('./episode-recipe-id');
const { stableUUID } = require('./qdrant');
const { recordWindow, loadWindow, sameWindow, RECIPE_ID_MIN } = require('./show-window');

const SID = 3391039;
const NOW = 1_792_000_000;

function sqliteDb() {
    const raw = new DatabaseSync(':memory:');
    raw.exec('CREATE TABLE podcasts (id INTEGER PRIMARY KEY, title TEXT)');
    raw.exec('CREATE TABLE ep_vec_tip_queue (podcast_id INTEGER PRIMARY KEY, tip_ep_id TEXT, enqueued_at INTEGER NOT NULL)');
    const toArgs = (args = []) => args.map((a) => (a && typeof a === 'object' && 'value' in a ? a.value : a));
    return {
        raw,
        text: (v) => ({ value: v === null || v === undefined ? null : String(v) }),
        query: async (sql, args) => {
            const st = raw.prepare(sql);
            if (!st.columns().length) {
                st.run(...toArgs(args));
                return [];
            }
            return st.all(...toArgs(args)).map((r) => Object.values(r));
        },
        atomic: async (stmts) => {
            raw.exec('BEGIN');
            try {
                for (const s of stmts) raw.prepare(s.sql).run(...toArgs(s.args));
                raw.exec('COMMIT');
                return { ok: true, error: null };
            } catch (e) {
                raw.exec('ROLLBACK');
                return { ok: false, error: e.message };
            }
        },
    };
}

/** A PI-shaped episode, newest first by `date`. */
const ep = (id, guid, date, extra = {}) => ({
    id, guid, title: `Episode ${guid}`, enclosureUrl: `https://cdn.example/${guid}.mp3`, description: `<p>About ${guid}</p>`,
    datePublished: date, duration: 600, image: `https://img.example/${guid}.jpg`, feedId: SID, ...extra,
});

describe('saved episode windows', () => {
    let db;
    beforeEach(async () => {
        db = sqliteDb();
        await book.ensureBookSchema(db);
    });

    it('records new episodes as book rows with descriptions, served under their PI IDs, and the window in list order', async () => {
        const list = [ep(103, 'c', 300), ep(102, 'b', 200), ep(101, 'a', 100)];
        const r = await recordWindow(db, SID, list, { cap: 20, gen: 5, now: NOW });
        assert.deepEqual([r.usable, r.count, r.newRows], [true, 3, 3]);
        const rows = db.raw.prepare('SELECT catalog_key, served_id, description, source FROM episode_book ORDER BY rid').all().map(Object.values);
        assert.deepEqual(rows, [['c', 103, '<p>About c</p>', 'pi'], ['b', 102, '<p>About b</p>', 'pi'], ['a', 101, '<p>About a</p>', 'pi']]);
        const win = db.raw.prepare('SELECT gen, complete, cap, rids FROM show_window WHERE sid = ?').get(SID);
        assert.deepEqual([win.gen, win.complete, win.cap], [5, 1, 20]);
        assert.equal(JSON.parse(win.rids).length, 3);

        const saved = await loadWindow(db, SID, 20, { now: NOW });
        assert.deepEqual(saved.items.map((it) => it.servedId), [103, 102, 101]);
        assert.deepEqual(saved.items[0].stamps, { eid: recipeId(SID, 'c'), catalog_key: 'c' });
        assert.equal(saved.items[0].uuid, stableUUID(103));
        assert.deepEqual(
            { title: saved.items[0].raw.title, url: saved.items[0].raw.enclosureUrl, date: saved.items[0].raw.datePublished, description: saved.items[0].raw.description, image: saved.items[0].raw.image },
            { title: 'Episode c', url: 'https://cdn.example/c.mp3', date: 300, description: '<p>About c</p>', image: 'https://img.example/c.jpg' },
        );
        assert.equal(saved.listLength, 3);
    });

    it('a known episode keeps its served ID; a new PI ID for it becomes an alias and marks the show', async () => {
        await book.bootstrapShow(db, SID, [{ key: 'a', eid: recipeId(SID, 'a'), piId: 61, servedId: 61 }], { now: NOW });
        const r = await recordWindow(db, SID, [ep(999, 'a', 100)], { cap: 20, gen: 1, now: NOW });
        assert.equal(r.usable, true);
        assert.equal(db.raw.prepare('SELECT served_id FROM episode_book WHERE catalog_key = ?').get('a').served_id, 61);
        assert.deepEqual(db.raw.prepare('SELECT pi_id FROM episode_pi_alias ORDER BY pi_id').all().map((x) => x.pi_id), [61, 999]);
        assert.equal(db.raw.prepare('SELECT COUNT(*) AS n FROM served_show').get().n, 1);
        const saved = await loadWindow(db, SID, 20, { now: NOW });
        assert.equal(saved.items[0].servedId, 61, 'the point keeps the ID old builds have');
        assert.equal(db.raw.prepare('SELECT description FROM episode_book').get().description, '<p>About a</p>', 'details refreshed');
    });

    it('a feed-merged list: a recipe-served episode is kept under its ID and never becomes a PI alias', async () => {
        const recipe = recipeId(SID, 'fresh');
        assert.ok(recipe >= RECIPE_ID_MIN);
        await book.bootstrapShow(db, SID, [{ key: 'fresh', eid: recipe, servedId: recipe }], { now: NOW, source: 'feed' });
        const r = await recordWindow(db, SID, [ep(recipe, 'fresh', 400), ep(102, 'b', 200)], { cap: 20, gen: 1, now: NOW });
        assert.equal(r.usable, true);
        assert.deepEqual(db.raw.prepare('SELECT pi_id FROM episode_pi_alias').all().map((x) => x.pi_id), [102]);
        const saved = await loadWindow(db, SID, 20, { now: NOW });
        assert.deepEqual(saved.items.map((it) => [it.servedId, it.piId]), [[recipe, null], [102, 102]]);
    });

    it('a shared episode (another show serves its PI ID) or one without a key leaves the window unusable and writes no rows', async () => {
        await book.bootstrapShow(db, 777, [{ key: 'x', eid: recipeId(777, 'x'), piId: 500, servedId: 500 }], { now: NOW });
        const before = db.raw.prepare('SELECT COUNT(*) AS n FROM episode_book').get().n;
        const shared = await recordWindow(db, SID, [ep(500, 'other-guid', 100)], { cap: 20, gen: 1, now: NOW });
        assert.deepEqual([shared.usable, shared.reason], [false, 'shared episode']);
        assert.equal(db.raw.prepare('SELECT COUNT(*) AS n FROM episode_book').get().n, before);
        assert.equal(await loadWindow(db, SID, 20, { now: NOW }), null);

        const noKey = await recordWindow(db, SID, [ep(9, null, 100, { enclosureUrl: '' })], { cap: 20, gen: 2, now: NOW });
        assert.deepEqual([noKey.usable, noKey.reason], [false, 'no catalog key']);
    });

    it('stage 4 gets the window only when it is long enough, fresh and fully served', async () => {
        const full = Array.from({ length: 5 }, (_, i) => ep(200 + i, `g${i}`, 1000 - i));
        await recordWindow(db, SID, full, { cap: 5, gen: 1, now: NOW });
        assert.equal((await loadWindow(db, SID, 3, { now: NOW })).items.length, 3, 'a smaller window is a prefix');
        assert.equal(await loadWindow(db, SID, 20, { now: NOW }), null, 'a full list fetched at cap 5 may be partial for cap 20');
        assert.equal(await loadWindow(db, SID, 5, { now: NOW + 4 * 24 * 3600 }), null, 'older than 3 days');

        await recordWindow(db, 42, [ep(301, 'only', 10)], { cap: 5, gen: 1, now: NOW });
        assert.equal((await loadWindow(db, 42, 20, { now: NOW })).items.length, 1, 'a short list is the whole show');

        db.raw.prepare('UPDATE episode_book SET served_id = NULL WHERE catalog_key = ?').run('g0');
        assert.equal(await loadWindow(db, SID, 5, { now: NOW }), null, 'a row without a served ID sends stage 4 back to PI');
    });

    it('sameWindow: same points in the same order', async () => {
        const list = [ep(103, 'c', 300), ep(102, 'b', 200)];
        await recordWindow(db, SID, list, { cap: 20, gen: 1, now: NOW });
        const saved = await loadWindow(db, SID, 20, { now: NOW });
        assert.equal(sameWindow(saved, [{ uuid: stableUUID(103) }, { uuid: stableUUID(102) }]), true);
        assert.equal(sameWindow(saved, [{ uuid: stableUUID(102) }, { uuid: stableUUID(103) }]), false);
        assert.equal(sameWindow(null, []), null);
        assert.equal(catalogKey('c', null), 'c');
    });
});

describe('tip queue generations', () => {
    it('stage 4 clears only rows enqueued before it started', async () => {
        const db = sqliteDb();
        await book.ensureBookSchema(db); // adds ep_vec_tip_queue.gen
        const turso = {
            execute: async (sql, args) => {
                await db.query(sql, args);
                return { results: [] };
            },
        };
        await tipQueue.upsert(turso, 1, 'a', { gen: 100 });
        await tipQueue.upsert(turso, 2, 'b', { gen: 300 });
        await tipQueue.upsert(turso, 1, 'a2', { gen: 150 });
        assert.deepEqual(db.raw.prepare('SELECT podcast_id, tip_ep_id, gen FROM ep_vec_tip_queue ORDER BY podcast_id').all().map(Object.values),
            [[1, 'a2', 150], [2, 'b', 300]], 'an upsert refreshes the generation');
        await tipQueue.removeMany(turso, [1, 2], { upToGen: 200 });
        assert.deepEqual(db.raw.prepare('SELECT podcast_id FROM ep_vec_tip_queue').all().map((r) => r.podcast_id), [2], 'a newer change stays queued');
    });
});
