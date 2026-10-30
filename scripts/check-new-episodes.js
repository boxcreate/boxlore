const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const lib = require('./check-new-episodes-lib');

// 1. Initialize Firebase Admin SDK using application default credentials (GCP_SA_KEY)
admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    databaseURL: "https://boxcasts-default-rtdb.asia-southeast1.firebasedatabase.app"
});

const db = admin.database();

// Podcast Index Configuration
const apiKey = process.env.PODCAST_INDEX_API_KEY;
const apiSecret = process.env.PODCAST_INDEX_API_SECRET;

if (!apiKey || !apiSecret) {
    console.error("Error: PODCAST_INDEX_API_KEY and PODCAST_INDEX_API_SECRET must be set.");
    process.exit(1);
}

// Generate authentication headers for Podcast Index API
function generateAuthHeaders() {
    const authDate = Math.floor(Date.now() / 1000);
    const data = apiKey + apiSecret + authDate;
    const authHeader = crypto.createHash('sha1').update(data).digest('hex');

    return {
        "X-Auth-Key": apiKey,
        "X-Auth-Date": authDate.toString(),
        "Authorization": authHeader,
        "User-Agent": "BoxLore/1.0"
    };
}

async function fetchPiLatest(podcastId) {
    const encoded = encodeURIComponent(String(podcastId));
    const url = `https://api.podcastindex.org/api/1.0/episodes/byfeedid?id=${encoded}&max=1`;
    const ac = new AbortController();
    const timer = setTimeout(() => ac.abort(), 15000);
    try {
        const response = await fetch(url, {
            headers: generateAuthHeaders(),
            signal: ac.signal,
        });
        if (!response.ok) {
            throw new Error(`Podcast Index API returned status ${response.status}`);
        }
        const result = await response.json();
        const episodes = result.items || [];
        return episodes[0] || null;
    } finally {
        clearTimeout(timer);
    }
}

async function fetchRssNewest(feedUrl) {
    const xml = await lib.fetchRssText(feedUrl);
    return lib.newestRssItem(lib.parseFeedItems(xml));
}

// Our API (own-episode-IDs plan P2): working feed addresses and served episode IDs. Optional: without
// the key, or when the API fails, the job runs as before and pushes open the show instead of an episode.
const API_BASE = (process.env.BOXLORE_API_BASE || 'https://api.aswin.cx').replace(/\/+$/, '');
const NOTIFIER_KEY = process.env.BOXLORE_SVC_NOTIFIER_KEY || '';
// Rehearsal: decide and log everything, send nothing, save nothing.
const DRY_RUN = process.env.NOTIFIER_DRY_RUN === '1';

async function notifierApi(route, body) {
    const ac = new AbortController();
    const timer = setTimeout(() => ac.abort(), 15000);
    try {
        const res = await fetch(`${API_BASE}/internal/notifier/${route}`, {
            method: 'POST',
            headers: { Authorization: `Svc ${NOTIFIER_KEY}`, 'Content-Type': 'application/json', 'User-Agent': 'BoxLore-Notifier/1.0' },
            body: JSON.stringify(body),
            signal: ac.signal,
        });
        if (!res.ok) throw new Error(`notifier API ${route} returned ${res.status}`);
        return await res.json();
    } finally {
        clearTimeout(timer);
    }
}

/** Working addresses for numeric show IDs: Map id -> { feedUrl, feedSource }. Empty when unavailable. */
async function fetchRegistry(ids) {
    const out = new Map();
    if (!NOTIFIER_KEY || !ids.length) return out;
    try {
        for (let i = 0; i < ids.length; i += 500) {
            const body = await notifierApi('shows', { ids: ids.slice(i, i + 500).map(Number) });
            for (const r of body.results || []) out.set(String(r.id), r);
        }
        const repaired = [...out.values()].filter((r) => String(r.feedSource || '').startsWith('registry:')).length;
        console.log(`Registry: ${out.size} shows, ${repaired} with a confirmed working address.`);
    } catch (e) {
        console.warn(`Registry unavailable (${e.message}); using registered addresses.`);
        out.clear();
    }
    return out;
}

/** The served ID for this release, or null (then the push opens the show). */
async function servedEpisodeId(podcastId, identity) {
    if (!NOTIFIER_KEY || !identity || String(podcastId).startsWith('rss:')) return null;
    if (identity.piId == null && !identity.guid && !identity.enclosureUrl) return null;
    try {
        const body = await notifierApi('episodes', { items: [{ sid: Number(podcastId), piId: identity.piId ?? undefined, guid: identity.guid || undefined, enclosureUrl: identity.enclosureUrl || undefined }] });
        return body.results?.[0]?.servedId ?? null;
    } catch (e) {
        console.warn(`Served ID lookup failed for ${podcastId} (${e.message}); the push opens the show.`);
        return null;
    }
}

async function sendFcm(podcastId, data) {
    const topic = lib.notificationTopic(podcastId, data.feedUrl);
    const message = lib.newEpisodeFcmMessage(topic, data);
    if (DRY_RUN) {
        // Never log feed addresses: private RSS feeds can carry credentials, and run logs can be public.
        const safe = String(podcastId).startsWith('rss:') ? 'rss show' : `show ${podcastId}`;
        console.log(`[dry run] would send: ${safe}, episode ${data.episodeId || '-'}, route ${data.route}, address ${data.feedUrl ? 'yes' : 'no'}`);
        return;
    }
    const messageId = await admin.messaging().send(message);
    console.log(`Sent notification ${messageId} to topic: ${topic}`);
}

