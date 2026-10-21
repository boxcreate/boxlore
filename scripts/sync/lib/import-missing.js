'use strict';

/**
 * Stage-2 helpers: chart→podcast missing-id resolution without a full
 * podcasts table scan when we already know the chart itunes id list.
 */

const EXISTENCE_IN_CHUNK = 400;

/**
 * Which of `itunesIds` already exist on podcasts (string-normalized).
 * Chunked IN — avoids paging every podcast row.
 *
 * @param {{ execute: Function, rows: Function }} turso
 * @param {string[]} itunesIds
 * @returns {Promise<Set<string>>}
 */
async function existingItunesAmong(turso, itunesIds) {
    const existing = new Set();
    const ids = (itunesIds || []).map(String).filter(Boolean);
    for (let i = 0; i < ids.length; i += EXISTENCE_IN_CHUNK) {
        const chunk = ids.slice(i, i + EXISTENCE_IN_CHUNK);
        // Prefer numeric binds when the id is all-digits (podcasts.itunes_id is
        // often INTEGER). No CAST — keeps idx_podcasts_itunes_id usable.
        const args = chunk.map((id) => {
            if (/^\d+$/.test(id)) {
                const n = Number(id);
                return Number.isSafeInteger(n) ? n : id;
            }
            return id;
        });
        const ph = args.map(() => '?').join(',');
        const res = await turso.execute(
            `SELECT itunes_id FROM podcasts WHERE itunes_id IN (${ph})`,
            args,
        );
        for (const row of turso.rows(res)) {
            if (row[0] != null && row[0] !== '') existing.add(String(row[0]));
        }
    }
    return existing;
}

/**
 * Prefer a fresh missing_itunes_ids.txt from precheck; else null (caller computes).
 * @param {string[]} fromFile
 * @param {{ maxAgeMs?: number, mtimeMs?: number|null, nowMs?: number }} opts
 * @returns {string[]|null}
 */
function reuseMissingIdsFile(fromFile, opts = {}) {
    const list = (fromFile || []).map(String).filter(Boolean);
    if (list.length === 0) return null;
    const maxAgeMs = opts.maxAgeMs ?? 2 * 60 * 60 * 1000;
    const mtimeMs = opts.mtimeMs;
    const nowMs = opts.nowMs ?? Date.now();
    if (mtimeMs != null && Number.isFinite(mtimeMs) && nowMs - mtimeMs > maxAgeMs) {
        return null;
    }
    return list;
}

/**
 * Looks each iTunes ID up and sorts the answers (own-episode-IDs P1b): a feed, "Podcast Index has
 * no such feed" (`notFoundIds`, denylisted by the caller), or a failed lookup (`failedIds`: outage
 * or rate limit; retried next run, never denylisted).
 * @param {string[]} itunesIds
 * @param {(itunesId: string) => Promise<object|null>} lookup
 * `onFound` runs for each feed as it arrives, so callers can write in batches before the pass ends.
 * @param {{ onError?: (itunesId: string, e: Error) => void, onEach?: () => void,
 *   onFound?: (hit: { itunesId: string, feed: object }) => Promise<void>|void }} [hooks]
 */
async function lookupItunesIds(itunesIds, lookup, hooks = {}) {
    const found = [];
    const notFoundIds = [];
    const failedIds = [];
    for (let i = 0; i < itunesIds.length; i++) {
        const itunesId = itunesIds[i];
        let feed;
        try {
            feed = await lookup(itunesId);
        } catch (e) {
            failedIds.push(String(itunesId));
            hooks.onError?.(String(itunesId), e);
            hooks.onEach?.();
            // Podcast Index paused (lib/podcast-index.js): the rest wait for the next run too.
            if (e?.code === 'PI_SUSPENDED') {
                failedIds.push(...itunesIds.slice(i + 1).map(String));
                break;
            }
            continue;
        }
        hooks.onEach?.();
        if (feed) {
            const hit = { itunesId: String(itunesId), feed };
            found.push(hit);
            await hooks.onFound?.(hit);
        } else {
            notFoundIds.push(String(itunesId));
        }
    }
    return { found, notFoundIds, failedIds };
}

module.exports = {
    lookupItunesIds,
    EXISTENCE_IN_CHUNK,
    existingItunesAmong,
    reuseMissingIdsFile,
};
