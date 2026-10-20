'use strict';

// Runs the book's SQL against a real SQLite database (node:sqlite, Node >= 22.5); sqld is libSQL,
// so the constraints, ON CONFLICT and partial unique index behave the same.
const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const book = require('./episode-book');
const turso = require('./turso');
const { recipeId } = require('./episode-recipe-id');

function sqliteDb() {
    const raw = new DatabaseSync(':memory:');
    raw.exec('CREATE TABLE podcasts (id INTEGER PRIMARY KEY, title TEXT)');
    raw.exec('CREATE TABLE ep_vec_tip_queue (podcast_id INTEGER PRIMARY KEY, latest_ep_id TEXT)');
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

const ep = (key, extra = {}) => ({ key, eid: recipeId(3391039, key), ...extra });

describe('episode book', () => {
    let db;
    beforeEach(async () => {
        db = sqliteDb();
        await book.ensureBookSchema(db);
    });

    it('creates the tables and adds the new columns once', async () => {
        const tables = (await db.query("SELECT name FROM sqlite_master WHERE type = 'table'")).map((r) => r[0]);
        for (const t of ['episode_book', 'episode_pi_alias', 'served_show', 'show_window']) assert.ok(tables.includes(t), t);
        const cols = (await db.query("SELECT name FROM pragma_table_info('podcasts')")).map((r) => r[0]);
        assert.ok(cols.includes('latest_ep_key') && cols.includes('latest_ep_eid'));
        assert.deepEqual(await book.ensureBookSchema(db), { added: [] }, 'second run changes nothing');
    });

    it('keeps identity fixed and refreshes metadata on upsert', async () => {
        const now = 1000;
        assert.equal((await book.upsertEpisodes(db, 3391039, [ep('a', { title: 'Old', piId: 61 })], { source: 'pi', now })).ok, true);
        await book.upsertEpisodes(db, 3391039, [ep('a', { title: 'New', piId: 62 })], { source: 'feed', now: now + 5 });
        const [row] = await db.query('SELECT title, source, first_seen_at, updated_at FROM episode_book');
        assert.deepEqual(row, ['New', 'pi', now, now + 5]);
        const aliases = await db.query('SELECT pi_id FROM episode_pi_alias ORDER BY pi_id');
        assert.deepEqual(aliases, [[61], [62]], 'every PI ID ever seen is kept');
    });

    it('keeps a digit-only catalog key as text', async () => {
        await book.upsertEpisodes(db, 1, [{ key: '00123', eid: recipeId(1, '00123') }], { source: 'pi', now: 1 });
        const [[key, type]] = await db.query('SELECT catalog_key, typeof(catalog_key) FROM episode_book');
        assert.deepEqual([key, type], ['00123', 'text']);
    });

    it('flags both rows when two keys share a recipe ID', async () => {
        await book.upsertEpisodes(db, 1, [{ key: 'x', eid: 4503599627370500 }, { key: 'y', eid: 4503599627370500 }], { source: 'pi', now: 1 });
        const flags = await db.query('SELECT catalog_key, eid_clash FROM episode_book ORDER BY catalog_key');
        assert.deepEqual(flags, [['x', 1], ['y', 1]]);
    });

    it('records the legacy served ID once and never moves it', async () => {
        await book.upsertEpisodes(db, 1, [ep('a'), ep('b')], { source: 'bootstrap', now: 1 });
        assert.deepEqual(await book.recordLegacyServedId(db, 1, 'a', 6100), { ok: true, servedId: 6100 });
        assert.deepEqual(await book.recordLegacyServedId(db, 1, 'a', 6200), { ok: true, servedId: 6100 });
        const clash = await book.recordLegacyServedId(db, 1, 'b', 6100);
        assert.equal(clash.ok, false, 'a served ID belongs to one episode');
        assert.equal(await book.servedIdOf(db, 1, 'b'), null);
    });

    it('issues a served ID and marks the show in one transaction, first writer wins', async () => {
        await book.upsertEpisodes(db, 7, [ep('a'), ep('b')], { source: 'feed', now: 1 });
        const first = await book.issueServedId(db, 7, 'a', 9000000000000001, 50);
        assert.deepEqual(first, { ok: true, servedId: 9000000000000001 });
        assert.deepEqual(await book.issueServedId(db, 7, 'a', 9000000000000002, 60), { ok: true, servedId: 9000000000000001 });
        assert.deepEqual(await db.query('SELECT sid, first_served_at FROM served_show'), [[7, 50]]);

        db.raw.exec('DELETE FROM served_show');
        const clash = await book.issueServedId(db, 7, 'b', 9000000000000001, 70);
        assert.equal(clash.clash, true);
        assert.deepEqual(await db.query('SELECT COUNT(*) FROM served_show'), [[0]], 'a clash writes nothing');
    });

    it('marks the show for the overlay when a PI ID differs from the served ID, and only then', async () => {
        await book.bootstrapShow(db, 9, [ep('a', { piId: 61, servedId: 61 })], { now: 5 });
        assert.deepEqual(await db.query('SELECT COUNT(*) FROM served_show'), [[0]], 'PI ID = served ID: nothing to translate');
        await book.bootstrapShow(db, 9, [ep('a', { piId: 75 })], { now: 6, source: 'pi' });
        assert.deepEqual(await db.query('SELECT sid, first_served_at FROM served_show'), [[9, 6]], 'PI renumbered the episode');
        await book.bootstrapShow(db, 11, [ep('b', { piId: 80, servedId: 80, extraPiIds: [81] })], { now: 7 });
        assert.deepEqual(await db.query('SELECT sid FROM served_show ORDER BY sid'), [[9], [11]], 'PI listed it twice');
    });

    it('bootstraps a show in one transaction, or not at all', async () => {
        const items = [ep('a', { piId: 61, servedId: 61, extraPiIds: [70] }), ep('b', { piId: 62, servedId: 62 })];
        assert.deepEqual(await book.bootstrapShow(db, 9, items, { now: 5 }), { ok: true, error: null });
        assert.deepEqual(await db.query('SELECT catalog_key, served_id, source FROM episode_book ORDER BY catalog_key'),
            [['a', 61, 'bootstrap'], ['b', 62, 'bootstrap']]);
        assert.deepEqual(await db.query('SELECT pi_id FROM episode_pi_alias ORDER BY pi_id'), [[61], [62], [70]]);

        const bad = await book.bootstrapShow(db, 10, [ep('c', { piId: 63, servedId: 63 }), ep('d', { piId: 64, servedId: 61 })], { now: 6 });
        assert.equal(bad.ok, false, 'served ID 61 already belongs to show 9');
        assert.deepEqual(await db.query('SELECT COUNT(*) FROM episode_book WHERE sid = 10'), [[0]]);
    });

    it('an unknown episode gets no served ID', async () => {
        assert.deepEqual(await book.issueServedId(db, 7, 'missing', 9000000000000003, 1), { ok: false, servedId: null });
    });
});

describe('turso.atomic', () => {
    it('chains every step on the previous one and rolls back on failure', async () => {
        const realFetch = global.fetch;
        let body;
        global.fetch = async (_url, init) => {
            body = JSON.parse(init.body);
            return {
                ok: true,
                json: async () => ({ results: [
                    { type: 'ok', response: { type: 'batch', result: {
                        step_results: [{}, {}, null, null, {}],
                        step_errors: [null, null, { message: 'UNIQUE constraint failed: episode_book.served_id' }, null, null],
                    } } },
                    { type: 'ok', response: { type: 'close' } },
                ] }),
            };
        };
        try {
            const res = await turso.atomic([
                { sql: 'UPDATE episode_book SET served_id = ? WHERE sid = ?', args: [1, 2] },
                { sql: 'INSERT INTO served_show (sid, first_served_at) VALUES (?, ?)', args: [2, 3] },
            ]);
            assert.equal(res.ok, false);
            assert.match(res.error, /UNIQUE/);
            const steps = body.requests[0].batch.steps;
            assert.deepEqual(steps.map((s) => s.stmt.sql.split(' ')[0]), ['BEGIN', 'UPDATE', 'INSERT', 'COMMIT', 'ROLLBACK']);
            assert.deepEqual(steps.slice(1, 4).map((s) => s.condition), [1, 2, 3].map((i) => ({ type: 'ok', step: i - 1 })));
            assert.deepEqual(steps[4].condition, { type: 'not', cond: { type: 'ok', step: 3 } });
        } finally {
            global.fetch = realFetch;
        }
    });
});
