# How to Fix Podcast Refresh for OPML-Imported Libraries in boxlore

> [!IMPORTANT]
> **Who is this guide for?**
> This guide is specifically for listeners who **imported their podcasts from another app using an OPML file** during older versions of **boxlore** (`v0.0.13` or earlier) and are experiencing missing new episodes on their Home screen.
>
> **Who is this NOT for?**
> If you subscribed to podcasts directly inside **boxlore** (by searching for them), or if your shows normally refresh new episodes within **30 seconds of opening the app**, **your library is healthy and you do not need to do anything!**

---

## The Issue: What Happened with Older OPML Imports?

In older versions of **boxlore** (`v0.0.13` and earlier), podcast subscriptions imported via **OPML files** were saved as standalone RSS feeds instead of being connected to our central podcast catalog.

Because of this:
* **Background sync was skipped:** The app's automated background refresh only synced catalog-connected shows. It did not automatically query new episodes for those standalone OPML feeds.
* **Episodes only showed up manually:** New episodes would only be checked and fetched when you explicitly navigated to that podcast's individual show page.
* **Why automated repair didn't catch every show:** We shipped an automatic repair tool in newer releases to match and fix these feeds, but because many publisher feeds use web redirects or changed URLs over time, some feeds could not be safely matched without risking library errors.
* **Why you cannot restore a JSON backup:** Older JSON backups retain the broken standalone feed format. Restoring an old backup re-imports the problem.

---

## 4-Step Resolution for Affected OPML Users

Follow these steps to permanently reconnect your OPML library to our live catalog and restore automatic background episode syncing.

### Step 1: Update boxlore
Ensure your app is running on the latest build:
* **`v0.0.24`** or newer if you use the **GitHub release** (APK).
* **`v0.0.18`** or newer if you installed via **Google Play Store**.

*(Check your installed version inside **boxlore** under **Settings → About**).*

---

### Step 2: Clear App Storage
To wipe out the legacy standalone feed entries:
1. Open your device's **Android Settings**.
2. Go to **Apps** → **boxlore**.
3. Tap **Storage & cache**.
4. Tap **Clear storage** (or **Clear data**) and confirm.

> [!WARNING]
> **DO NOT RESTORE A JSON BACKUP.**
> Restoring a JSON backup exported from older builds will bring back the broken feed state.
> 
> *We sincerely apologize for this inconvenience, as clearing data will reset your local listening history and episode progress. However, starting clean is required to permanently reconnect your shows to the automatic refresh engine.*

---

### Step 3: Confirm Fresh Launch
1. Open **boxlore**.
2. Verify that the app opens directly to the fresh **Welcome / Onboarding screen**.

---

### Step 4: Re-import Your OPML File
1. Re-import your `.opml` or `.xml` file during onboarding, or go to **Settings → Library → Import OPML**.
2. Wait a few moments for the import to finish.

On modern builds, **boxlore** intelligently matches every OPML feed against our catalog and live publisher feeds right from the start. All your shows will now refresh new episodes automatically in the background and on your Home screen!

---

## Frequently Asked Questions (FAQ)

### How can I tell if my shows are already refreshing normally?
Under normal conditions, **boxlore** checks and refreshes all your subscribed podcasts within **at most 30 seconds of opening the app**. If you open the app and your new episodes appear on Home within 30 seconds, your library is working properly.

### What if I don't have my original OPML file anymore?
If you still have your previous podcast app (such as Pocket Casts, AntennaPod, Apple Podcasts, etc.), you can export a fresh OPML file anytime from its settings and import it into **boxlore**.

### Will this ever happen again?
**No.** Current builds completely resolved OPML processing. Newly imported OPML shows are linked to live publisher feeds immediately.

### What about private or patron-only feeds?
If you subscribe to private RSS feeds (like Patreon) that are not in the public directory, they will continue to function as direct RSS feeds. You can refresh them anytime on their podcast page.

### Still having trouble?
If you completed these steps and are still experiencing refresh issues, please let us know by [opening an issue on GitHub](https://github.com/boxcreate/boxlore/issues).
