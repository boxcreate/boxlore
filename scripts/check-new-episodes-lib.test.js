'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const lib = require('./check-new-episodes-lib');

const RSS_OLDEST_FIRST = `<?xml version="1.0"?>
<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
  <channel>
    <item>
      <title>Old</title>
      <guid>guid-old</guid>
      <pubDate>Wed, 01 Jan 2020 00:00:00 GMT</pubDate>
      <enclosure url="https://cdn.example.com/old.mp3" type="audio/mpeg"/>
      <itunes:duration>00:10:00</itunes:duration>
    </item>
    <item>
      <title>New drop</title>
      <guid isPermaLink="false">guid-new</guid>
      <pubDate>Wed, 02 Jan 2020 00:00:00 GMT</pubDate>
      <enclosure url="https://cdn.example.com/new.mp3" type="audio/mpeg"/>
      <itunes:duration>3600</itunes:duration>
      <itunes:image href="https://cdn.example.com/new.jpg"/>
    </item>
  </channel>
</rss>`;

describe('check-new-episodes-lib', () => {
    it('usableFeedUrl keeps https only', () => {
        assert.equal(lib.usableFeedUrl('https://feeds.example/a.xml'), 'https://feeds.example/a.xml');
        assert.equal(lib.usableFeedUrl('http://feeds.example/a.xml'), '');
        assert.equal(lib.usableFeedUrl(''), '');
    });

    it('rssItemKey prefers guid then enclosure', () => {
        assert.equal(lib.rssItemKey({ guid: 'g1', enclosureUrl: 'https://a' }), 'g1');
        assert.equal(lib.rssItemKey({ guid: '  ', enclosureUrl: 'https://a' }), 'https://a');
        assert.equal(lib.rssItemKey({}), '');
    });

    it('parses RSS and picks newest by pubDate', () => {
        const newest = lib.newestRssItem(lib.parseFeedItems(RSS_OLDEST_FIRST));
        assert.equal(newest.guid, 'guid-new');
        assert.equal(newest.title, 'New drop');
        assert.equal(newest.enclosureUrl, 'https://cdn.example.com/new.mp3');
        assert.equal(newest.image, 'https://cdn.example.com/new.jpg');
        assert.equal(lib.durationMinutes(newest.duration), '60');
    });

    it('parses Atom enclosure link', () => {
        const xml = `<?xml version="1.0"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <id>atom-1</id>
            <title>Atom ep</title>
            <updated>2020-01-03T00:00:00Z</updated>
            <link href="https://cdn.example.com/atom.mp3" rel="enclosure"/>
          </entry>
        </feed>`;
        const newest = lib.newestRssItem(lib.parseFeedItems(xml));
        assert.equal(newest.guid, 'atom-1');
        assert.equal(newest.enclosureUrl, 'https://cdn.example.com/atom.mp3');
    });

    it('ignores newer non-media and untitled entries instead of alerting an unplayable release', () => {
        const xml = RSS_OLDEST_FIRST.replace('</channel>', `
          <item><guid>announcement</guid><title>News</title><pubDate>2025-01-01</pubDate></item>
          <item><guid>document</guid><title>PDF</title><pubDate>2025-01-02</pubDate><enclosure url="https://cdn.example/notes.pdf" type="application/pdf"/></item>
          <item><guid>untitled</guid><pubDate>2025-01-03</pubDate><enclosure url="https://cdn.example/empty.mp3"/></item>
        </channel>`);
        const items = lib.parseFeedItems(xml);
        assert.equal(items.length, 2);
        assert.equal(lib.newestRssItem(items).guid, 'guid-new');
    });

    it('accepts playable media:content with a token URL without a media extension', () => {
        const items = lib.parseFeedItems('<rss><channel><item><guid>media</guid><title>Episode</title><media:content url="https://cdn.example/play?token=one" medium="audio"/></item></channel></rss>');
        assert.equal(items[0].enclosureUrl, 'https://cdn.example/play?token=one');
    });

    it('decodes numeric XML references in release keys and enclosures for exact Android hydration', () => {
        const [item] = lib.parseFeedItems('<rss><channel><item><guid>g&#x26;one</guid><title>Episode &#128512;</title><enclosure type="audio/mpeg" url="https://cdn.example/play?t=&#65;&amp;p=2"/></item></channel></rss>');
        assert.equal(item.guid, 'g&one');
        assert.equal(item.title, 'Episode 😀');
        assert.equal(item.enclosureUrl, 'https://cdn.example/play?t=A&p=2');
    });

    it('preserves literal CDATA keys and decodes escaped references only once', () => {
        const [item] = lib.parseFeedItems('<rss><channel><item><guid><![CDATA[g&amp;literal]]></guid><title>Episode &amp;#38;</title><enclosure url="https://cdn.example/ep.mp3"/></item></channel></rss>');
        assert.equal(item.guid, 'g&amp;literal');
        assert.equal(item.title, 'Episode &#38;');
    });

    it('rssMatchesPi uses enclosure then guid', () => {
        assert.equal(
            lib.rssMatchesPi(
                { enclosureUrl: 'https://a.mp3', guid: 'g' },
                { enclosureUrl: 'https://a.mp3', guid: 'other' },
            ),
            true,
        );
        assert.equal(
            lib.rssMatchesPi(
                { enclosureUrl: 'https://a.mp3', guid: 'g' },
                { enclosureUrl: 'https://b.mp3', guid: 'g' },
            ),
            true,
        );
        assert.equal(
            lib.rssMatchesPi(
                { enclosureUrl: 'https://a.mp3', guid: 'g' },
                { enclosureUrl: 'https://b.mp3', guid: 'x' },
            ),
            false,
        );
    });

    it('applyCheck RSS first see with a different PI id notifies', () => {
        const result = lib.applyCheck({
            existing: { lastEpisodeId: '99', lastEpisodeTitle: 'Old PI', lastCheckedAt: 1 },
            source: 'rss',
            newest: { key: 'guid-new', title: 'New drop', piEpisodeId: '100' },
            now: 50,
        });
        assert.equal(result.notify, true);
        assert.equal(result.reason, 'rss-new-after-pi');
        assert.equal(result.nextState.lastRssKey, 'guid-new');
        assert.equal(result.nextState.lastEpisodeId, '100');
    });

    it('applyCheck RSS first see with the same PI id is a quiet baseline', () => {
        const result = lib.applyCheck({
            existing: { lastEpisodeId: '100', lastEpisodeTitle: 'Same', lastCheckedAt: 1 },
            source: 'rss',
            newest: { key: 'guid-new', title: 'Same', piEpisodeId: '100' },
            now: 50,
        });
        assert.equal(result.notify, false);
        assert.equal(result.reason, 'rss-baseline');
        assert.equal(result.nextState.lastRssKey, 'guid-new');
    });

    it('applyCheck RSS notifies on key change and preserves PI id when unmatched', () => {
        const result = lib.applyCheck({
            existing: {
                lastRssKey: 'guid-old',
                lastEpisodeId: '10',
                lastEpisodeTitle: 'Old',
                lastCheckedAt: 1,
            },
            source: 'rss',
            newest: { key: 'guid-new', title: 'New drop' },
            now: 50,
        });
        assert.equal(result.notify, true);
        assert.equal(result.reason, 'rss-new');
        assert.equal(result.nextState.lastRssKey, 'guid-new');
        assert.equal(result.nextState.lastEpisodeId, '10');
    });

    it('applyCheck PI preserves lastRssKey and does not notify on same id', () => {
        const existing = {
            lastEpisodeId: '10',
            lastRssKey: 'guid-x',
            lastEpisodeTitle: 'Same',
            lastCheckedAt: 1,
        };
        const same = lib.applyCheck({
            existing,
            source: 'pi',
            newest: { piEpisodeId: '10', title: 'Same' },
            now: 50,
        });
        assert.equal(same.notify, false);
        assert.equal(same.nextState.lastRssKey, 'guid-x');

        const changed = lib.applyCheck({
            existing,
            source: 'pi',
            newest: { piEpisodeId: '11', title: 'Next' },
            now: 50,
        });
        assert.equal(changed.notify, true);
        assert.equal(changed.nextState.lastRssKey, 'guid-x');
        assert.equal(changed.nextState.lastEpisodeId, '11');
    });

    it('buildRssFcmData omits PI episodeId when unmatched and never mints negative ids', () => {
        const data = lib.buildRssFcmData({
            podcastId: '123',
            podcastTitle: 'Show',
            imageUrl: 'https://img',
            feedUrl: 'https://feeds.example/show.xml',
            rssItem: {
                title: 'Feed only',
                guid: 'guid-new',
                enclosureUrl: 'https://cdn.example.com/new.mp3',
                duration: '1800',
            },
            piEpisode: { id: '99', enclosureUrl: 'https://other.mp3', guid: 'other' },
        });
        assert.equal(data.route, 'boxlore://podcast/123');
        assert.equal(data.episodeId, undefined);
        assert.equal(data.guid, 'guid-new');
        assert.equal(data.feedUrl, 'https://feeds.example/show.xml');
        assert.ok(!Object.values(data).some((v) => String(v).startsWith('-')));
    });

    it('buildRssFcmData includes PI episodeId when enclosure matches', () => {
        const data = lib.buildRssFcmData({
            podcastId: '123',
            podcastTitle: 'Show',
            imageUrl: 'https://img',
            feedUrl: 'https://feeds.example/show.xml',
            rssItem: {
                title: 'Matched',
                guid: 'g',
                enclosureUrl: 'https://cdn.example.com/ep.mp3',
            },
            piEpisode: { id: 555, enclosureUrl: 'https://cdn.example.com/ep.mp3' },
        });
        assert.equal(data.episodeId, '555');
        assert.equal(data.route, 'boxlore://episode/555?autoplay=false');
    });

    it('durationMinutes covers empty, seconds, clock forms, and negatives', () => {
        assert.equal(lib.durationMinutes(''), '0');
        assert.equal(lib.durationMinutes('1800'), '30');
        assert.equal(lib.durationMinutes('12:00'), '12');
        assert.equal(lib.durationMinutes('01:00:00'), '60');
        assert.equal(lib.durationMinutes('nope'), '0');
        assert.equal(lib.durationMinutes('-1'), '0');
        assert.equal(lib.durationMinutes('-1:00'), '0');
    });

    it('applyCheck does not notify twice when RSS then PI describe the same episode', () => {
        const afterRss = lib.applyCheck({
            existing: {
                lastRssKey: 'guid-old',
                lastEpisodeId: '10',
                lastEpisodeTitle: 'Old',
                lastCheckedAt: 1,
            },
            source: 'rss',
            newest: { key: 'guid-new', title: 'Feed only' },
            now: 50,
        });
        assert.equal(afterRss.notify, true);

        const piCatchUp = lib.applyCheck({
            existing: afterRss.nextState,
            source: 'pi',
            newest: {
                piEpisodeId: '99',
                title: 'Feed only',
                rssKey: 'guid-new',
            },
            now: 60,
        });
        assert.equal(piCatchUp.notify, false);
        assert.equal(piCatchUp.nextState.lastEpisodeId, '99');
        assert.equal(piCatchUp.nextState.lastRssKey, 'guid-new');
    });

    it('rssDownloadDecision allows The Daily-sized Content-Length under the 25MB cap', () => {
        const dailyBytes = 18519046;
        assert.equal(
            lib.rssDownloadDecision({
                received: 0,
                declared: dailyBytes,
                maxBytes: 5_000_000,
                xml: '',
            }),
            'too-large',
        );
        assert.equal(
            lib.rssDownloadDecision({
                received: 0,
                declared: dailyBytes,
                xml: '',
            }),
            'continue',
        );
        assert.equal(
            lib.rssDownloadDecision({
                received: 2 * 1024 * 1024,
                declared: dailyBytes,
                xml: '<rss><channel><item><title>x</title></item>',
            }),
            'continue',
        );
    });

    it('reads a large oldest-first feed through to its newest playable episode at the end', async () => {
        const split = RSS_OLDEST_FIRST.indexOf('<item>', RSS_OLDEST_FIRST.indexOf('<item>') + 1);
        const first = RSS_OLDEST_FIRST.slice(0, split) + '<!--' + 'x'.repeat(2 * 1024 * 1024) + '-->';
        const body = (async function* () {
            yield Buffer.from(first);
            yield Buffer.from(RSS_OLDEST_FIRST.slice(split));
        })();
        const xml = await lib.fetchRssText('https://publisher.example/feed', {
            fetchImpl: async () => ({ ok: true, headers: { get: () => null }, body }),
        });
        assert.equal(lib.newestRssItem(lib.parseFeedItems(xml)).guid, 'guid-new');
    });

    it('rejects an interrupted feed even when an earlier complete item was received', async () => {
        const body = (async function* () {
            yield Buffer.from('<rss><channel><item><guid>old</guid><title>Old</title><enclosure url="https://cdn.example/old.mp3"/></item>');
            const error = new Error('interrupted');
            error.name = 'AbortError';
            throw error;
        })();
        await assert.rejects(lib.fetchRssText('https://publisher.example/feed', {
            fetchImpl: async () => ({ ok: true, headers: { get: () => null }, body }),
        }), { name: 'AbortError' });
    });

    it('aborts oversized streams and declared oversized responses without accepting a prefix', async () => {
        let signal;
        let closed = false;
        const body = (async function* () {
            try { yield Buffer.from('too large'); } finally { closed = true; }
        })();
        await assert.rejects(lib.fetchRssText('https://publisher.example/feed', {
            maxBytes: 3,
            fetchImpl: async (_, options) => { signal = options.signal; return { ok: true, headers: { get: () => null }, body }; },
        }), /too large/);
        assert.equal(signal.aborted, true);
        assert.equal(closed, true);
        await assert.rejects(lib.fetchRssText('https://publisher.example/feed', {
            maxBytes: 3,
            fetchImpl: async () => ({ ok: true, headers: { get: () => '4' }, body: {} }),
        }), /too large/);
    });

    it('fails a timeout even when a stream ends quietly after its signal is aborted', async () => {
        await assert.rejects(lib.fetchRssText('https://publisher.example/feed', {
            timeoutMs: 5,
            fetchImpl: async (_, { signal }) => ({
                ok: true, headers: { get: () => null },
                body: (async function* () {
                    yield Buffer.from('<rss><channel><item></item>');
                    await new Promise((resolve) => signal.addEventListener('abort', resolve, { once: true }));
                })(),
            }),
        }), /timed out/);
    });

    it('rejects an HTTPS feed that redirects to HTTP', async () => {
        await assert.rejects(lib.fetchRssText('https://publisher.example/feed', {
            fetchImpl: async () => ({ ok: true, url: 'http://publisher.example/feed' }),
        }), /non-HTTPS/);
    });

    it('applyCheck RSS does not notify when PI id already recorded', () => {
        const result = lib.applyCheck({
            existing: {
                lastRssKey: 'guid-old',
                lastEpisodeId: '555',
                lastEpisodeTitle: 'Same',
                lastCheckedAt: 1,
            },
            source: 'rss',
            newest: { key: 'guid-new', title: 'Same', piEpisodeId: '555' },
            now: 50,
        });
        assert.equal(result.notify, false);
        assert.equal(result.nextState.lastRssKey, 'guid-new');
    });
});

