'use strict';

/**
 * Last-charted dates on Qdrant points (own-episode-IDs P3b, plan section 10b item 4).
 *
 * Every point in both collections carries `charted_at`: the UTC day (epoch seconds) its show was
 * last seen on a chart. The show's catalog row and state record go 7 days after it leaves the
 * charts, so the date has to live on the points for the 90-day vector retention to read it.
 * Nothing here deletes: the cleanup keeps its 7-day rule until the 90-day rule is switched on.
 *
 * Stamps come from the cleanup's chart clock (state `shows[id].s`, set daily by stage 6):
 *   - a show's points get the day of its last chart sighting;
 *   - a point is rewritten only when it has no date or its date is more than REFRESH_DAYS older
 *     than the show's, so dates only move forward and a charting show's points are rewritten
 *     about once a week, not daily;
 *   - new points get the day they were embedded (stage 4 and 5), since those shows chart.
 */

const cfg = require('./config');

const FIELD = 'charted_at';
/** Both collections: episodes name their show in podcast_id, show points in id. */
const COLLECTIONS = [
    { name: cfg.EPISODES_COLLECTION, showKey: 'podcast_id' },
    { name: cfg.PODCASTS_COLLECTION, showKey: 'id' },
];
const DAY_S = 86_400;
/** Shows per Qdrant filter call. */
const CHUNK = 500;

/** UTC day start in epoch seconds for a time in ms. */
function dayStartSec(ms) {
    return Math.floor(Number(ms) / 1000 / DAY_S) * DAY_S;
}

/**
 * Groups shows by stamp day into filter-sized batches.
 * @param {{ id: string|number }[]} pods
 * @param {object} shows state.shows (rec.s = last chart sighting, ms)
 * @returns {{ value: number, ids: number[] }[]} newest day first; shows without a sighting are skipped
 */
function planBatches(pods, shows, chunk = CHUNK) {
    const byDay = new Map();
    for (const pod of pods || []) {
        const id = Number(pod.id);
        const seen = shows?.[String(pod.id)]?.s;
        if (!Number.isSafeInteger(id) || id <= 0 || !(seen > 0)) continue;
        const day = dayStartSec(seen);
        if (!byDay.has(day)) byDay.set(day, []);
        byDay.get(day).push(id);
    }
    const out = [];
    for (const day of [...byDay.keys()].sort((a, b) => b - a)) {
        const ids = byDay.get(day).sort((a, b) => a - b);
        for (let i = 0; i < ids.length; i += chunk) out.push({ value: day, ids: ids.slice(i, i + chunk) });
    }
    return out;
}

/**
 * Points of these shows whose date is missing or more than refreshSec older than `value`.
 * @param {'podcast_id'|'id'} showKey episodes use podcast_id, the podcasts collection id
 */
function staleFilter(showKey, ids, value, refreshSec) {
    return {
        must: [{ key: showKey, match: { any: ids } }],
        should: [
            { is_empty: { key: FIELD } },
            { key: FIELD, range: { lt: value - refreshSec } },
        ],
    };
}

/**
 * Stamps both collections. Never throws: a failed batch is counted and the rest still run.
 * @param {{ setPayloadByFilter: Function, countPoints: Function }} qdrant
 * @param {{ value: number, ids: number[] }[]} batches
 * @param {{ collections: { name: string, showKey: string }[], refreshSec: number, dry?: boolean,
 *   warn?: (msg: string) => void }} opts
 * @returns {Promise<{ batches: number, failed: number, points: Record<string, number>, dry: boolean }>}
 *   points: per collection, points that needed a date (counted before writing)
 */
async function stamp(qdrant, batches, { collections, refreshSec, dry = false, warn = () => {} }) {
    const out = { batches: batches.length, failed: 0, points: {}, dry };
    for (const c of collections) out.points[c.name] = 0;
    for (const b of batches) {
        for (const c of collections) {
            const filter = staleFilter(c.showKey, b.ids, b.value, refreshSec);
            try {
                const n = await qdrant.countPoints(c.name, filter);
                out.points[c.name] += n;
                if (!dry && n > 0) await qdrant.setPayloadByFilter(c.name, { [FIELD]: b.value }, filter, true);
            } catch (e) {
                out.failed++;
                warn(`[CHARTED_AT] ${c.name}: ${b.ids.length} shows at ${new Date(b.value * 1000).toISOString().slice(0, 10)} not stamped (${e.message})`);
            }
        }
    }
    return out;
}

/** One line for the run report. */
function summary(r) {
    const pts = Object.entries(r.points).map(([name, n]) => `${name} ${n}`).join(', ');
    return `charted_at ${r.dry ? 'dry run' : 'stamped'}: ${pts} points in ${r.batches} batches` +
        (r.failed ? `, ${r.failed} failed calls` : '');
}

module.exports = { FIELD, COLLECTIONS, CHUNK, DAY_S, dayStartSec, planBatches, staleFilter, stamp, summary };
