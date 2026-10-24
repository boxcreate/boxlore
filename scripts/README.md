# Scripts

Development guidance for catalog synchronization, notification checks, feed repair, and CI helpers. Read this file and the current [`sync/lib/config.js`](sync/lib/config.js) before changing catalog sync.

## Catalog sync and deployment

Catalog sync refreshes charts, imports podcasts, syncs episodes, and vectorizes content. It runs outside GitHub Actions; the former `sync-pi-data` workflow has been removed. Adding a replacement GitHub sync workflow requires explicit maintainer approval.

Scripts need Node.js 22.13 or newer (`engines` in `scripts/package.json`): stage 3 reads the feed-address registry and Apple's snapshot with `node:sqlite`, which needs a flag before 22.13. The runner uses Node 22, and so do the workflows that install `scripts/`.

A Git push does not deploy this pipeline. Changes to `scripts/sync/` or `scripts/package.json` require a separate deployment and verification that the active runner uses the updated code and configuration. Read the production runbook in root `AGENTS.internal.md` before deployment. If it is unavailable, obtain it from the maintainer before performing production operations.

## Catalog components

| Path | Role |
| :--- | :--- |
| [`scripts/sync/lib/config.js`](sync/lib/config.js) | Countries, tiers, check cadence, embedding provider, and budgets; the country list is defined here |
| [`scripts/sync/01-refresh-charts.js`](sync/01-refresh-charts.js) … [`07-record-stats.js`](sync/07-record-stats.js) | Staged pipeline: charts → import → episodes → cleanup before vectors when `CLEANUP=1` → stats |
| [`scripts/sync/lib/tip-queue.js`](sync/lib/tip-queue.js) | Turso `ep_vec_tip_queue` — durable tip lane (IDs); stage 3 upsert, stage 4 delete on complete |
| [`scripts/sync/lib/vectorize-lanes.js`](sync/lib/vectorize-lanes.js) | Tip-first drain order + partial-complete flag rules for stage 4 |
| [`scripts/sync/lib/pi-handoff.js`](sync/lib/pi-handoff.js) | Same-run episode payloads stage 3→4 (not cross-run durable) |
| [`scripts/sync/lib/turso.js`](sync/lib/turso.js) + [`turso-page.js`](sync/lib/turso-page.js) | HTTP Turso client; **always page** large SELECTs (`RESPONSE_TOO_LARGE`). Digit-only strings bind as integers by default; wrap catalog keys and other opaque IDs in `turso.text()` to keep them text. Objects and arrays are refused instead of being stored as `[object Object]`; `JSON.stringify` structured values on purpose. `atomic()` runs statements all-or-nothing |
| [`scripts/sync/lib/staleness.js`](sync/lib/staleness.js) | Core vs relaxed episode-check windows, and when stage 3 re-reads its cached candidate list from Turso (`candidateRefreshReason`): no list, charts refreshed since, the list is over 20 hours old, the core countries differ from the state's `relaxedTagsCore` (the list the relaxed `x` tags were computed with, recorded by each refresh), or, with the check cadence on, more than 5% of candidates (at least 50) have no iTunes ID. Core shows carry no `x`, so a missing tag alone never triggers a refresh. Otherwise the run reads no candidates from Turso |
| [`scripts/sync/lib/episode-caps.js`](sync/lib/episode-caps.js) | Per-storefront episode vector caps (us/gb 50, else 20), plus earned caps: a show keeps the highest cap it had while its vectors are kept (`shows[id].k` in the sync state), so leaving the US/GB charts doesn't delete and later re-embed 30 vectors |
| [`scripts/sync/lib/trim-episode-caps.js`](sync/lib/trim-episode-caps.js) | Nightly per-show trim (stage 6 and `remediate-qdrant.js`). Uses the larger of the chart cap and the earned cap, and **fails closed**: if the cap lookup fails, nothing is trimmed. Returns the trimmed show IDs, which `remediate-qdrant.js` re-flags for healing |
| [`scripts/sync/lib/vector-window.js`](sync/lib/vector-window.js) | Stage 4's window: shows with no vectors get their newest `NEW_ENTRANT_EPISODES` (5) first and the rest after `NEW_ENTRANT_DEPTH_DAYS` (7; `shows[id].d`, cleared only after the show's re-flag is stored, so a failed write retries next run); pruning only against a full window; stage 3 never hands stage 4 a one-item fallback |
| [`scripts/sync/lib/vector-spool.js`](sync/lib/vector-spool.js) | Computed vectors whose upload failed are written to `SYNC_SPOOL_DIR` and uploaded first by the next stage 4; batches older than 7 days are dropped. The default, `scripts/data/vector-spool/` (gitignored), is inside the checkout whatever the working directory, so a production runner must set `SYNC_SPOOL_DIR` to storage that survives redeploys |
| [`scripts/sync/lib/embedder.js`](sync/lib/embedder.js) | Embedding providers: `bge` (default) and `qwen` |
| [`scripts/sync/lib/podcast-index.js`](sync/lib/podcast-index.js) | The one PI API client for every stage. It is generous by default and slows down as soon as Podcast Index pushes back. **Rate:** up to `PI_MAX_RPS` (10) request starts per second. **Trouble** (403, 429, any 5xx including Cloudflare 52x, timeouts, network errors, `status: false`): the rate halves, down to `PI_MIN_RPS` (0.5). Every worker cools off together, for Retry-After when given, else 15 s doubling per trouble in a row, at most 10 min. Every 50 clean answers raise the rate by a quarter. **Pause:** after `PI_SUSPEND_AFTER` (12) failed attempts in a row, or 15 min of cooling in one process, it stops calling Podcast Index and saves the pause to `PI_PAUSE_FILE`. The pause lasts 30 min, doubling up to 6 h, and later stages and runs skip Podcast Index meanwhile. One probe call then clears or extends it. While paused, stages carry the work over: stage 3 leaves the remaining checks due, stage 2 retries the remaining lookups next run, and stage 4 uses saved windows and leaves the rest pending. The run itself succeeds. Run reports end with the client's summary (calls, final rate, throttled, cooling, pause) |
| [`scripts/sync/lib/pi-unavailable.js`](sync/lib/pi-unavailable.js) + [`import-missing.js`](sync/lib/import-missing.js) | Stage 2's not-found list for chart shows (30-day TTL). A show is listed only when the dump misses it (`not_in_dump`) or Podcast Index answers with no feed (`api_not_found`). A lookup that fails (outage, rate limit) is retried next run, never listed. With `PI_DUMP_DB` set, `not_in_dump` entries recorded before that dump was built are released, so a new dump is checked for them |
| [`scripts/sync/lib/text.js`](sync/lib/text.js) | Description cleaning + embed text (**uncapped**; `PAYLOAD_DESCRIPTION_MAX` null) |
| [`scripts/sync/lib/scalars.js`](sync/lib/scalars.js) | Scrub non-scalars before Qdrant/Turso writes |
| [`scripts/sync/lib/catalog-rows.js`](sync/lib/catalog-rows.js) | Scrubbed values for stage 2's API import and stage 3's latest-episode update: an odd optional field from Podcast Index becomes empty instead of failing the write. The latest-episode update stores the episode's served ID in `latest_ep_id` (what Home, trending and bootstrap send), plus `latest_ep_key` and `latest_ep_eid`, from `tipIdentity` in `lib/episode-identity.js` (by catalog key, else Podcast Index's ID as an alias in the same show, else that ID). Change detection and the tip queue keep Podcast Index's own ID |
| [`scripts/sync/lib/stale-tips.js`](sync/lib/stale-tips.js) | Stage 3's tips for stale shows (`R5_MODE`: `off` by default, `shadow`, `on`). After the Podcast Index pass, checked chart shows that the published feed-address registry (`REGISTRY_DB`) flags `episode_stale` ask the local feed reader (`FEEDS_URL`, `POST /feeds/latest`, 40 shows per call) for their newest episode. When it is playable, a different episode and newer than the stored tip, it becomes the tip under its served ID, catalog key and recipe ID. Only the tip columns change: `qdrant_vectorized` and the tip queue stay with the Podcast Index pass. `shadow` reads only the feed's dates and never writes. A registry or feed-reader failure skips it, and the Podcast Index tips stay |
| [`scripts/sync/lib/show-window.js`](sync/lib/show-window.js) | Saved episode windows (`WINDOWS_MODE`: `off` by default, `shadow`, `on`). Stage 3 records each full list it fetches as episode-book rows, with descriptions, and a `show_window` row: the book row IDs in list order. It fetches at the cap stage 4 enforces, chart or earned. Shows whose R5 tip came from the feed also save the feed reader's merged list (`GET /feeds/episodes`), so their feed-only episodes get vectors. With `on`, stage 4 reads a saved window instead of fetching the list from Podcast Index, when it is usable, fresh (3 days) and long enough. `shadow` records windows and has stage 4 compare them with its own fetch. A window with an episode that has no catalog key, or that another show serves, is unusable, and stage 4 takes the fetch path. Tip-queue rows carry `gen`, and stage 4 clears only rows enqueued before it started |
| [`scripts/sync/lib/cadence.js`](sync/lib/cadence.js) | Per-show check schedule for stage 3, in shadow (`CADENCE_MODE`: `shadow` by default, `off`). Checks still follow the news/core/relaxed tiers; the run report adds what the schedule would have checked. Each show's interval follows its pace, the average gap between its newest 11 episodes: news shows hourly, others a quarter of the gap within 3 to 48 hours, and dormant shows weekly when both the newest episode and a fresh Apple answer are over 180 days old. A show with no known pace keeps its tier. A show Apple lists a newer episode for (`APPLE_LATEST_DB`, the applefeeds snapshot) is due until it has been checked 6 hours past that date. Pace and the newest date come from each fetched list and from the episode book about daily (400 shows per read); show records gain `g`, `p` and `i` (iTunes ID). The report line counts shows the schedule would skip or check earlier, new tips it would have found later and by how much, and checks a day against today's tiers. A book or snapshot failure only skips that part |
| [`scripts/sync/lib/attribution.js`](sync/lib/attribution.js) | Vector attribution log: one JSON line per episode point stored or deleted (prune, cap trim), show vector stored, and show imported or deleted. Written to `SYNC_ATTRIBUTION_DIR` (default `scripts/data/attribution/`, gitignored) as daily files, gzipped after the day ends and kept 60 days. Best effort: a write failure never fails a stage. Stage 4 adds `stored`, `pruned` and `reasons …` to its run line |
| [`scripts/sync/attribution-report.js`](sync/attribution-report.js) | Read-only report over that log: embeddings by reason (new episode, first coverage, returning show, re-embed after cap trim or prune, Podcast Index ID change, backfill), deletions, and how long chart entrants stay. `--days N`, `--json` |
| [`scripts/sync/lib/episode-book.js`](sync/lib/episode-book.js) + [`episode-book-cli.js`](sync/episode-book-cli.js) | The episode book in sqld: one permanent row per (show, catalog key) with its recipe ID and served ID, plus Podcast Index aliases, `served_show` and `show_window`. Every book write also marks the show in `served_show` when one of its episodes is listed by Podcast Index under an ID other than its served ID (renumbered, duplicated or recipe-served), so the Worker's v1 overlay translates that ID back. That check reaches a show's aliases through `episode_pi_alias_rid` (an index on `rid`); without it each check scanned every alias inside the write. `ensure` creates the tables, indexes and columns; `stats` counts. Writes go through `turso.atomic()` (one Hrana batch: every step runs only after the previous one succeeded, then COMMIT, else ROLLBACK). Rules: [`docs/EPISODE_IDS.md`](../docs/EPISODE_IDS.md) |
| [`scripts/sync/lib/episode-identity.js`](sync/lib/episode-identity.js) | Stage 4's episode identity: each window episode gets its catalog key and recipe ID, and its point is named by the book's served ID. A new episode's book row (served ID = its current Podcast Index ID) is written before its vector; a Podcast Index ID change keeps the point and adds an alias instead of re-embedding. Points are stamped `eid` and `catalog_key` (`eid_missing` without a key). A new key whose Podcast Index ID is already served for another key is deferred: today's naming, no row. Its point belongs to the owner: stamped with the owner's `eid` and `catalog_key` when the owner is in the same show (a changed GUID), or `eid_shared` with the owning show when it is another show (a duplicate feed: Podcast Index lists one podcast under two show IDs that share episode IDs). If the book write fails, the show keeps today's naming for that run and the bootstrap's `--catch-up` records it later; a finished catch-up pass starts over when run again |
| [`scripts/sync/bootstrap-book.js`](sync/bootstrap-book.js) + [`lib/book-bootstrap.js`](sync/lib/book-bootstrap.js) | One resumable pass over every show with episode points: maps each point's payload ID (its Podcast Index ID) to a catalog key through PI's episode list, writes the show's book rows in one transaction (served ID = that PI ID), then stamps `eid` and `catalog_key` on the points (`eid_missing` when unmapped). A point whose Podcast Index ID another key already serves gets no row, so the show's transaction can't fail on it: it is stamped with that key's identity when it is in the same show, or `eid_shared` with the owning show for a duplicate feed. Point IDs and vectors never change. `--dry`, `--limit-shows N`, `--minutes N`; progress in `SYNC_BOOTSTRAP_STATE`. `--catch-up` takes only shows that still have points with none of `eid`, `eid_missing` and `eid_shared`. `--retry-missing` (own-episode-IDs P3; monthly on the VPS) runs the pass again for shows with `eid_missing` points: a point mapped this time gets its stamp and loses the mark |
| [`scripts/sync/lib/charted-at.js`](sync/lib/charted-at.js) + [`stamp-charted-at.js`](sync/stamp-charted-at.js) | Last-charted dates on Qdrant points (own-episode-IDs P3b): every point in both collections carries `charted_at`, the UTC day (epoch seconds) its show was last seen on a chart, so a 90-day vector retention can outlive the show's catalog row and state record. Stage 6 stamps every show it keeps from its chart clock (`shows[id].s`) before deleting; a point is rewritten only when it has no date or one more than `CHARTED_AT_REFRESH_DAYS` (default 7) older than its show's, so dates only move forward and a charting show's points are rewritten about weekly. Stages 4 and 5 give new points the day they are embedded. Failures are logged and never block the cleanup. `stamp-charted-at.js` stamps every point once from the catalog and the chart clock without changing anything else (`--dry` counts only; run from the repository root under the shared job lock). Nothing deletes by this date yet |
| [`scripts/sync/lib/retention.js`](sync/lib/retention.js) | Vector retention after a show leaves the charts (own-episode-IDs P3b, decided 24 Oct 2026): each departed show keeps its newest `ARCHIVE_EPISODES` (5) episode points and its show vector for `RETENTION_DAYS` (90) after it was last on a chart, read from `charted_at`; its catalog row still goes after the 7-day grace. `RETENTION_MODE` (stage 6): `count` by default keeps today's deletes and adds what `on` would keep, delete and expire to the report; `on` keeps the newest episodes instead of deleting the show's vectors, stamps their exact last chart day, and deletes points (both collections) last charted more than `RETENTION_DAYS` ago; `off` reports nothing. Size guard on episode points: over `QDRANT_POINTS_ALERT` (1.8M) the run warns; over `QDRANT_POINTS_CEILING` (2.0M) retention steps down to 60 then 30 days, and an urgent alert goes out through `SYNC_NOTIFY_SCRIPT` while it stays over (resolved once under). Fails safe: expiry is held back (counted only) in a run whose date stamps failed; a departed show whose kept points couldn't be dated keeps its catalog row and chart clock until a later run succeeds; with `on`, a retention failure fails the stage. Numeric settings accept whole numbers only. Attribution logs kept episodes on `show-` (`kept`) and expired points as `exp` (`retention` in the report), written only after Qdrant confirms the delete. Qdrant's memory limit is 6 GB for this |
| [`scripts/sync/lib/episode-recipe-id.js`](sync/lib/episode-recipe-id.js) | boxlore episode IDs from (show ID, catalog key); must match the app's `EpisodeRecipeId` and the shared fixture in `core/rss/src/test/resources/fixtures/` |
| [`scripts/package.json`](package.json) | Sync Node deps + `npm run test:sync` |

