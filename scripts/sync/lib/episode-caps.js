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

module.exports = { loadCapsByPodcastId, capsFromCountriesByItunes, ID_CHUNK };
