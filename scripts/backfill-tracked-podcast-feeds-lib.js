'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { groupTrackedPodcasts } = require('./check-new-episodes-lib');

const BACKFILL_USER_AGENT = 'boxlore-rtdb-feed-backfill/1.0';
const REQUEST_TIMEOUT_MS = 20_000;
const WEEKLY_BACKUP_LIMIT = 10;
const WEEKLY_BACKUP_NAME = /^\d{4}-W\d{2}\.json$/;

function utcIsoWeekName(now = new Date()) {
    const date =
        new Date(
            Date.UTC(
                now.getUTCFullYear(),
                now.getUTCMonth(),
                now.getUTCDate(),
            ),
        );
    const day = date.getUTCDay() || 7;
    date.setUTCDate(date.getUTCDate() + 4 - day);
    const weekYear = date.getUTCFullYear();
    const yearStart = new Date(Date.UTC(weekYear, 0, 1));
    const week = Math.ceil((((date - yearStart) / 86_400_000) + 1) / 7);
    return `${weekYear}-W${String(week).padStart(2, '0')}.json`;
}

function sortJson(value) {
    if (Array.isArray(value)) return value.map(sortJson);
    if (!value || typeof value !== 'object') return value;
    return Object.fromEntries(
        Object.keys(value)
            .sort()
            .map((key) => [key, sortJson(value[key])]),
    );
}

function writeWeeklyTrackedPodcastBackup({
    directory,
    trackedPodcasts,
    now = new Date(),
    limit = WEEKLY_BACKUP_LIMIT,
}) {
    if (!Number.isInteger(limit) || limit < 1) {
        throw new Error('Weekly backup limit must be a positive integer');
    }
    fs.mkdirSync(directory, { recursive: true });
    const filename = utcIsoWeekName(now);
    fs.writeFileSync(
        path.join(directory, filename),
        `${JSON.stringify(sortJson(groupTrackedPodcasts(trackedPodcasts)), null, 2)}\n`,
    );
    const weeklyFiles =
        fs.readdirSync(directory)
            .filter((name) => WEEKLY_BACKUP_NAME.test(name))
            .sort()
            .reverse();
    const removed = weeklyFiles.slice(limit);
    for (const name of removed) {
        fs.unlinkSync(path.join(directory, name));
    }
    return { filename, removed };
}

function usableHttpsFeedUrl(raw) {
    const value = String(raw || '').trim();
    try {
        const url = new URL(value);
        return url.protocol === 'https:' && url.hostname ? url.toString() : '';
    } catch {
        return '';
    }
}

function missingTrackedPodcasts(trackedPodcasts) {
    return Object.entries(trackedPodcasts || {})
        .filter(([id, row]) => !String(id).startsWith('rss:') && row && typeof row === 'object' && !usableHttpsFeedUrl(row.feedUrl))
        .map(([id, row]) => ({
            id: String(id),
            title: String(row.title || '').trim(),
        }));
}

/**
 * The weekly repair plan for tracked catalog shows, from one RTDB read and the registry view
 * (`/internal/notifier/shows`: `{ feedUrl, feedSource, flags }` per show ID).
 * - `fill`: no valid HTTPS address saved. Uses the registry's HTTPS address when it has one;
 *   otherwise `to` is empty and the caller resolves one (API, HTTPS upgrade, exact Apple title).
 * - `replace`: the registry proved the show's old address dead (`addressRepair`) and confirmed a
 *   different working one (`registry:` source). The same rule the API uses to repair show pages.
 * `read` is the exact saved value, so the write can refuse a row that changed since this read.
 */
function planTrackedFeedRepairs(trackedPodcasts, registry = new Map()) {
    const plan = [];
    for (const [id, row] of Object.entries(trackedPodcasts || {})) {
        if (String(id).startsWith('rss:') || !row || typeof row !== 'object') continue;
        const read = row.feedUrl ?? null;
        const title = String(row.title || '').trim();
        const entry = registry.get(String(id));
        const known = usableHttpsFeedUrl(entry?.feedUrl);
        if (!usableHttpsFeedUrl(read)) {
            plan.push({ id: String(id), title, read, to: known, reason: 'fill' });
            continue;
        }
        const confirmed = known && String(entry.feedSource || '').startsWith('registry:');
        if (confirmed && entry.flags?.addressRepair === true && known !== usableHttpsFeedUrl(read)) {
            plan.push({ id: String(id), title, read, to: known, reason: 'replace' });
        }
    }
    return plan;
}

const sameValue = (a, b) => JSON.stringify(a ?? null) === JSON.stringify(b ?? null);

/**
 * RTDB transaction update for one `feedUrl` leaf: writes `next` only while the saved value is still
 * `read`. The first call can see null before the server's value arrives; returning null then makes
 * the server re-run it with the real value (or commits the same null when the leaf is empty).
 */
function compareAndSetFeedUrl(read, next) {
    return (current) => {
        if (sameValue(current, read)) return next;
        if (current === null) return null;
        return undefined;
    };
}

