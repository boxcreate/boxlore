# What we track and why

---

## Our Core Commitments

1. **No App Ads, Forever:** We do not have banner ads, sponsor tracking pixels, or cross-site advertising SDKs. boxlore will never show ads in the app. Podcasts may still carry ads placed by their creators.
2. **Accounts Stay Separate From Usage Data:** Accounts in boxlore are completely optional and used purely for cloud backup and library sync across your devices. Your email is logged solely by Firebase (Google) for secure auth verification and is never stored on our application servers. Most importantly, **your account email is never connected, linked, or cross-referenced with your app usage or telemetry event profile**. Usage events carry a random analytics ID, not your name or email.
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
* **What is sent:** App version, Android OS version, device manufacturer and model, local time zone hour and day of the week, an anonymous random analytics ID, and approximate location (country, region, city and postcode area) that PostHog works out from your IP address. The IP address itself is discarded and not stored. Events recorded before 16 October 2026 still include it.
* **Why:** Telemetry tracks basic device environment properties by default. This helps us spot platform-specific bugs (for example, if a background crash only happens on Samsung Android 14 devices). We use rough location to see which countries boxlore is used in, for example to check that support prices make sense in each country once payments are on.
* **What is not sent:** boxlore never asks for or reads your phone's GPS location.

### 6. Crash and Error Logs
* **What is sent:** Technical stack traces, error codes, and crash contexts.
* **Why:** If playback fails when reconnecting to Android Auto in your vehicle, or if a malformed podcast feed causes a crash, we need the stack trace so we can fix it.

### 7. New-Episode Alerts
* **What is sent:** When you turn on new-episode alerts for a show, the app adds that show (its title, artwork link and, for shows added by RSS link, its feed URL) to a shared list our episode checker watches, and subscribes your phone to that show's notification topic through Firebase Cloud Messaging.
* **Why:** So boxlore can tell you about new episodes even when the app is closed.
* **Good to know:** The shared list isn't tied to your account, but backups of it are published in this repository. Keep alerts off for private or paid feeds whose links you don't want public.

### 8. Support Page Design Test
* **What is sent:** Which of six Support boxlore designs you were shown (the original power-cell page or one of five new ones), which support levels you looked at, whether you tapped pay and at which price, and how long you stayed on the page.
* **Why:** To see which design people respond to best. Each person keeps seeing the same design, so the comparison stays fair.
* **Good to know:** This is part design survey, part our attempt to earn back what it costs to keep boxlore running. Rest assured, it's tied only to the anonymous analytics ID described above, never to your name, email or account. We just want a rough idea of which designs lead more people to support boxlore.

---

## What Stays Strictly Local

* **Your Library Database & Sync Isolation:** Your downloaded audio files, custom tags, and complete library records live on your phone. If you choose to enable cloud sync, your backup data is stored solely for device synchronization and is never tied to or cross-referenced with telemetry events. (Individual actions like liking an episode or playing a show emit anonymous telemetry events as described above, but they are never connected to your personal identity or account email).
* **Your Local Learning Brain:** The local ranking weights, skip counters, and playback habit adjustments that tune the order of your feeds live in your phone's local SQLite database.
* **Your Backups:** A full JSON backup you export in Settings is created on your phone and saved to your phone storage; we don't receive a copy. If you turn on cloud sync, your library is stored on our servers so it can sync across your devices, as described above.

---

## Full Transparency: The Event Glossary

We believe in complete, open-book transparency. Every single event name, property payload, and emission rule captured by our analytics is publicly documented:

* **[Analytics Event Glossary](ANALYTICS_EVENT_GLOSSARY.md)**: The full technical schema of every event emitted by the app.
* **[Companion Event CSV](analytics/event_glossary.csv)**: Machine-readable reference of our analytics inventory.
