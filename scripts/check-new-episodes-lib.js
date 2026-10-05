'use strict';

const crypto = require('crypto');

/** RSS registrations are device-local, while release state and topics use canonical show IDs. */
function groupTrackedPodcasts(registrations) {
    const shows = Object.create(null);
    for (const [registrationId, data] of Object.entries(registrations || {})) {
        if (!data || typeof data !== 'object') continue;
        const pureRss = registrationId.startsWith('rss:');
        const id = pureRss ? registrationId.split('~')[0] : registrationId;
        if (pureRss && !usableFeedUrl(data.feedUrl)) continue;
        if (!shows[id] || (!usableFeedUrl(shows[id].feedUrl) && usableFeedUrl(data.feedUrl))) shows[id] = data;
    }
    return shows;
}

function notificationTopic(podcastId) {
    return `new_ep_${String(podcastId).replace(/^rss:/, 'rss_')}`;
}

/** Re-enabling a feed after all listeners left starts quietly rather than alerting an archive tip. */
function activeEpisodeState(existing, tracked) {
    return Object.fromEntries(Object.entries(existing || {}).filter(([id]) => !id.startsWith('rss:') || Object.hasOwn(tracked, id)));
}

/** Pure RSS never has a PI fallback. Public state contains a digest, not a credential-bearing key. */
function applyPureRssCheck({ existing, item, now = Date.now() }) {
    const rawKey = rssItemKey(item);
    if (!rawKey) return null;
    const digest = (key) => `sha256:${crypto.createHash('sha256').update(key).digest('hex')}`;
    const prior = existing && existing.lastRssKey
        ? { ...existing, lastRssKey: existing.lastRssKey.startsWith('sha256:') ? existing.lastRssKey : digest(existing.lastRssKey) }
        : undefined;
    const decision = applyCheck({ existing: prior, source: 'rss', newest: { key: digest(rawKey), title: item.title || 'New Episode' }, now });
    if (decision.reason === 'unchanged' && existing && !existing.lastRssKey.startsWith('sha256:')) decision.reason = 'rss-state-migrated';
    return decision;
}

async function resolveTrackedRelease({ podcastId, podcastData, existing, fetchRssNewest, fetchPiLatest, now }) {
    const pureRss = String(podcastId).startsWith('rss:');
    const feedUrl = usableFeedUrl(podcastData.feedUrl);
    let rssItem = null;
    if (feedUrl) {
        try {
            rssItem = await fetchRssNewest(feedUrl);
        } catch (error) {
            if (pureRss) throw error; // Keep last-good state and retry; no unrelated catalog lookup.
        }
    }
    if (pureRss) {
        const decision = applyPureRssCheck({ existing, item: rssItem, now });
        return decision ? { decision, data: buildRssFcmData({ podcastId, podcastTitle: podcastData.title || 'Podcast', imageUrl: podcastData.imageUrl, rssItem, feedUrl }) } : null;
    }
    if (rssItem && rssItemKey(rssItem)) {
        let piEpisode = null;
        try { piEpisode = await fetchPiLatest(podcastId); } catch (_) { /* RSS owns the release. */ }
        const matched = rssMatchesPi(rssItem, piEpisode);
        const decision = applyCheck({ existing, source: 'rss', newest: { key: rssItemKey(rssItem), title: rssItem.title || 'New Episode', piEpisodeId: matched ? String(piEpisode.id) : undefined }, now });
        return { decision, data: buildRssFcmData({ podcastId, podcastTitle: podcastData.title || 'Podcast', imageUrl: podcastData.imageUrl, rssItem, piEpisode: matched ? piEpisode : null, feedUrl }) };
    }
    const piEpisode = await fetchPiLatest(podcastId);
    if (!piEpisode) return null;
    const decision = applyCheck({ existing, source: 'pi', newest: { piEpisodeId: String(piEpisode.id), title: piEpisode.title || 'New Episode', rssKey: rssItemKey(piEpisode) }, now });
    return { decision, data: buildPiFcmData({ podcastId, podcastTitle: podcastData.title || 'Podcast', imageUrl: podcastData.imageUrl, piEpisode }) };
}

