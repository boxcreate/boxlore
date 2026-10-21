'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
    BACKFILL_USER_AGENT,
    compareAndSetFeedUrl,
    exactItunesFeedUrl,
    fetchRegistryView,
    missingTrackedPodcasts,
    planTrackedFeedRepairs,
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

    it('sends the scoped service key instead of the app key when configured', async () => {
        const calls = [];
        await resolveTrackedPodcastFeed({
            id: '42',
            title: 'Show',
            apiBaseUrl: 'https://api.example',
            appKey: 'secret',
            serviceKey: 'gh-backfill.svc-secret',
            fetchImpl: async (url, options) => {
                calls.push(options);
                return { ok: true, json: async () => ({ feed: { url: 'https://feeds.example/show.xml' } }) };
            },
        });
        assert.equal(calls[0].headers.Authorization, 'Svc gh-backfill.svc-secret');
        assert.equal(calls[0].headers['X-App-Key'], undefined);
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

describe('RSS tracking backup and repair', () => {
    it('never sends RSS-only registrations to catalog matching', async () => {
        assert.deepEqual(missingTrackedPodcasts({ 'rss:abc~device': { title: 'Private show' } }), []);
        const resolved = await resolveTrackedPodcastFeed({ id: 'rss:abc~device', title: 'Private', apiBaseUrl: 'https://api.example', appKey: 'key', fetchImpl: async () => { assert.fail('RSS must never query a catalog'); } });
        assert.equal(resolved, '');
    });

    it('backs up consented public feed metadata without device registration IDs', () => {
        const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'rss-tracking-backup-'));
        try {
            const result = writeWeeklyTrackedPodcastBackup({ directory, now: new Date('2026-10-05'), trackedPodcasts: {
                'rss:abc~device-a': { title: 'Public', feedUrl: 'https://example.com/public.xml' },
                'rss:abc~device-b': { title: 'Public', feedUrl: 'https://example.com/public.xml' },
                'rss:abc~device-c': { title: 'Other accepted feed', feedUrl: 'https://example.com/other.xml' },
                '123': { title: 'Catalog', feedUrl: 'https://example.com/catalog.xml' },
            } });
            const text = fs.readFileSync(path.join(directory, result.filename), 'utf8');
            const backup = JSON.parse(text);
            const scope = require('./check-new-episodes-lib').rssScopeId('rss:abc', 'https://example.com/public.xml');
            const otherScope = require('./check-new-episodes-lib').rssScopeId('rss:abc', 'https://example.com/other.xml');
            assert.deepEqual(Object.keys(backup), ['123', scope, otherScope]);
            assert.equal(backup[scope].feedUrl, 'https://example.com/public.xml');
            assert.equal(backup[otherScope].feedUrl, 'https://example.com/other.xml');
            assert.equal(text.includes('device-a'), false);
            assert.equal(text.includes('device-b'), false);
            assert.equal(text.includes('device-c'), false);
        } finally { fs.rmSync(directory, { recursive: true, force: true }); }
    });
});