it('visible release alerts request high Android priority without changing payload identity', () => {
    const data = { type: 'new_episode', podcastId: '123', guid: 'unindexed', enclosureUrl: 'https://cdn/new.mp3' };
    assert.deepEqual(lib.newEpisodeFcmMessage('new_ep_123', data), {
        topic: 'new_ep_123', data, android: { priority: 'high' },
    });
});

describe('pure RSS notifications', () => {
    const podcastId = 'rss:012345';
    const feedUrl = 'https://publisher.example/public.xml';
    const podcastData = { title: 'Public show', imageUrl: '', feedUrl };
    const item = { guid: 'https://publisher.example/item?token=secret', title: 'Release', enclosureUrl: 'https://audio.example/release.mp3' };
    const noPi = async () => { assert.fail('Pure RSS must never query Podcast Index'); };

    it('topic mapping preserves numeric topics and accepts rss IDs', () => {
        assert.equal(lib.notificationTopic('123'), 'new_ep_123');
        assert.equal(lib.notificationTopic(podcastId), 'new_ep_rss_012345');
        assert.match(lib.notificationTopic(podcastId), /^[a-zA-Z0-9\-_.~%]+$/);
    });

    it('first check quietly seeds a digest and does not publish feed URL or raw key', async () => {
        const result = await lib.resolveTrackedRelease({ podcastId, podcastData, fetchRssNewest: async () => item, fetchPiLatest: noPi, now: 100 });
        assert.equal(result.decision.notify, false);
        assert.match(result.decision.nextState.lastRssKey, /^sha256:[a-f0-9]{64}$/);
        const state = JSON.stringify(result.decision.nextState);
        assert.equal(state.includes('secret'), false);
        assert.equal(state.includes(feedUrl), false);
        assert.equal(result.data.podcastId, podcastId);
        assert.equal(result.data.episodeId, undefined);
    });

    it('new release alerts once and same release remains quiet', async () => {
        const first = lib.applyPureRssCheck({ item, now: 100 });
        const nextItem = { ...item, guid: 'new-guid' };
        const next = await lib.resolveTrackedRelease({ podcastId, podcastData, existing: first.nextState, fetchRssNewest: async () => nextItem, fetchPiLatest: noPi, now: 200 });
        assert.equal(next.decision.notify, true);
        const again = lib.applyPureRssCheck({ existing: next.decision.nextState, item: nextItem, now: 300 });
        assert.equal(again.notify, false);
    });

    it('metadata edits do not alert and raw legacy keys are migrated quietly', () => {
        const migrated = lib.applyPureRssCheck({ existing: { lastRssKey: item.guid, lastEpisodeTitle: 'Old title' }, item, now: 100 });
        assert.equal(migrated.notify, false);
        assert.equal(migrated.reason, 'rss-state-migrated');
        assert.equal(JSON.stringify(migrated.nextState).includes('secret'), false);
        assert.equal(lib.applyPureRssCheck({ existing: migrated.nextState, item: { ...item, title: 'Edited title' } }).notify, false);
    });

    it('feed failure retains baseline for retry without a catalog fallback', async () => {
        const existing = lib.applyPureRssCheck({ item }).nextState;
        await assert.rejects(lib.resolveTrackedRelease({ podcastId, podcastData, existing, fetchRssNewest: async () => { throw new Error('403'); }, fetchPiLatest: noPi }), /403/);
        assert.equal(lib.applyPureRssCheck({ existing, item }).notify, false);
    });

    it('disabled tracking and empty feeds do not query catalog or replace state', async () => {
        assert.equal(await lib.resolveTrackedRelease({ podcastId, podcastData: { title: 'Show' }, fetchRssNewest: async () => { assert.fail('No URL means no fetch'); }, fetchPiLatest: noPi }), null);
        assert.equal(await lib.resolveTrackedRelease({ podcastId, podcastData, fetchRssNewest: async () => null, fetchPiLatest: noPi }), null);
    });

    it('catalog shows retain the PI fallback when RSS fails', async () => {
        const result = await lib.resolveTrackedRelease({ podcastId: '123', podcastData, fetchRssNewest: async () => { throw new Error('offline'); }, fetchPiLatest: async () => ({ id: 42, title: 'PI release' }), now: 100 });
        assert.equal(result.decision.reason, 'pi-baseline');
        assert.equal(result.data.episodeId, '42');
    });
});

