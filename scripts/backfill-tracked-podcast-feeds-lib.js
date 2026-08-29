'use strict';

const BACKFILL_USER_AGENT = 'boxlore-rtdb-feed-backfill/1.0';
const REQUEST_TIMEOUT_MS = 20_000;

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
        .filter(([, row]) => row && typeof row === 'object' && !usableHttpsFeedUrl(row.feedUrl))
        .map(([id, row]) => ({
            id: String(id),
            title: String(row.title || '').trim(),
        }));
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

async function resolveTrackedPodcastFeed({
    id,
    title,
    apiBaseUrl,
    appKey,
    fetchImpl = fetch,
}) {
    let apiFeedUrl = '';
    try {
        const endpoint = new URL('podcast', `${apiBaseUrl.replace(/\/?$/, '/')}`);
        endpoint.searchParams.set('id', id);
        const response = await fetchImpl(endpoint, {
            signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
            headers: {
                'Accept': 'application/json',
                'User-Agent': BACKFILL_USER_AGENT,
                'X-App-Key': appKey,
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
    exactItunesFeedUrl,
    missingTrackedPodcasts,
    resolveTrackedPodcastFeed,
    usableHttpsFeedUrl,
};
