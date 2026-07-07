'use strict';

/**
 * Shared Podcast Index API client with a global token-bucket rate limiter
 * (~3 req/s) and exponential backoff on 403/429/5xx, transient network errors,
 * and API-level failures (status=false). All pipeline PI calls go through
 * here so total API pressure is bounded regardless of stage.
 */

const crypto = require('crypto');
const log = require('./log');

const API_KEY = process.env.PODCAST_INDEX_API_KEY;
const API_SECRET = process.env.PODCAST_INDEX_API_SECRET;
const API_BASE = 'https://api.podcastindex.org/api/1.0';

const RATE_LIMIT_RPS = 3.0;
const MAX_RETRIES = 5;
const INITIAL_BACKOFF_MS = 2000;

let apiCallCount = 0;
let nextSlotAt = 0;

function assertEnv() {
    if (!API_KEY || !API_SECRET) {
        log.error('Missing PODCAST_INDEX_API_KEY or PODCAST_INDEX_API_SECRET');
        process.exit(1);
    }
}

function getApiCallCount() {
    return apiCallCount;
}

function authHeaders() {
    const t = Math.floor(Date.now() / 1000);
    const hash = crypto.createHash('sha1').update(API_KEY + API_SECRET + t).digest('hex');
    return {
        'User-Agent': 'BoxLore/1.0 (github.com/ashwkun/boxlore; podcast-sync)',
        'X-Auth-Key': API_KEY,
        'X-Auth-Date': String(t),
        'Authorization': hash,
    };
}

function isTransientHttp(status) {
    return status === 403 || status === 429 || status === 502 || status === 503 || status === 504;
}

function isTransientNetwork(message) {
    return /fetch failed|socket|timeout|UND_ERR|ECONNRESET|ETIMEDOUT/.test(message);
}

/** Global rate limiter: serializes request start times at RATE_LIMIT_RPS. */
async function acquireSlot() {
    const interval = 1000 / RATE_LIMIT_RPS;
    const now = Date.now();
    const slot = Math.max(nextSlotAt, now);
    nextSlotAt = slot + interval;
    if (slot > now) {
        await new Promise(r => setTimeout(r, slot - now));
    }
}

async function backoff(attempt, path, reason) {
    const delay = INITIAL_BACKOFF_MS * Math.pow(2, attempt - 1);
    log.warn(`PI API ${reason} on ${path}; backing off ${delay}ms (attempt ${attempt}/${MAX_RETRIES})`);
    await new Promise(r => setTimeout(r, delay));
}

async function apiGet(path) {
    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        await acquireSlot();
        try {
            apiCallCount++;
            const res = await fetch(`${API_BASE}${path}`, { headers: authHeaders() });
            if (isTransientHttp(res.status)) {
                if (attempt < MAX_RETRIES) {
                    await backoff(attempt, path, `HTTP ${res.status}`);
                    continue;
                }
                throw new Error(`PI API error: ${res.status}`);
            }
            if (!res.ok) throw new Error(`PI API error: ${res.status}`);

            let data;
            try {
                data = await res.json();
            } catch (e) {
                if (attempt < MAX_RETRIES) {
                    await backoff(attempt, path, 'invalid JSON body');
                    continue;
                }
                throw new Error(`PI API invalid JSON on ${path}: ${e.message}`);
            }

            // PI returns { status: "false", description: "..." } on some throttle/error paths.
            if (data && (data.status === 'false' || data.status === false)) {
                const detail = data.description || data.error || 'status=false';
                if (attempt < MAX_RETRIES) {
                    await backoff(attempt, path, detail);
                    continue;
                }
                throw new Error(`PI API status=false: ${detail}`);
            }

            return data;
        } catch (e) {
            if (attempt < MAX_RETRIES && isTransientNetwork(e.message)) {
                await backoff(attempt, path, e.message);
                continue;
            }
            throw e;
        }
    }
    throw new Error(`PI API exhausted retries for ${path}`);
}

/**
 * Latest episodes for a feed (newest first). Throws on failure.
 * Empty items[] is valid for feeds with no episodes; callers distinguish
 * that from API errors (which throw or return status=false above).
 */
async function episodesByFeedId(feedId, max) {
    const data = await apiGet(`/episodes/byfeedid?id=${feedId}&max=${max}`);
    return data.items || [];
}

/** Feed metadata by PI feed id. Returns null when not found. */
async function podcastByFeedId(feedId) {
    const data = await apiGet(`/podcasts/byfeedid?id=${feedId}`);
    const feed = data.feed;
    if (!feed || (Array.isArray(feed) && feed.length === 0) || !feed.id) return null;
    return feed;
}

/** Feed metadata by iTunes id. Returns null when not found. */
async function podcastByItunesId(itunesId) {
    const data = await apiGet(`/podcasts/byitunesid?id=${itunesId}`);
    const feed = data.feed;
    if (!feed || (Array.isArray(feed) && feed.length === 0) || !feed.id) return null;
    return feed;
}

module.exports = {
    assertEnv, getApiCallCount,
    episodesByFeedId, podcastByFeedId, podcastByItunesId,
};
