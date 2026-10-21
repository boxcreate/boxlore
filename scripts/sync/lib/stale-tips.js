'use strict';

/**
 * Stale-show tips from the live feed (R5; own-episode-IDs P1b, plan sections 6 and 10b).
 *
 * For chart shows the registry flags `episode_stale` (Podcast Index weeks or months behind the
 * show's own feed), stage 3 asks boxlore-feeds for the newest episode after its Podcast Index pass.
 * When that episode is newer than the stored tip, it becomes the show's latest episode: Home tips,
 * trending and catalog cards on every build. The episode comes under its committed served ID
 * (boxlore-feeds issues it like `/feeds/episodes`), with its catalog key and recipe ID.
 *
 * Only the tip columns change. `qdrant_vectorized` and the tip queue stay with the Podcast Index
 * pass, because stage 4 can't embed a feed-only episode until it reads windows from the book.
 *
 * Modes (`R5_MODE`): off; shadow asks for the feed's newest dates only, counts what would change and
 * never writes or issues an ID; on writes. A registry or feeds failure skips R5 for the run (or for
 * that chunk); the Podcast Index tips stay.
 */

const { catalogKey, recipeId } = require('./episode-recipe-id');
const { tipUpdateArgs } = require('./catalog-rows');

const CHUNK = 40; // boxlore-feeds' limit for `episodes: true`
const READ_CHUNK = 200;
const TIMEOUT_MS = 120_000;

/** Same columns and order as stage 3's tip UPDATE, without touching qdrant_vectorized. */
const TIP_SQL = `
    UPDATE podcasts SET
        latest_ep_id = ?, latest_ep_title = ?, latest_ep_date = ?,
        latest_ep_duration = ?, latest_ep_url = ?, latest_ep_image = ?,
        latest_ep_type = ?, latest_ep_description = ?,
        latest_ep_chapters_url = ?, latest_ep_transcript_url = ?,
        latest_ep_persons = ?, latest_ep_transcripts = ?,
        medium = ?, last_ep_sync = ?, latest_ep_key = ?, latest_ep_eid = ?
    WHERE id = ?
`;

/** Show IDs the published registry flags `episode_stale`. Throws when it can't be read (needs Node 22.5+ for node:sqlite). */
function staleShowIds(registryPath) {
    const { DatabaseSync } = require('node:sqlite');
    const db = new DatabaseSync(registryPath, { readOnly: true });
    try {
        return new Set(db.prepare('SELECT id FROM show_flags WHERE episode_stale = 1').all().map((r) => Number(r.id)));
    } finally {
        db.close();
    }
}

/** One `POST /feeds/latest` call (local, no token). `episodes` asks for served-ID items. */
async function feedsLatest({ baseUrl, ids, episodes, fetchImpl = fetch, timeoutMs = TIMEOUT_MS }) {
    const res = await fetchImpl(`${baseUrl.replace(/\/+$/, '')}/feeds/latest`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', accept: 'application/json' },
        body: JSON.stringify(episodes ? { ids, episodes: true, mode: 'live' } : { ids, maxAgeMinutes: 24 * 60 }),
        signal: AbortSignal.timeout(timeoutMs),
    });
    if (!res.ok) throw new Error(`boxlore-feeds HTTP ${res.status}`);
    const body = await res.json();
    if (!Array.isArray(body?.results)) throw new Error('boxlore-feeds: no results');
    return body.results;
}

/** Current tips: Map show ID → { id, date }. */
async function currentTips(db, ids) {
    const out = new Map();
    for (let i = 0; i < ids.length; i += READ_CHUNK) {
        const chunk = ids.slice(i, i + READ_CHUNK);
        const res = await db.execute(
            `SELECT id, latest_ep_id, latest_ep_date FROM podcasts WHERE id IN (${chunk.map(() => '?').join(',')})`,
            chunk,
        );
        for (const [id, epId, date] of db.rows(res)) out.set(Number(id), { id: epId == null ? null : String(epId), date: Number(date) || 0 });
    }
    return out;
}

/**
 * The feed's episode replaces the tip only when it is playable, a different episode, and newer.
 * `item` is boxlore-feeds' PI-shaped episode (its `id` is the served ID).
 */
function shouldReplace(tip, item) {
    if (!item || typeof item !== 'object' || !item.enclosureUrl) return false;
    const date = Number(item.datePublished) || 0;
    if (!date || !Number.isSafeInteger(Number(item.id))) return false;
    if (tip?.id != null && String(item.id) === tip.id) return false;
    return date > (tip?.date || 0);
}

