# What we track and why

Most privacy pages are written by corporate lawyers to disguise what an app takes from you. 

This page is written by the people building **boxlore**, to tell you the unvarnished truth about what data leaves your phone, why we collect it, and the strict boundaries we never cross.

> **boxlore is not built for profit. It is a few friends finishing a childhood dream: watching code turn into magic on screens.**
>
> *Never sold · Zero ads · Anonymous usage only*

---

## The Three Ironclad Rules

1. **Zero Ads, Forever:** We do not have banner ads, sponsor tracking pixels, or cross-site advertising SDKs. boxlore will never have ads.
2. **Anonymous Usage & Zero Profile Linking:** Accounts in boxlore are completely optional and used purely for cloud backup and library sync across your devices. Your email is logged solely by Firebase (Google) for secure auth verification and is never stored on our application servers. Most importantly, **your account email is never connected, linked, or cross-referenced with your app usage or telemetry event profile**. Your telemetry activity remains strictly anonymous.
3. **Your Taste Stays on Your Phone:** Your learned recommendation model, skip habits, and playback affinities are calculated strictly on your device using a local database. They are never sent to our servers.

---

## Why We Track Anything at All

Building an Android media player that works across thousands of phone models, car stereos, and malformed RSS feeds is difficult. 

If an episode fails to stream, we need to know. If a button is confusing, we need to see where people get stuck. If nobody uses a feature, we want to remove the dead weight instead of letting the app become bloated.

And honestly, we track usage because we care. When we sit up at 2:00 AM fixing an audio service bug, watching the charts and seeing hundreds of real people listening to podcasts brings us genuine joy. It reminds us that the work matters.

---

## Exactly What Leaves Your Phone

We use PostHog for telemetry. Here are the five categories of data collected, and exactly how we use them:

### 1. App Navigation and Feature Use
* **What is sent:** Screen opens, button taps, carousel swipes, settings adjustments, and general time spent.
* **Why:** To see how many people actually use boxlore, which features feel alive, and which ones flop, so we can make better product calls.
* **Real example:** Earlier versions had a Radio feature. The data showed that almost nobody clicked on it, so we removed it completely instead of keeping bloat in the app.

### 2. Search Queries and Onboarding Text
* **What is sent:** The search terms you type and the results returned, along with text entered during AI onboarding.
* **Why:** Podcast Index and Apple directory search rely heavily on exact-word matching, which often produces terrible results for normal human queries. Seeing real queries helps us tune our typo tolerance and semantic topic search so the app actually finds what you want.
* **Real example:** Many people typed real show names into the onboarding assistant expecting it to act like a search bar. Because we saw this in the queries, we built a smart detection layer that recognizes show titles and offers a one-tap subscription button immediately.

### 3. Listening Signals and Playback Events
* **What is sent:** Podcast and episode identifiers, playback progress, completed plays, likes, and downloads.
* **Why:** Public charts are heavily manipulated and do not give reliable play-level signals. We use these events to understand aggregate listening trends and power our community charts.

### 4. Device and Operating System Details
* **What is sent:** App version, Android OS version, device manufacturer and model, local time zone hour, and an anonymous random analytics ID.
* **Why:** PostHog tracks basic device environment properties by default. This helps us spot platform-specific bugs (for example, if a background crash only happens on Samsung Android 14 devices).

### 5. Crash and Error Logs
* **What is sent:** Technical stack traces, error codes, and crash contexts.
* **Why:** If playback fails when reconnecting to Android Auto in your vehicle, or if a malformed podcast feed causes a crash, we need the stack trace so we can fix it.

---

## What Stays Strictly Local

* **Your Library Database & Sync Isolation:** Your downloaded audio files, custom tags, and complete library records live on your phone. If you choose to enable cloud sync, your backup data is stored solely for device synchronization and is never tied to or cross-referenced with telemetry events. (Individual actions like liking an episode or playing a show emit anonymous telemetry events as described above, but they are never connected to your personal identity or account email).
* **Your Personalization Brain:** boxlore runs an on-device Bayesian ranking model. The math that learns what you skip, finish, and like stays inside your phone's local database.
* **Your Backups:** When you export a full JSON backup of your library, it is generated locally and saved to your phone storage. We never hold a copy of your backup on our servers unless you explicitly share it.

---

## Full Transparency: The Event Glossary

We believe in complete, open-book transparency. Every single event name, property payload, and emission rule captured by our analytics is publicly documented:

* **[Analytics Event Glossary](ANALYTICS_EVENT_GLOSSARY.md)**: The full technical schema of every event emitted by the app.
* **[Companion Event CSV](analytics/event_glossary.csv)**: Machine-readable reference of our analytics inventory.
