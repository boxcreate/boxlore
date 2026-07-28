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
/**
 * First-page keyset for charts.itunes_id (TEXT).
 * turso.mapArgType turns '' into SQL NULL, and `itunes_id > NULL` matches nothing —
 * that made Stage 2 report 0 chart shows. Digit itunes ids sort after '!'.
 */
const CHARTS_ITUNES_FIRST_CURSOR = '!';

async function loadCountriesByItunesId(turso) {
    const pageSize = cfg.TURSO_PAGE_SIZE;
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
            args: [
                after == null ? CHARTS_ITUNES_FIRST_CURSOR : String(after),
                limit,
            ],
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
 * @param {Iterable<string>|Set<string>|null|undefined} allowCountries
 * @returns {Set<string>|null} lowercased allowlist, or null = any chart country
 */
function toCountryAllowSet(allowCountries) {
    if (allowCountries == null) return null;
    if (allowCountries instanceof Set) {
        if (allowCountries.size === 0) return null;
        const out = new Set();
        for (const c of allowCountries) out.add(String(c).toLowerCase());
        return out;
    }
    const out = new Set();
    for (const c of allowCountries) out.add(String(c).toLowerCase());
    return out.size ? out : null;
}

/**
 * True when itunes_id appears on charts (optionally restricted to allowlist).
 * @param {Map<string, string>} countriesByItunes
 * @param {string|number|null|undefined} itunesId
 * @param {Iterable<string>|Set<string>|null|undefined} [allowCountries]
 */
function itunesInCountries(countriesByItunes, itunesId, allowCountries) {
    if (itunesId == null || itunesId === '') return false;
    const csv = countriesByItunes.get(String(itunesId));
    if (csv === undefined) return false;
    const allow = toCountryAllowSet(allowCountries);
    if (!allow) return true;
    return String(csv)
        .split(',')
        .some((c) => allow.has(c.trim().toLowerCase()));
}

/**
 * Chart countries for an itunes_id, optionally intersected with allowlist.
 * @param {Map<string, string>} countriesByItunes
 * @param {string|number|null|undefined} itunesId
 * @param {Iterable<string>|Set<string>|null|undefined} [allowCountries]
 * @returns {string[]}
 */
function countriesForItunes(countriesByItunes, itunesId, allowCountries) {
    if (itunesId == null || itunesId === '') return [];
    const csv = countriesByItunes.get(String(itunesId));
    if (csv === undefined || csv === '') return [];
    const allow = toCountryAllowSet(allowCountries);
    const out = [];
    for (const part of String(csv).split(',')) {
        const c = part.trim().toLowerCase();
        if (!c) continue;
        if (allow && !allow.has(c)) continue;
        out.push(c);
    }
    return out;
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
    CHARTS_ITUNES_FIRST_CURSOR,
    loadCountriesByItunesId,
    itunesInCountries,
    countriesForItunes,
    mergePodcastRowsWithCountries,
};
