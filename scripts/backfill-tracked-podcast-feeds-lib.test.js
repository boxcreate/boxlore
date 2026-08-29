'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
    BACKFILL_USER_AGENT,
    exactItunesFeedUrl,
    missingTrackedPodcasts,
    resolveTrackedPodcastFeed,
    utcIsoWeekName,
    usableHttpsFeedUrl,
    writeWeeklyTrackedPodcastBackup,
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

    it('uses UTC ISO week names across year boundaries', () => {
        assert.equal(utcIsoWeekName(new Date('2024-12-30T12:00:00Z')), '2025-W01.json');
        assert.equal(utcIsoWeekName(new Date('2025-01-05T23:59:59Z')), '2025-W01.json');
        assert.equal(utcIsoWeekName(new Date('2025-01-06T00:00:00Z')), '2025-W02.json');
    });

    it('keeps ten weekly snapshots and overwrites the current week', () => {
        const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'boxlore-feed-backups-'));
        try {
            fs.writeFileSync(path.join(directory, 'README.md'), 'keep me');
            let latest;
            for (let week = 0; week < 12; week++) {
                latest =
                    writeWeeklyTrackedPodcastBackup({
                        directory,
                        trackedPodcasts: {
                            2: { title: `Show ${week}` },
                            1: { feedUrl: 'https://feeds.example/one.xml' },
                        },
                        now: new Date(Date.UTC(2025, 0, 6 + (week * 7))),
                    });
            }

            const jsonFiles =
                fs.readdirSync(directory)
                    .filter((name) => name.endsWith('.json'))
                    .sort();
            assert.equal(jsonFiles.length, 10);
            assert.equal(jsonFiles[0], '2025-W04.json');
            assert.equal(jsonFiles.at(-1), '2025-W13.json');
            assert.equal(fs.existsSync(path.join(directory, 'README.md')), true);

            writeWeeklyTrackedPodcastBackup({
                directory,
                trackedPodcasts: { 1: { title: 'Replacement' } },
                now: new Date(Date.UTC(2025, 2, 24)),
            });
            assert.equal(
                JSON.parse(fs.readFileSync(path.join(directory, latest.filename), 'utf8'))['1'].title,
                'Replacement',
            );
            assert.equal(
                fs.readdirSync(directory).filter((name) => name.endsWith('.json')).length,
                10,
            );
        } finally {
            fs.rmSync(directory, { recursive: true, force: true });
        }
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