/** The tip's identity for tipUpdateArgs: served ID, catalog key and recipe ID. */
function feedIdentity(showId, item) {
    const key = catalogKey(item.guid, item.enclosureUrl);
    return { servedId: Number(item.id), key, eid: key ? recipeId(showId, key) : null };
}

/**
 * R5 for the shows this run checked. Never throws: returns counts, with `error` when the registry
 * couldn't be read (R5 skipped).
 * @param {{ mode: 'off'|'shadow'|'on', due: (string|number)[], db: { execute: Function, rows: Function },
 *   feedsUrl: string, registryPath: string, mediumOf?: (id: number) => string, now?: () => number,
 *   fetchImpl?: Function, staleIds?: () => Set<number>, warn?: (msg: string) => void }} opts
 */
async function runStaleTips(opts) {
    const { mode, due, db, feedsUrl, registryPath, fetchImpl = fetch, warn = () => {} } = opts;
    const mediumOf = opts.mediumOf || (() => 'podcast');
    const now = opts.now || Date.now;
    const out = { mode, stale: 0, answered: 0, newer: 0, updated: 0, noItem: 0, failedChunks: 0, error: null, updatedIds: [] };
    if (mode !== 'shadow' && mode !== 'on') return out;

    let flagged;
    try {
        flagged = (opts.staleIds || (() => staleShowIds(registryPath)))();
    } catch (e) {
        out.error = `registry unreadable: ${e.message}`;
        warn(`[R5] ${out.error}; stale tips skipped this run`);
        return out;
    }
    const ids = [...new Set(due.map(Number))].filter((id) => flagged.has(id));
    out.stale = ids.length;
    if (!ids.length) return out;

    let tips;
    try {
        tips = await currentTips(db, ids);
    } catch (e) {
        out.error = `tip read failed: ${e.message}`;
        warn(`[R5] ${out.error}; stale tips skipped this run`);
        return out;
    }
    for (let i = 0; i < ids.length; i += CHUNK) {
        const chunk = ids.slice(i, i + CHUNK);
        let results;
        try {
            results = await feedsLatest({ baseUrl: feedsUrl, ids: chunk, episodes: mode === 'on', fetchImpl });
        } catch (e) {
            out.failedChunks++;
            warn(`[R5] boxlore-feeds failed for ${chunk.length} shows: ${e.message}`);
            continue;
        }
        for (const r of results) {
            const showId = Number(r?.id);
            if (!chunk.includes(showId)) continue;
            out.answered++;
            if (mode === 'shadow') {
                const date = Number(r?.newest?.publishedAt) || 0;
                if (date > (tips.get(showId)?.date || 0)) out.newer++;
                continue;
            }
            if (!r?.item) {
                out.noItem++;
                continue;
            }
            if (!shouldReplace(tips.get(showId), r.item)) continue;
            out.newer++;
            try {
                await db.execute(TIP_SQL, tipUpdateArgs(r.item, mediumOf(showId), now(), showId, feedIdentity(showId, r.item)));
                out.updated++;
                out.updatedIds.push(showId);
            } catch (e) {
                warn(`[R5] show ${showId}: tip write failed (${e.message})`);
            }
        }
    }
    return out;
}

/**
 * A stale show's merged episode list from boxlore-feeds (`GET /feeds/episodes`, live: episodes
 * that need one get their served ID), newest first, for its saved window.
 */
async function feedWindow({ baseUrl, sid, cap, fetchImpl = fetch, timeoutMs = 60_000 }) {
    const q = new URLSearchParams({ id: String(sid), limit: String(cap), offset: '0', sort: 'newest', mode: 'live' });
    const res = await fetchImpl(`${baseUrl.replace(/\/+$/, '')}/feeds/episodes?${q}`, {
        headers: { accept: 'application/json' },
        signal: AbortSignal.timeout(timeoutMs),
    });
    if (!res.ok) throw new Error(`boxlore-feeds HTTP ${res.status}`);
    const body = await res.json();
    if (!Array.isArray(body?.items)) throw new Error('boxlore-feeds: no items');
    return body.items;
}

/** One line for the run report. */
function summary(r) {
    if (r.mode !== 'shadow' && r.mode !== 'on') return 'R5 off';
    if (r.error) return `R5 ${r.mode}: skipped (${r.error})`;
    return r.mode === 'shadow'
        ? `R5 shadow: ${r.stale} stale due, ${r.answered} answered, ${r.newer} with a newer feed episode, ${r.failedChunks} failed chunks`
        : `R5 on: ${r.stale} stale due, ${r.answered} answered, ${r.updated} tips from the feed, ${r.noItem} without a served ID yet, ${r.failedChunks} failed chunks`;
}

module.exports = { runStaleTips, staleShowIds, shouldReplace, feedIdentity, feedWindow, summary, TIP_SQL, CHUNK };
