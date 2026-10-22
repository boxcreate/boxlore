'use strict';

/**
 * Per-show check cadence (own-episode-IDs P1b, plan section 10b items 1–2), in shadow: stage 3
 * still checks on today's tiers (lib/staleness.js) and reports what this schedule would have done.
 * P3b switches it on together with hourly runs.
 *
 * A show's interval follows how often it publishes:
 *   news                                         → 1 h
 *   dormant (newest > 180 d, and Apple agrees)   → 7 d
 *   known pace                                   → a quarter of its typical gap, 3 h to 48 h
 *   unknown pace                                 → today's tier threshold
 * Apple's latest-episode date is the safety net: a show Apple lists a newer episode for than the
 * newest one we know is due at once, until we have checked it for a few hours past that date.
 * Never-checked shows are always due. Intervals carry the same per-show jitter as today's tiers.
 *
 * Show record fields (lib/state.js): g = typical gap between episodes in hours (newest 11 dates),
 * p = newest episode date we know (epoch seconds), i = iTunes ID (for Apple's snapshot).
 */

const cfg = require('./config');
const staleness = require('./staleness');

const HOUR_MS = 3_600_000;
const NEWS_MS = HOUR_MS;
const MIN_MS = 3 * HOUR_MS;
const MAX_MS = 48 * HOUR_MS;
const DORMANT_MS = 7 * 24 * HOUR_MS;
const DORMANT_AFTER_S = 180 * 86_400;
/** Apple's answer counts as evidence for dormancy only this long after Apple last confirmed it. */
const APPLE_FRESH_S = 30 * 86_400;
/** After Apple's newer date, checks are forced this long (Podcast Index indexes hours late). */
const APPLE_FORCE_WINDOW_S = 6 * 3_600;
/** Episode dates used for a show's pace. */
const PACE_DATES = 11;

/**
 * Typical gap in hours between a show's newest 11 dated episodes: the span over the gaps. The book
 * seed (paceFromSpan) uses the same rule, so a list and the book agree. Null under 3 dates.
 */
function paceFromDates(dates) {
    const sorted = (dates || []).map(Number).filter((d) => d > 0).sort((a, b) => b - a).slice(0, PACE_DATES);
    return paceFromSpan(sorted.length, sorted[sorted.length - 1], sorted[0]);
}

/** Average gap in hours from a count and date span, or null under 3 episodes. */
function paceFromSpan(count, oldest, newest) {
    const n = Number(count);
    const span = Number(newest) - Number(oldest);
    if (!(n >= 3) || !(span >= 0)) return null;
    return Math.round((span / (n - 1) / 3600) * 10) / 10;
}

/** Records a fetched list's pace and newest date on the show record (dates in epoch seconds). */
function notePace(rec, dates) {
    const g = paceFromDates(dates);
    if (g != null) rec.g = g;
    noteNewest(rec, Math.max(0, ...(dates || []).map((d) => Number(d) || 0)));
    return rec;
}

/** Keeps the newest episode date we know. */
function noteNewest(rec, dateS) {
    const d = Number(dateS) || 0;
    if (d > (rec.p || 0)) rec.p = d;
    return rec;
}

/**
 * The shadow interval for one show.
 * @param {object} rec show record
 * @param {string} id show ID (jitter seed)
 * @param {{ nowS: number, apple?: { releaseDate: number, verifiedAt: number } | null }} ctx
 * @returns {{ ms: number, reason: 'news'|'dormant'|'pace'|'tier' }}
 */
function intervalFor(rec, id, { nowS, apple = null }) {
    const jitter = staleness.jitterFactor(id);
    if (rec.n === 1) return { ms: NEWS_MS * jitter, reason: 'news' };
    const newest = rec.p || 0;
    const appleQuiet = apple && nowS - (apple.verifiedAt || 0) <= APPLE_FRESH_S &&
        (apple.releaseDate || 0) > 0 && nowS - apple.releaseDate > DORMANT_AFTER_S;
    if (newest > 0 && nowS - newest > DORMANT_AFTER_S && appleQuiet) {
        return { ms: DORMANT_MS * jitter, reason: 'dormant' };
    }
    if (rec.g != null) {
        const ms = Math.min(MAX_MS, Math.max(MIN_MS, (rec.g * HOUR_MS) / 4));
        return { ms: ms * jitter, reason: 'pace' };
    }
    return { ms: staleness.thresholdMs(rec, id), reason: 'tier' };
}

/** True when Apple lists a newer episode than we know and we haven't checked well past its date. */
function appleForces(rec, apple) {
    if (!apple || !(apple.releaseDate > 0)) return false;
    const lastCheckS = (rec.c || 0) / 1000;
    return apple.releaseDate > (rec.p || 0) + 3600 && lastCheckS < apple.releaseDate + APPLE_FORCE_WINDOW_S;
}

/**
 * What the cadence would check now, over every candidate (no per-run cap).
 * @param {string[]} candidateIds
 * @param {object} shows state.shows
 * @param {{ now?: number, appleOf?: (itunesId: number) => ({ releaseDate: number, verifiedAt: number } | null) }} [opts]
 */
