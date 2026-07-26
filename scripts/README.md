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
| [`scripts/sync/01-refresh-charts.js`](sync/01-refresh-charts.js) … [`07-record-stats.js`](sync/07-record-stats.js) | Staged pipeline (charts → import → episodes → vectors → cleanup → stats) |
| [`scripts/sync/lib/turso.js`](sync/lib/turso.js) + [`turso-page.js`](sync/lib/turso-page.js) | HTTP Turso client; **always page** large SELECTs (`RESPONSE_TOO_LARGE`) |
| [`scripts/sync/lib/staleness.js`](sync/lib/staleness.js) | Core vs relaxed episode-check windows |
| [`scripts/sync/lib/episode-caps.js`](sync/lib/episode-caps.js) | Per-storefront episode vector caps |
| [`scripts/sync/lib/embedder.js`](sync/lib/embedder.js) | `bge` (default) vs `qwen` on VPS |
| [`scripts/sync/lib/scalars.js`](sync/lib/scalars.js) | Scrub non-scalars before Qdrant/Turso writes |
| [`scripts/package.json`](package.json) | Sync Node deps + `npm run test:sync` |

## Agent checklist before sync edits

1. Read this README + current [`sync/lib/config.js`](sync/lib/config.js) country list.
2. Prefer hermetic tests under `scripts/sync/lib/*.test.js` (`npm run test:sync` from `scripts/`).
3. Keep large Turso reads on `fetchAllPaged` / country×category pages — do not reintroduce unbounded wide SELECTs.
4. After commit/push (when asked): **deploy to `/opt/boxlore-sync/repo`** and confirm the runner sees the new countries/flags (`node -e '…require config…'` on the VPS).
5. Never commit `.env`, PI keys, Turso tokens, or Telegram secrets.

## Other things under `scripts/`

Non-sync helpers (CI stubs, one-offs, data files) may also live here. They are unrelated to the VPS catalog cron unless documented otherwise. When in doubt, ask before assuming GHA runs them.