describe('registry-based repair', () => {
    const registry = new Map([
        ['1', { id: 1, feedUrl: 'https://apple.example/one.xml', feedSource: 'registry:apple', flags: { addressRepair: true } }],
        ['2', { id: 2, feedUrl: 'https://pi.example/two.xml', feedSource: 'pidump', flags: { addressRepair: false } }],
        ['3', { id: 3, feedUrl: 'https://apple.example/three.xml', feedSource: 'registry:apple', flags: { addressRepair: false } }],
        ['4', { id: 4, feedUrl: 'https://same.example/four.xml', feedSource: 'registry:apple', flags: { addressRepair: true } }],
        ['5', { id: 5, feedUrl: 'http://pi.example/five.xml', feedSource: 'pidump', flags: {} }],
    ]);

    it('fills missing addresses from the registry and replaces only proven-dead ones', () => {
        const plan = planTrackedFeedRepairs({
            1: { title: 'Dead', feedUrl: 'https://dead.example/one.xml' },
            2: { title: 'Missing' },
            3: { title: 'Confirmed but not flagged', feedUrl: 'https://other.example/three.xml' },
            4: { title: 'Already right', feedUrl: 'https://same.example/four.xml' },
            5: { title: 'Only http known', feedUrl: 'http://pi.example/five.xml' },
            6: { title: 'Not in registry' },
            7: { title: 'Healthy', feedUrl: 'https://ok.example/seven.xml' },
            'rss:abc~device': { title: 'Private' },
        }, registry);
        assert.deepEqual(plan, [
            { id: '1', title: 'Dead', read: 'https://dead.example/one.xml', to: 'https://apple.example/one.xml', reason: 'replace' },
            { id: '2', title: 'Missing', read: null, to: 'https://pi.example/two.xml', reason: 'fill' },
            { id: '5', title: 'Only http known', read: 'http://pi.example/five.xml', to: '', reason: 'fill' },
            { id: '6', title: 'Not in registry', read: null, to: '', reason: 'fill' },
        ]);
    });

    it('never replaces a saved address without the registry', () => {
        assert.deepEqual(planTrackedFeedRepairs({ 1: { feedUrl: 'https://dead.example/one.xml' } }), []);
    });

    // RTDB runs the update first against its local cache (null when nothing is cached), then again
    // with the server's value whenever the two differ.
    function runTransaction(serverValue, update) {
        let local = null;
        for (let attempt = 0; attempt < 3; attempt++) {
            const next = update(local);
            if (next === undefined) return { committed: false, value: serverValue };
            if (JSON.stringify(local) === JSON.stringify(serverValue)) return { committed: true, value: next };
            local = serverValue;
        }
        throw new Error('transaction did not settle');
    }

    it('writes only while the saved address is still the one read', () => {
        const read = 'https://dead.example/one.xml';
        const to = 'https://apple.example/one.xml';
        assert.deepEqual(runTransaction(read, compareAndSetFeedUrl(read, to)), { committed: true, value: to });
        assert.deepEqual(runTransaction(null, compareAndSetFeedUrl(null, to)), { committed: true, value: to });
        assert.deepEqual(
            runTransaction('https://app.example/new.xml', compareAndSetFeedUrl(read, to)),
            { committed: false, value: 'https://app.example/new.xml' },
            'an app write since the read wins',
        );
        assert.deepEqual(
            runTransaction('https://app.example/new.xml', compareAndSetFeedUrl(null, to)),
            { committed: false, value: 'https://app.example/new.xml' },
            'a fill never overwrites an address saved since the read',
        );
        assert.deepEqual(
            runTransaction(null, compareAndSetFeedUrl(read, to)),
            { committed: true, value: null },
            'a removed address stays removed',
        );
    });

    it('asks the registry in batches of 500 with the scoped key', async () => {
        const calls = [];
        const ids = Array.from({ length: 501 }, (_, i) => String(i + 1));
        const view = await fetchRegistryView({
            ids,
            apiBaseUrl: 'https://api.example',
            serviceKey: 'gh-notifier.secret',
            fetchImpl: async (url, options) => {
                const body = JSON.parse(options.body);
                calls.push({ url: url.toString(), auth: options.headers.Authorization, size: body.ids.length });
                return { ok: true, json: async () => ({ results: body.ids.map((id) => ({ id, feedUrl: `https://f.example/${id}` })) }) };
            },
        });
        assert.deepEqual(calls.map((c) => c.size), [500, 1]);
        assert.equal(calls[0].url, 'https://api.example/internal/notifier/shows');
        assert.equal(calls[0].auth, 'Svc gh-notifier.secret');
        assert.equal(view.get('501').feedUrl, 'https://f.example/501');
    });

    it('fails loudly when the registry refuses, and skips it without a key', async () => {
        await assert.rejects(
            fetchRegistryView({ ids: ['1'], apiBaseUrl: 'https://api.example', serviceKey: 'k', fetchImpl: async () => ({ ok: false, status: 503, body: null }) }),
            /503/,
        );
        const view = await fetchRegistryView({ ids: ['1'], apiBaseUrl: 'https://api.example', serviceKey: '', fetchImpl: async () => assert.fail('no key, no call') });
        assert.equal(view.size, 0);
    });
});