The runner orchestrator lives outside this repository. It invokes the stages in order and, before stage 2's bulk path, produces `podcasts_export.csv` (columns must match `PODCAST_IMPORT_COLUMNS` in `sync/lib/config.js`) from a locally stored copy of the public Podcast Index weekly dump, which it refreshes separately. Stage scripts never download the dump themselves.

## Changing catalog sync

- Extend hermetic tests under `sync/lib/*.test.js` and run `npm run test:sync` from `scripts/`.
- Keep large Turso reads on `fetchAllPaged` or country/category pages; avoid unbounded wide SELECTs.
- Preserve the chart index: `charts.itunes_id` is TEXT, so casting that column to INTEGER disables the index and inflates `rows_read`. Use `c.itunes_id = CAST(p.itunes_id AS TEXT)`, [`sync/lib/chart-countries.js`](sync/lib/chart-countries.js) (`loadCountriesByItunesId` plus a JavaScript filter), or page podcasts by `id` and normalize IDs in JavaScript. This applies to stages 2, 3, 4, 5, and remediation.
- Keep credentials and environment files out of Git, including `.env`, Podcast Index keys, Turso tokens, and Telegram secrets.

Use Node.js 22.13 or newer to match the runner and the script workflows. From the repository root:

```bash
npm ci --prefix scripts
npm run test:sync --prefix scripts
npm run test:check-new-episodes --prefix scripts
node --test scripts/backfill-tracked-podcast-feeds-lib.test.js
```

