#!/usr/bin/env node
'use strict';

/**
 * Stage 1: Refresh the Turso charts table from iTunes RSS for all configured
 * countries. Runs on the 00:00 UTC schedule (or manual dispatch) only.
 *
 * - Existing chart rows loaded per country×category page (full-table SELECT
 *   exceeds sqld response size once many storefronts are populated).
 * - iTunes requests are spaced (600ms) with a polite User-Agent, retried up
 *   to 5× with exponential backoff on 403/429 (honors Retry-After), network
 *   errors, and empty feeds (Apple often returns HTTP 200 + 0 entries when
 *   throttling). Consecutive soft-fails trip a circuit: pause 60s every 5,
 *   abort after 15.
 * - Only changed rows are written, in batched transactions.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const state = require('./lib/state');
const cfg = require('./lib/config');

const ITUNES_SPACING_MS = 600;
const ITUNES_RETRIES = 5;
const ITUNES_USER_AGENT = 'BoxLore/1.0 (github.com/boxcreate/boxlore; podcast-chart-sync)';
/** After this many consecutive soft-fails, pause hard before continuing. */
const ITUNES_CIRCUIT_CONSECUTIVE = 5;
const ITUNES_CIRCUIT_PAUSE_MS = 60_000;
/** Abort the stage after this many consecutive soft-fails (protect Apple + remaining budget). */
const ITUNES_CIRCUIT_ABORT_AFTER = 15;

function parseRetryAfterMs(headerVal, fallbackMs) {
    const floor = Math.max(0, Number(fallbackMs) || 0);
    if (headerVal == null || headerVal === '') return Math.max(floor, 2000);
    const s = String(headerVal).trim();
    const asInt = Number(s);
    if (Number.isFinite(asInt) && asInt >= 0) {
        return Math.min(Math.max(asInt * 1000, floor), 120_000);
    }
    const when = Date.parse(s);
    if (Number.isFinite(when)) {
        const ms = when - Date.now();
        if (ms > 0) return Math.min(Math.max(ms, floor), 120_000);
    }
    return Math.max(floor, 2000);
}

async function fetchItunesChart(country, category) {
    const genreParam = category === 'all' ? '' : `/genre=${cfg.GENRE_MAP[category]}`;
    const url = `https://itunes.apple.com/${country}/rss/toppodcasts${genreParam}/limit=200/json`;

    for (let attempt = 1; attempt <= ITUNES_RETRIES; attempt++) {
        try {
            const res = await fetch(url, {
                headers: { 'User-Agent': ITUNES_USER_AGENT },
            });
            if (res.status === 403 || res.status === 429) {
                const exp = 2000 * Math.pow(2, attempt - 1);
                const backoff = parseRetryAfterMs(res.headers.get('retry-after'), exp);
                log.warn(`iTunes ${res.status} for ${country}/${category}; backing off ${backoff}ms (attempt ${attempt}/${ITUNES_RETRIES})`);
                await new Promise(r => setTimeout(r, backoff));
                continue;
            }
            if (!res.ok) throw new Error(`iTunes HTTP ${res.status}`);
            const data = await res.json();
            const raw = data.feed?.entry;
            const entries = raw == null ? [] : (Array.isArray(raw) ? raw : [raw]);
            if (entries.length === 0) {
                // Apple sometimes returns HTTP 200 with an empty feed when throttling
                // GitHub Actions IPs — retry with backoff instead of skipping immediately.
                const backoff = 2000 * Math.pow(2, attempt - 1);
                log.warn(`iTunes empty feed for ${country}/${category}; backing off ${backoff}ms (attempt ${attempt}/${ITUNES_RETRIES})`);
                if (attempt < ITUNES_RETRIES) {
                    await new Promise(r => setTimeout(r, backoff));
                    continue;
                }
                return [];
            }
            return entries.map(e => ({
                itunesId: e.id?.attributes?.['im:id'] || '',
                name: e['im:name']?.label || '',
                artist: e['im:artist']?.label || '',
                imageUrl: e['im:image']?.[2]?.label || e['im:image']?.[1]?.label || '',
            })).filter(p => p.itunesId);
        } catch (e) {
            if (attempt === ITUNES_RETRIES) throw e;
            const backoff = 2000 * Math.pow(2, attempt - 1);
            log.warn(`iTunes fetch failed for ${country}/${category}: ${e.message}; backing off ${backoff}ms (attempt ${attempt}/${ITUNES_RETRIES})`);
            await new Promise(r => setTimeout(r, backoff));
        }
    }
    throw new Error(`iTunes fetch exhausted retries for ${country}/${category}`);
}

