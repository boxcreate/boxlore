'use strict';

/**
 * Load podcast_id → episode cap from chart countries (us/gb=50, else 20).
 */

const turso = require('./turso');
const cfg = require('./config');

/**
 * @param {string[]} [podcastIds] optional filter; omit for all chart shows in full-tier countries
 * @returns {Promise<Map<string, number>>}
 */
async function loadCapsByPodcastId(podcastIds) {
    const countries = cfg.FULL_TIER_COUNTRIES;
    const ph = countries.map(() => '?').join(',');
    let sql = `
        SELECT CAST(p.id AS TEXT), GROUP_CONCAT(DISTINCT c.country)
        FROM podcasts p
        INNER JOIN charts c ON CAST(c.itunes_id AS INTEGER) = p.itunes_id
        WHERE c.country IN (${ph})
    `;
    const args = [...countries];
    if (podcastIds && podcastIds.length > 0) {
        const idPh = podcastIds.map(() => '?').join(',');
        sql += ` AND p.id IN (${idPh})`;
        args.push(...podcastIds.map(String));
    }
    sql += ' GROUP BY p.id';

    const res = await turso.execute(sql, args);
    const map = new Map();
    for (const [id, countryCsv] of turso.rows(res)) {
        const list = String(countryCsv || '')
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean);
        map.set(String(id), cfg.episodeCapForCountries(list));
    }
    return map;
}

module.exports = { loadCapsByPodcastId };