These commands test local logic; running a production script requires the internal runbook and deployment authorization. The catalog suite is a local check and is not scheduled by GitHub Actions.

## Other scripts

CI configuration helpers, maintenance tools, and data files also live here. Check the owning workflow or runbook before using a tool; its presence in this directory does not mean it runs automatically.

### Authored release copy and isolated rehearsals

The publication and same-version refresh jobs build the direct `assembleRelease` APK and installer-free `bundlePlayRelease` AAB, validate their optimized contracts separately, and verify the downloadable APK before generating/uploading `update.json`. The manifest identifies the immutable release APK URL, version code/name, Android minimum, byte size, checksum and full reviewed listener bullets. A refresh updates its checksum without creating a newer version or sending an update alert. If manifest publication fails after verified assets, rerun publication/refresh to repair the manifest; do not bump again solely for a failed upload. No manifest is advertised before the download check passes. App checks remain read-only and do not rely on FCM delivery.

Release tooling lives in `.github/scripts/`. The PR author or coding agent writes the exact developer and listener copy in the [PR template](../.github/PULL_REQUEST_TEMPLATE.md). `update_changelog.py` normalizes historical impact-label aliases consistently with tag resolution, requires exactly one recognized impact label, rejects multiple labels even when they normalize to the same level, requires developer copy for every PR and listener copy for all user-impact labels, and never calls a text-generation service. The **Release copy** check refreshes on PR body and label edits. It is a fast standalone check; no Android build or repository-rule change is involved.

