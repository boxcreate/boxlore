'use strict';

/**
 * Stage 4's episode identity (own-episode-IDs plan P1, "writer handover"): every episode in a show's
 * window gets its catalog key and recipe ID, and its point is named by the book's served ID.
 *
 * - A key already in the book keeps its served ID, so a Podcast Index ID change no longer deletes and
 *   re-embeds the episode: the point keeps its name and the new PI ID becomes an alias.
 * - A new key's served ID is its current PI ID (what v1 clients already get); its book row is
 *   written before the vector, so identity is committed first.
 * - An item with no GUID and no enclosure keeps today's naming and is stamped `eid_missing`.
 * - Two items with one key in a window: the first wins (PI's order; feeds use document order).
 * - A new key whose PI ID is already served for another key is deferred: no book row, today's
 *   naming, so the rest of the show is still recorded and no served ID is ever taken from another
 *   episode (docs/EPISODE_IDS.md §4). Almost always a duplicate feed: Podcast Index lists one podcast
 *   under two show IDs that share episode IDs. The point is stamped `eid_shared` with the owning show,
 *   so the bootstrap's catch-up doesn't pick it up again.
 *
 * The bootstrap and stage 4 never overlap (both hold the shared job lock), so no per-show fence
 * is needed. COMPAT(own-ids): permanent.
 */

const { catalogKey, recipeId } = require('./episode-recipe-id');
const { stableUUID } = require('./qdrant');

const num = (v) => {
    const n = Number(v);
    return Number.isSafeInteger(n) && n > 0 ? n : null;
};

/**
 * @param {number} sid show ID
 * @param {object[]} episodes PI items in window order
 * @param {Map<string, number>} servedByKey the book's served IDs for this show
 * @param {Map<number, string>} [ownerByServed] who holds these PI IDs as served IDs ("sid\u0000key")
 * @returns {{ items: object[], newRows: object[], aliases: object[], duplicates: number, renamed: number, deferred: number }}
 *   items: { raw, piId, servedId, uuid, stamps } per kept episode
 */
function resolveIdentities(sid, episodes, servedByKey, ownerByServed = new Map()) {
    const items = [];
    const newRows = [];
    const aliases = [];
    const seen = new Set();
    let duplicates = 0;
    let renamed = 0;
    let deferred = 0;
    for (const raw of episodes) {
        const piId = num(raw?.id);
        const key = catalogKey(raw?.guid, raw?.enclosureUrl);
        if (!key) {
            items.push({ raw, piId, servedId: piId, uuid: stableUUID(raw?.id), stamps: { eid_missing: true } });
            continue;
        }
        if (seen.has(key)) {
            duplicates++;
            continue;
        }
        seen.add(key);
        const eid = recipeId(sid, key);
        const known = servedByKey.get(key);
        if (known == null && piId != null && ownerByServed.has(piId) && ownerByServed.get(piId) !== `${sid}\u0000${key}`) {
            deferred++;
            const ownerSid = Number(ownerByServed.get(piId).split('\u0000')[0]);
            items.push({ raw, piId, servedId: piId, uuid: stableUUID(piId), stamps: { eid_shared: ownerSid }, deferred: true });
            continue;
        }
        const servedId = known ?? piId;
        if (known == null) {
            newRows.push({
                key, eid, piId, servedId: piId,
                title: typeof raw.title === 'string' ? raw.title : null,
                audioUrl: typeof raw.enclosureUrl === 'string' ? raw.enclosureUrl : null,
                imageUrl: (typeof raw.image === 'string' && raw.image) || (typeof raw.feedImage === 'string' && raw.feedImage) || null,
                duration: num(raw.duration),
                publishedAt: num(raw.datePublished),
                chaptersUrl: typeof raw.chaptersUrl === 'string' ? raw.chaptersUrl : null,
                transcriptUrl: typeof raw.transcriptUrl === 'string' ? raw.transcriptUrl : null,
            });
        } else if (piId != null && known !== piId) {
            renamed++;
            aliases.push({ key, eid, piId });
        }
        items.push({ raw, piId, servedId, uuid: stableUUID(servedId), stamps: { eid, catalog_key: key } });
    }
    return { items, newRows, aliases, duplicates, renamed, deferred };
}

/** The book's served IDs for these keys of one show. */
async function loadServedIds(db, sid, keys) {
    const out = new Map();
    for (let i = 0; i < keys.length; i += 200) {
        const chunk = keys.slice(i, i + 200);
        const rows = await db.query(
            `SELECT catalog_key, served_id FROM episode_book WHERE sid = ? AND served_id IS NOT NULL
             AND catalog_key IN (${chunk.map(() => '?').join(',')})`,
            [sid, ...chunk.map((k) => db.text(k))],
        );
        for (const [key, served] of rows) out.set(String(key), Number(served));
    }
    return out;
}

/** Which (show, key) already holds each of these IDs as its served ID: Map id -> "sid\u0000key". */
async function loadServedOwners(db, ids) {
    const out = new Map();
    const unique = [...new Set(ids)];
    for (let i = 0; i < unique.length; i += 200) {
        const chunk = unique.slice(i, i + 200);
        const rows = await db.query(
            `SELECT served_id, sid, catalog_key FROM episode_book WHERE served_id IN (${chunk.map(() => '?').join(',')})`,
            chunk,
        );
        for (const [served, sid, key] of rows) out.set(Number(served), `${Number(sid)}\u0000${key}`);
    }
    return out;
}

module.exports = { resolveIdentities, loadServedIds, loadServedOwners };
