'use strict';

/**
 * Central pipeline configuration.
 * Countries: tier 'full' = charts + episode sync + vectors.
 *            tier 'charts-only' = charts + podcast import + latest_ep sync, NO vectors.
 * Episode-check cadence (independent of vector tier):
 *   check 'core'     → non-News shows rechecked every REGULAR_STALE_MS (24h)
 *   check 'relaxed'  → non-News shows rechecked every RELAXED_STALE_MS (48h)
 * News (any country) → NEWS_STALE_MS (8h). Show on both core+relaxed → core wins.
 *
 * Phase 2 country additions happen HERE only - no country knowledge lives in
 * the workflow YAML or individual stage scripts.
 *
 * Embedder: EMBED_PROVIDER=bge (default, GHA/cloud) or qwen (VPS local ONNX).
 * Episode caps: us/gb = 50, else 20 (must match VPS Qwen backfill).
 */
const COUNTRIES = [
    { code: 'us', tier: 'full', check: 'core' },
    { code: 'in', tier: 'full', check: 'core' },
    { code: 'gb', tier: 'full', check: 'core' },
    { code: 'fr', tier: 'full', check: 'core' },
    // Secondary storefronts — slower episode tip checks to cut PI load
    { code: 'de', tier: 'full', check: 'relaxed' },
    { code: 'nl', tier: 'full', check: 'relaxed' },
    { code: 'sg', tier: 'full', check: 'relaxed' },
    { code: 'es', tier: 'full', check: 'relaxed' },
    { code: 'br', tier: 'full', check: 'relaxed' },
    { code: 'ru', tier: 'full', check: 'relaxed' },
    { code: 'id', tier: 'full', check: 'relaxed' },
];

const FULL_TIER_COUNTRIES = COUNTRIES.filter(c => c.tier === 'full').map(c => c.code);
const ALL_COUNTRIES = COUNTRIES.map(c => c.code);
const CORE_CHECK_COUNTRIES = COUNTRIES.filter(c => c.check === 'core').map(c => c.code);
const RELAXED_CHECK_COUNTRIES = COUNTRIES.filter(c => c.check === 'relaxed').map(c => c.code);

/** Per-storefront episode vector caps (latest N). */
const EPISODE_CAP_BY_COUNTRY = Object.freeze({
    us: 50,
    gb: 50,
});
const EPISODE_CAP_DEFAULT = 20;
const EPISODE_CAP_MAX = Math.max(EPISODE_CAP_DEFAULT, ...Object.values(EPISODE_CAP_BY_COUNTRY));

/**
 * Cap for a show given the chart countries it appears on.
 * Uses the max cap across those countries (us/gb on the chart → 50).
 * @param {string[]} countries
 */
function episodeCapForCountries(countries) {
    let cap = EPISODE_CAP_DEFAULT;
    for (const c of countries || []) {
        const n = EPISODE_CAP_BY_COUNTRY[String(c).toLowerCase()] || EPISODE_CAP_DEFAULT;
        if (n > cap) cap = n;
    }
    return cap;
}

// iTunes chart categories (genre id map used by stage 1)
const GENRE_MAP = {
    'News': '1489',
    'Technology': '1318',
    'Business': '1321',
    'Comedy': '1303',
    'True Crime': '1488',
    'Sports': '1545',
    'Health': '1512',
    'History': '1487',
    'Arts': '1301',
    'Society & Culture': '1324',
    'Education': '1304',
    'Science': '1533',
    'TV & Film': '1309',
    'Fiction': '1483',
    'Music': '1310',
    'Religion & Spirituality': '1314',
    'Kids & Family': '1305',
    'Leisure': '1502',
    'Government': '1511',
};
const CATEGORIES = ['all', ...Object.keys(GENRE_MAP)];

const EMBED_PROVIDER = (process.env.EMBED_PROVIDER || 'bge').toLowerCase();
const EMBED_MODEL = EMBED_PROVIDER === 'qwen'
    ? (process.env.EMBED_MODEL || 'onnx-community/Qwen3-Embedding-0.6B-ONNX')
    : (process.env.EMBED_MODEL || 'Xenova/bge-m3');

module.exports = {
    COUNTRIES,
    ALL_COUNTRIES,
    FULL_TIER_COUNTRIES,
    CORE_CHECK_COUNTRIES,
    RELAXED_CHECK_COUNTRIES,
    GENRE_MAP,
    CATEGORIES,

    EPISODE_CAP_BY_COUNTRY,
    EPISODE_CAP_DEFAULT,
    EPISODE_CAP_MAX,
    episodeCapForCountries,
    /** @deprecated use episodeCapForCountries — kept as max for naive callers */
    EPISODES_PER_SHOW: EPISODE_CAP_MAX,

    // --- Vectorization ---
    // bge (GHA/cloud): mean pool. qwen (VPS): last_token, no Instruct on docs.
    EMBED_PROVIDER,
    EMBED_MODEL,
    EMBED_DTYPE: process.env.EMBED_DTYPE || 'q8',
    EMBED_CACHE_DIR: process.env.EMBED_CACHE_DIR || './.cache',
    EPISODES_COLLECTION: 'episodes',
    PODCASTS_COLLECTION: 'podcasts',
    VECTOR_DIM: 1024,
    MAX_EMBEDDINGS_PER_RUN: parseInt(process.env.MAX_EMBEDDINGS_PER_RUN || '6000', 10),
    PAYLOAD_DESCRIPTION_MAX: 1000,
    // HTTP SELECT page size (sqld RESPONSE_TOO_LARGE guard). Override via TURSO_PAGE_SIZE.
    TURSO_PAGE_SIZE: Math.max(50, parseInt(process.env.TURSO_PAGE_SIZE || '300', 10) || 300),
    // Extra pending rows to pull beyond the embed budget (already-in-Qdrant flag flips).
    SHOW_VEC_FETCH_SLACK: parseInt(process.env.SHOW_VEC_FETCH_SLACK || '500', 10),

    // --- Episode sync staleness tiers ---
    NEWS_STALE_MS: 8 * 60 * 60 * 1000,
    REGULAR_STALE_MS: 24 * 60 * 60 * 1000,
    RELAXED_STALE_MS: 48 * 60 * 60 * 1000,
    MAX_CHECKS_PER_RUN: parseInt(process.env.MAX_CHECKS_PER_RUN || '4000', 10),
    STALENESS_JITTER: 0.10,

    // --- Import ---
    DUMP_THRESHOLD: 300,
    API_IMPORT_CAP: 200,
    PI_UNAVAILABLE_TTL_MS: 30 * 24 * 60 * 60 * 1000,

    // --- Cleanup ---
    CLEANUP_GRACE_DAYS: 7,
    CLEANUP_SAFETY_MIN_CHARTS: 500,

    // --- State / files ---
    STATE_FILE: 'scripts/data/sync_cache.json',
    PI_UNAVAILABLE_FILE: 'scripts/data/pi_unavailable_itunes_ids.json',
    HISTORY_FILE: 'scripts/data/db_cost_history.json',
    REPORT_FILE: 'scripts/data/db_cost_report.md',
    RUN_STATS_FILE: '/tmp/db_run_stats.json',
    PI_EPISODE_HANDOFF_FILE: process.env.PI_EPISODE_HANDOFF_FILE || '/tmp/boxlore_pi_episode_handoff.json',

    PODCAST_IMPORT_COLUMNS: [
        'id', 'itunes_id', 'title', 'author', 'description', 'image_url',
        'feed_url', 'website_url', 'categories', 'language', 'explicit', 'type',
    ],
};