README order is Critical → New features → Improvements → Fixes → Security → Other. Within a category, higher impact leads, then newer PRs; bullet order within each PR is preserved. CHANGELOG uses Keep a Changelog categories, then impact and PR order. All authored bullets are retained. `no-user-impact` stays in CHANGELOG only. Existing release notes and legacy markers remain readable.

If copy is missing, fill that PR's release-copy region. For a merged PR, run **Changelog and Release → backfill-changelog** with its number, then retry the failed operation. Do not hand-edit generated regions. Release/tooling PRs can still use the established `[skip changelog]` exception.

**Offline release rehearsal** writes candidate version files, release notes and an announcement payload to an artifact without editing source files, creating a release, using credentials or sending FCM. Same-version refresh previews keep the version unchanged and produce no announcement. Locally:

```bash
python3 .github/scripts/rehearse_release.py --output /tmp/boxlore-release-preview
python3 .github/scripts/rehearse_release.py --output /tmp/boxlore-refresh-preview --operation refresh-latest-artifacts
```

For a custom announcement, **Send boxlore Notification** separates three steps:

1. `preview_only=true` writes `announcement-preview.json` without Firebase access or delivery. Import it in the admin composer or use Download preview there.
2. `dry_run=true` validates with Firebase without device delivery. Test mode permits only debug_users or isolated test_users.
3. Real delivery requires the exact preview's approval digest. In the admin, Review announcement snapshots the text, appearance, destinations and audience; tick the review box and approve. Changing the draft requires another review. Automatic release publication prepares this artifact and does not send until approved.

