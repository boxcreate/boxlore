'use strict';

/**
 * Episode-check staleness helpers (stage 3).
 *
 * Tiers:
 *   news     — News category, any country → NEWS_STALE_MS (8h)
 *   core     — on ≥1 core check country, non-News → REGULAR_STALE_MS (24h)
 *   relaxed  — only on relaxed check countries, non-News → RELAXED_STALE_MS (48h)
 *
 * State flags on show rec: n=1 (news), x=1 (relaxed-only). Core wins over relaxed.
 */

const cfg = require('./config');

const CORE_SET = new Set(cfg.CORE_CHECK_COUNTRIES.map((c) => String(c).toLowerCase()));

function hoursLabel(ms) {
    const h = ms / 3_600_000;
    return Number.isInteger(h) ? `${h}h` : `${h.toFixed(1)}h`;
}

function policySummary() {
    const j = Math.round(cfg.STALENESS_JITTER * 100);
    return {
        news: hoursLabel(cfg.NEWS_STALE_MS),
        core: hoursLabel(cfg.REGULAR_STALE_MS),
        relaxed: hoursLabel(cfg.RELAXED_STALE_MS),
        jitterPct: j,
        label:
            `News ${hoursLabel(cfg.NEWS_STALE_MS)} · core countries ${hoursLabel(cfg.REGULAR_STALE_MS)} · ` +
            `relaxed countries ${hoursLabel(cfg.RELAXED_STALE_MS)} (±${j}% jitter)`,
        coreCountries: [...cfg.CORE_CHECK_COUNTRIES],
        relaxedCountries: [...cfg.RELAXED_CHECK_COUNTRIES],
    };
}

/**
 * @param {string|string[]} countryCsvOrList
 * @returns {boolean} true if show is only on relaxed countries (no core)
 */
function isRelaxedOnly(countryCsvOrList) {
    const list = Array.isArray(countryCsvOrList)
        ? countryCsvOrList
        : String(countryCsvOrList || '')
            .split(',')
            .map((s) => s.trim().toLowerCase())
            .filter(Boolean);
    if (!list.length) return false; // unknown → treat as core (safer / more frequent)
    return !list.some((c) => CORE_SET.has(c));
}

/**
 * Apply country-driven flags onto a state show record.
 * @param {object} rec
 * @param {string|string[]} countryCsvOrList
 */
function applyCountryCheckFlag(rec, countryCsvOrList) {
    if (isRelaxedOnly(countryCsvOrList)) rec.x = 1;
    else delete rec.x;
    return rec;
}

/** @returns {'news'|'core'|'relaxed'} */
function checkTier(rec) {
    if (rec?.n === 1) return 'news';
    if (rec?.x === 1) return 'relaxed';
    return 'core';
}

function jitterFactor(id) {
    let h = 0;
    const s = String(id);
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
    return 1 + ((h % 1000) / 1000 - 0.5) * 2 * cfg.STALENESS_JITTER;
}

function thresholdMs(rec, id) {
    const tier = checkTier(rec);
    const base =
        tier === 'news' ? cfg.NEWS_STALE_MS
            : tier === 'relaxed' ? cfg.RELAXED_STALE_MS
                : cfg.REGULAR_STALE_MS;
    return base * jitterFactor(id);
}

/**
 * @param {string[]} candidateIds
 * @param {object} shows state.shows
 * @param {number} now
 */
function planDue(candidateIds, shows, now = Date.now()) {
    let neverChecked = 0;
    let staleNews = 0;
    let staleCore = 0;
    let staleRelaxed = 0;
    let poolNews = 0;
    let poolCore = 0;
    let poolRelaxed = 0;

    const allDue = [];
    for (const id of candidateIds || []) {
        const rec = shows[id] || {};
        const tier = checkTier(rec);
        if (tier === 'news') poolNews++;
        else if (tier === 'relaxed') poolRelaxed++;
        else poolCore++;

        if (!rec.c) {
            neverChecked++;
            allDue.push(id);
            continue;
        }
        if (now - rec.c >= thresholdMs(rec, id)) {
            if (tier === 'news') staleNews++;
            else if (tier === 'relaxed') staleRelaxed++;
            else staleCore++;
            allDue.push(id);
        }
    }

    allDue.sort((a, b) => (shows[a]?.c || 0) - (shows[b]?.c || 0));
    return {
        allDue,
        neverChecked,
        staleNews,
        staleCore,
        staleRelaxed,
        poolNews,
        poolCore,
        poolRelaxed,
    };
}

module.exports = {
    hoursLabel,
    policySummary,
    isRelaxedOnly,
    applyCountryCheckFlag,
    checkTier,
    jitterFactor,
    thresholdMs,
    planDue,
    CORE_SET,
};
