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
            <link rel="enclosure" href="https://cdn.example.com/atom.mp3"/>
          </entry>
        </feed>`;
        const newest = lib.newestRssItem(lib.parseFeedItems(xml));
        assert.equal(newest.guid, 'atom-1');
        assert.equal(newest.enclosureUrl, 'https://cdn.example.com/atom.mp3');
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
                received: lib.RSS_PREFIX_BYTES,
                declared: dailyBytes,
                xml: '<rss><channel><item><title>x</title></item>',
            }),
            'prefix-enough',
        );
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
