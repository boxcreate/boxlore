'use strict';

const crypto = require('crypto');

function canonicalPodcastId(id) {
    return String(id).startsWith('rss:') ? String(id).split('~')[0] : String(id);
}

function rssScopeId(podcastId, feedUrl) {
    return `${canonicalPodcastId(podcastId)}~${crypto.createHash('sha256').update(String(feedUrl).trim(), 'utf8').digest('hex')}`;
}

/** Group only identical show + accepted URL scopes, never differing feeds under one saved ID. */
function groupTrackedPodcasts(registrations) {
    const shows = Object.create(null);
    for (const [registrationId, data] of Object.entries(registrations || {})) {
        if (!data || typeof data !== 'object') continue;
        const pureRss = registrationId.startsWith('rss:');
        const feedUrl = usableFeedUrl(data.feedUrl);
        if (pureRss && !feedUrl) continue;
        const id = pureRss ? rssScopeId(registrationId, feedUrl) : registrationId;
        if (!shows[id] || (!usableFeedUrl(shows[id].feedUrl) && feedUrl)) shows[id] = data;
    }
    return shows;
}

function notificationTopic(podcastId, feedUrl) {
    const id = canonicalPodcastId(podcastId);
    const topic = `new_ep_${id.replace(/^rss:/, 'rss_')}`;
    return id.startsWith('rss:') && usableFeedUrl(feedUrl) ? `${topic}_${rssScopeId(id, feedUrl).split('~')[1]}` : topic;
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

/**
 * The feed address the job reads and puts in pushes (own-episode-IDs plan P2, R6). A confirmed
 * registry address (the old one is dead and a working one was verified) wins over the address the
 * phone registered; with no registered address, the registry's view (Podcast Index's) is used.
 * @param {{ feedUrl?: string, feedSource?: string }|null} registryEntry from /internal/notifier/shows
 */
function workingFeedUrl(podcastData, registryEntry) {
    const registered = usableFeedUrl(podcastData && podcastData.feedUrl);
    const known = registryEntry ? usableFeedUrl(registryEntry.feedUrl) : null;
    if (known && String(registryEntry.feedSource || '').startsWith('registry:')) return known;
    return registered || known || null;
}

/**
 * Records which address the RSS state came from when it isn't the registered one, and treats the
 * first read of a different address as a quiet baseline: a moved feed can relabel old episodes, and
 * that must never look like a new release.
 */
function withFeedAddress(decision, { existing, feedUrl, registered, newest, now }) {
    const previous = (existing && existing.lastFeedUrl) || registered;
    if (existing && existing.lastRssKey && feedUrl && previous && feedUrl !== previous) {
        decision = {
            notify: false,
            reason: 'rss-address-changed',
            nextState: { ...existing, lastRssKey: newest.key, lastEpisodeTitle: newest.title, lastCheckedAt: now },
        };
    }
    const nextState = { ...decision.nextState };
    if (feedUrl && feedUrl !== registered) nextState.lastFeedUrl = feedUrl;
    else delete nextState.lastFeedUrl;
    return { ...decision, nextState };
}

/** True when the state for a show changed and must be saved (even when the decision is "unchanged"). */
function stateChanged(previous, next) {
    return JSON.stringify(previous ?? null) !== JSON.stringify(next ?? null);
}

async function resolveTrackedRelease({ podcastId, podcastData, existing, fetchRssNewest, fetchPiLatest, now, registryEntry = null }) {
    const pureRss = String(podcastId).startsWith('rss:');
    const registered = usableFeedUrl(podcastData.feedUrl);
    // Without the registry this run (API down, no key), keep the address the state last read, so
    // an outage never flips a show back to a dead address and back again.
    const remembered = usableFeedUrl(existing && existing.lastFeedUrl);
    const feedUrl = pureRss
        ? registered
        : (registryEntry ? workingFeedUrl(podcastData, registryEntry) : (remembered || registered || null));
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
        const newest = { key: rssItemKey(rssItem), title: rssItem.title || 'New Episode', piEpisodeId: matched ? String(piEpisode.id) : undefined };
        const decision = withFeedAddress(applyCheck({ existing, source: 'rss', newest, now }), { existing, feedUrl, registered, newest, now });
        return {
            decision,
            data: buildRssFcmData({ podcastId, podcastTitle: podcastData.title || 'Podcast', imageUrl: podcastData.imageUrl, rssItem, piEpisode: matched ? piEpisode : null, feedUrl }),
            identity: { piId: matched ? piEpisode.id : null, guid: rssItem.guid || null, enclosureUrl: rssItem.enclosureUrl || null },
        };
    }
    const piEpisode = await fetchPiLatest(podcastId);
    if (!piEpisode) return null;
    const decision = applyCheck({ existing, source: 'pi', newest: { piEpisodeId: String(piEpisode.id), title: piEpisode.title || 'New Episode', rssKey: rssItemKey(piEpisode) }, now });
    return {
        decision,
        data: buildPiFcmData({ podcastId, podcastTitle: podcastData.title || 'Podcast', imageUrl: podcastData.imageUrl, piEpisode, feedUrl }),
        identity: { piId: piEpisode.id, guid: piEpisode.guid || null, enclosureUrl: piEpisode.enclosureUrl || null },
    };
}

