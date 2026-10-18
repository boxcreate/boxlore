'use strict';

/**
 * Load podcast_id → episode cap from chart countries (us/gb=50, else 20).
 */

const turso = require('./turso');
const cfg = require('./config');
const { CHARTS_MATCH_PODCAST, countriesForItunes } = require('./chart-countries');

/** Keep Turso IN-lists bounded (sqld / planner friendly). */
const ID_CHUNK = 400;

/**
 * Derive caps from a preloaded charts map (no Turso join).
 * @param {Array<{ id: string|number, itunesId?: any, itunes_id?: any }>} items
 * @param {Map<string, string>} countriesByItunes
 * @param {Iterable<string>|Set<string>} [allowCountries]
 * @returns {Map<string, number>}
 */
function capsFromCountriesByItunes(
    items,
    countriesByItunes,
    allowCountries = cfg.FULL_TIER_COUNTRIES,
) {
    const map = new Map();
    for (const item of items || []) {
        const id = item?.id;
        if (id == null || id === '') continue;
        const itunesId = item.itunesId ?? item.itunes_id;
        const list = countriesForItunes(countriesByItunes, itunesId, allowCountries);
        if (!list.length) continue;
        map.set(String(id), cfg.episodeCapForCountries(list));
    }
    return map;
}

/**
 * @param {string[]} [podcastIds] optional filter; omit for all chart shows in full-tier countries
 * @returns {Promise<Map<string, number>>}
 */
async function loadCapsByPodcastId(podcastIds) {
    const countries = cfg.FULL_TIER_COUNTRIES;
    const ph = countries.map(() => '?').join(',');
    // Join on CAST(p.itunes_id AS TEXT) — charts.itunes_id is TEXT; casting the
    // charts column to INTEGER forces a full charts scan per join.
    const baseSql = `
        SELECT CAST(p.id AS TEXT), GROUP_CONCAT(DISTINCT c.country)
        FROM podcasts p
        INNER JOIN charts c ON ${CHARTS_MATCH_PODCAST}
        WHERE c.country IN (${ph})
    `;

    const map = new Map();
    const ingest = (res) => {
        for (const [id, countryCsv] of turso.rows(res)) {
            const list = String(countryCsv || '')
                .split(',')
                .map((s) => s.trim())
                .filter(Boolean);
            map.set(String(id), cfg.episodeCapForCountries(list));
        }
    };

    if (podcastIds && podcastIds.length > 0) {
        for (let i = 0; i < podcastIds.length; i += ID_CHUNK) {
            const chunk = podcastIds.slice(i, i + ID_CHUNK).map(String);
            const idPh = chunk.map(() => '?').join(',');
            const res = await turso.execute(
                `${baseSql} AND p.id IN (${idPh}) GROUP BY p.id`,
                [...countries, ...chunk],
            );
            ingest(res);
        }
        return map;
    }

    const res = await turso.execute(`${baseSql} GROUP BY p.id`, countries);
    ingest(res);
    return map;
}

/**
 * Retention-aware caps: a show keeps the highest cap it earned while its vectors are kept.
 *
 * Without this, a US/GB show that leaves those charts (or every chart, during its grace
 * window) drops from 50 to 20, loses 30 vectors, and re-embeds them if it comes back.
 * The earned cap lives in the per-show sync state (`shows[id].k`, see state.js), which
 * stage 6 deletes together with the show's vectors, so it lasts exactly as long as they do.
 *
 * Added in P1a of the own-episode-IDs plan (plan section 10b, "cap drop on chart exit").
 */

/**
 * Raises each show's earned cap to its current chart cap. Returns how many were raised.
 * @param {{ shows: Record<string, object> }} st sync state (state.load())
 * @param {Map<string, number>} chartCaps podcast id → cap from today's charts
 */
function rememberEarnedCaps(st, chartCaps) {
    let raised = 0;
    for (const [id, cap] of chartCaps) {
        const rec = st.shows[String(id)] || (st.shows[String(id)] = {});
        if (!(Number(rec.k) >= cap)) {
            rec.k = cap;
            raised++;
        }
    }
    return raised;
}

/**
 * The cap to enforce: the larger of today's chart cap and the earned cap, else the default.
 * @param {string|number} id
 * @param {Map<string, number>} chartCaps
 * @param {{ shows?: Record<string, object> } | null} st
 */
function effectiveCap(id, chartCaps, st) {
    const chart = chartCaps.get(String(id)) || 0;
    const earned = Number(st?.shows?.[String(id)]?.k) || 0;
    const best = Math.max(chart, earned);
    return best > 0 ? best : cfg.EPISODE_CAP_DEFAULT;
}

module.exports = { loadCapsByPodcastId, capsFromCountriesByItunes, rememberEarnedCaps, effectiveCap, ID_CHUNK };
