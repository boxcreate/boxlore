'use strict';

/**
 * Which episodes of a show stage 4 embeds, and when pruning is safe.
 *
 * - Newest first for shows with no vectors yet: only the newest NEW_ENTRANT_EPISODES
 *   are embedded. The rest of the window waits NEW_ENTRANT_DEPTH_DAYS (the "depth"
 *   date, `shows[id].d` in the sync state). Most chart entrants leave before that, and
 *   chart churn was ~73% of daily embeddings.
 * - Pruning only against a full window: if PI returned fewer episodes than the cap,
 *   the list may be partial (or the show is short), so nothing is deleted; the nightly
 *   trim still enforces caps.
 *
 * Added in P1a of the own-episode-IDs plan (plan section 10b, items 5 and 5b).
 */

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Plans one show's window. Mutates `rec` (the show's sync state) when a newest-first phase starts.
 * @param {object} rec            sync state for the show (state.shows[id]); created by the caller
 * @param {number} existingCount  points the show has in Qdrant right now
 * @param {number} showCap        the cap to enforce (chart or earned)
 * @param {{ now: number, newestEpisodes: number, depthDays: number }} opts
 * @returns {{ cap: number, phase: 'normal' | 'newest' | 'depth' }}
 */
function planWindow(rec, existingCount, showCap, { now, newestEpisodes, depthDays }) {
    if (!(depthDays > 0) || newestEpisodes >= showCap) return { cap: showCap, phase: 'normal' };
    if (rec.d != null) {
        return Number(rec.d) > now
            ? { cap: Math.min(showCap, newestEpisodes), phase: 'newest' }
            : { cap: showCap, phase: 'depth' };
    }
    if (existingCount === 0) {
        rec.d = now + depthDays * DAY_MS;
        return { cap: Math.min(showCap, newestEpisodes), phase: 'newest' };
    }
    return { cap: showCap, phase: 'normal' };
}

/**
 * Shows whose depth date has passed: stage 4 flags them for one more pass at full cap.
 * Clears their `d` so it happens once. Shows no longer charted simply never get picked up.
 * @returns {string[]} podcast ids
 */
function takeDueForDepth(st, now) {
    const due = [];
    for (const [id, rec] of Object.entries(st.shows || {})) {
        if (rec && rec.d != null && Number(rec.d) <= now) {
            delete rec.d;
            due.push(id);
        }
    }
    return due;
}

/** Prune only when the list fills the window; a shorter list may be partial. */
function pruneIsSafe(windowLength, windowCap) {
    return windowLength >= windowCap;
}

/**
 * Stage 3's list for stage 4: the full fetch when it returned something, else nothing.
 * The one-item tip is never handed over as if it were the window (stage 4 would prune
 * every other vector of the show); without a handoff, stage 4 fetches the list itself.
 * @returns {object[] | null} the list to hand off, or null for none
 */
function handoffList(full, maxEps) {
    if (maxEps <= 1) return null;
    return Array.isArray(full) && full.length > 0 ? full : null;
}

module.exports = { planWindow, takeDueForDepth, pruneIsSafe, handoffList, DAY_MS };
