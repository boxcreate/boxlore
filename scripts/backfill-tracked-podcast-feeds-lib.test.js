'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
    BACKFILL_USER_AGENT,
    exactItunesFeedUrl,
    missingTrackedPodcasts,
    resolveTrackedPodcastFeed,
    usableHttpsFeedUrl,
} = require('./backfill-tracked-podcast-feeds-lib');

describe('backfill-tracked-podcast-feeds-lib', () => {
    it('selects only rows without a valid HTTPS feed URL', () => {
        assert.deepEqual(
            missingTrackedPodcasts({
                1: { title: 'Ready', feedUrl: 'https://feeds.example/ready.xml' },
                2: { title: 'Missing' },
                3: { title: 'Legacy', feedUrl: 'http://feeds.example/legacy.xml' },
                4: null,
            }),
            [
                { id: '2', title: 'Missing' },
                { id: '3', title: 'Legacy' },
            ],
        );
        assert.equal(usableHttpsFeedUrl('javascript:alert(1)'), '');
    });

    it('uses the authenticated boxlore API HTTPS URL first', async () => {
        const calls = [];
        const feedUrl =
            await resolveTrackedPodcastFeed({
                id: '42',
                title: 'Show',
                apiBaseUrl: 'https://api.example',
                appKey: 'secret',
                fetchImpl: async (url, options) => {
                    calls.push({ url: url.toString(), options });
                    return {
                        ok: true,
                        json: async () => ({ feed: { url: 'https://feeds.example/show.xml' } }),
                    };
                },
            });

        assert.equal(feedUrl, 'https://feeds.example/show.xml');
        assert.equal(calls.length, 1);
        assert.equal(calls[0].options.headers['X-App-Key'], 'secret');
        assert.equal(calls[0].options.headers['User-Agent'], BACKFILL_USER_AGENT);
        assert.match(calls[0].url, /\/podcast\?id=42$/);
    });

    it('probes a secure upgrade for legacy HTTP API URLs', async () => {
        const calls = [];
        const feedUrl =
            await resolveTrackedPodcastFeed({
                id: '42',
                title: 'Show',
                apiBaseUrl: 'https://api.example',
                appKey: 'secret',
                fetchImpl: async (url) => {
                    calls.push(url.toString());
                    if (calls.length === 1) {
                        return {
                            ok: true,
                            json: async () => ({ feed: { url: 'http://feeds.example/show.xml' } }),
                        };
                    }
                    return {
                        ok: true,
                        url: 'https://cdn.example/show.xml',
                        body: { cancel: async () => {} },
                    };
                },
            });

        assert.equal(feedUrl, 'https://cdn.example/show.xml');
        assert.equal(calls[1], 'https://feeds.example/show.xml');
    });

    it('falls back to an exact Apple title match', async () => {
        let call = 0;
        const feedUrl =
            await resolveTrackedPodcastFeed({
                id: '42',
                title: 'Crimes Reais Revelados',
                apiBaseUrl: 'https://api.example',
                appKey: 'secret',
                fetchImpl: async () => {
                    call++;
                    if (call === 1) {
                        return { ok: true, json: async () => ({ feed: { url: '' } }) };
                    }
                    return {
                        ok: true,
                        json: async () => ({
                            results: [
                                {
                                    collectionName: 'Crimes Reais Revelados',
                                    feedUrl: 'https://feeds.example/crimes.xml',
                                },
                            ],
                        }),
                    };
                },
            });

        assert.equal(feedUrl, 'https://feeds.example/crimes.xml');
    });

    it('rejects non-exact Apple title matches and insecure feeds', () => {
        assert.equal(
            exactItunesFeedUrl(
                {
                    results: [
                        {
                            collectionName: 'A Different Show',
                            feedUrl: 'https://feeds.example/different.xml',
                        },
                        {
                            collectionName: 'Target Show',
                            feedUrl: 'http://feeds.example/target.xml',
                        },
                    ],
                },
                'Target Show',
            ),
            '',
        );
    });
});
