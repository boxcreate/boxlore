'use strict';

/**
 * Scalar coercion helpers for sync → Turso / Qdrant.
 *
 * Root footgun: `String(someObject)` becomes `"[object Object]"`, which then
 * lands in TEXT columns and Qdrant payloads and breaks client JSON parsers
 * (and embeds garbage when title/description are polluted).
 *
 * Policy: scrub non-critical fields to empty and still save the point.
 * Refuse / skip only when a critical field (title, ids, audio/RSS URL, image)
 * is missing or coerced garbage.
 */

const OBJECT_OBJECT_RE = /\[object Object\]/i;

/** Episode Qdrant payload — must have these after scrub. */
const EPISODE_CRITICAL_KEYS = ['id', 'title', 'podcast_id', 'audio_url'];
/** Optional episode fields — corrupt values become ''. */
const EPISODE_OPTIONAL_STRING_KEYS = [
    'description',
    'podcast_title',
    'podcast_author',
    'podcast_image_url',
    'podcast_categories',
    'language',
    'image_url',
];

/** Show Qdrant payload — must have these after scrub. */
const SHOW_CRITICAL_KEYS = ['id', 'title', 'feed_url'];
const SHOW_OPTIONAL_STRING_KEYS = [
    'author',
    'description',
    'image_url',
    'categories',
    'language',
    'website_url',
];

function looksLikeObjectCoercion(v) {
    if (v == null) return false;
    if (typeof v === 'object') return true;
    return OBJECT_OBJECT_RE.test(String(v));
}

/**
 * Coerce to a plain string for TEXT columns / payload fields.
 * Objects and prior "[object Object]" strings collapse to `fallback`.
 */
function asScalarString(v, fallback = '') {
    if (v == null) return fallback;
    if (typeof v === 'object') return fallback;
    if (typeof v === 'number' || typeof v === 'boolean') return String(v);
    const s = String(v);
    if (!s || OBJECT_OBJECT_RE.test(s) || s === 'null' || s === 'undefined') {
        return fallback;
    }
    return s;
}

/** Positive integer id (episode/podcast). Returns fallback when unsafe. */
function asPositiveInt(v, fallback = 0) {
    if (v == null || typeof v === 'object') return fallback;
    const s = String(v).trim();
    if (!s || OBJECT_OBJECT_RE.test(s) || !/^-?\d+$/.test(s)) return fallback;
    const n = Number.parseInt(s, 10);
    return Number.isFinite(n) && n > 0 ? n : fallback;
}

/** Non-negative integer (dates, durations). */
function asNonNegInt(v, fallback = 0) {
    if (v == null || typeof v === 'object') return fallback;
    const s = String(v).trim();
    if (!s || OBJECT_OBJECT_RE.test(s) || !/^-?\d+$/.test(s)) return fallback;
    const n = Number.parseInt(s, 10);
    return Number.isFinite(n) && n >= 0 ? n : fallback;
}

/** True when any string leaf contains [object Object]. */
function payloadLooksCorrupt(payload) {
    if (!payload || typeof payload !== 'object') return false;
    const stack = [payload];
    while (stack.length) {
        const cur = stack.pop();
        if (cur == null) continue;
        if (typeof cur === 'string') {
            if (OBJECT_OBJECT_RE.test(cur)) return true;
            continue;
        }
        if (typeof cur === 'object') {
            if (Array.isArray(cur)) stack.push(...cur);
            else stack.push(...Object.values(cur));
        }
    }
    return false;
}

/**
 * Deep-scrub: every string leaf that looks like a coerced object → ''.
 * Does not delete keys (keeps payload shape stable for Qdrant indexes).
 */
function scrubCorruptStrings(payload) {
    if (!payload || typeof payload !== 'object') return payload;
    const out = Array.isArray(payload) ? [...payload] : { ...payload };
    for (const [k, v] of Object.entries(out)) {
        if (typeof v === 'string' && OBJECT_OBJECT_RE.test(v)) {
            out[k] = '';
        } else if (v && typeof v === 'object' && !Array.isArray(v)) {
            // Scalar slots should never be plain objects — blank them.
            // Nested maps are uncommon in our payloads; blank to be safe.
            out[k] = Array.isArray(v) ? v.map((item) => (
                typeof item === 'string' && OBJECT_OBJECT_RE.test(item) ? '' : item
            )) : '';
        } else if (Array.isArray(v)) {
            out[k] = v.map((item) => (
                typeof item === 'string' && OBJECT_OBJECT_RE.test(item) ? '' : item
            ));
        }
    }
    return out;
}

