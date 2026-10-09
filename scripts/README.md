# Scripts

Development guidance for catalog synchronization, notification checks, feed repair, and CI helpers. Read this file and the current [`sync/lib/config.js`](sync/lib/config.js) before changing catalog sync.

## Catalog sync and deployment

Catalog sync refreshes charts, imports podcasts, syncs episodes, and vectorizes content. It runs outside GitHub Actions; the former `sync-pi-data` workflow has been removed. Adding a replacement GitHub sync workflow requires explicit maintainer approval.

A Git push does not deploy this pipeline. Changes to `scripts/sync/` or `scripts/package.json` require a separate deployment and verification that the active runner uses the updated code and configuration. Read the production runbook in root `AGENTS.internal.md` before deployment. If it is unavailable, obtain it from the maintainer before performing production operations.

## Catalog components

| Path | Role |
| :--- | :--- |
| [`scripts/sync/lib/config.js`](sync/lib/config.js) | Countries, tiers, check cadence, embedding provider, and budgets; the country list is defined here |
| [`scripts/sync/01-refresh-charts.js`](sync/01-refresh-charts.js) … [`07-record-stats.js`](sync/07-record-stats.js) | Staged pipeline: charts → import → episodes → cleanup before vectors when `CLEANUP=1` → stats |
| [`scripts/sync/lib/tip-queue.js`](sync/lib/tip-queue.js) | Turso `ep_vec_tip_queue` — durable tip lane (IDs); stage 3 upsert, stage 4 delete on complete |
| [`scripts/sync/lib/vectorize-lanes.js`](sync/lib/vectorize-lanes.js) | Tip-first drain order + partial-complete flag rules for stage 4 |
| [`scripts/sync/lib/pi-handoff.js`](sync/lib/pi-handoff.js) | Same-run episode payloads stage 3→4 (not cross-run durable) |
| [`scripts/sync/lib/turso.js`](sync/lib/turso.js) + [`turso-page.js`](sync/lib/turso-page.js) | HTTP Turso client; **always page** large SELECTs (`RESPONSE_TOO_LARGE`) |
| [`scripts/sync/lib/staleness.js`](sync/lib/staleness.js) | Core vs relaxed episode-check windows |
| [`scripts/sync/lib/episode-caps.js`](sync/lib/episode-caps.js) | Per-storefront episode vector caps |
| [`scripts/sync/lib/embedder.js`](sync/lib/embedder.js) | Embedding providers: `bge` (default) and `qwen` |
| [`scripts/sync/lib/podcast-index.js`](sync/lib/podcast-index.js) | PI API client — Retry-After / global cooldown failsafes |
| [`scripts/sync/lib/text.js`](sync/lib/text.js) | Description cleaning + embed text (**uncapped**; `PAYLOAD_DESCRIPTION_MAX` null) |
| [`scripts/sync/lib/scalars.js`](sync/lib/scalars.js) | Scrub non-scalars before Qdrant/Turso writes |
| [`scripts/package.json`](package.json) | Sync Node deps + `npm run test:sync` |

## Changing catalog sync

- Extend hermetic tests under `sync/lib/*.test.js` and run `npm run test:sync` from `scripts/`.
- Keep large Turso reads on `fetchAllPaged` or country/category pages; avoid unbounded wide SELECTs.
- Preserve the chart index: `charts.itunes_id` is TEXT, so casting that column to INTEGER disables the index and inflates `rows_read`. Use `c.itunes_id = CAST(p.itunes_id AS TEXT)`, [`sync/lib/chart-countries.js`](sync/lib/chart-countries.js) (`loadCountriesByItunesId` plus a JavaScript filter), or page podcasts by `id` and normalize IDs in JavaScript. This applies to stages 2, 3, 4, 5, and remediation.
- Keep credentials and environment files out of Git, including `.env`, Podcast Index keys, Turso tokens, and Telegram secrets.

Use Node.js 20 to match the script workflows. From the repository root:

```bash
npm ci --prefix scripts
npm run test:sync --prefix scripts
npm run test:check-new-episodes --prefix scripts
node --test scripts/backfill-tracked-podcast-feeds-lib.test.js
```

These commands test local logic; running a production script requires the internal runbook and deployment authorization. The catalog suite is a local check and is not scheduled by GitHub Actions.

## Other scripts

CI configuration helpers, maintenance tools, and data files also live here. Check the owning workflow or runbook before using a tool; its presence in this directory does not mean it runs automatically.

### Check New Episodes

[`.github/workflows/new-episode-check.yml`](../.github/workflows/new-episode-check.yml) runs approximately every 30 minutes on GitHub Actions. It polls notification registrations independently of catalog sync.

| What | Where |
| :--- | :--- |
| Script | [`scripts/check-new-episodes.js`](check-new-episodes.js) + [`check-new-episodes-lib.js`](check-new-episodes-lib.js) |
| Who to poll | Firebase RTDB `tracked_podcasts/{podcastIndexId}` (client writes this when **show notifications** are on). Rules allow `title`, `imageUrl`, and optional HTTPS `feedUrl` only — extra children are rejected. |
| Last-notified state | [`scripts/data/episode-tracker.json`](data/episode-tracker.json) (the Action commits this) |
| Tests | `npm ci` then `npm run test:check-new-episodes` from `scripts/` (the Check New Episodes workflow runs the same script after `npm ci`) |

