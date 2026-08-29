'use strict';

const admin = require('firebase-admin');
const {
    missingTrackedPodcasts,
    resolveTrackedPodcastFeed,
    usableHttpsFeedUrl,
} = require('./backfill-tracked-podcast-feeds-lib');

const DATABASE_URL = 'https://boxcasts-default-rtdb.asia-southeast1.firebasedatabase.app';
const LOOKUP_CONCURRENCY = 4;

async function mapWithConcurrency(items, limit, worker) {
    const results = new Array(items.length);
    let nextIndex = 0;

    async function runWorker() {
        while (nextIndex < items.length) {
            const index = nextIndex++;
            results[index] = await worker(items[index]);
        }
    }

    await Promise.all(
        Array.from(
            { length: Math.min(limit, items.length) },
            () => runWorker(),
        ),
    );
    return results;
}

async function main() {
    const apiBaseUrl = String(process.env.BOXLORE_API_BASE_URL || '').trim();
    const appKey = String(process.env.APP_SECRET_KEY || '').trim();
    if (!apiBaseUrl || !appKey) {
        throw new Error('BOXLORE_API_BASE_URL and APP_SECRET_KEY are required');
    }

    admin.initializeApp({
        credential: admin.credential.applicationDefault(),
        databaseURL: DATABASE_URL,
    });
    const trackedRef = admin.database().ref('tracked_podcasts');
    const snapshot = await trackedRef.once('value');
    const trackedPodcasts = snapshot.val() || {};
    const missing = missingTrackedPodcasts(trackedPodcasts);
    console.log(`Found ${missing.length} tracked podcasts without a valid HTTPS feedUrl.`);
    if (missing.length === 0) return;

    const resolutions =
        await mapWithConcurrency(
            missing,
            LOOKUP_CONCURRENCY,
            async (podcast) => ({
                ...podcast,
                feedUrl:
                    await resolveTrackedPodcastFeed({
                        ...podcast,
                        apiBaseUrl,
                        appKey,
                    }),
            }),
        );
    const resolved = resolutions.filter((result) => usableHttpsFeedUrl(result.feedUrl));
    const unresolved = resolutions.filter((result) => !usableHttpsFeedUrl(result.feedUrl));

    let updated = 0;
    let alreadyHealed = 0;
    for (const result of resolved) {
        const transaction =
            await trackedRef
                .child(result.id)
                .child('feedUrl')
                .transaction((current) => {
                    if (usableHttpsFeedUrl(current)) return;
                    return result.feedUrl;
                });
        if (transaction.committed) {
            updated++;
        } else {
            alreadyHealed++;
        }
    }

    console.log(
        `Feed URL repair complete: updated=${updated}, already_healed=${alreadyHealed}, unresolved=${unresolved.length}.`,
    );
    for (const result of unresolved) {
        console.warn(`No HTTPS feed found for ${result.id} (${result.title || 'untitled'}).`);
    }
    if (resolved.length === 0) {
        throw new Error('No missing tracked podcast feed URLs could be resolved');
    }
}

main()
    .catch((error) => {
        console.error('Tracked podcast feed URL repair failed:', error);
        process.exitCode = 1;
    })
    .finally(async () => {
        await Promise.all(admin.apps.map((app) => app.delete()));
    });
