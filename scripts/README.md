# scripts/ — **READ BEFORE EDITING (agents)**

> **Stop.** If you are about to change anything under `scripts/sync/`, read this file first.
> Shipping sync code to GitHub alone does **not** run the catalog pipeline.

## Sync does **not** run on GitHub

- The old GitHub Actions workflow **`sync-pi-data`** is **sunset / removed**.
- There is **no** GHA cron that refreshes charts, imports podcasts, syncs episodes, or vectorizes.
- Do **not** re-add a GitHub sync workflow unless the user explicitly asks.
- Do **not** assume `git push` to `master` updates the live pipeline by itself.

## Sync runs on the Netcup VPS

| What | Where |
| :--- | :--- |
| Live runner root | `/opt/boxlore-sync/` |
| **Code the cron executes** | `/opt/boxlore-sync/repo/scripts/sync/` (`run-sync.sh` → `cd $REPO`) |
| Orchestrator | `/opt/boxlore-sync/run-sync.sh` (systemd timers; panel install from `netcup-panel`) |
| Secrets / budgets | `/opt/boxlore-sync/.env` (never commit) |
| Run logs | `/opt/boxlore-sync/logs/runs/` |
| Local Turso / Qdrant | `/opt/boxlore-stack` (sqld + qdrant on the same box) |

**Deploy rule:** after changing `scripts/sync/` (or `scripts/package.json`), you must **redeploy to the VPS `repo` tree** (rsync/pull into `/opt/boxlore-sync/repo`) or the live job keeps running the old code. GitHub is the source of truth for *what to deploy*, not the runner itself.

A second tree `/opt/boxlore-sync/boxlore-src` may exist as a snapshot — **cron does not use it**. Only `repo` matters for the live sync.

## Tagged surfaces (edit with care)

| Path | Role |
| :--- | :--- |
| [`scripts/sync/lib/config.js`](sync/lib/config.js) | **Countries, tiers, check cadence, embed provider, budgets** — Phase-2 country list lives here only |
| [`scripts/sync/01-refresh-charts.js`](sync/01-refresh-charts.js) … [`07-record-stats.js`](sync/07-record-stats.js) | Staged pipeline (charts → import → episodes → **cleanup before vectors on CLEANUP=1** → stats) |
| [`scripts/sync/lib/tip-queue.js`](sync/lib/tip-queue.js) | Turso `ep_vec_tip_queue` — durable tip lane (IDs); stage 3 upsert, stage 4 delete on complete |
| [`scripts/sync/lib/vectorize-lanes.js`](sync/lib/vectorize-lanes.js) | Tip-first drain order + partial-complete flag rules for stage 4 |
| [`scripts/sync/lib/pi-handoff.js`](sync/lib/pi-handoff.js) | Same-run episode payloads stage 3→4 (not cross-run durable) |
| [`scripts/sync/lib/turso.js`](sync/lib/turso.js) + [`turso-page.js`](sync/lib/turso-page.js) | HTTP Turso client; **always page** large SELECTs (`RESPONSE_TOO_LARGE`) |
| [`scripts/sync/lib/staleness.js`](sync/lib/staleness.js) | Core vs relaxed episode-check windows |
| [`scripts/sync/lib/episode-caps.js`](sync/lib/episode-caps.js) | Per-storefront episode vector caps |
| [`scripts/sync/lib/embedder.js`](sync/lib/embedder.js) | `bge` (default) vs `qwen` on VPS |
| [`scripts/sync/lib/podcast-index.js`](sync/lib/podcast-index.js) | PI API client — Retry-After / global cooldown failsafes |
| [`scripts/sync/lib/text.js`](sync/lib/text.js) | Description cleaning + embed text (**uncapped**; `PAYLOAD_DESCRIPTION_MAX` null) |
| [`scripts/sync/lib/scalars.js`](sync/lib/scalars.js) | Scrub non-scalars before Qdrant/Turso writes |
| [`scripts/package.json`](package.json) | Sync Node deps + `npm run test:sync` |

