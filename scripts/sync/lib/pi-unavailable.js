'use strict';

/**
 * Denylist of chart iTunes IDs that are not present (or broken) in Podcast Index.
 * Persisted in git (scripts/data/pi_unavailable_itunes_ids.json) so Stage 2
 * precheck can exclude them before deciding need_dump.
 *
 * {
 *   "updatedAt": <ms>,
 *   "ids": {
 *     "<itunesId>": { "reason": "not_in_dump"|"api_not_found", "seenAt": <ms> }
 *   }
 * }
 */

const fs = require('fs');
const path = require('path');
const log = require('./log');
const { PI_UNAVAILABLE_FILE, PI_UNAVAILABLE_TTL_MS } = require('./config');

function emptyDoc() {
    return { updatedAt: 0, ids: {} };
}

function load(filePath = PI_UNAVAILABLE_FILE) {
    try {
        if (!fs.existsSync(filePath)) return emptyDoc();
        const raw = JSON.parse(fs.readFileSync(filePath, 'utf8') || '{}');
        if (!raw || typeof raw !== 'object' || typeof raw.ids !== 'object' || raw.ids === null) {
            return emptyDoc();
        }
        return { updatedAt: Number(raw.updatedAt) || 0, ids: raw.ids };
    } catch (e) {
        log.warn(`Failed to parse ${filePath}, starting fresh: ${e.message}`);
        return emptyDoc();
    }
}

function save(doc, filePath = PI_UNAVAILABLE_FILE) {
    const dir = path.dirname(filePath);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const out = {
        updatedAt: doc.updatedAt || Date.now(),
        ids: doc.ids || {},
    };
    fs.writeFileSync(filePath, `${JSON.stringify(out, null, 2)}\n`);
}

/** Drop entries older than TTL. Returns number removed. */
function pruneExpired(doc, { now = Date.now(), ttlMs = PI_UNAVAILABLE_TTL_MS } = {}) {
    let pruned = 0;
    for (const [id, rec] of Object.entries(doc.ids || {})) {
        const seenAt = rec && typeof rec.seenAt === 'number' ? rec.seenAt : 0;
        if (now - seenAt >= ttlMs) {
            delete doc.ids[id];
            pruned++;
        }
    }
    if (pruned > 0) doc.updatedAt = now;
    return pruned;
}

/** Active (non-expired) iTunes IDs as a Set of strings. */
function activeIdSet(doc, { now = Date.now(), ttlMs = PI_UNAVAILABLE_TTL_MS } = {}) {
    const out = new Set();
    for (const [id, rec] of Object.entries(doc.ids || {})) {
        const seenAt = rec && typeof rec.seenAt === 'number' ? rec.seenAt : 0;
        if (now - seenAt < ttlMs) out.add(String(id));
    }
    return out;
}

/**
 * Mark IDs unavailable. Refreshes seenAt for existing entries.
 * @returns {number} count newly added (not previously present)
 */
function add(doc, itunesIds, reason, { now = Date.now() } = {}) {
    if (!doc.ids) doc.ids = {};
    let added = 0;
    for (const raw of itunesIds) {
        const id = String(raw);
        if (!id) continue;
        if (!doc.ids[id]) added++;
        doc.ids[id] = { reason: String(reason || 'unknown'), seenAt: now };
    }
    if (itunesIds.length > 0) doc.updatedAt = now;
    return added;
}

/** Remove IDs (e.g. after a successful API import). Returns count removed. */
function remove(doc, itunesIds) {
    let removed = 0;
    for (const raw of itunesIds) {
        const id = String(raw);
        if (doc.ids && doc.ids[id]) {
            delete doc.ids[id];
            removed++;
        }
    }
    if (removed > 0) doc.updatedAt = Date.now();
    return removed;
}

module.exports = {
    load,
    save,
    pruneExpired,
    activeIdSet,
    add,
    remove,
    emptyDoc,
};