function rowKey(country, category, itunesId) {
    return `${country}|${category}|${itunesId}`;
}

// Charts are expensive to refresh (~36k writes). Skip if already refreshed
// within this window to protect the daily write budget when multiple runs
// or manual dispatches fire close together.
const CHARTS_REFRESH_MIN_GAP_MS = 20 * 60 * 60 * 1000; // 20 hours
const FORCE = process.argv.includes('--force');

async function main() {
    turso.assertEnv();
    turso.beginStep('refresh-charts');
    await turso.healthCheck();

    // --- Skip-if-fresh gate ---
    // Load state once here so we can reuse it when saving chartsRefreshedAt below.
    const st = state.load();
    const last = Number(st.chartsRefreshedAt) || 0;
    const now = Date.now();
    let ageMs = now - last;
    if (last > now) {
        log.warn(`[CANDIDATES] Future chartsRefreshedAt detected (${new Date(last).toISOString()}) - resetting to stale`);
        st.chartsRefreshedAt = 0;
        state.save(st);
        ageMs = CHARTS_REFRESH_MIN_GAP_MS + 1;
    }
    const ageH = (ageMs / 3600000).toFixed(1);
    if (!FORCE && ageMs < CHARTS_REFRESH_MIN_GAP_MS) {
        log.banner('Stage 1 · Refresh Charts', {
            'Countries': cfg.ALL_COUNTRIES.map(c => c.toUpperCase()).join(', '),
            'Skipped': `Charts refreshed ${ageH}h ago (threshold 20h) — use --force to override`,
        });
        turso.flushStats();
        return;
    }
    // (st is reused below when saving chartsRefreshedAt)

    log.banner('Stage 1 · Refresh Charts', {
        'Countries': cfg.ALL_COUNTRIES.map(c => c.toUpperCase()).join(', '),
        'Categories': String(cfg.CATEGORIES.length),
        'Chart fetches': String(cfg.ALL_COUNTRIES.length * cfg.CATEGORIES.length),
        ...(FORCE ? { 'Mode': '--force (skip-if-fresh bypassed)' } : {}),
    });

    // --- Read charts in pages (full-table SELECT trips sqld RESPONSE_TOO_LARGE) ---
    log.group('Read existing charts');
    const existing = new Map();
    for (const country of cfg.ALL_COUNTRIES) {
        for (const category of cfg.CATEGORIES) {
            const pageRes = await turso.execute(
                `SELECT country, category, itunes_id, name, artist, image_url, rank
                 FROM charts WHERE country = ? AND category = ?`,
                [country, category],
            );
            for (const [c, cat, itunesId, name, artist, imageUrl, rank] of turso.rows(pageRes)) {
                existing.set(rowKey(c, cat, String(itunesId)), {
                    name: name || '',
                    artist: artist || '',
                    imageUrl: imageUrl || '',
                    rank: parseInt(rank, 10),
                });
            }
        }
    }
    log.info(`Existing chart rows: ${log.fmt(existing.size)} (paged by country×category)`);
    log.endGroup();

    // --- Fetch all charts, spaced ---
    let upserts = 0;
    let deletes = 0;
    let unchanged = 0;
    let failedPairs = 0;
    let consecutiveSoftFails = 0;
    let circuitAborted = false;
    const totalPairs = cfg.ALL_COUNTRIES.length * cfg.CATEGORIES.length;
    const prog = log.progress(totalPairs, 'charts');

    outer:
    for (const country of cfg.ALL_COUNTRIES) {
        log.group(`Country: ${country.toUpperCase()}`);
        for (const category of cfg.CATEGORIES) {
            await new Promise(r => setTimeout(r, ITUNES_SPACING_MS));
            let podcasts;
            try {
                podcasts = await fetchItunesChart(country, category);
            } catch (e) {
                log.warn(`SKIPPING ${country}/${category} entirely (fetch failed: ${e.message}) - no upserts, no deletes`);
                failedPairs++;
                consecutiveSoftFails++;
                prog.tick();
                if (consecutiveSoftFails % ITUNES_CIRCUIT_CONSECUTIVE === 0) {
                    log.warn(
                        `iTunes throttle circuit: ${consecutiveSoftFails} consecutive soft-fails — pausing ${ITUNES_CIRCUIT_PAUSE_MS / 1000}s`,
                    );
                    await new Promise(r => setTimeout(r, ITUNES_CIRCUIT_PAUSE_MS));
                }
                if (consecutiveSoftFails >= ITUNES_CIRCUIT_ABORT_AFTER) {
                    log.error(
                        `iTunes circuit open after ${consecutiveSoftFails} consecutive failures — aborting remaining chart fetches`,
                    );
                    circuitAborted = true;
                    log.endGroup();
                    break outer;
                }
                continue;
            }
            if (podcasts.length === 0) {
                // Empty response is suspicious (charts are never empty); treat like a failure.
                log.warn(`SKIPPING ${country}/${category}: iTunes returned 0 entries`);
                failedPairs++;
                consecutiveSoftFails++;
                prog.tick();
                if (consecutiveSoftFails % ITUNES_CIRCUIT_CONSECUTIVE === 0) {
                    log.warn(
                        `iTunes throttle circuit: ${consecutiveSoftFails} consecutive soft-fails — pausing ${ITUNES_CIRCUIT_PAUSE_MS / 1000}s`,
                    );
                    await new Promise(r => setTimeout(r, ITUNES_CIRCUIT_PAUSE_MS));
                }
                if (consecutiveSoftFails >= ITUNES_CIRCUIT_ABORT_AFTER) {
                    log.error(
                        `iTunes circuit open after ${consecutiveSoftFails} consecutive failures — aborting remaining chart fetches`,
                    );
                    circuitAborted = true;
                    log.endGroup();
                    break outer;
                }
                continue;
            }

            consecutiveSoftFails = 0;

            const statements = [];
            const activeIds = [];
            for (let i = 0; i < podcasts.length; i++) {
                const p = podcasts[i];
                const rank = i + 1;
                activeIds.push(String(p.itunesId));
                const prev = existing.get(rowKey(country, category, String(p.itunesId)));
                if (prev && prev.rank === rank && prev.name === p.name && prev.artist === p.artist && prev.imageUrl === p.imageUrl) {
                    unchanged++;
                    continue;
                }
                statements.push({
                    sql: `INSERT INTO charts (itunes_id, name, artist, image_url, country, category, rank)
                          VALUES (?, ?, ?, ?, ?, ?, ?)
                          ON CONFLICT(itunes_id, country, category) DO UPDATE SET
                              rank = excluded.rank, name = excluded.name,
                              artist = excluded.artist, image_url = excluded.image_url`,
                    args: [p.itunesId, p.name, p.artist, p.imageUrl, country, category, rank],
                });
            }

            // Delete rows that fell off this chart (safe: fetch succeeded above)
            const currentSet = new Set(activeIds);
            let droppedCount = 0;
            for (const key of existing.keys()) {
                const [c, cat, id] = key.split('|');
                if (c === country && cat === category && !currentSet.has(id)) droppedCount++;
            }
            if (droppedCount > 0) {
                const placeholders = activeIds.map(() => '?').join(',');
                statements.push({
                    sql: `DELETE FROM charts WHERE country = ? AND category = ? AND itunes_id NOT IN (${placeholders})`,
                    args: [country, category, ...activeIds],
                });
            }

            if (statements.length > 0) {
                await turso.transaction(statements);
                upserts += statements.length - (droppedCount > 0 ? 1 : 0);
                deletes += droppedCount;
            }
            prog.tick();
        }
        log.endGroup();
    }

    if (circuitAborted || failedPairs > totalPairs / 2) {
        log.error(
            circuitAborted
                ? `iTunes circuit aborted charts (${failedPairs} failed pairs so far / ${totalPairs}) - failing run`
                : `More than half of chart fetches failed (${failedPairs}/${totalPairs}) - failing run`,
        );
        process.exit(1);
    }

    if (failedPairs === 0) {
        // Record charts refresh time in state so stage 3 refreshes its candidate cache
        st.chartsRefreshedAt = Date.now();
        state.save(st);
    } else {
        log.warn(`[CANDIDATES] skipped recording chartsRefreshedAt due to ${failedPairs} failed chart pairs (allowing retry next run)`);
    }

    const stats = turso.getStats();
    log.costFooter('Stage 1 · Refresh Charts', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: totalPairs - failedPairs,
        detail: `${log.fmt(upserts)} upserted · ${log.fmt(deletes)} dropped · ${log.fmt(unchanged)} unchanged · ${failedPairs} pairs skipped`,
    });
    log.summaryTable('Stage 1: Refresh Charts', [{
        stage: 'refresh-charts',
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: totalPairs - failedPairs,
        detail: `${upserts} upserts, ${deletes} deletes, ${failedPairs} failed pairs`,
    }]);
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`refresh-charts failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
