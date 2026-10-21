'use strict';

/**
 * Saved episode windows (own-episode-IDs P1b, plan sections 8 and 10b item 3).
 *
 * Stage 3 records the list it fetched for a changed show: every episode becomes (or refreshes) a
 * book row, with its description, and `show_window` keeps the book row IDs in list order. Stage 4
 * then reads the window from the book instead of fetching the list from Podcast Index again, in
 * this run or a later one. A window from boxlore-feeds (R5 shows) also carries the feed-only
 * episodes, under their served IDs, so stage 4 can embed them.
 *
 * Only a window whose every episode is this show's own book row is usable. One with an episode
 * that has no catalog key, or whose Podcast Index ID another show serves (duplicate feeds), is
 * stored as unusable and stage 4 takes today's path for that show.
 *
 * Modes (`WINDOWS_MODE`): off; shadow records windows and has stage 4 compare them with its own
 * Podcast Index fetch; on lets stage 4 use them.
 * COMPAT(own-ids): permanent; the window is how the sync reads the book.
 */

const { resolveIdentities, loadServedIds, loadServedOwners } = require('./episode-identity');
const { bootstrapShow } = require('./episode-book');
const { catalogKey } = require('./episode-recipe-id');
const { stableUUID } = require('./qdrant');

/** Recipe IDs start at 2^52 (docs/EPISODE_IDS.md); they are never Podcast Index IDs. */
const RECIPE_ID_MIN = 2 ** 52;
const ROW_CHUNK = 200;
/** Stage 4 uses a saved window only while it is this fresh (stage 3 rewrites it on every change). */
const MAX_AGE_SEC = 3 * 24 * 3600;

const num = (v) => {
    const n = Number(v);
    return Number.isSafeInteger(n) && n > 0 ? n : null;
};
const str = (v) => (typeof v === 'string' && v !== '' ? v : null);

/** A Podcast Index ID, or null for a recipe-range served ID that came from the feed. */
const piIdOf = (v) => {
    const n = num(v);
    return n != null && n < RECIPE_ID_MIN ? n : null;
};

/**
 * Records one show's fetched list as book rows plus its window.
 * @param {object} book bookDb adapter (query, atomic, text)
 * @param {number} sid
 * @param {object[]} list PI-shaped items, newest first (PI's list, or boxlore-feeds' merged list)
 * @param {{ cap: number, gen: number, now: number, source?: string }} opts
 * @returns {Promise<{ usable: boolean, count: number, reason?: string }>}
 */
async function recordWindow(book, sid, list, { cap, gen, now, source = 'pi' }) {
    const items = (list || []).slice(0, cap);
    if (!items.length) return { usable: false, count: 0, reason: 'empty' };
    const keys = [...new Set(items.map((ep) => catalogKey(ep?.guid, ep?.enclosureUrl)).filter(Boolean))];
    const piIds = items.map((ep) => piIdOf(ep?.id)).filter((n) => n != null);
    const ident = resolveIdentities(sid, items, await loadServedIds(book, sid, keys), await loadServedOwners(book, piIds));
    const unusable = ident.items.find((it) => it.deferred || !it.stamps?.catalog_key);
    if (unusable) {
        await writeWindow(book, sid, { gen, cap, rids: [], usable: false, now });
        return { usable: false, count: items.length, reason: unusable.deferred ? 'shared episode' : 'no catalog key' };
    }

    const newKeys = new Set(ident.newRows.map((r) => r.key));
    const writes = ident.items.map((it) => {
        const raw = it.raw;
        const key = it.stamps.catalog_key;
        const piId = piIdOf(raw.id);
        return {
            key,
            eid: it.stamps.eid,
            piId,
            // A new row is served under its own ID; a known one keeps the ID it has.
            servedId: newKeys.has(key) ? it.servedId : null,
            title: str(raw.title),
            audioUrl: str(raw.enclosureUrl),
            imageUrl: str(raw.image) || str(raw.feedImage),
            duration: num(raw.duration),
            publishedAt: num(raw.datePublished),
            description: str(raw.description),
            chaptersUrl: str(raw.chaptersUrl),
            transcriptUrl: str(raw.transcriptUrl),
        };
    });
    const res = await bootstrapShow(book, sid, writes, { now, source });
    if (!res.ok) throw new Error(res.error || 'book write failed');

    const ridByKey = await ridsForKeys(book, sid, writes.map((w) => w.key));
    const rids = writes.map((w) => ridByKey.get(w.key));
    if (rids.some((r) => r == null)) {
        await writeWindow(book, sid, { gen, cap, rids: [], usable: false, now });
        return { usable: false, count: items.length, reason: 'row missing after write' };
    }
    await writeWindow(book, sid, { gen, cap, rids, usable: true, now });
    return { usable: true, count: rids.length, newRows: ident.newRows.length };
}