async function run() {
    console.log("Checking for new podcast episodes...");

    // 2. Read tracked podcasts list from Firebase Realtime Database
    let trackedPodcasts = {};
    try {
        const snapshot = await db.ref('tracked_podcasts').once('value');
        trackedPodcasts = snapshot.val() || {};
        console.log(`Retrieved ${Object.keys(trackedPodcasts).length} tracked podcasts from Realtime Database.`);
    } catch (e) {
        console.error("Failed to read tracked podcasts from RTDB:", e);
        process.exit(1);
    }

    // 3. Read state file (local json)
    // On the VPS the state lives outside the code checkout (NOTIFIER_STATE_FILE); the repo copy is
    // the last state from when the job ran on GitHub Actions.
    const statePath = process.env.NOTIFIER_STATE_FILE || path.join(__dirname, 'data/episode-tracker.json');
    
    // Ensure the data directory exists
    const dataDir = path.dirname(statePath);
    if (!fs.existsSync(dataDir)) {
        fs.mkdirSync(dataDir, { recursive: true });
    }

    let state = { lastRun: "", podcasts: {} };
    if (fs.existsSync(statePath)) {
        try {
            state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
        } catch (e) {
            console.warn("Failed to parse state file, initializing fresh state:", e);
        }
    }
    if (!state.podcasts) {
        state.podcasts = {};
    }

    state.lastRun = new Date().toISOString();
    let changeCount = 0;
    let sendFailures = 0;
    const activePodcasts = lib.groupTrackedPodcasts(trackedPodcasts);
    state.podcasts = lib.activeEpisodeState(state.podcasts, activePodcasts);
    const registry = await fetchRegistry(Object.keys(activePodcasts).filter((id) => /^[1-9][0-9]{0,15}$/.test(id)));

    // 4. Poll each tracked podcast for new episodes. Each show's state is written as soon as it is
    // decided, so a run that stops early (or is killed) never resends what it already sent. A show
    // that hangs is skipped after a few minutes, and no new show starts past the run budget, so the
    // run always ends well inside the service's 45-minute limit.
    const runStartedAt = Date.now();
    const runBudgetMs = (Number(process.env.NOTIFIER_RUN_BUDGET_MIN) || 40) * 60 * 1000;
    const showLimitMs = (Number(process.env.NOTIFIER_SHOW_LIMIT_SEC) || 180) * 1000;
    const saveState = () => {
        if (!DRY_RUN) lib.writeStateFile(statePath, state);
    };
    const label = (id) => (String(id).startsWith('rss:') ? 'an rss show' : id);
    try {
        const stateIds = Object.keys(activePodcasts);
        const outcome = await lib.checkEachShow(stateIds, {
            showLimitMs,
            deadlineAt: runStartedAt + runBudgetMs,
            onTimeout: (stateId) => console.warn(`Checking ${label(lib.canonicalPodcastId(stateId))} took too long; skipped, checked again next run.`),
            check: async (stateId) => {
                const podcastData = activePodcasts[stateId];
                const podcastId = lib.canonicalPodcastId(stateId);
                if (!podcastData || typeof podcastData !== 'object') {
                    return;
                }
                const podcastTitle = podcastData.title || "Podcast";
                const existingState = state.podcasts[stateId];
                let release = null;
                try {
                    release = await lib.resolveTrackedRelease({
                        podcastId, podcastData, existing: existingState, fetchRssNewest, fetchPiLatest,
                        registryEntry: registry.get(String(podcastId)) || null,
                    });
                    if (!release) return;
                    const { decision } = release;
                    if (decision.notify) {
                        const servedId = await servedEpisodeId(podcastId, release.identity);
                        await sendFcm(podcastId, lib.applyServedId(release.data, podcastId, servedId));
                    }
                    // Save every change, including "unchanged" decisions that moved a key forward.
                    if (lib.stateChanged(existingState, decision.nextState)) {
                        state.podcasts[stateId] = decision.nextState;
                        changeCount++;
                        saveState();
                    }
                    console.log(`Checked ${podcastId}: ${decision.reason}`);
                } catch (podcastError) {
                    if (podcastError && /refusing to send/.test(podcastError.message)) {
                        // A broadcast topic would be a bug that reaches every install: stop the run.
                        if (lib.BROADCAST_TOPICS.has(lib.notificationTopic(podcastId, release?.data?.feedUrl))) throw podcastError;
                        sendFailures++;
                        console.error(`Refused the topic for ${String(podcastId).startsWith('rss:') ? 'an rss show' : podcastId}; skipping it.`);
                        return;
                    }
                    if (podcastError && podcastError.code && String(podcastError.code).startsWith('messaging/')) sendFailures++;
                    // Never print private feed URLs or request errors for pure RSS registrations.
                    if (String(podcastId).startsWith('rss:')) console.warn(`RSS check failed for ${podcastId}; retaining baseline for retry`);
                    else console.error(`Error checking podcast ${podcastTitle} (${podcastId}):`, podcastError);
                }
            },
        });
        if (outcome.stoppedEarly) {
            console.warn(`Run budget reached after ${outcome.started} of ${stateIds.length} shows; the rest are checked next run.`);
        }
    } finally {
        // 5. Write updated state file back (never in a dry run).
        if (DRY_RUN) {
            console.log(`[dry run] ${changeCount} state changes not saved.`);
        } else {
            try {
                lib.writeStateFile(statePath, state);
                console.log(`State file updated successfully. ${changeCount} changes recorded.`);
            } catch (writeError) {
                console.error("Failed to write state file:", writeError);
                process.exitCode = 1;
            }
        }
    }

    // A failed push is retried next run (its state wasn't saved); the run shows as failed so it's noticed.
    if (sendFailures) {
        console.error(`${sendFailures} notification(s) failed to send; they will be retried.`);
        process.exit(1);
    }
    process.exit(process.exitCode || 0);
}

run();
