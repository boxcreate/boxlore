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

async function fetchText(url, { timeoutMs = 15000, maxBytes = 5_000_000 } = {}) {
    const ac = new AbortController();
    const timer = setTimeout(() => ac.abort(), timeoutMs);
    try {
        const response = await fetch(url, {
            signal: ac.signal,
            redirect: 'follow',
            headers: {
                'User-Agent': 'BoxLore/1.0',
                'Accept': 'application/rss+xml, application/atom+xml, application/xml, text/xml, */*',
            },
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const buffer = await response.arrayBuffer();
        if (buffer.byteLength > maxBytes) {
            throw new Error(`feed too large (${buffer.byteLength} bytes)`);
        }
        return new TextDecoder('utf-8').decode(buffer);
    } finally {
        clearTimeout(timer);
    }
}

async function fetchPiLatest(podcastId) {
    const url = `https://api.podcastindex.org/api/1.0/episodes/byfeedid?id=${podcastId}&max=1`;
    const response = await fetch(url, { headers: generateAuthHeaders() });
    if (!response.ok) {
        throw new Error(`Podcast Index API returned status ${response.status}`);
    }
    const result = await response.json();
    const episodes = result.items || [];
    return episodes[0] || null;
}

async function fetchRssNewest(feedUrl) {
    const xml = await fetchText(feedUrl);
    return lib.newestRssItem(lib.parseFeedItems(xml));
}

async function sendFcm(podcastId, data) {
    const topic = `new_ep_${podcastId}`;
    const messageId = await admin.messaging().send({ topic, data });
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

    // 4. Poll each tracked podcast for new episodes
    for (const [podcastId, podcastData] of Object.entries(trackedPodcasts)) {
        if (!podcastData || typeof podcastData !== 'object') {
            continue;
        }
        const podcastTitle = podcastData.title || "Podcast";
        const imageUrl = podcastData.imageUrl || "";
        const existingState = state.podcasts[podcastId];

        try {
            const feedUrl = lib.usableFeedUrl(podcastData.feedUrl);
            let rssItem = null;
            if (feedUrl) {
                try {
                    rssItem = await fetchRssNewest(feedUrl);
                } catch (rssError) {
                    console.warn(
                        `RSS fetch failed for ${podcastTitle} (${podcastId}); falling back to Podcast Index:`,
                        rssError.message || rssError,
                    );
                }
            }

            const rssKey = lib.rssItemKey(rssItem);
            if (rssItem && rssKey) {
                let piEpisode = null;
                try {
                    piEpisode = await fetchPiLatest(podcastId);
                } catch (piError) {
                    console.warn(
                        `Podcast Index lookup failed after RSS for ${podcastTitle} (${podcastId}):`,
                        piError.message || piError,
                    );
                }
                const matched = lib.rssMatchesPi(rssItem, piEpisode);
                const decision = lib.applyCheck({
                    existing: existingState,
                    source: 'rss',
                    newest: {
                        key: rssKey,
                        title: rssItem.title || 'New Episode',
                        piEpisodeId: matched && piEpisode ? String(piEpisode.id) : undefined,
                    },
                });
                if (decision.notify) {
                    console.log(`[NEW EPISODE] "${rssItem.title || 'New Episode'}" detected for ${podcastTitle} (RSS)`);
                    try {
                        await sendFcm(podcastId, lib.buildRssFcmData({
                            podcastId,
                            podcastTitle,
                            imageUrl,
                            rssItem,
                            piEpisode: matched ? piEpisode : null,
                            feedUrl,
                        }));
                    } catch (fcmError) {
                        console.error(`Failed to send FCM notification for ${podcastTitle}:`, fcmError);
                    }
                } else {
                    console.log(`RSS ${decision.reason} for ${podcastTitle} (${podcastId})`);
                }
                if (decision.reason !== 'unchanged') {
                    state.podcasts[podcastId] = decision.nextState;
                    changeCount++;
                }
                continue;
            }

            const latestEp = await fetchPiLatest(podcastId);
            if (!latestEp) {
                console.log(`No episodes found in Podcast Index for ${podcastTitle} (${podcastId})`);
                continue;
            }

            const latestEpId = String(latestEp.id);
            const latestEpTitle = latestEp.title || "New Episode";
            const decision = lib.applyCheck({
                existing: existingState,
                source: 'pi',
                newest: {
                    piEpisodeId: latestEpId,
                    title: latestEpTitle,
                },
            });
            if (decision.notify) {
                console.log(`[NEW EPISODE] "${latestEpTitle}" detected for ${podcastTitle}`);
                try {
                    await sendFcm(podcastId, lib.buildPiFcmData({
                        podcastId,
                        podcastTitle,
                        imageUrl,
                        piEpisode: latestEp,
                    }));
                } catch (fcmError) {
                    console.error(`Failed to send FCM notification for ${podcastTitle}:`, fcmError);
                }
            }
            if (decision.reason !== 'unchanged') {
                state.podcasts[podcastId] = decision.nextState;
                changeCount++;
            }
        } catch (podcastError) {
            console.error(`Error checking podcast ${podcastTitle} (${podcastId}):`, podcastError);
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
