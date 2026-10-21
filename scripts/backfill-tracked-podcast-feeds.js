'use strict';

const admin = require('firebase-admin');
const path = require('node:path');
const {
    compareAndSetFeedUrl,
    feedHost,
    fetchRegistryView,
    planTrackedFeedRepairs,
    resolveTrackedPodcastFeed,
    usableHttpsFeedUrl,
    writeWeeklyTrackedPodcastBackup,
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
    const mode = process.argv[2] || '--backup-and-repair';
    if (!['--backup-only', '--repair-only', '--backup-and-repair'].includes(mode)) {
        throw new Error(`Unsupported mode: ${mode}`);
    }
    const apiBaseUrl = String(process.env.BOXLORE_API_BASE_URL || '').trim();
    const serviceKey = String(process.env.BOXLORE_SVC_KEY || '').trim();
    // Registry view (`notifier:read`): confirmed working addresses for shows whose old one is dead.
    const registryKey = String(process.env.BOXLORE_SVC_NOTIFIER_KEY || '').trim();
    const appKey = String(process.env.APP_SECRET_KEY || '').trim();
    if (mode !== '--backup-only' && (!apiBaseUrl || (!serviceKey && !appKey))) {
        throw new Error('BOXLORE_API_BASE_URL and BOXLORE_SVC_KEY (or the legacy APP_SECRET_KEY) are required');
    }

    admin.initializeApp({
        credential: admin.credential.applicationDefault(),
        databaseURL: DATABASE_URL,
    });
    const trackedRef = admin.database().ref('tracked_podcasts');
    const snapshot = await trackedRef.once('value');
    const trackedPodcasts = snapshot.val() || {};
    if (mode !== '--repair-only') {
        const backup =
            writeWeeklyTrackedPodcastBackup({
                directory: path.join(__dirname, 'data', 'tracked-podcasts-backups'),
                trackedPodcasts,
            });
        console.log(
            `Saved pre-repair RTDB backup ${backup.filename}; pruned ${backup.removed.length} expired backup(s).`,
        );
    }
    if (mode === '--backup-only') return;

    const catalogIds = Object.keys(trackedPodcasts).filter((id) => /^[1-9][0-9]{0,15}$/.test(id));
    let registry = new Map();
    try {
        registry = await fetchRegistryView({ ids: catalogIds, apiBaseUrl, serviceKey: registryKey });
        console.log(`Registry view: ${registry.size} of ${catalogIds.length} tracked shows.`);
    } catch (error) {
        // Without the registry, only missing addresses are filled (the older behavior).
        console.warn(`Registry view unavailable (${error.message}); filling missing addresses only.`);
    }
    const plan = planTrackedFeedRepairs(trackedPodcasts, registry);
    const fills = plan.filter((row) => row.reason === 'fill');
    const replacements = plan.filter((row) => row.reason === 'replace');
    console.log(`Found ${fills.length} tracked podcasts without a valid HTTPS feedUrl and ${replacements.length} with a dead address the registry has replaced.`);
    if (plan.length === 0) return;

    const resolutions =
        await mapWithConcurrency(
            plan,
            LOOKUP_CONCURRENCY,
            async (row) => (row.to ? row : {
                ...row,
                to:
                    await resolveTrackedPodcastFeed({
                        ...row,
                        apiBaseUrl,
                        appKey,
                        serviceKey,
                    }),
            }),
        );
    const resolved = resolutions.filter((row) => usableHttpsFeedUrl(row.to));
    const unresolved = resolutions.filter((row) => !usableHttpsFeedUrl(row.to));

    let updated = 0;
    let replaced = 0;
    let changedSinceRead = 0;
    for (const row of resolved) {
        // Only while the saved address is still the one read above: a newer app write always wins.
        const transaction =
            await trackedRef
                .child(row.id)
                .child('feedUrl')
                .transaction(compareAndSetFeedUrl(row.read, row.to));
        if (!transaction.committed || transaction.snapshot.val() !== row.to) {
            changedSinceRead++;
        } else if (row.reason === 'replace') {
            replaced++;
            console.log(`Replaced ${row.id} (${row.title || 'untitled'}): ${feedHost(row.read)} -> ${feedHost(row.to)}.`);
        } else {
            updated++;
        }
    }

    console.log(
        `Feed URL repair complete: updated=${updated}, replaced=${replaced}, changed_since_read=${changedSinceRead}, unresolved=${unresolved.length}.`,
    );
    for (const row of unresolved) {
        console.warn(`No HTTPS feed found for ${row.id} (${row.title || 'untitled'}).`);
    }
    if (fills.length > 0 && !resolved.some((row) => row.reason === 'fill')) {
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