The composer offers compact/fullscreen alerts, controlled accent/image styles and exact selected-profile rendering, with a separate system-notification text preview. Release alerts have native Download update, View on GitHub and Dismiss actions. Optional fields retain old payload compatibility. Direct release delivery targets the new direct_users topic and excludes play_users/test_users; old clients sharing prod_users are not silently included. Ordinary announcements still support both channels, and manual include-Play is explicit. Topic payloads are checked against the 2,048-byte UTF-8 limit. FCM acceptance is not device receipt confirmation.

If preview validation fails, correct the reported field or shorten the message, then preview again. If approval is stale, review the current draft and approve it again. If Firebase validation fails, inspect that workflow step; no message was delivered. If delivery is accepted but the device shows nothing, check the selected build/audience, notification permission, release code and Play exclusion. Retry a live message only after confirming that a previous request was not accepted.

The public composer and renderer are versioned here; its existing private gateway/auth/hosting configuration remains unchanged and ignored. Deploying the panel is a separate operation. See [admin composer](../docs/ANNOUNCEMENT_COMPOSER.md) and [isolated app testing](../docs/TESTING.md#isolated-update-and-announcement-testing).

The local updater fixture accepts `--notes-file` for long UTF-8 sample release notes. It preserves the supplied text in the test manifest and `/notes` page, validates the Android character/response bounds, and never publishes or sends anything.

### Check New Episodes

The checker runs **hourly on the VPS** (netcup-panel `stack/notifier`: a systemd timer runs this script from the sync runner's copy, with its own Firebase service account that can only read `tracked_podcasts` and send pushes). GitHub's scheduled runs came every 2–6 hours instead of every 30 minutes. [`.github/workflows/new-episode-check.yml`](../.github/workflows/new-episode-check.yml) is now a manual fallback that rehearses (`dry_run`) by default; a live run there would use the repo's older state file. It polls notification registrations independently of catalog sync.

| What | Where |
| :--- | :--- |
| Script | [`scripts/check-new-episodes.js`](check-new-episodes.js) + [`check-new-episodes-lib.js`](check-new-episodes-lib.js) |
| Who to poll | Firebase RTDB `tracked_podcasts/{podcastIndexId}` (client writes this when **show notifications** are on). Rules allow `title`, `imageUrl`, and optional HTTPS `feedUrl` only — extra children are rejected. |
| Last-notified state | `NOTIFIER_STATE_FILE` on the VPS (`/var/lib/boxlore-notifier/episode-tracker.json`, in the daily backup). [`scripts/data/episode-tracker.json`](data/episode-tracker.json) is the last state from GitHub Actions |
| Tests | `npm ci` then `npm run test:check-new-episodes` from `scripts/` (the Check New Episodes workflow runs the same script after `npm ci`) |

Release selection and recovery:

- A tracked HTTPS `feedUrl` selects RSS/Atom and compares `lastRssKey` (GUID, otherwise enclosure), independently of the phone's Missing episodes? cache setting. Other catalog rows use Podcast Index `episodes/byfeedid?max=1` and `lastEpisodeId`.
- The first check without saved state quietly seeds a baseline. When an existing catalog row has `lastEpisodeId` but no `lastRssKey`, its first RSS check can notify if the newest feed item matches a PI episode with a different ID. Catalog RSS failures fall back to Podcast Index without clearing `lastRssKey`.
- Read the complete body within the **25 MB** cap shared with Android `RssFeedClient`; oldest-first feeds may put the newest release at the end. Interrupted, oversized, or failed reads retain the last-good baseline. Parsing accepts playable enclosures regardless of attribute order and ignores non-media or untitled entries.
- The Action never creates negative episode IDs. Unmatched feed-only releases omit `episodeId` and open the podcast page. The phone persists the raw GUID/enclosure hint before hydration and resolves the release in its publisher-feed Room catalog.
- Visible alerts request high Android FCM priority. Foreground discovery recovers missed episodes by default. Background auto-download discovery is off by default and requires separate consent; the phone has no periodic notification worker.

Working addresses and episode IDs (with the `BOXLORE_SVC_NOTIFIER_KEY` secret; without it the checker behaves as before):

- At the start of a run the checker asks the boxlore API (`POST /internal/notifier/shows`) for each catalog show's working feed address. A confirmed address (the registered one is dead and a working one was verified) replaces it for the RSS check and in the push; Podcast Index fallback pushes now carry `feedUrl` too. The first read of a different address quietly seeds a baseline (`rss-address-changed`, with `lastFeedUrl` in the state), because a moved feed can relabel old episodes.
- Before sending, it asks `POST /internal/notifier/episodes` for the episode's served ID, the ID that never changes, and puts it in `episodeId` and `route`. With none (a feed-only episode nobody has been served yet) or when the API fails, the push opens the show instead.
- Every state change is saved, including `unchanged` decisions that moved a key forward. The state file is written even when a run stops part-way, the commit step always runs, and a failed push leaves that show's state alone so it is retried (the run then shows as failed).
- New-episode pushes only go to `new_ep_<id>` and `new_ep_rss_…` topics; broadcast topics are refused.
- A manual run with `dry_run` decides and logs (never feed addresses) without sending or saving anything.

### Public RSS notification registrations

The checker supports explicitly opted-in public `rss:` subscriptions from RTDB. Topics use `new_ep_rss_<id>_<sha256-of-trimmed-feed-url>` and payloads retain `rss:<id>`. Pure RSS failures or disabled URLs preserve the last-good state for retry and never fall back to PI. Each new URL scope quietly seeds a baseline; repeated keys remain quiet and FCM failures do not advance it.

RSS-only Git state uses `rss:<id>~<url-hash>` keys with a SHA-256 episode-key digest and episode title, excluding the feed URL, raw GUID, and enclosure. Inactive and legacy show-only scopes are retired. Feed URLs remain in RTDB and push payloads and are published by the weekly backup workflow, so this shared checker is unsuitable for private or premium feeds. These registrations do not require catalog sync changes.

Device RTDB rows use `rss:<show-id>~<registration-id>~<url-hash>`. Group only matching show IDs and exact trimmed feed URLs: different URLs under a preserved show ID have independent topics and release histories. The device journals registration and cleanup before publication, awaits RTDB and FCM acknowledgements, and retries failed cleanup through event-driven WorkManager work even when notifications are disabled. Migration removes legacy device rows and show-only topic subscriptions.

### Weekly tracked-podcast feed repair

[`backfill-tracked-podcast-feeds.yml`](../.github/workflows/backfill-tracked-podcast-feeds.yml) runs weekly and supports manual dispatch. Its `tracked-podcast-rtdb-maintenance` concurrency group prevents overlap with the notification checker.

Before mutation, [`backfill-tracked-podcast-feeds.js`](backfill-tracked-podcast-feeds.js) saves numeric registrations and aggregated public RSS show metadata to [`data/tracked-podcasts-backups/`](data/tracked-podcasts-backups/), retaining one snapshot per UTC ISO week for the latest 10 weeks. The workflow must successfully commit a changed snapshot to `master` before repairing rows. Backups preserve every accepted public URL scope without publishing device registration IDs.

Repair first reads the feed-address registry for every tracked catalog show (`/internal/notifier/shows`, with the read-only `BOXLORE_SVC_NOTIFIER_KEY` secret). It changes two kinds of rows:
- **Missing:** no valid HTTPS `feedUrl`. Uses the registry's HTTPS address when it has one. Otherwise it uses the authenticated boxlore `/podcast` endpoint (with the scoped read-only `BOXLORE_SVC_BACKFILL_KEY` secret when set, otherwise the legacy app key), probes HTTPS upgrades for legacy HTTP feeds, and falls back to an exact-title Apple directory match.
- **Dead:** the registry proved the saved address dead and confirmed a different working one. This is the same rule the API uses to repair show pages.

Each write is a transaction on the `feedUrl` leaf that commits only while the leaf still holds the value this run read. Newer app writes, `title` and `imageUrl` are always preserved. The pre-repair snapshot from the same run holds the old addresses. Without the registry, the run only fills missing addresses. Unresolved rows are logged and retried next run. RSS rows are skipped rather than searched in the catalog.

### Release artifact contract guard

The release workflow runs `.github/scripts/verify_release_contracts.py` after optimized APK/AAB builds and before either publication path uploads artifacts. It compares pre-R8 project classes with optimized DEX using Android SDK `dexdump`, checks dynamic resource names with `aapt2`, and verifies named raw-resource payloads in both artifacts. Literal resource URIs are discovered from main/release Kotlin sources, including notification sounds. Run locally after `assembleRelease bundlePlayRelease`:

```sh
python3 .github/scripts/verify_release_contracts.py --sdk <android-sdk> \
  --report app/build/reports/release-contracts.json \
  app/build/outputs/apk/release/app-release.apk
python3 .github/scripts/verify_release_contracts.py --sdk <android-sdk> --app-variant playRelease \
  --report app/build/reports/play-release-contracts.json \
  app/build/outputs/bundle/playRelease/app-playRelease.aab
```

`release_classfile.py` reads compiled JVM contracts; `release_dex.py` reads optimized contracts and R8 class names. `test_release_contracts.py` covers missing/private constructors, persisted names, nested JSON graphs, field/default changes, generic erasure, annotations, serializers, resource loss, and workflow wiring. These fast tests run in PR CI. Actions → Manual optimized release validation → Run workflow optionally exercises the real optimizer on the selected branch without release credentials. It does not run automatically on PRs, publish a release or send notifications. See [optimized-release validation](../docs/TESTING.md#optimized-release-validation) for scope and manual acceptance.

All three artifact-build jobs explicitly request `platform-tools` during Android SDK setup; the pinned setup action's default also requests the removed `tools` package and fails before compilation. The workflow regression check covers the manual preflight and both signed publication paths.

The update manifest preserves authored listener-note emphasis and links as Markdown instead of flattening them to plain text. The app renders the supported listener Markdown subset.

Local update fixtures support `--upcoming-file` for testing an unreleased Markdown preview. `upcoming_notes` extracts authored listener copy from README Upcoming Changes. The approved workflow refreshes only optional upcoming text in the latest verified update feed after README synchronization. Metadata writers share a concurrency group with `queue: max` to prevent stale overwrites without dropping pending changelog/publication jobs; release preparation uses a separate queued group. Artifact refresh publishes its verified manifest using that job’s available workflow token. This does not change APK metadata or send FCM; it becomes active only when these workflow changes are shipped.

Manual announcements reject a GitHub release URL unless Release alert is enabled, before approval or provider access. Update-feed note limits count UTF-16 units to match Android, in addition to the UTF-8 response limit.