async function ridsForKeys(book, sid, keys) {
    const out = new Map();
    for (let i = 0; i < keys.length; i += ROW_CHUNK) {
        const chunk = keys.slice(i, i + ROW_CHUNK);
        const rows = await book.query(
            `SELECT catalog_key, rid FROM episode_book WHERE sid = ? AND catalog_key IN (${chunk.map(() => '?').join(',')})`,
            [sid, ...chunk.map((k) => book.text(k))],
        );
        for (const [key, rid] of rows) out.set(String(key), Number(rid));
    }
    return out;
}

async function writeWindow(book, sid, { gen, cap, rids, usable, now }) {
    await book.query(
        `INSERT INTO show_window (sid, gen, complete, cap, rids, written_at) VALUES (?, ?, ?, ?, ?, ?)
         ON CONFLICT (sid) DO UPDATE SET gen = excluded.gen, complete = excluded.complete, cap = excluded.cap,
           rids = excluded.rids, written_at = excluded.written_at`,
        [sid, gen, usable ? 1 : 0, cap, book.text(JSON.stringify(rids)), now],
    );
}

/**
 * The saved window for stage 4, as resolved items (the shape `resolveIdentities` returns), or null
 * when there is none, it is unusable, or it is shorter than the cap stage 4 needs.
 * @returns {Promise<null | { items: object[], listLength: number, gen: number }>}
 */
async function loadWindow(book, sid, cap, { now = Math.floor(Date.now() / 1000), maxAgeSec = MAX_AGE_SEC } = {}) {
    const rows = await book.query('SELECT gen, complete, cap, rids, written_at FROM show_window WHERE sid = ?', [sid]);
    if (!rows.length) return null;
    const [gen, complete, storedCap, ridsText, writtenAt] = rows[0];
    if (Number(complete) !== 1) return null;
    // An old snapshot (a show back after a long gap) is no substitute for today's list.
    if (now - Number(writtenAt) > maxAgeSec) return null;
    let rids;
    try {
        rids = JSON.parse(String(ridsText));
    } catch {
        return null;
    }
    if (!Array.isArray(rids) || !rids.length) return null;
    // A list fetched at a smaller cap can't stand in for a bigger window (it may be partial).
    if (cap > Number(storedCap) && rids.length >= Number(storedCap)) return null;
    const want = rids.slice(0, cap);
    const byRid = new Map();
    for (let i = 0; i < want.length; i += ROW_CHUNK) {
        const chunk = want.slice(i, i + ROW_CHUNK);
        const found = await book.query(
            `SELECT rid, served_id, eid, catalog_key, title, audio_url, image_url, duration, published_at, description,
                    chapters_url, transcript_url
             FROM episode_book WHERE sid = ? AND rid IN (${chunk.map(() => '?').join(',')})`,
            [sid, ...chunk],
        );
        for (const r of found) byRid.set(Number(r[0]), r);
    }
    const items = [];
    for (const rid of want) {
        const r = byRid.get(Number(rid));
        if (!r || r[1] == null) return null; // a row gone or never served: fetch instead
        const servedId = Number(r[1]);
        items.push({
            raw: {
                id: servedId,
                title: r[4] ?? '',
                enclosureUrl: r[5] ?? '',
                image: r[6] ?? '',
                duration: r[7] == null ? 0 : Number(r[7]),
                datePublished: r[8] == null ? 0 : Number(r[8]),
                description: r[9] ?? '',
                chaptersUrl: r[10] ?? null,
                transcriptUrl: r[11] ?? null,
            },
            piId: piIdOf(servedId),
            servedId,
            uuid: stableUUID(servedId),
            stamps: { eid: Number(r[2]), catalog_key: String(r[3]) },
        });
    }
    return { items, listLength: rids.length, gen: Number(gen) };
}

/** Shadow comparison: do the saved window and the fetched list name the same points in order? */
function sameWindow(saved, fetchedItems) {
    if (!saved) return null;
    const a = saved.items.map((it) => it.uuid);
    const b = fetchedItems.slice(0, a.length).map((it) => it.uuid);
    return a.length === b.length && a.every((u, i) => u === b[i]);
}

module.exports = { recordWindow, loadWindow, sameWindow, RECIPE_ID_MIN, MAX_AGE_SEC, piIdOf };