function criticalMissing(payload, criticalKeys) {
    for (const key of criticalKeys) {
        const v = payload[key];
        if (v == null) return key;
        if (typeof v === 'number') {
            if (!(v > 0)) return key;
            continue;
        }
        const s = asScalarString(v, '');
        if (!s) return key;
    }
    return null;
}

/**
 * Build a Qdrant-ready payload:
 * - coerce known string fields
 * - scrub any leftover [object Object]
 * - skip (ok:false) if a critical field is empty/corrupt
 *
 * @returns {{ ok: true, payload: object, scrubbed: string[] } | { ok: false, reason: string }}
 */
function prepareQdrantPayload(raw, { criticalKeys, optionalStringKeys = [] }) {
    const scrubbed = [];
    const payload = { ...raw };

    for (const key of optionalStringKeys) {
        if (!(key in payload)) continue;
        const before = payload[key];
        if (looksLikeObjectCoercion(before) || (typeof before === 'string' && OBJECT_OBJECT_RE.test(before))) {
            scrubbed.push(key);
        }
        payload[key] = asScalarString(before, '');
    }

    // Critical strings/ids
    for (const key of criticalKeys) {
        if (!(key in payload)) {
            return { ok: false, reason: `missing critical field ${key}` };
        }
        const v = payload[key];
        if (typeof v === 'number') {
            if (!(v > 0)) return { ok: false, reason: `critical ${key} invalid` };
            continue;
        }
        if (looksLikeObjectCoercion(v) || (typeof v === 'string' && OBJECT_OBJECT_RE.test(v))) {
            return { ok: false, reason: `critical ${key} corrupt ([object Object])` };
        }
        if (typeof v === 'string' || v == null) {
            const s = asScalarString(v, '');
            if (!s) return { ok: false, reason: `critical ${key} empty` };
            payload[key] = s;
        }
    }

    const cleaned = scrubCorruptStrings(payload);
    const missing = criticalMissing(cleaned, criticalKeys);
    if (missing) {
        return { ok: false, reason: `critical ${missing} empty after scrub` };
    }
    return { ok: true, payload: cleaned, scrubbed };
}

function prepareEpisodePayload(raw) {
    return prepareQdrantPayload(raw, {
        criticalKeys: EPISODE_CRITICAL_KEYS,
        optionalStringKeys: EPISODE_OPTIONAL_STRING_KEYS,
    });
}

function prepareShowPayload(raw) {
    return prepareQdrantPayload(raw, {
        criticalKeys: SHOW_CRITICAL_KEYS,
        optionalStringKeys: SHOW_OPTIONAL_STRING_KEYS,
    });
}

/**
 * Last-line defense before upsert: scrub corrupt strings; throw only if a
 * critical key (when provided) is still bad. Default = scrub-only.
 */
function assertPayloadClean(payload, label = 'payload', criticalKeys = []) {
    if (!payload || typeof payload !== 'object') return;
    const scrubbed = scrubCorruptStrings(payload);
    Object.assign(payload, scrubbed);
    if (criticalKeys.length) {
        const missing = criticalMissing(payload, criticalKeys);
        if (missing) {
            throw new Error(`${label}: critical ${missing} empty/corrupt — refusing write`);
        }
    }
    if (payloadLooksCorrupt(payload)) {
        // Should be unreachable after scrub; keep as safety net.
        throw new Error(`${label}: still contains [object Object] after scrub`);
    }
}

module.exports = {
    OBJECT_OBJECT_RE,
    EPISODE_CRITICAL_KEYS,
    EPISODE_OPTIONAL_STRING_KEYS,
    SHOW_CRITICAL_KEYS,
    SHOW_OPTIONAL_STRING_KEYS,
    looksLikeObjectCoercion,
    asScalarString,
    asPositiveInt,
    asNonNegInt,
    payloadLooksCorrupt,
    scrubCorruptStrings,
    prepareQdrantPayload,
    prepareEpisodePayload,
    prepareShowPayload,
    assertPayloadClean,
};