/**
 * Puts the episode's served ID in a push (own-episode-IDs plan P2): a Podcast Index ID can change
 * or belong to a renumbered episode, the served ID never does. Without one (an episode only the feed
 * lists that no one has been served yet, or the lookup failed) the push opens the show instead, so
 * a push never carries an ID that could point at the wrong episode.
 */
function applyServedId(data, podcastId, servedId) {
    const out = { ...data };
    if (servedId != null && /^[1-9][0-9]{0,15}$/.test(String(servedId))) {
        out.episodeId = String(servedId);
        out.route = `boxlore://episode/${servedId}?autoplay=false`;
    } else {
        delete out.episodeId;
        out.route = `boxlore://podcast/${canonicalPodcastId(podcastId)}`;
    }
    return out;
}

/** Broadcast topics reach every install: a new-episode push must never go there (0.0.25 hydrates and notifies for any). */
const BROADCAST_TOPICS = new Set(['all_users', 'prod_users', 'play_users', 'debug_users', 'beta_users']);
const NEW_EPISODE_TOPIC = /^new_ep_([0-9]{1,16}|rss_[A-Za-z0-9._~%-]{1,800})$/;

function assertNewEpisodeTopic(topic) {
    if (typeof topic !== 'string' || BROADCAST_TOPICS.has(topic) || !NEW_EPISODE_TOPIC.test(topic)) {
        throw new Error(`refusing to send a new-episode push to topic ${JSON.stringify(topic)}`);
    }
    return topic;
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

function buildPiFcmData({ podcastId, podcastTitle, imageUrl, piEpisode, feedUrl }) {
    const episodeId = String(piEpisode.id);
    return omitEmpty({
        type: 'new_episode',
        podcastId: String(podcastId),
        podcastTitle: String(podcastTitle),
        episodeTitle: String(piEpisode.title || 'New Episode'),
        episodeId,
        duration: durationMinutes(piEpisode.duration),
        image: String(piEpisode.image || piEpisode.feedImage || imageUrl || ''),
        // R6: the working address rides along, as it does in RSS pushes.
        feedUrl: feedUrl || '',
        route: `boxlore://episode/${episodeId}?autoplay=false`,
    });
}

module.exports = {
    canonicalPodcastId,
    rssScopeId,
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
    workingFeedUrl,
    withFeedAddress,
    stateChanged,
    applyServedId,
    assertNewEpisodeTopic,
    BROADCAST_TOPICS,
};

// These topics are opted-in visible release alerts, qualifying for prompt Android delivery.
module.exports.newEpisodeFcmMessage = (topic, data) => ({ topic: assertNewEpisodeTopic(topic), data, android: { priority: 'high' } });
