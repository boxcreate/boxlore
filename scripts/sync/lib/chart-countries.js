'use strict';

/**
 * Chart country lookups without CAST(charts.itunes_id AS INTEGER).
 *
 * charts.itunes_id is TEXT; podcasts.itunes_id is INTEGER. Joining with
 * CAST(c.itunes_id AS INTEGER) disables the charts index and turns correlated
 * subqueries into multi-billion rows_read. Prefer:
 *   c.itunes_id = CAST(p.itunes_id AS TEXT)
 * or pre-aggregate charts once and join in JS.
 */

const cfg = require('./config');

/** SQL predicate: charts row matches podcast.itunes_id (index-friendly). */
const CHARTS_MATCH_PODCAST = 'c.itunes_id = CAST(p.itunes_id AS TEXT)';

/**
 * Page-load itunes_id (TEXT) → comma-separated lower(country) for all chart rows.
 * One linear scan of charts (~rows ≈ chart row count), not per-podcast.
 *
 * @param {{ fetchAllPaged: Function }} turso
 * @returns {Promise<Map<string, string>>}
 */
async function loadCountriesByItunesId(turso) {
    const pageSize = cfg.TURSO_PAGE_SIZE;
    // mapArgType turns '' into SQL NULL, and `itunes_id > NULL` matches nothing.
    // Chart itunes_ids are digit strings; '!' sorts before '0' so the first page is complete.
    const FIRST = '!';
    const rows = await turso.fetchAllPaged({
        pageSize,
        rowId: (r) => String(r[0]),
        buildPage: (after, limit) => ({
            sql: `
                SELECT itunes_id, GROUP_CONCAT(DISTINCT lower(country))
                FROM charts
                WHERE itunes_id IS NOT NULL
                  AND itunes_id > ?
                GROUP BY itunes_id
                ORDER BY itunes_id ASC
                LIMIT ?
            `,
            args: [after == null ? FIRST : String(after), limit],
        }),
    });
    const map = new Map();
    for (const [itunesId, csv] of rows) {
        if (itunesId == null || itunesId === '') continue;
        map.set(String(itunesId), csv || '');
    }
    return map;
}

/**
 * Keep podcast rows that appear on charts; attach country CSV.
 * @param {Array<[any, any, any, any, any]>} podcastRows
 *   [id, latest_ep_id, categories, medium, itunes_id]
 * @param {Map<string, string>} countriesByItunes
 * @returns {Array<{ id: string, latestEpId: any, categories: any, medium: any, countryCsv: string }>}
 */
function mergePodcastRowsWithCountries(podcastRows, countriesByItunes) {
    const out = [];
    for (const row of podcastRows) {
        const [id, latestEpId, categories, medium, itunesId] = row;
        if (itunesId == null || itunesId === '') continue;
        const countryCsv = countriesByItunes.get(String(itunesId));
        if (countryCsv === undefined) continue;
        out.push({
            id: String(id),
            latestEpId,
            categories,
            medium,
            countryCsv,
        });
    }
    return out;
}

module.exports = {
    CHARTS_MATCH_PODCAST,
    loadCountriesByItunesId,
    mergePodcastRowsWithCountries,
};
