'use strict';

/**
 * Episode recipe IDs: boxlore's own episode identifiers (recipe "boxlore-ep1").
 *
 * An episode is identified by (show ID, catalog key). The catalog key is the
 * episode's GUID, or its audio URL when the GUID is blank, trimmed with exactly
 * the characters Kotlin's String.trim() removes on Android: the JVM's whitespace
 * plus U+180E, which Android's libcore still treats as whitespace. Do not use
 * String#trim: it also strips U+FEFF and keeps U+001C..U+001F and U+180E, so keys
 * would differ from the ones the app stored.
 *
 *   recipeId = 2^52 + (first 8 bytes of SHA-256("boxlore-ep1" \0 showId \0 key),
 *                      read big-endian, AND 2^52 - 1)
 *
 * The result is a compact handle, not a unique key: anything that stores a
 * recipe ID keeps the catalog key beside it and refuses to merge two different
 * keys that share one ID. Every implementation (app, Worker, sync, notifier,
 * feeds service) must reproduce core/rss/src/test/resources/fixtures/episode-recipe-vectors.json.
 *
 * Added in P0 of the own-episode-IDs plan (plan section 4, recipe spec).
 * COMPAT(own-ids): the recipe and trim set behind every issued episode ID. Permanent: never remove (MIGRATION.md §5).
 */

const crypto = require('node:crypto');

const RECIPE = 'boxlore-ep1';
const RECIPE_ID_MIN = 2 ** 52;
const RECIPE_ID_MAX = Number.MAX_SAFE_INTEGER; // 2^53 - 1
const LOW_52_BITS = (1n << 52n) - 1n;

/** UTF-16 code units Kotlin's String.trim() removes on Android (Char.isWhitespace). */
const TRIM_RANGES = [
    [0x0009, 0x000d],
    [0x001c, 0x0020],
    [0x00a0, 0x00a0],
    [0x1680, 0x1680],
    [0x180e, 0x180e],
    [0x2000, 0x200a],
    [0x2028, 0x2029],
    [0x202f, 0x202f],
    [0x205f, 0x205f],
    [0x3000, 0x3000],
];

/** True when the UTF-16 code unit is one the recipe trims. */
function isTrimCodeUnit(code) {
    return TRIM_RANGES.some(([lo, hi]) => code >= lo && code <= hi);
}

/**
 * Trims leading and trailing recipe whitespace.
 * @param {unknown} value
 * @returns {string | null} the trimmed string, or null when nothing is left
 */
function trimKey(value) {
    if (typeof value !== 'string') return null;
    let start = 0;
    let end = value.length;
    while (start < end && isTrimCodeUnit(value.charCodeAt(start))) start++;
    while (end > start && isTrimCodeUnit(value.charCodeAt(end - 1))) end--;
    return start < end ? value.slice(start, end) : null;
}

/**
 * The episode's catalog key: the trimmed GUID, else the trimmed enclosure URL.
 * @returns {string | null} null means the item has no identity and must be skipped
 */
function catalogKey(guid, enclosureUrl) {
    return trimKey(guid) ?? trimKey(enclosureUrl);
}

/**
 * Canonical decimal form of a show ID used as the recipe's episode namespace.
 * Only positive numeric show IDs get recipe IDs; `rss:` shows keep their own IDs.
 * @param {number | bigint | string} showId
 * @returns {string | null} null when the show can't have recipe IDs
 */
function episodeNamespace(showId) {
    if (typeof showId === 'number') {
        return Number.isSafeInteger(showId) && showId > 0 ? String(showId) : null;
    }
    if (typeof showId === 'bigint') {
        return showId > 0n && showId <= BigInt(Number.MAX_SAFE_INTEGER) ? showId.toString() : null;
    }
    if (typeof showId === 'string' && /^[1-9][0-9]{0,15}$/.test(showId)) {
        return Number.isSafeInteger(Number(showId)) ? showId : null;
    }
    return null;
}

/**
 * Recipe ID for an already-trimmed catalog key.
 * @param {number | bigint | string} showId positive numeric show ID
 * @param {string} key a catalog key exactly as catalogKey() returns it
 * @returns {number} a safe integer in [2^52, 2^53 - 1]
 * @throws when the show ID can't have recipe IDs or the key isn't canonical
 */
function recipeId(showId, key) {
    const ns = episodeNamespace(showId);
    if (ns == null) throw new Error(`recipe IDs need a positive numeric show ID, got ${JSON.stringify(String(showId))}`);
    if (typeof key !== 'string' || key === '' || trimKey(key) !== key) {
        throw new Error('recipe IDs need a trimmed, non-empty catalog key');
    }
    const digest = crypto.createHash('sha256').update(`${RECIPE}\u0000${ns}\u0000${key}`, 'utf8').digest();
    return RECIPE_ID_MIN + Number(digest.readBigUInt64BE(0) & LOW_52_BITS);
}

/**
 * Recipe ID for a feed item, or null when it has no catalog key (skip the item).
 * @throws when the show ID can't have recipe IDs
 */
function recipeIdForItem(showId, guid, enclosureUrl) {
    const key = catalogKey(guid, enclosureUrl);
    return key == null ? null : recipeId(showId, key);
}

/**
 * True for a value in the recipe range. Strings must be canonical decimals;
 * legacy IDs (Podcast Index, negative RSS) are never in range.
 */
function isRecipeId(value) {
    const n = typeof value === 'string' && /^[1-9][0-9]{15}$/.test(value) ? Number(value) : value;
    return Number.isSafeInteger(n) && n >= RECIPE_ID_MIN && n <= RECIPE_ID_MAX;
}

module.exports = {
    RECIPE,
    RECIPE_ID_MIN,
    RECIPE_ID_MAX,
    TRIM_RANGES,
    trimKey,
    catalogKey,
    episodeNamespace,
    recipeId,
    recipeIdForItem,
    isRecipeId,
};
