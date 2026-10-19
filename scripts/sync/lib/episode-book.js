'use strict';

/**
 * The episode book in sqld: one permanent row per (show ID, catalog key), the identity record behind
 * every boxlore episode ID (contract: docs/EPISODE_IDS.md).
 *
 * - `eid` is the recipe ID (indexed, not unique: two keys can share one; both get `eid_clash`).
 * - `served_id` is the ID first handed out for the episode. Set once, never changed: bootstrapped
 *   rows keep the Podcast Index ID old builds already have.
 * - Identity columns (sid, catalog_key, eid, served_id, source, first_seen_at) never change after
 *   insert. Metadata is refreshed on every upsert.
 *
 * Functions take a small `db` adapter so the same SQL runs against sqld (`bookDb()` below) and a
 * local SQLite file in tests:
 *   query(sql, args) -> rows as arrays
 *   atomic([{ sql, args }]) -> { ok, error } (all statements commit, or none)
 *   text(value) -> a value bound as TEXT even when it is all digits
 *
 * Added in P1 of the own-episode-IDs plan (plan section 8, sqld catalog).
 * COMPAT(own-ids): permanent identity storage. Never remove (MIGRATION.md §5).
 */

const BOOK_TABLES = [
    `CREATE TABLE IF NOT EXISTS episode_book (
        rid INTEGER PRIMARY KEY,
        eid INTEGER NOT NULL,
        sid INTEGER NOT NULL,
        catalog_key TEXT NOT NULL,
        served_id INTEGER,
        title TEXT, audio_url TEXT, image_url TEXT, duration INTEGER, published_at INTEGER,
        description TEXT, chapters_url TEXT, transcript_url TEXT,
        source TEXT NOT NULL,
        confirmed INTEGER NOT NULL DEFAULT 1,
        eid_clash INTEGER NOT NULL DEFAULT 0,
        first_seen_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
        meta_expired_at INTEGER,
        UNIQUE (sid, catalog_key)
    )`,
    'CREATE INDEX IF NOT EXISTS episode_book_eid ON episode_book(eid)',
    'CREATE UNIQUE INDEX IF NOT EXISTS episode_book_served ON episode_book(served_id) WHERE served_id IS NOT NULL',
    'CREATE INDEX IF NOT EXISTS episode_book_sid_date ON episode_book(sid, published_at DESC)',
    // Every Podcast Index episode ID ever seen for a book row (PI IDs can change).
    `CREATE TABLE IF NOT EXISTS episode_pi_alias (
        pi_id INTEGER PRIMARY KEY, rid INTEGER NOT NULL, seen_at INTEGER NOT NULL
    )`,
    // Shows where any episode was served with a recipe ID (drives the Worker's v1 overlay).
    `CREATE TABLE IF NOT EXISTS served_show (
        sid INTEGER PRIMARY KEY, first_served_at INTEGER NOT NULL
    )`,
    // The complete episode list stage 4 vectorizes, per show (written from P1b).
    `CREATE TABLE IF NOT EXISTS show_window (
        sid INTEGER PRIMARY KEY, gen INTEGER NOT NULL, complete INTEGER NOT NULL,
        cap INTEGER NOT NULL, rids TEXT NOT NULL, written_at INTEGER NOT NULL
    )`,
];

/** Additive columns on existing tables: CREATE TABLE IF NOT EXISTS never adds them. */
const COLUMN_MIGRATIONS = [
    ['ep_vec_tip_queue', 'gen', 'INTEGER NOT NULL DEFAULT 0'],
    ['podcasts', 'latest_ep_key', 'TEXT'],
    ['podcasts', 'latest_ep_eid', 'INTEGER'],
];

const META_COLUMNS = ['title', 'audio_url', 'image_url', 'duration', 'published_at', 'description', 'chapters_url', 'transcript_url'];

/** Creates the book tables and adds missing columns. Safe to run every time. */
async function ensureBookSchema(db) {
    for (const sql of BOOK_TABLES) await db.query(sql);
    const added = [];
    for (const [table, column, decl] of COLUMN_MIGRATIONS) {
        const cols = (await db.query(`SELECT name FROM pragma_table_info('${table}')`)).map((r) => r[0]);
        if (cols.length > 0 && !cols.includes(column)) {
            await db.query(`ALTER TABLE ${table} ADD COLUMN ${column} ${decl}`);
            added.push(`${table}.${column}`);
        }
    }
    return { added };
}

const nullable = (v) => (v === undefined || v === '' ? null : v);

/**
 * Inserts or refreshes book rows for one show, and records PI aliases. Identity never changes:
 * an existing (sid, key) keeps its eid, served_id, source and first_seen_at.
 * @param {object[]} items { key, eid, piId?, title?, audioUrl?, imageUrl?, duration?, publishedAt?,
 *   description?, chaptersUrl?, transcriptUrl? } with key already trimmed (episode-recipe-id.js)
 * @returns {Promise<{ ok: boolean, error?: string, written: number }>}
 */
async function upsertEpisodes(db, sid, items, { source, now }) {
    const res = await db.atomic(upsertStatements(db, sid, items, { source, now }));
    return { ok: res.ok, error: res.error, written: res.ok ? items.length : 0 };
}