function decodeXml(value) {
    const named = { lt: '<', gt: '>', quot: '"', apos: "'", amp: '&' };
    return String(value || '').replace(/&(lt|gt|quot|apos|amp|#(?:x[\da-f]+|\d+));/gi, (reference, entity) => {
        if (Object.hasOwn(named, entity)) return named[entity];
        const hex = entity.slice(0, 2).toLowerCase() === '#x';
        const codePoint = Number.parseInt(entity.slice(hex ? 2 : 1), hex ? 16 : 10);
        return Number.isFinite(codePoint) && codePoint > 0 && codePoint <= 0x10ffff && !(codePoint >= 0xd800 && codePoint <= 0xdfff)
            ? String.fromCodePoint(codePoint) : reference;
    }).trim();
}

function usableFeedUrl(raw) {
    const url = String(raw || '').trim();
    return url.toLowerCase().startsWith('https://') ? url : '';
}

function rssItemKey(item) {
    if (!item) return '';
    const guid = String(item.guid || '').trim();
    if (guid) return guid;
    return String(item.enclosureUrl || '').trim();
}

function durationMinutes(raw) {
    if (raw == null || raw === '') return '0';
    const s = String(raw).trim();
    if (s.includes(':')) {
        const parts = s.split(':').map((p) => Number(p) || 0);
        let seconds = 0;
        if (parts.length === 3) {
            seconds = parts[0] * 3600 + parts[1] * 60 + parts[2];
        } else if (parts.length === 2) {
            seconds = parts[0] * 60 + parts[1];
        } else {
            seconds = parts[0];
        }
        if (!Number.isFinite(seconds) || seconds < 0) return '0';
        return String(Math.round(seconds / 60) || 0);
    }
    const n = Number(s);
    if (!Number.isFinite(n) || n < 0) return '0';
    return String(Math.round(n / 60) || 0);
}

function rssMatchesPi(rssItem, piEpisode) {
    if (!rssItem || !piEpisode) return false;
    const rssEnc = String(rssItem.enclosureUrl || '').trim();
    const piEnc = String(piEpisode.enclosureUrl || '').trim();
    if (rssEnc && piEnc && rssEnc === piEnc) return true;
    const rssGuid = String(rssItem.guid || '').trim();
    const piGuid = String(piEpisode.guid || '').trim();
    return Boolean(rssGuid && piGuid && rssGuid === piGuid);
}

function tagText(xml, tag) {
    const re = new RegExp(
        `<${tag}(?:\\s[^>]*)?>(?:<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>|([^<]*))</${tag}>`,
        'i',
    );
    const match = xml.match(re);
    if (!match) return '';
    // CDATA is literal text, not XML entity references. Decode normal text once.
    return match[1] !== undefined ? match[1].trim() : decodeXml(match[2]);
}

function attrValue(xml, tag, attrName) {
    const re = new RegExp(
        `<${tag}[^>]*\\s${attrName}=["']([^"']+)["']`,
        'i',
    );
    const match = xml.match(re);
    return match ? decodeXml(match[1]) : '';
}

function parseItemXml(itemXml, kind) {
    const guid =
        kind === 'atom'
            ? tagText(itemXml, 'id')
            : tagText(itemXml, 'guid');
    const title = tagText(itemXml, 'title');
    const enclosureUrl = playableEnclosureUrl(itemXml);
    // Android omits untitled/non-media entries; do not advance alerts to an unplayable tip.
    if (!title || !enclosureUrl) return null;
    const duration =
        tagText(itemXml, 'itunes:duration') || tagText(itemXml, 'duration');
    const image =
        attrValue(itemXml, 'itunes:image', 'href') ||
        attrValue(itemXml, 'media:thumbnail', 'url') ||
        attrValue(itemXml, 'media:content', 'url');
    const dateText =
        tagText(itemXml, 'pubDate') ||
        tagText(itemXml, 'published') ||
        tagText(itemXml, 'updated');
    const parsed = Date.parse(dateText);
    return {
        guid,
        title,
        enclosureUrl,
        duration,
        image,
        pubMs: Number.isFinite(parsed) ? parsed : 0,
    };
}