describe('shared RSS registrations', () => {
    it('groups devices into one canonical feed and leaves numeric show keys unchanged', () => {
        const rows = {
            'rss:abc~device-a': { title: 'Show', feedUrl: 'https://example.com/feed' },
            'rss:abc~device-b': { title: 'Show', feedUrl: 'https://example.com/feed' },
            '123': { title: 'Catalog' },
        };
        const scope = lib.rssScopeId('rss:abc', 'https://example.com/feed');
        const grouped = lib.groupTrackedPodcasts(rows);
        assert.deepEqual(Object.keys(grouped).sort(), ['123', scope]);
        assert.equal(grouped[scope].feedUrl, 'https://example.com/feed');
        delete rows['rss:abc~device-a'];
        assert.equal(lib.groupTrackedPodcasts(rows)[scope].feedUrl, 'https://example.com/feed');
        delete rows['rss:abc~device-b'];
        assert.equal(lib.groupTrackedPodcasts(rows)[scope], undefined);
    });

    it('ignores disabled RSS rows while keeping valid registrations', () => {
        const grouped = lib.groupTrackedPodcasts({
            'rss:abc~disabled': { title: 'Show' },
            'rss:abc~active': { title: 'Show', feedUrl: 'https://example.com/feed' },
            'rss:invalid': { title: 'Invalid', feedUrl: 'http://example.com/feed' },
        });
        assert.deepEqual(Object.keys(grouped), [lib.rssScopeId('rss:abc', 'https://example.com/feed')]);
    });
});