## Agent checklist before sync edits

1. Read this README + current [`sync/lib/config.js`](sync/lib/config.js) country list.
2. Prefer hermetic tests under `scripts/sync/lib/*.test.js` (`npm run test:sync` from `scripts/`).
3. Keep large Turso reads on `fetchAllPaged` / country×category pages — do not reintroduce unbounded wide SELECTs.
3b. **Never** join/filter charts with `CAST(c.itunes_id AS INTEGER)` (charts column is TEXT). That disables the index and can turn candidate / pending scans into huge `rows_read`. Use `c.itunes_id = CAST(p.itunes_id AS TEXT)`, [`sync/lib/chart-countries.js`](sync/lib/chart-countries.js) (`loadCountriesByItunesId` + JS filter), or page podcasts by `id` and normalize itunes ids in JS. Stages 2/4/5/remediate must follow the same rule as Stage 3.
4. After commit/push (when asked): **deploy to `/opt/boxlore-sync/repo`** and confirm the runner sees the new countries/flags (`node -e '…require config…'` on the VPS).
5. Never commit `.env`, PI keys, Turso tokens, or Telegram secrets.

## Other things under `scripts/`

Non-sync helpers (CI stubs, one-offs, data files) may also live here. They are unrelated to the VPS catalog cron unless documented otherwise. When in doubt, ask before assuming GHA runs them.

### Check New Episodes (GitHub Actions — not VPS catalog sync)

[`.github/workflows/new-episode-check.yml`](../.github/workflows/new-episode-check.yml) still runs on a ~30 minute cron. It is **not** the sunset catalog pipeline.

| What | Where |
| :--- | :--- |
| Script | [`scripts/check-new-episodes.js`](check-new-episodes.js) + [`check-new-episodes-lib.js`](check-new-episodes-lib.js) |
| Who to poll | Firebase RTDB `tracked_podcasts/{podcastIndexId}` (client writes this when **show notifications** are on). Rules allow `title`, `imageUrl`, and optional HTTPS `feedUrl` only — extra children are rejected. |
| Last-notified state | [`scripts/data/episode-tracker.json`](data/episode-tracker.json) (the Action commits this) |
| Tests | `npm ci` then `npm run test:check-new-episodes` from `scripts/` (the Check New Episodes workflow runs the same script after `npm ci`) |

If a tracked row has HTTPS `feedUrl` (opted-in Missing episodes?), the checker polls that RSS/Atom feed and compares `lastRssKey` (guid, else enclosure). Otherwise it keeps Podcast Index `episodes/byfeedid?max=1` vs `lastEpisodeId`. First see is a baseline (no notify). RSS fetch failure falls back to Podcast Index and does not wipe `lastRssKey`. Publisher feeds may be tens of MB (The Daily ~18 MB); the checker uses the same **25 MB** hard cap as Android `RssFeedClient` and **stops after 2 MB** once a complete `<item>` / `<entry>` is in the buffer so mega-feeds are not fully downloaded every 30 minutes. The Action never mints negative episode ids; unmatched feed-only drops omit `episodeId` and deep-link the podcast page. The phone hydrates the local supplement cache on receive.

### Weekly tracked-podcast feed repair

[`backfill-tracked-podcast-feeds.yml`](../.github/workflows/backfill-tracked-podcast-feeds.yml) runs weekly and can be dispatched manually. It shares the `tracked-podcast-rtdb-maintenance` concurrency group with the new-episode checker, so the two RTDB jobs never run at the same time. [`backfill-tracked-podcast-feeds.js`](backfill-tracked-podcast-feeds.js) reads `tracked_podcasts`, resolves only rows without a valid HTTPS `feedUrl` through the authenticated boxlore `/podcast` endpoint, probes HTTPS upgrades for legacy HTTP feeds, and uses an exact-title Apple directory match only when the API cannot supply a secure URL. Each result is written with a transaction to the individual `feedUrl` leaf, so a newer app write wins and `title` / `imageUrl` cannot be replaced. Unresolved rows are logged and retried on the next run.
