# How to Fix Podcast Refresh & Missing Episodes in boxlore

If new episodes of your subscribed podcasts only appear when you manually open the individual show's page—rather than automatically showing up on your **Home** screen—your library may have been affected by a feed configuration issue from an older build.

This guide walks you through fixing it in just a couple of minutes.

---

> [!NOTE]
> **Check First (Avoid False Positives):**
> Under normal operation, **boxlore** checks and refreshes all your subscribed podcasts within **at most 30 seconds of opening the app**.
> 
> If you open the app and your new episodes show up within 30 seconds, **your library is healthy and you do NOT need to reset anything.**
> 
> Only follow this guide if new episodes consistently fail to appear on your Home screen even after waiting 30 seconds, and only show up when you tap into that show's specific page.

---

## Why Is This Happening?

In older versions of **boxlore** (`v0.0.13` and earlier), podcasts imported from another app using an **OPML file** were mistakenly saved as generic, standalone RSS feeds rather than being linked to our central podcast directory.

* **What went wrong:** Standalone feeds were not included in the app's automatic background refresh. The app only checked for new episodes when you explicitly opened that specific podcast's detail screen.
* **Why did the automatic update not fix it?** Recent versions included an automatic repair tool to fix this in the background, but because podcast feeds frequently change their web addresses or use redirects, some feeds could not be safely matched without risking corrupting your library.
* **Why can't I restore my JSON backup?** Restoring a backup created while in this broken state will restore the old, faulty feed links, bringing the problem right back.

---

## Step-by-Step Fix

To permanently restore automatic background refreshes, follow these 4 simple steps:

### Step 1: Make sure your app is up to date
Verify that you are running the latest version of **boxlore**:
- **`v0.0.24`** or newer if you installed via **GitHub releases** (APK).
- **`v0.0.18`** or newer if you installed via **Google Play Store**.

*(You can check your version in **boxlore** under **Settings → About**).*

---

### Step 2: Clear App Data (Reset the app)
To wipe the old, broken feed connections:
1. Open your phone's **Settings**.
2. Tap **Apps** (or **App Management**) → **boxlore**.
3. Tap **Storage & cache**.
4. Tap **Clear storage** (or **Clear data**) and confirm.

> [!WARNING]
> **DO NOT RESTORE A JSON BACKUP.**
> Restoring a JSON backup will bring back the faulty feed settings and break automatic updates again.
>
> We sincerely apologize for this inconvenience, as clearing data will reset your local listening history and progress. However, starting clean is necessary to permanently fix the automatic refresh pipeline.

---

### Step 3: Launch Fresh to the Welcome Screen
1. Open **boxlore**.
2. Ensure you see the fresh **Welcome / Onboarding screen**.
3. Proceed through initial setup.

---

### Step 4: Re-import Your OPML File
1. Import your original `.opml` or `.xml` file (either during onboarding or by going to **Settings → Library → Import OPML**).
2. Allow the import to complete.

On modern builds of **boxlore**, imported podcasts are automatically connected to the live directory and publisher feeds. Your Home screen will now update automatically with new episodes!

---

## Frequently Asked Questions (FAQ)

### Will this issue happen again?
**No.** Current builds completely overhaul how OPML files and podcast subscriptions are processed. All imported shows are now properly linked to live publisher feeds right from the start.

### What if I don't have my original OPML file?
If you still have your previous podcast app (like Pocket Casts, AntennaPod, Apple Podcasts, etc.), you can easily export a fresh OPML file from its settings menu and import it into **boxlore**.

### What about private or patron-only RSS feeds?
If you manually added a private RSS feed (e.g. Patreon), it will continue to work as a direct RSS subscription. You can refresh it anytime directly from its podcast page.

### Still having trouble?
If you followed these steps and are still experiencing issues with new episodes, please let us know by [opening an issue on GitHub](https://github.com/boxcreate/boxlore/issues).