function shadowPlan(candidateIds, shows, { now = Date.now(), appleOf = () => null } = {}) {
    const nowS = now / 1000;
    const due = new Set();
    const dueAt = new Map();
    const counts = { never: 0, appleForced: 0, news: 0, dormant: 0, pace: 0, tier: 0 };
    let perDay = 0;
    let perDayNow = 0;
    for (const id of candidateIds || []) {
        const rec = shows[id] || {};
        const apple = rec.i ? appleOf(Number(rec.i)) : null;
        const { ms, reason } = intervalFor(rec, id, { nowS, apple });
        counts[reason]++;
        // Checks a day with hourly runs (no show more than once an hour), against today's tiers.
        perDay += 24 / Math.max(1, ms / HOUR_MS);
        perDayNow += 24 / (staleness.thresholdMs(rec, id) / HOUR_MS);
        if (!rec.c) {
            counts.never++;
            due.add(id);
            continue;
        }
        if (appleForces(rec, apple)) {
            counts.appleForced++;
            due.add(id);
            continue;
        }
        const at = rec.c + ms;
        dueAt.set(id, at);
        if (now >= at) due.add(id);
    }
    return { due, dueAt, counts, perDay: Math.round(perDay), perDayNow: Math.round(perDayNow) };
}

/**
 * Shadow against what this run did.
 * @param {ReturnType<typeof shadowPlan>} plan
 * @param {string[]} currentDue every show today's tiers made due (before the per-run cap)
 * @param {string[]} newTipIds shows whose tip changed this run
 */
function compare(plan, currentDue, newTipIds, now = Date.now()) {
    const current = new Set(currentDue.map(String));
    let skipped = 0;
    for (const id of current) if (!plan.due.has(id)) skipped++;
    let earlier = 0;
    for (const id of plan.due) if (!current.has(id)) earlier++;
    const late = [];
    for (const id of new Set(newTipIds.map(String))) {
        if (plan.due.has(id)) continue;
        const at = plan.dueAt.get(id);
        late.push(at == null ? 0 : Math.max(0, (at - now) / HOUR_MS));
    }
    late.sort((a, b) => a - b);
    return {
        wouldCheck: plan.due.size,
        current: current.size,
        skipped,
        earlier,
        late: late.length,
        lateMedianH: late.length ? late[late.length >> 1] : 0,
        lateMaxH: late.length ? late[late.length - 1] : 0,
    };
}

/** Shows per book read: each answer is at most this many rows (sqld's RESPONSE_TOO_LARGE). */
const BOOK_CHUNK = 400;

/** Pace for a chunk of shows from their newest 11 dated book episodes (index `episode_book_sid_date`). */
function bookPaceSql(n) {
    return `
    SELECT sid, COUNT(*), MIN(published_at), MAX(published_at)
    FROM (SELECT sid, published_at,
                 ROW_NUMBER() OVER (PARTITION BY sid ORDER BY published_at DESC) AS rn
          FROM episode_book WHERE sid IN (${Array(n).fill('?').join(',')}) AND published_at > 0)
    WHERE rn <= ${PACE_DATES}
    GROUP BY sid`;
}

/**
 * Seeds g and p for candidate shows from the book (stage 3's saved windows and the bootstrap), so
 * the shadow has a pace for shows whose tip hasn't changed since it started.
 * @returns {number} shows seeded
 */
async function seedFromBook(book, shows, candidateIds) {
    const ids = [...new Set((candidateIds || []).map(String))].filter((id) => /^[1-9][0-9]*$/.test(id));
    let seeded = 0;
    for (let i = 0; i < ids.length; i += BOOK_CHUNK) {
        const chunk = ids.slice(i, i + BOOK_CHUNK);
        for (const [sid, count, oldest, newest] of await book.query(bookPaceSql(chunk.length), chunk.map(Number))) {
            const rec = shows[String(sid)] || (shows[String(sid)] = {});
            const g = paceFromSpan(count, oldest, newest);
            if (g != null) rec.g = g;
            noteNewest(rec, newest);
            seeded++;
        }
    }
    return seeded;
}

/**
 * Apple's latest-episode snapshot (applefeeds `apple_latest.db`), read-only. Returns a lookup, or
 * null with the reason when the file can't be opened (the shadow then runs without Apple).
 */
function openApple(path) {
    try {
        const { DatabaseSync } = require('node:sqlite');
        const db = new DatabaseSync(path, { readOnly: true });
        const st = db.prepare('SELECT release_date, verified_at FROM apple_latest WHERE itunes_id = ?');
        return {
            lookup: (itunesId) => {
                const row = st.get(itunesId);
                return row ? { releaseDate: Number(row.release_date) || 0, verifiedAt: Number(row.verified_at) || 0 } : null;
            },
            close: () => db.close(),
            error: null,
        };
    } catch (e) {
        return { lookup: () => null, close: () => {}, error: e.message };
    }
}

const fmtH = (h) => (h >= 10 ? String(Math.round(h)) : h.toFixed(1));

/** One line for the run report. */
function summary(mode, plan, cmp, { appleError = null, paceShows = 0, candidates = 0 } = {}) {
    if (mode !== 'shadow' || !plan) return 'cadence off';
    const c = plan.counts;
    const pct = candidates ? Math.round((paceShows / candidates) * 100) : 0;
    return `cadence shadow: would check ${cmp.wouldCheck} (today ${cmp.current}; ${cmp.skipped} skipped, ${cmp.earlier} earlier, ` +
        `${c.appleForced} Apple-forced, ${c.never} never checked) · ${cmp.late} new tips not yet due` +
        (cmp.late ? ` (median ${fmtH(cmp.lateMedianH)} h, max ${fmtH(cmp.lateMaxH)} h late)` : '') +
        ` · checks/day ${plan.perDay} vs ${plan.perDayNow} · news ${c.news}, dormant ${c.dormant}, pace ${c.pace}, tier ${c.tier}` +
        ` · pace known ${pct}%` + (appleError ? ` · Apple unread (${appleError})` : '');
}

module.exports = {
    paceFromDates,
    paceFromSpan,
    notePace,
    noteNewest,
    intervalFor,
    appleForces,
    shadowPlan,
    compare,
    seedFromBook,
    openApple,
    summary,
    BOOK_CHUNK,
    HOUR_MS,
};