Release selection and recovery:

- A tracked HTTPS `feedUrl` selects RSS/Atom and compares `lastRssKey` (GUID, otherwise enclosure), independently of the phone's Missing episodes? cache setting. Other catalog rows use Podcast Index `episodes/byfeedid?max=1` and `lastEpisodeId`.
- The first check without saved state quietly seeds a baseline. When an existing catalog row has `lastEpisodeId` but no `lastRssKey`, its first RSS check can notify if the newest feed item matches a PI episode with a different ID. Catalog RSS failures fall back to Podcast Index without clearing `lastRssKey`.
- Read the complete body within the **25 MB** cap shared with Android `RssFeedClient`; oldest-first feeds may put the newest release at the end. Interrupted, oversized, or failed reads retain the last-good baseline. Parsing accepts playable enclosures regardless of attribute order and ignores non-media or untitled entries.
- The Action never creates negative episode IDs. Unmatched feed-only releases omit `episodeId` and open the podcast page. The phone persists the raw GUID/enclosure hint before hydration and resolves the release in its publisher-feed Room catalog.
- Visible alerts request high Android FCM priority. Foreground discovery recovers missed episodes by default. Background auto-download discovery is off by default and requires separate consent; the phone has no periodic notification worker.

### Public RSS notification registrations

The checker supports explicitly opted-in public `rss:` subscriptions from RTDB. Topics use `new_ep_rss_<id>_<sha256-of-trimmed-feed-url>` and payloads retain `rss:<id>`. Pure RSS failures or disabled URLs preserve the last-good state for retry and never fall back to PI. Each new URL scope quietly seeds a baseline; repeated keys remain quiet and FCM failures do not advance it.

RSS-only Git state uses `rss:<id>~<url-hash>` keys with a SHA-256 episode-key digest and episode title, excluding the feed URL, raw GUID, and enclosure. Inactive and legacy show-only scopes are retired. Feed URLs remain in RTDB and push payloads and are published by the weekly backup workflow, so this shared checker is unsuitable for private or premium feeds. These registrations do not require catalog sync changes.

Device RTDB rows use `rss:<show-id>~<registration-id>~<url-hash>`. Group only matching show IDs and exact trimmed feed URLs: different URLs under a preserved show ID have independent topics and release histories. The device journals registration and cleanup before publication, awaits RTDB and FCM acknowledgements, and retries failed cleanup through event-driven WorkManager work even when notifications are disabled. Migration removes legacy device rows and show-only topic subscriptions.

### Weekly tracked-podcast feed repair

[`backfill-tracked-podcast-feeds.yml`](../.github/workflows/backfill-tracked-podcast-feeds.yml) runs weekly and supports manual dispatch. Its `tracked-podcast-rtdb-maintenance` concurrency group prevents overlap with the notification checker.

Before mutation, [`backfill-tracked-podcast-feeds.js`](backfill-tracked-podcast-feeds.js) saves numeric registrations and aggregated public RSS show metadata to [`data/tracked-podcasts-backups/`](data/tracked-podcasts-backups/), retaining one snapshot per UTC ISO week for the latest 10 weeks. The workflow must successfully commit a changed snapshot to `master` before repairing rows. Backups preserve every accepted public URL scope without publishing device registration IDs.

Repair considers catalog rows without a valid HTTPS `feedUrl`. It uses the authenticated boxlore `/podcast` endpoint, probes HTTPS upgrades for legacy HTTP feeds, and falls back to an exact-title Apple directory match when the API cannot supply a secure URL. Transactions update only the `feedUrl` leaf, preserving newer app writes, `title`, and `imageUrl`. Unresolved rows are logged and retried next run. RSS rows are skipped rather than searched in the catalog.

### Release artifact contract guard

The release workflow runs `.github/scripts/verify_release_contracts.py` after optimized APK/AAB builds and before either publication path uploads artifacts. It compares pre-R8 project classes with optimized DEX using Android SDK `dexdump`, checks dynamic resource names with `aapt2`, and verifies named raw-resource payloads in both artifacts. Literal resource URIs are discovered from main/release Kotlin sources, including notification sounds. Run locally after `assembleRelease bundleRelease`:

```sh
python3 .github/scripts/verify_release_contracts.py --sdk <android-sdk> \
  --report app/build/reports/release-contracts.json \
  app/build/outputs/apk/release/app-release.apk \
  app/build/outputs/bundle/release/app-release.aab
```

`release_classfile.py` reads compiled JVM contracts; `release_dex.py` reads optimized contracts and R8 class names. `test_release_contracts.py` covers missing/private constructors, persisted names, nested JSON graphs, field/default changes, generic erasure, annotations, serializers, resource loss, and workflow wiring. The unsigned PR job exercises the real optimizer without release credentials. No catalog job or notification test is dispatched. See [optimized-release validation](../docs/TESTING.md#optimized-release-validation) for scope and manual acceptance.
