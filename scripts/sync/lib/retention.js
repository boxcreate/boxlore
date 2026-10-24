'use strict';

/**
 * Vector retention after a show leaves the charts (own-episode-IDs P3b, plan section 10b item 4).
 *
 * Decided 24 Oct (user): keep each departed show's newest ARCHIVE_EPISODES (5) episode points and
 * its show vector for RETENTION_DAYS (90) after it was last on a chart, with Qdrant's memory limit
 * raised to 6 GB. Catalog rows keep today's 7-day lifetime; identity (the episode book) is kept for
 * good. The date comes from `charted_at` on each point (lib/charted-at.js).
 *
 * RETENTION_MODE (stage 6):
 *   off   — today's behaviour: a show past the 7-day grace loses its vectors with its catalog row.
 *   count — today's behaviour, and the run report says what `on` would keep, trim and expire.
 *   on    — a show past the grace keeps its newest 5 episodes and its show vector; points whose
 *           show was last on a chart more than RETENTION_DAYS ago are deleted.
 *
 * Size guard: past POINTS_CEILING episode points, retention steps down (90 → 60 → 30 days) until
 * the index fits; past POINTS_ALERT the run reports it, and an urgent alert goes out while the
 * index stays over the ceiling.
 */

const cfg = require('./config');

const DAY_S = 86_400;

/** Retention steps for the size guard: the configured days, then 60 and 30 when shorter. */
function retentionSteps(days = cfg.RETENTION_DAYS) {
    return [...new Set([days, 60, 30].filter((d) => d <= days && d > 0))].sort((a, b) => b - a);
}

/**
 * Which of a departed show's episode points stay: the newest `keep` by published date.
 * @param {{ id: string, date: number }[]} points
 * @returns {{ keep: string[], drop: string[] }}
 */
function archiveSplit(points, keep) {
    const sorted = [...(points || [])].sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
    return { keep: sorted.slice(0, keep).map((p) => p.id), drop: sorted.slice(keep).map((p) => p.id) };
}

/** Points last charted before `cutoffSec`. Points without a date never match. */
function expiryFilter(cutoffSec) {
    return { must: [{ key: 'charted_at', range: { lt: cutoffSec } }] };
}

/**
 * Archives departed shows in place: deletes all but each show's newest `keep` episode points.
 * The show vector stays. Throws on a Qdrant failure (the caller treats the chunk as failed).
 * @param {{ scrollAll: Function, deleteByIds: Function }} qdrant
 * @param {number[]} showIds
 * @returns {Promise<Map<string, { kept: number, dropped: { id: string, date: number, eid: number }[] }>>}
 */
async function archiveShows(qdrant, showIds, { keep, dry = false, collection = cfg.EPISODES_COLLECTION } = {}) {
    const byShow = new Map(showIds.map((id) => [String(id), []]));
    if (!showIds.length) return new Map();
    const points = await qdrant.scrollAll(collection,
        { must: [{ key: 'podcast_id', match: { any: showIds } }] },
        ['podcast_id', 'published_date', 'id'], 1000);
    for (const p of points) {
        const sid = String(p.payload?.podcast_id);
        if (byShow.has(sid)) byShow.get(sid).push({ id: p.id, date: p.payload?.published_date || 0, eid: p.payload?.id });
    }
    const out = new Map();
    const dropIds = [];
    for (const [sid, pts] of byShow) {
        const { keep: kept, drop } = archiveSplit(pts, keep);
        const dropSet = new Set(drop);
        out.set(sid, { kept: kept.length, dropped: pts.filter((p) => dropSet.has(p.id)) });
        dropIds.push(...drop);
    }
    if (!dry) {
        for (let i = 0; i < dropIds.length; i += 500) await qdrant.deleteByIds(collection, dropIds.slice(i, i + 500), true);
    }
    return out;
}

/**
 * Deletes expired points and applies the size guard. In count mode nothing is deleted and the
 * result says what the first step would expire.
 * @param {{ countPoints: Function, deleteByFilter: Function, facetCounts?: Function }} qdrant
 * @param {{ mode: 'count'|'on', nowSec: number, steps: number[], ceiling: number, alert: number,
 *   collections: string[], onExpired?: (collection: string, filter: object) => Promise<void> }} opts
 * @returns {Promise<{ mode: string, retentionDays: number, expired: Record<string, number>,
 *   total: number, overAlert: boolean, overCeiling: boolean }>}
 */
async function enforce(qdrant, { mode, nowSec, steps, ceiling, alert, collections, onExpired = async () => {} }) {
    const episodes = collections[0];
    const out = { mode, retentionDays: steps[0], expired: Object.fromEntries(collections.map((c) => [c, 0])), total: 0, overAlert: false, overCeiling: false };
    for (let i = 0; i < steps.length; i++) {
        const filter = expiryFilter(nowSec - steps[i] * DAY_S);
        out.retentionDays = steps[i];
        for (const c of collections) {
            const n = await qdrant.countPoints(c, filter);
            if (mode === 'on' && n > 0) {
                await onExpired(c, filter);
                await qdrant.deleteByFilter(c, filter, true);
            }
            out.expired[c] += n;
        }
        out.total = await qdrant.countPoints(episodes);
        // Count mode never steps down: it reports the index as it is.
        if (mode !== 'on' || out.total <= ceiling) break;
    }
    out.overCeiling = out.total > ceiling;
    out.overAlert = out.total > alert;
    return out;
}

/** One line for the run report. */
function summary(mode, { archived = 0, kept = 0, trimmed = 0 } = {}, r = null) {
    if (mode === 'off') return 'retention off';
    const on = mode === 'on';
    const parts = [`retention ${mode}: ${on ? 'kept' : 'would keep'} ${archived} departed shows (${kept} episodes; ${trimmed} older ones ${on ? 'deleted' : 'would go'})`];
    if (r) {
        const exp = Object.entries(r.expired).map(([c, n]) => `${c} ${n}`).join(', ');
        parts.push(`${mode === 'on' ? 'expired' : 'past'} ${r.retentionDays} d: ${exp}`);
        parts.push(`${r.total} episode points${r.overCeiling ? ' (over the ceiling)' : r.overAlert ? ' (over the alert level)' : ''}`);
    }
    return parts.join(' · ');
}

module.exports = { DAY_S, retentionSteps, archiveSplit, expiryFilter, archiveShows, enforce, summary };
