'use strict';

function decodeXml(value) {
    return String(value || '')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&amp;/g, '&')
        .trim();
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
    return decodeXml(match[1] || match[2] || '');
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
    let enclosureUrl = attrValue(itemXml, 'enclosure', 'url');
    if (!enclosureUrl) {
        const enclosureLink = itemXml.match(
            /<link[^>]*rel=["']enclosure["'][^>]*href=["']([^"']+)["']/i,
        );
        enclosureUrl = enclosureLink ? decodeXml(enclosureLink[1]) : '';
    }
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

function parseFeedItems(xml) {
    const source = String(xml || '');
    const items = [];
    const itemRe = /<item[\s>][\s\S]*?<\/item>/gi;
    let match;
    while ((match = itemRe.exec(source))) {
        items.push(parseItemXml(match[0], 'rss'));
    }
    if (items.length > 0) return items;
    const entryRe = /<entry[\s>][\s\S]*?<\/entry>/gi;
    while ((match = entryRe.exec(source))) {
        items.push(parseItemXml(match[0], 'atom'));
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

/** Stop after this many bytes once a complete RSS item / Atom entry is in the buffer. */
const RSS_PREFIX_BYTES = 2 * 1024 * 1024;

function feedHasCompleteItem(xml) {
    const source = String(xml || '');
    return /<\/item>/i.test(source) || /<\/entry>/i.test(source);
}

/**
 * @returns {'too-large' | 'prefix-enough' | 'continue'}
 */
function rssDownloadDecision({
    received = 0,
    declared,
    maxBytes = MAX_FEED_BYTES,
    prefixBytes = RSS_PREFIX_BYTES,
    xml = '',
} = {}) {
    const declaredN = Number(declared);
    if (received === 0 && Number.isFinite(declaredN) && declaredN > maxBytes) {
        return 'too-large';
    }
    if (received > maxBytes) return 'too-large';
    if (received >= prefixBytes && feedHasCompleteItem(xml)) return 'prefix-enough';
    return 'continue';
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
    RSS_PREFIX_BYTES,
    feedHasCompleteItem,
    rssDownloadDecision,
};
