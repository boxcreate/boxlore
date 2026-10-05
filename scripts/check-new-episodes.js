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

async function sendFcm(podcastId, data) {
    const topic = lib.notificationTopic(podcastId);
    const messageId = await admin.messaging().send(lib.newEpisodeFcmMessage(topic, data));
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
    const statePath = path.join(__dirname, 'data/episode-tracker.json');
    
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
    const activePodcasts = lib.groupTrackedPodcasts(trackedPodcasts);
    state.podcasts = lib.activeEpisodeState(state.podcasts, activePodcasts);

    // 4. Poll each tracked podcast for new episodes
    for (const [podcastId, podcastData] of Object.entries(activePodcasts)) {
        if (!podcastData || typeof podcastData !== 'object') {
            continue;
        }
        const podcastTitle = podcastData.title || "Podcast";
        const existingState = state.podcasts[podcastId];
        try {
            const release = await lib.resolveTrackedRelease({
                podcastId, podcastData, existing: existingState, fetchRssNewest, fetchPiLatest,
            });
            if (!release) continue;
            const { decision, data } = release;
            if (decision.notify) await sendFcm(podcastId, data);
            if (decision.reason !== 'unchanged') {
                state.podcasts[podcastId] = decision.nextState;
                changeCount++;
            }
            console.log(`Checked ${podcastId}: ${decision.reason}`);
        } catch (podcastError) {
            // Never print private feed URLs or request errors for pure RSS registrations.
            if (String(podcastId).startsWith('rss:')) console.warn(`RSS check failed for ${podcastId}; retaining baseline for retry`);
            else console.error(`Error checking podcast ${podcastTitle} (${podcastId}):`, podcastError);
        }
    }

    // 5. Write updated state file back
    try {
        fs.writeFileSync(statePath, JSON.stringify(state, null, 2), 'utf8');
        console.log(`State file updated successfully. ${changeCount} changes recorded.`);
    } catch (writeError) {
        console.error("Failed to write state file:", writeError);
    }

    process.exit(0);
}

run();
