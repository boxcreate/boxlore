'use strict';

/**
 * Pure planning for the book bootstrap (own-episode-IDs plan P1, section 8 "Bootstrap"): maps one
 * show's existing Qdrant points to (show, catalog key) through Podcast Index's episode list, and
 * says which book rows to write and which payload stamps to set. No I/O here.
 *
 * - A point's payload `id` is the PI episode ID it was embedded with, and stays its served ID.
 * - A point whose PI ID isn't in the list, or whose item has no GUID and no enclosure, is
 *   `eid_missing`: it keeps its identity and is retried later. A key is never guessed.
 * - Two points with the same key (PI re-listed the episode under a new ID) share one book row;
 *   the extra PI IDs become aliases. Stage 4's served-ID naming removes the duplicate later.
 */

const { catalogKey, recipeId } = require('./episode-recipe-id');

const num = (v) => {
    const n = Number(v);
    return Number.isSafeInteger(n) ? n : null;
};

/**
 * @param {number} sid show ID
 * @param {{ id: string, payload: object }[]} points the show's Qdrant points
 * @param {object[]} piItems PI `episodes/byfeedid` items
 * @returns {{ items: object[], stamps: { pointId: string, eid: number, key: string }[],
 *   missing: string[], duplicates: number }}
 */
function planShow(sid, points, piItems) {
    const byPiId = new Map();
    for (const e of piItems || []) {
        const id = num(e?.id);
        if (id != null && !byPiId.has(id)) byPiId.set(id, e);
    }
    const items = new Map();
    const stamps = [];
    const missing = [];
    let duplicates = 0;
    for (const p of points) {
        const piId = num(p.payload?.id);
        const e = piId == null ? null : byPiId.get(piId);
        const key = e ? catalogKey(e.guid, e.enclosureUrl) : null;
        if (!key) {
            missing.push(p.id);
            continue;
        }
        const eid = recipeId(sid, key);
        const existing = items.get(key);
        if (existing) {
            duplicates++;
            if (piId !== existing.piId) existing.extraPiIds.push(piId);
        } else {
            items.set(key, {
                key,
                eid,
                piId,
                servedId: piId,
                extraPiIds: [],
                title: typeof e.title === 'string' ? e.title : null,
                audioUrl: typeof e.enclosureUrl === 'string' ? e.enclosureUrl : null,
                imageUrl: (typeof e.image === 'string' && e.image) || (typeof e.feedImage === 'string' && e.feedImage) || null,
                duration: num(e.duration),
                publishedAt: num(e.datePublished),
                chaptersUrl: typeof e.chaptersUrl === 'string' ? e.chaptersUrl : null,
                transcriptUrl: typeof e.transcriptUrl === 'string' ? e.transcriptUrl : null,
            });
        }
        stamps.push({ pointId: p.id, eid, key });
    }
    return { items: [...items.values()], stamps, missing, duplicates };
}

/** Qdrant batch operations that stamp a show's points (one value set per point). */
function stampOperations(plan) {
    const ops = plan.stamps.map((s) => ({ set_payload: { payload: { eid: s.eid, catalog_key: s.key }, points: [s.pointId] } }));
    if (plan.missing.length) ops.push({ set_payload: { payload: { eid_missing: true }, points: plan.missing } });
    return ops;
}

module.exports = { planShow, stampOperations };