function playableEnclosureUrl(xml) {
    const extensions = /\.(mp3|m4a|aac|ogg|opus|wav|mp4|m4v|webm|m3u8)$/i;
    for (const match of xml.matchAll(/<(enclosure|link|media:content)\b[^>]*>/gi)) {
        const tag = match[1];
        if (tag.toLowerCase() === 'link' && attrValue(match[0], tag, 'rel') !== 'enclosure') continue;
        const url = attrValue(match[0], tag, tag.toLowerCase() === 'link' ? 'href' : 'url');
        const type = attrValue(match[0], tag, 'type').toLowerCase();
        const medium = attrValue(match[0], tag, 'medium').toLowerCase();
        if (url && (/^(audio|video)\//.test(type) || /^(audio|video)$/.test(medium) || extensions.test(url.split(/[?#]/)[0]))) return url;
    }
    return '';
}

function parseFeedItems(xml) {
    const source = String(xml || '');
    const items = [];
    const itemRe = /<item[\s>][\s\S]*?<\/item>/gi;
    let match;
    while ((match = itemRe.exec(source))) {
        const item = parseItemXml(match[0], 'rss');
        if (item) items.push(item);
    }
    if (items.length > 0) return items;
    const entryRe = /<entry[\s>][\s\S]*?<\/entry>/gi;
    while ((match = entryRe.exec(source))) {
        const item = parseItemXml(match[0], 'atom');
        if (item) items.push(item);
    }
    return items;
}

function newestRssItem(items) {
    if (!items || items.length === 0) return null;
    return items.reduce((best, item) => {
        if ((item.pubMs || 0) > (best.pubMs || 0)) return item;
        return best;
    });
}

function applyCheck({ existing, source, newest, now = Date.now() }) {
    if (source === 'rss') {
        if (!existing || !existing.lastRssKey) {
            const differentPi =
                existing?.lastEpisodeId &&
                newest.piEpisodeId &&
                String(existing.lastEpisodeId) !== String(newest.piEpisodeId);
            if (differentPi) {
                return {
                    notify: true,
                    reason: 'rss-new-after-pi',
                    nextState: {
                        lastRssKey: newest.key,
                        lastEpisodeTitle: newest.title,
                        lastEpisodeId: newest.piEpisodeId,
                        lastCheckedAt: now,
                    },
                };
            }
            return {
                notify: false,
                reason: 'rss-baseline',
                nextState: {
                    lastRssKey: newest.key,
                    lastEpisodeTitle: newest.title,
                    lastEpisodeId: newest.piEpisodeId || existing?.lastEpisodeId,
                    lastCheckedAt: now,
                },
            };
        }
        if (existing.lastRssKey === newest.key) {
            return { notify: false, reason: 'unchanged', nextState: existing };
        }
        if (
            newest.piEpisodeId &&
            existing.lastEpisodeId &&
            String(existing.lastEpisodeId) === String(newest.piEpisodeId)
        ) {
            return {
                notify: false,
                reason: 'unchanged',
                nextState: {
                    lastRssKey: newest.key,
                    lastEpisodeTitle: newest.title,
                    lastEpisodeId: existing.lastEpisodeId,
                    lastCheckedAt: now,
                },
            };
        }
        return {
            notify: true,
            reason: 'rss-new',
            nextState: {
                lastRssKey: newest.key,
                lastEpisodeTitle: newest.title,
                lastEpisodeId: newest.piEpisodeId || existing.lastEpisodeId,
                lastCheckedAt: now,
            },
        };
    }

    if (!existing) {
        return {
            notify: false,
            reason: 'pi-baseline',
            nextState: {
                lastEpisodeId: newest.piEpisodeId,
                lastEpisodeTitle: newest.title,
                lastRssKey: newest.rssKey || undefined,
                lastCheckedAt: now,
            },
        };
    }
    if (String(existing.lastEpisodeId) === String(newest.piEpisodeId)) {
        return { notify: false, reason: 'unchanged', nextState: existing };
    }
    if (newest.rssKey && existing.lastRssKey && newest.rssKey === existing.lastRssKey) {
        return {
            notify: false,
            reason: 'unchanged',
            nextState: {
                lastEpisodeId: newest.piEpisodeId,
                lastEpisodeTitle: newest.title,
                lastRssKey: existing.lastRssKey,
                lastCheckedAt: now,
            },
        };
    }
    return {
        notify: true,
        reason: 'pi-new',
        nextState: {
            lastEpisodeId: newest.piEpisodeId,
            lastEpisodeTitle: newest.title,
            lastRssKey: newest.rssKey || existing.lastRssKey,
            lastCheckedAt: now,
        },
    };
}

/** Same 25 MB ceiling as Android `RssFeedClient` — GHA previously used 5 MB. */
const MAX_FEED_BYTES = 25 * 1024 * 1024;

/**
 * Full feeds are required: oldest-first feeds can put their newest release at the end.
 * @returns {'too-large' | 'continue'}
 */
function rssDownloadDecision({
    received = 0,
    declared,
    maxBytes = MAX_FEED_BYTES,
} = {}) {
    const declaredN = Number(declared);
    if (received === 0 && Number.isFinite(declaredN) && declaredN > maxBytes) {
        return 'too-large';
    }
    if (received > maxBytes) return 'too-large';
    return 'continue';
}

/** A timeout, failed stream or oversized body must never become a partial release baseline. */
async function fetchRssText(url, { timeoutMs = 60000, maxBytes = MAX_FEED_BYTES, fetchImpl = fetch } = {}) {
    const ac = new AbortController();
    const timer = setTimeout(() => ac.abort(), timeoutMs);
    const decoder = new TextDecoder('utf-8');
    let xml = '';
    let received = 0;
    try {
        const response = await fetchImpl(url, {
            signal: ac.signal,
            redirect: 'follow',
            headers: { 'User-Agent': 'BoxLore/1.0', 'Accept': 'application/rss+xml, application/atom+xml, application/xml, text/xml, */*' },
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        if (response.url && !usableFeedUrl(response.url)) throw new Error('Feed redirected to a non-HTTPS URL');
        const declared = Number(response.headers.get('content-length'));
        if (rssDownloadDecision({ declared, maxBytes }) === 'too-large') throw new Error('Feed too large');
        if (!response.body) throw new Error('Empty body');
        for await (const chunk of response.body) {
            received += chunk.byteLength;
            if (rssDownloadDecision({ received, maxBytes }) === 'too-large') throw new Error('Feed too large');
            xml += decoder.decode(chunk, { stream: true });
        }
        if (ac.signal.aborted) throw new Error('Feed timed out');
        return xml + decoder.decode();
    } finally {
        clearTimeout(timer);
        ac.abort();
    }
}

function omitEmpty(data) {
    const out = {};
    for (const [key, value] of Object.entries(data)) {
        if (value == null) continue;
        const text = String(value);
        if (text === '') continue;
        out[key] = text;
    }
    return out;
}

function buildRssFcmData({
    podcastId,
    podcastTitle,
    imageUrl,
    rssItem,
    piEpisode,
    feedUrl,
}) {
    const matched = rssMatchesPi(rssItem, piEpisode);
    const data = {
        type: 'new_episode',
        podcastId: String(podcastId),
        podcastTitle: String(podcastTitle),
        episodeTitle: String((rssItem && rssItem.title) || 'New Episode'),
        duration: durationMinutes(
            (rssItem && rssItem.duration) || (piEpisode && piEpisode.duration),
        ),
        image: String(
            (rssItem && rssItem.image) ||
                (piEpisode && (piEpisode.image || piEpisode.feedImage)) ||
                imageUrl ||
                '',
        ),
        feedUrl: feedUrl || '',
        guid: (rssItem && rssItem.guid) || '',
        enclosureUrl: (rssItem && rssItem.enclosureUrl) || '',
    };
    if (matched && piEpisode && piEpisode.id) {
        data.episodeId = String(piEpisode.id);
        data.route = `boxlore://episode/${piEpisode.id}?autoplay=false`;
    } else {
        data.route = `boxlore://podcast/${podcastId}`;
    }
    return omitEmpty(data);
}

function buildPiFcmData({ podcastId, podcastTitle, imageUrl, piEpisode }) {
    const episodeId = String(piEpisode.id);
    return omitEmpty({
        type: 'new_episode',
        podcastId: String(podcastId),
        podcastTitle: String(podcastTitle),
        episodeTitle: String(piEpisode.title || 'New Episode'),
        episodeId,
        duration: durationMinutes(piEpisode.duration),
        image: String(piEpisode.image || piEpisode.feedImage || imageUrl || ''),
        route: `boxlore://episode/${episodeId}?autoplay=false`,
    });
}

module.exports = {
    activeEpisodeState,
    groupTrackedPodcasts,
    notificationTopic,
    applyPureRssCheck,
    resolveTrackedRelease,
    usableFeedUrl,
    rssItemKey,
    durationMinutes,
    rssMatchesPi,
    parseFeedItems,
    newestRssItem,
    applyCheck,
    buildRssFcmData,
    buildPiFcmData,
    MAX_FEED_BYTES,
    rssDownloadDecision,
    fetchRssText,
};

// These topics are opted-in visible release alerts, qualifying for prompt Android delivery.
module.exports.newEpisodeFcmMessage = (topic, data) => ({ topic, data, android: { priority: 'high' } });