/** Registry view for these show IDs in batches of 500; an empty map when the API can't answer. */
async function fetchRegistryView({ ids, apiBaseUrl, serviceKey, fetchImpl = fetch }) {
    const out = new Map();
    if (!serviceKey || !apiBaseUrl || !ids.length) return out;
    const endpoint = new URL('internal/notifier/shows', `${apiBaseUrl.replace(/\/?$/, '/')}`);
    for (let i = 0; i < ids.length; i += 500) {
        const response = await fetchImpl(endpoint, {
            method: 'POST',
            signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                'User-Agent': BACKFILL_USER_AGENT,
                Authorization: `Svc ${serviceKey}`,
            },
            body: JSON.stringify({ ids: ids.slice(i, i + 500).map(Number) }),
        });
        if (!response.ok) {
            await discardBody(response);
            throw new Error(`registry view returned ${response.status}`);
        }
        const payload = await response.json();
        for (const entry of payload?.results || []) out.set(String(entry.id), entry);
    }
    return out;
}

function feedHost(raw) {
    try {
        return new URL(String(raw)).host;
    } catch {
        return '(none)';
    }
}

function normalizedTitle(raw) {
    return String(raw || '')
        .normalize('NFKC')
        .trim()
        .toLocaleLowerCase('en-US');
}

async function discardBody(response) {
    try {
        await response.body?.cancel();
    } catch {
        // The response may already be fully consumed or expose no cancellable body.
    }
}

async function tryHttpsUpgrade(rawUrl, fetchImpl) {
    let candidate;
    try {
        candidate = new URL(String(rawUrl || '').trim());
    } catch {
        return '';
    }
    if (candidate.protocol !== 'http:') return '';
    candidate.protocol = 'https:';

    try {
        const response = await fetchImpl(candidate.toString(), {
            redirect: 'follow',
            signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
            headers: { 'User-Agent': BACKFILL_USER_AGENT },
        });
        const finalUrl = usableHttpsFeedUrl(response.url || candidate.toString());
        await discardBody(response);
        return response.ok ? finalUrl : '';
    } catch {
        return '';
    }
}

function exactItunesFeedUrl(payload, title) {
    const target = normalizedTitle(title);
    if (!target) return '';
    for (const result of payload?.results || []) {
        if (normalizedTitle(result.collectionName) !== target) continue;
        const feedUrl = usableHttpsFeedUrl(result.feedUrl);
        if (feedUrl) return feedUrl;
    }
    return '';
}

/**
 * Credential for the API: the workflow's scoped service key (`<id>.<secret>`,
 * read-only catalog access) when configured, else the legacy app key.
 */
function catalogAuthHeaders({ serviceKey, appKey }) {
    return serviceKey ? { Authorization: `Svc ${serviceKey}` } : { 'X-App-Key': appKey };
}

async function resolveTrackedPodcastFeed({
    id,
    title,
    apiBaseUrl,
    appKey,
    serviceKey,
    fetchImpl = fetch,
}) {
    if (String(id).startsWith('rss:')) return ''; // Private/premium feeds have no catalog identity to repair.

    let apiFeedUrl = '';
    try {
        const endpoint = new URL('podcast', `${apiBaseUrl.replace(/\/?$/, '/')}`);
        endpoint.searchParams.set('id', id);
        const response = await fetchImpl(endpoint, {
            signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
            headers: {
                'Accept': 'application/json',
                'User-Agent': BACKFILL_USER_AGENT,
                ...catalogAuthHeaders({ serviceKey, appKey }),
            },
        });
        if (response.ok) {
            const payload = await response.json();
            apiFeedUrl = String(payload?.feed?.url || '').trim();
            const directUrl = usableHttpsFeedUrl(apiFeedUrl);
            if (directUrl) return directUrl;
        } else {
            await discardBody(response);
        }
    } catch {
        // Apple title lookup below is the bounded fallback.
    }

    const upgradedUrl = await tryHttpsUpgrade(apiFeedUrl, fetchImpl);
    if (upgradedUrl) return upgradedUrl;
    if (!title) return '';

    try {
        const endpoint = new URL('https://itunes.apple.com/search');
        endpoint.searchParams.set('term', title);
        endpoint.searchParams.set('media', 'podcast');
        endpoint.searchParams.set('entity', 'podcast');
        endpoint.searchParams.set('limit', '5');
        const response = await fetchImpl(endpoint, {
            signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
            headers: { 'User-Agent': BACKFILL_USER_AGENT },
        });
        if (!response.ok) {
            await discardBody(response);
            return '';
        }
        return exactItunesFeedUrl(await response.json(), title);
    } catch {
        return '';
    }
}

module.exports = {
    BACKFILL_USER_AGENT,
    catalogAuthHeaders,
    compareAndSetFeedUrl,
    exactItunesFeedUrl,
    feedHost,
    fetchRegistryView,
    missingTrackedPodcasts,
    planTrackedFeedRepairs,
    resolveTrackedPodcastFeed,
    utcIsoWeekName,
    usableHttpsFeedUrl,
    writeWeeklyTrackedPodcastBackup,
};