/** The statements behind upsertEpisodes, for callers that add more to the same transaction. */
function upsertStatements(db, sid, items, { source, now }) {
    const stmts = [];
    for (const it of items) {
        const meta = [it.title, it.audioUrl, it.imageUrl, it.duration, it.publishedAt, it.description, it.chaptersUrl, it.transcriptUrl]
            .map(nullable);
        stmts.push({
            sql: `INSERT INTO episode_book (eid, sid, catalog_key, ${META_COLUMNS.join(', ')}, source, first_seen_at, updated_at)
                  VALUES (?, ?, ?, ${META_COLUMNS.map(() => '?').join(', ')}, ?, ?, ?)
                  ON CONFLICT (sid, catalog_key) DO UPDATE SET
                  ${META_COLUMNS.map((c) => `${c} = COALESCE(excluded.${c}, ${c})`).join(', ')}, updated_at = excluded.updated_at`,
            args: [it.eid, sid, db.text(it.key), ...meta, source, now, now],
        });
        for (const piId of [it.piId, ...(it.extraPiIds || [])]) {
            if (piId == null) continue;
            stmts.push({
                sql: `INSERT INTO episode_pi_alias (pi_id, rid, seen_at)
                      SELECT ?, rid, ? FROM episode_book WHERE sid = ? AND catalog_key = ?
                      ON CONFLICT (pi_id) DO UPDATE SET seen_at = excluded.seen_at`,
                args: [piId, now, sid, db.text(it.key)],
            });
        }
    }
    // Two keys with one recipe ID: flag every row of that eid (resolved by key, never re-keyed).
    const eids = [...new Set(items.map((it) => it.eid))];
    for (let i = 0; i < eids.length; i += 200) {
        const chunk = eids.slice(i, i + 200);
        stmts.push({
            sql: `UPDATE episode_book SET eid_clash = 1 WHERE eid_clash = 0 AND eid IN (
                    SELECT eid FROM episode_book WHERE eid IN (${chunk.map(() => '?').join(',')})
                    GROUP BY eid HAVING COUNT(*) > 1)`,
            args: chunk,
        });
    }
    return stmts;
}

/**
 * Bootstrap of one show in one transaction: its book rows, every PI alias, and each row's legacy
 * served ID (the ID its point already has). Nothing is written if any statement fails.
 * @param {object[]} items as for upsertEpisodes, plus `servedId`
 */
async function bootstrapShow(db, sid, items, { now }) {
    const stmts = upsertStatements(db, sid, items, { source: 'bootstrap', now });
    for (const it of items) {
        if (it.servedId == null) continue;
        stmts.push({
            sql: 'UPDATE episode_book SET served_id = COALESCE(served_id, ?) WHERE sid = ? AND catalog_key = ?',
            args: [it.servedId, sid, db.text(it.key)],
        });
    }
    const res = await db.atomic(stmts);
    return { ok: res.ok, error: res.error };
}

/**
 * Bootstrap: records the ID an existing point already serves (its Podcast Index ID). Set once;
 * a row that already has a served ID keeps it. Fails, changing nothing, if another row holds it.
 * @returns {Promise<{ ok: boolean, servedId: number|null, error?: string }>}
 */
async function recordLegacyServedId(db, sid, key, servedId) {
    const res = await db.atomic([{
        sql: 'UPDATE episode_book SET served_id = COALESCE(served_id, ?) WHERE sid = ? AND catalog_key = ?',
        args: [servedId, sid, db.text(key)],
    }]);
    if (!res.ok) return { ok: false, servedId: null, error: res.error };
    return { ok: true, servedId: await servedIdOf(db, sid, key) };
}

/**
 * Issues a served ID for a feed episode before it is shown: sets it if unset and marks the show in
 * `served_show`, in one transaction, then returns the committed value (another writer's wins).
 * `clash` means the candidate is already served for a different episode (docs/EPISODE_IDS.md §4).
 * @returns {Promise<{ ok: boolean, servedId: number|null, clash?: boolean, error?: string }>}
 */
async function issueServedId(db, sid, key, candidate, now) {
    const res = await db.atomic([
        {
            sql: 'UPDATE episode_book SET served_id = COALESCE(served_id, ?) WHERE sid = ? AND catalog_key = ?',
            args: [candidate, sid, db.text(key)],
        },
        { sql: 'INSERT OR IGNORE INTO served_show (sid, first_served_at) VALUES (?, ?)', args: [sid, now] },
    ]);
    if (!res.ok) {
        const clash = /UNIQUE/i.test(res.error || '');
        return { ok: false, servedId: null, clash, error: res.error };
    }
    const servedId = await servedIdOf(db, sid, key);
    return { ok: servedId != null, servedId };
}

async function servedIdOf(db, sid, key) {
    const rows = await db.query('SELECT served_id FROM episode_book WHERE sid = ? AND catalog_key = ?', [sid, db.text(key)]);
    return rows.length && rows[0][0] != null ? Number(rows[0][0]) : null;
}

/**
 * Adapter over the sync's Turso client. `atomic` uses one Hrana batch whose steps run only while the
 * previous one succeeded, then COMMIT, or ROLLBACK when any step failed.
 */
function bookDb(turso) {
    return {
        text: turso.text,
        query: async (sql, args = []) => turso.rows(await turso.execute(sql, args)),
        atomic: (stmts) => turso.atomic(stmts),
    };
}

module.exports = {
    BOOK_TABLES,
    COLUMN_MIGRATIONS,
    ensureBookSchema,
    upsertEpisodes,
    upsertStatements,
    bootstrapShow,
    recordLegacyServedId,
    issueServedId,
    servedIdOf,
    bookDb,
};
