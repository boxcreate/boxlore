'use strict';

/**
 * In-run handoff of PI episode payloads from stage 3 → stage 4 so vectorize
 * does not re-fetch the same shows in the same pipeline run.
 *
 * Keeps the map in memory during the run; flushes to disk periodically and
 * at end of stage 3. (Per-put full-file writes made stage 3 crawl as the
 * map grew.)
 */

const fs = require('fs');
const cfg = require('./config');

const FLUSH_EVERY = 100;

/** @type {Record<string, object[]>|null} */
let mem = null;
let dirty = 0;
let putsSinceFlush = 0;

function handoffPath() {
    return cfg.PI_EPISODE_HANDOFF_FILE;
}

function clear() {
    mem = {};
    dirty = 0;
    putsSinceFlush = 0;
    try {
        fs.writeFileSync(handoffPath(), '{}');
    } catch {
        // best-effort
    }
}

function load() {
    if (mem) return mem;
    try {
        const raw = fs.readFileSync(handoffPath(), 'utf8');
        const data = JSON.parse(raw);
        mem = data && typeof data === 'object' ? data : {};
    } catch {
        mem = {};
    }
    return mem;
}

function flushSync() {
    if (!mem) return;
    fs.writeFileSync(handoffPath(), JSON.stringify(mem));
    dirty = 0;
    putsSinceFlush = 0;
}

/**
 * Store episodes for one show. Memory-only unless FLUSH_EVERY reached.
 * Prefer storing only when stage 4 may need them (caller decides).
 */
function put(podcastId, episodes) {
    const map = load();
    map[String(podcastId)] = episodes || [];
    dirty = 1;
    putsSinceFlush++;
    if (putsSinceFlush >= FLUSH_EVERY) {
        flushSync();
    }
}

/** Durable flush before stage 4. */
async function flush() {
    flushSync();
}

/** @returns {object[]|null} */
function take(podcastId) {
    const map = load();
    const key = String(podcastId);
    if (!Object.prototype.hasOwnProperty.call(map, key)) return null;
    return map[key];
}

module.exports = { clear, load, put, take, flush, handoffPath };
