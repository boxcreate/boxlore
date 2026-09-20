# What we track and why

---

## Our Core Commitments

1. **Zero Ads, Forever:** We do not have banner ads, sponsor tracking pixels, or cross-site advertising SDKs. boxlore will never have ads.
2. **Anonymous Usage & Zero Profile Linking:** Accounts in boxlore are completely optional and used purely for cloud backup and library sync across your devices. Your email is logged solely by Firebase (Google) for secure auth verification and is never stored on our application servers. Most importantly, **your account email is never connected, linked, or cross-referenced with your app usage or telemetry event profile**. Your telemetry activity remains strictly anonymous.
3. **Discovery Without Ad Profiling:** We never sell your data, build advertising dossiers, or track you across the web. To help you discover new shows, the app sends bounded listening seeds (recent shows you played, liked, or completed) to our servers to run semantic vector search against our catalog. The server returns candidate shows statelessly—it does not build a persistent behavioral profile to monetize your attention.

### How Discovery & Recommendations Work (The Hybrid Architecture)

Unlike platforms that use centralized engagement algorithms to maximize screen time, boxlore uses a two-stage hybrid architecture designed purely for discovery:

* **Server-side semantic matching:** To find relevant candidates across a vast catalog, the app sends bounded listening seeds (recent shows you completed, liked, or engaged with) to our servers to run semantic vector search against our podcast index.
* **On-device taste tuning:** The candidate shows returned by the server are then re-ranked locally on your phone using an on-device learning model that adapts to your skip habits and time of day—without building a persistent cloud advertising profile.

---

## Why We Track Anything at All

Building an Android media player that works across thousands of phone models, car stereos, and malformed RSS feeds is difficult. 

If an episode fails to stream, we need to know. If a button is confusing, we need to see where people get stuck. If nobody uses a feature, we want to remove the dead weight instead of letting the app become bloated.

And honestly, we track usage because we care. When we sit up at 2:00 AM fixing an audio service bug, watching the charts and seeing hundreds of real people listening to podcasts brings us genuine joy. It reminds us that the work matters.

---

## Exactly What Leaves Your Phone

Here is the data collected by our application and API services, and exactly how we use it:

### 1. App Navigation and Feature Use
* **What is sent:** Screen opens, button taps, carousel swipes, settings adjustments, and general time spent.
* **Why:** To see how many people actually use boxlore, which features feel alive, and which ones flop, so we can make better product calls.
* **Real example:** Earlier versions had a Radio feature. The data showed that almost nobody clicked on it, so we removed it completely instead of keeping bloat in the app.

### 2. Search Queries and Onboarding Text
* **What is sent:** The search terms you type and the results returned, along with text entered during AI onboarding.
* **Why:** Podcast Index and Apple directory search rely heavily on exact-word matching, which often produces terrible results for normal human queries. Seeing real queries helps us tune our typo tolerance and semantic topic search so the app actually finds what you want.
* **Real example:** Many people typed real show names into the onboarding assistant expecting it to act like a search bar. Because we saw this in the queries, we built a smart detection layer that recognizes show titles and offers a one-tap subscription button immediately.

### 3. Recommendation Discovery Seeds
* **What is sent:** When refreshing recommendations, the app sends bounded listening seeds (up to 12 recent episode or podcast titles you completed, liked, or engaged with), a list of already-played episode IDs (to prevent recommending episodes you have already heard), and your subscribed show IDs.
* **Why:** To run server-side semantic vector search against our podcast catalog embeddings and return relevant candidate episodes and shows.
* **What is not sent:** Your detailed skip counters, local ranking weights, or private library folders. The server processes this request statelessly to retrieve candidates, without building an advertising profile or saving a personal dossier.

### 4. Listening Signals and Playback Events
* **What is sent:** Podcast and episode identifiers, playback progress, completed plays, likes, and downloads.
* **Why:** Public charts are heavily manipulated and do not give reliable play-level signals. We use these events to understand aggregate listening trends and power our community charts.

### 5. Device and Operating System Details
* **What is sent:** App version, Android OS version, device manufacturer and model, local time zone hour, and an anonymous random analytics ID.
* **Why:** Telemetry tracks basic device environment properties by default. This helps us spot platform-specific bugs (for example, if a background crash only happens on Samsung Android 14 devices).

### 6. Crash and Error Logs
* **What is sent:** Technical stack traces, error codes, and crash contexts.
* **Why:** If playback fails when reconnecting to Android Auto in your vehicle, or if a malformed podcast feed causes a crash, we need the stack trace so we can fix it.

---

## What Stays Strictly Local

* **Your Library Database & Sync Isolation:** Your downloaded audio files, custom tags, and complete library records live on your phone. If you choose to enable cloud sync, your backup data is stored solely for device synchronization and is never tied to or cross-referenced with telemetry events. (Individual actions like liking an episode or playing a show emit anonymous telemetry events as described above, but they are never connected to your personal identity or account email).
* **Your Local Learning Brain:** The local ranking weights, skip counters, and playback habit adjustments that tune the order of your feeds live in your phone's local SQLite database.
* **Your Backups:** When you export a full JSON backup of your library, it is generated locally and saved to your phone storage. We never hold a copy of your backup on our servers unless you explicitly share it.

---

## Full Transparency: The Event Glossary

We believe in complete, open-book transparency. Every single event name, property payload, and emission rule captured by our analytics is publicly documented:

* **[Analytics Event Glossary](ANALYTICS_EVENT_GLOSSARY.md)**: The full technical schema of every event emitted by the app.
* **[Companion Event CSV](analytics/event_glossary.csv)**: Machine-readable reference of our analytics inventory.