it('RSS reactivation after all listeners leave seeds a quiet baseline', () => {
    const old = lib.applyPureRssCheck({ item: { guid: 'archive', title: 'Old' } }).nextState;
    const retained = lib.activeEpisodeState({ 'rss:abc': old, '123': { lastEpisodeId: '12' } }, { '123': {} });
    assert.equal(retained['rss:abc'], undefined);
    assert.equal(retained['123'].lastEpisodeId, '12');
    const reactivated = lib.applyPureRssCheck({ existing: retained['rss:abc'], item: { guid: 'latest', title: 'New' } });
    assert.equal(reactivated.notify, false);
    assert.equal(reactivated.reason, 'rss-baseline');
    assert.equal(lib.activeEpisodeState({ 'rss:abc': old }, { 'rss:abc': {} })['rss:abc'], old);
});


describe('accepted RSS URL isolation', () => {
    const id = 'rss:012345';
    const url = 'https://publisher.example/public.xml';
    const other = 'https://publisher.example/other.xml';
    it('uses the same SHA-256 topic contract as Android without exposing the URL', () => {
        assert.equal(lib.notificationTopic(id, url), 'new_ep_rss_012345_4828c14697465b35180be700e5bc4ce4122e206d27db253f0232414f6f656026');
        assert.notEqual(lib.notificationTopic(id, url), lib.notificationTopic(id, other));
        assert.equal(lib.notificationTopic('123', url), 'new_ep_123');
    });
    it('keeps feeds sharing a restored show ID separate while grouping identical URLs', () => {
        const groups = lib.groupTrackedPodcasts({
            [`${id}~device-a~hash-a`]: { title: 'A', feedUrl: url },
            [`${id}~device-b~hash-b`]: { title: 'B', feedUrl: other },
            [`${id}~device-c`]: { title: 'A', feedUrl: url },
        });
        assert.equal(Object.keys(groups).length, 2);
        assert.equal(groups[lib.rssScopeId(id, url)].feedUrl, url);
        assert.equal(groups[lib.rssScopeId(id, other)].feedUrl, other);
        for (const scope of Object.keys(groups)) assert.equal(lib.canonicalPodcastId(scope), id);
    });
    it('retires another URL scope and legacy show-only state without inheriting its baseline', () => {
        const oldScope = lib.rssScopeId(id, url);
        const newScope = lib.rssScopeId(id, other);
        const prior = lib.applyPureRssCheck({ item: { guid: 'old' } }).nextState;
        const kept = lib.activeEpisodeState({ [id]: prior, [oldScope]: prior }, { [newScope]: {} });
        assert.equal(kept[id], undefined);
        assert.equal(kept[oldScope], undefined);
        assert.equal(lib.applyPureRssCheck({ existing: kept[newScope], item: { guid: 'new' } }).notify, false);
    });
});
