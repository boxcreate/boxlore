#!/usr/bin/env node
'use strict';

/**
 * Stage 6 (20:00 run only): Delete shows that have been absent from ALL
 * country charts for CLEANUP_GRACE_DAYS consecutive days, then hard-trim
 * every remaining show in Qdrant to EPISODES_PER_SHOW (latest by date).
 *
 * Grace period prevents delete/re-vectorize thrash: a show dipping out of
 * the top 200 for a day keeps its Turso row and Qdrant vectors; only
 * sustained absence triggers deletion. Last-seen timestamps live in the git
 * state file (shows[id].s) - zero Turso cost.
 *
 * Reliability:
 * - Qdrant deletes use wait=true before Turso rows are removed.
 * - Only shows whose Qdrant chunk succeeded are deleted from Turso (no
 *   all-or-nothing abort that leaves grace clocks stuck).
 * - Episode-cap trim is best-effort: failure is logged but does not roll
 *   back a successful non-chart cleanup.
 *
 * Last-charted dates (lib/charted-at.js, P3b): before deleting, every kept show's Qdrant points
 * get the day it was last seen on a chart. Best-effort: a failure never blocks the cleanup.
 *
 * Retention (lib/retention.js, RETENTION_MODE, P3b): with `on`, a show past the grace keeps its
 * newest ARCHIVE_EPISODES episode points and its show vector (its catalog row still goes), and
 * points last charted more than RETENTION_DAYS ago are deleted, shorter while the index is over
 * the size ceiling. `count` keeps today's deletes and reports what `on` would do.
 *
 * FTS safety: the podcasts_ad delete trigger does a full FTS-content scan
 * PER ROW, so we drop it, bulk-delete, clean FTS orphans in one scan, and
 * recreate it.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const state = require('./lib/state');
const cfg = require('./lib/config');
const tipQueue = require('./lib/tip-queue');
const handoff = require('./lib/pi-handoff');
const { trimEpisodeCaps } = require('./lib/trim-episode-caps');
const { CHARTS_ITUNES_FIRST_CURSOR } = require('./lib/chart-countries');
const attribution = require('./lib/attribution');
const chartedAt = require('./lib/charted-at');
const retention = require('./lib/retention');
const fs = require('fs');
const { spawnSync } = require('child_process');

const QDRANT_CHUNK = 500;
const DELETE_CHUNK = 200;

async function runCapTrim() {
    try {
        const trim = await trimEpisodeCaps();
        log.info(
            `Episode cap trim: ${log.fmt(trim.trimmedShows)} shows · ${log.fmt(trim.deletedPoints)} points removed`
        );
        return trim;
    } catch (e) {
        log.error(`Episode cap trim failed (non-fatal): ${e.message}`);
        return { showsScanned: 0, trimmedShows: 0, deletedPoints: 0, failed: true };
    }
}

/** Stamps kept shows' points with their last chart day. Never throws; returns the report line. */
async function stampChartedAt(pods, shows) {
    try {
        for (const c of chartedAt.COLLECTIONS) await qdrant.ensurePayloadIndex(c.name, chartedAt.FIELD, 'integer');
        const r = await chartedAt.stamp(qdrant, chartedAt.planBatches(pods, shows), {
            collections: chartedAt.COLLECTIONS,
            refreshSec: cfg.CHARTED_AT_REFRESH_DAYS * chartedAt.DAY_S,
            warn: (m) => log.warn(m),
        });
        const line = chartedAt.summary(r);
        log.info(`[CHARTED_AT] ${line}`);
        return line;
    } catch (e) {
        log.warn(`[CHARTED_AT] skipped: ${e.message}`);
        return 'charted_at skipped';
    }
}

/** Urgent Telegram alert, or its all-clear; skipped when the notifier isn't installed. */
function notify(args) {
    if (!fs.existsSync(cfg.NOTIFY_SCRIPT)) return;
    const r = spawnSync('python3', [cfg.NOTIFY_SCRIPT, ...args], { stdio: 'ignore', timeout: 30_000 });
    if (r.status !== 0) log.warn(`[RETENTION] notifier exited ${r.status ?? r.error?.message}`);
}

/** Expired points and the size guard. Never throws; returns the report line. */
async function runRetention(archive) {
    const mode = cfg.RETENTION_MODE;
    if (mode === 'off') return retention.summary(mode);
    try {
        const r = await retention.enforce(qdrant, {
            mode,
            nowSec: Math.floor(Date.now() / 1000),
            steps: retention.retentionSteps(),
            ceiling: cfg.QDRANT_POINTS_CEILING,
            alert: cfg.QDRANT_POINTS_ALERT,
            collections: [cfg.EPISODES_COLLECTION, cfg.PODCASTS_COLLECTION],
            onExpired: async (collection, filter) => {
                if (collection !== cfg.EPISODES_COLLECTION) return;
                const perShow = await qdrant.facetCounts(collection, 'podcast_id', filter, 100_000).catch(() => null);
                if (perShow) for (const [pid, n] of perShow) attribution.pointsExpired(pid, n);
                attribution.flush();
            },
        });
        const line = retention.summary(mode, archive, r);
        log.info(`[RETENTION] ${line}`);
        if (r.overAlert) {
            log.warn(`[RETENTION] ${log.fmt(r.total)} episode points: over the alert level of ${log.fmt(cfg.QDRANT_POINTS_ALERT)}`);
        }
        if (r.overCeiling) {
            notify(['--alert', 'qdrant-size', 'Qdrant is over its size ceiling',
                `${r.total} episode points (ceiling ${cfg.QDRANT_POINTS_CEILING}) after retention ${r.retentionDays} days`]);
        } else {
            notify(['--resolve', 'qdrant-size', `Qdrant back under its size ceiling (${r.total} episode points)`]);
        }
        return line;
    } catch (e) {
        log.warn(`[RETENTION] skipped: ${e.message}`);
        return 'retention skipped';
    }
}

/** The cap trim, then retention: the tail of every cleanup run. */
async function finish(archive) {
    const trim = await runCapTrim();
    const retentionLine = await runRetention(archive);
    return { trim, retentionLine };
}

async function main() {
    turso.assertEnv();
    qdrant.assertEnv();
    turso.beginStep('cleanup-non-chart');
    await turso.healthCheck();
    await tipQueue.ensureTable(turso);

    try {
        await qdrant.ensurePayloadIndex(cfg.EPISODES_COLLECTION, 'podcast_id', 'integer');
    } catch (e) {
        log.warn(`Could not ensure episodes podcast_id index: ${e.message}`);
    }

    log.banner('Stage 6 · Cleanup Non-chart Shows', {
        'Grace period': `${cfg.CLEANUP_GRACE_DAYS} days`,
        'Safety floor': `${log.fmt(cfg.CLEANUP_SAFETY_MIN_CHARTS)} chart shows`,
    });

    // --- Safety: charts must look healthy ---
    const chartCountRes = await turso.execute(
        'SELECT COUNT(DISTINCT itunes_id) FROM charts WHERE itunes_id IS NOT NULL'
    );
    const activeChartCount = parseInt(turso.scalar(chartCountRes), 10) || 0;
    log.info(`Active chart shows: ${log.fmt(activeChartCount)}`);
    if (activeChartCount < cfg.CLEANUP_SAFETY_MIN_CHARTS) {
        log.error(`Safety abort: chart count ${activeChartCount} < ${cfg.CLEANUP_SAFETY_MIN_CHARTS} - charts table may be broken`);
        process.exit(1);
    }

    // --- Compute chart membership + grace clock (paged) ---
    const chartIdRows = await turso.fetchAllPaged({
        pageSize: cfg.TURSO_PAGE_SIZE,
        rowId: (r) => String(r[0]),
        buildPage: (after, limit) => ({
            sql: `
                SELECT DISTINCT itunes_id
                FROM charts
                WHERE itunes_id IS NOT NULL
                  AND itunes_id > ?
                ORDER BY itunes_id ASC
                LIMIT ?
            `,
            args: [after == null ? CHARTS_ITUNES_FIRST_CURSOR : after, limit],
        }),
    });
    const chartItunesIds = new Set(chartIdRows.map(r => String(r[0])));

    const podRows = await turso.fetchAllPaged({
        pageSize: cfg.TURSO_PAGE_SIZE,
        rowId: (r) => Number(r[0]),
        buildPage: (after, limit) => ({
            sql: `
                SELECT id, itunes_id, title
                FROM podcasts
                WHERE id > ?
                ORDER BY id ASC
                LIMIT ?
            `,
            args: [after == null ? 0 : after, limit],
        }),
    });
    const allPods = podRows.map(r => ({
        id: String(r[0]),
        itunesId: r[1] !== null ? String(r[1]) : null,
        title: r[2] || 'Unknown',
    }));
    log.info(`Podcasts in DB: ${log.fmt(allPods.length)}`);

    const st = state.load();
    const now = Date.now();
    const graceMs = cfg.CLEANUP_GRACE_DAYS * 24 * 60 * 60 * 1000;
    const toDelete = [];
    let inCharts = 0;
    let inGrace = 0;

    for (const pod of allPods) {
        const rec = st.shows[pod.id] || (st.shows[pod.id] = {});
        if (pod.itunesId && chartItunesIds.has(pod.itunesId)) {
            rec.s = now;
            inCharts++;
        } else if (!rec.s) {
            rec.s = now; // start the grace clock
            inGrace++;
        } else if (now - rec.s > graceMs) {
            toDelete.push(pod);
        } else {
            inGrace++;
        }
    }

    log.group('Cleanup plan');
    log.info(`In charts:                ${log.fmt(inCharts)}`);
    log.info(`In grace period (<${cfg.CLEANUP_GRACE_DAYS}d):   ${log.fmt(inGrace)}`);
    log.info(`Past grace - deleting:    ${log.fmt(toDelete.length)}`);
    log.endGroup();

    // Persist grace clocks even when nothing is deleted yet.
    state.save(st);

    // Last-charted dates for the shows that stay (points of shows deleted below go anyway).
    const deleting = new Set(toDelete.map(p => p.id));
    const chartedLine = await stampChartedAt(allPods.filter(p => !deleting.has(p.id)), st.shows);

    if (toDelete.length === 0) {
        log.info('Nothing past the grace period - done');
        const { trim, retentionLine } = await finish({});
        log.summaryTable('Stage 6: Cleanup', [{
            stage: 'cleanup-non-chart',
            reads: turso.getStats().reads,
            writes: turso.getStats().writes,
            detail: `0 deleted, ${inGrace} in grace, cap-trim ${trim.deletedPoints} pts${trim.failed ? ' (trim failed)' : ''} · ${chartedLine} · ${retentionLine}`,
        }]);
        if (trim.failed) process.exitCode = 1;
        return;
    }

    // --- Qdrant deletion first (wait=true); only successful chunks go to Turso ---
    const archiving = cfg.RETENTION_MODE === 'on';
    log.group(archiving ? `Qdrant: keep each show's newest ${cfg.ARCHIVE_EPISODES} episodes` : 'Qdrant vector deletion');
    const qdrantOk = [];
    let qdrantFailedChunks = 0;
    // Departed shows kept (on) or that would be kept (count), and their episodes kept and deleted.
    const archive = { archived: 0, kept: 0, trimmed: 0 };
    for (let i = 0; i < toDelete.length; i += QDRANT_CHUNK) {
        const chunk = toDelete.slice(i, i + QDRANT_CHUNK);
        const intIds = chunk.map(p => parseInt(p.id, 10)).filter(n => !isNaN(n));
        const uuids = chunk.map(p => qdrant.stableUUID(p.id));
        const chunkFilter = { must: [{ key: 'podcast_id', match: { any: intIds } }] };
        // Points per show, for the attribution log only.
        let pointCounts = null;
        try {
            pointCounts = await qdrant.facetCounts(cfg.EPISODES_COLLECTION, 'podcast_id', chunkFilter, intIds.length);
        } catch {
            pointCounts = null;
        }
        try {
            if (archiving) {
                const kept = await retention.archiveShows(qdrant, intIds, { keep: cfg.ARCHIVE_EPISODES });
                // Kept points carry the exact day the show was last on a chart.
                await chartedAt.stamp(qdrant, chartedAt.planBatches(chunk, st.shows), {
                    collections: chartedAt.COLLECTIONS, refreshSec: 0, warn: (m) => log.warn(m),
                });
                qdrantOk.push(...chunk);
                for (const p of chunk) {
                    const r = kept.get(String(p.id)) || { kept: 0, dropped: [] };
                    archive.archived++;
                    archive.kept += r.kept;
                    archive.trimmed += r.dropped.length;
                    attribution.showDeleted(p.id, {
                        points: r.dropped.length, kept: r.kept, lastSeen: st.shows[p.id]?.s, itunesId: p.itunesId,
                    });
                }
            } else {
                await qdrant.deleteByFilter(cfg.EPISODES_COLLECTION, chunkFilter, true);
                await qdrant.deleteByIds(cfg.PODCASTS_COLLECTION, uuids, true);
                qdrantOk.push(...chunk);
                for (const p of chunk) {
                    const n = pointCounts ? (pointCounts.get(String(p.id)) || 0) : undefined;
                    if (n !== undefined) {
                        archive.archived++;
                        archive.kept += Math.min(n, cfg.ARCHIVE_EPISODES);
                        archive.trimmed += Math.max(0, n - cfg.ARCHIVE_EPISODES);
                    }
                    attribution.showDeleted(p.id, { points: n, lastSeen: st.shows[p.id]?.s, itunesId: p.itunesId });
                }
            }
            attribution.flush();
            log.info(`${archiving ? 'Kept newest episodes' : 'Deleted vectors'} for shows ${i + 1}-${Math.min(i + QDRANT_CHUNK, toDelete.length)} of ${toDelete.length}`);
        } catch (e) {
            qdrantFailedChunks++;
            log.error(`Qdrant deletion failed for chunk at ${i}: ${e.message}`);
        }
    }
    log.info(`Qdrant OK: ${log.fmt(qdrantOk.length)} · failed chunks: ${qdrantFailedChunks}`);
    log.endGroup();

    if (qdrantOk.length === 0) {
        log.error('No Qdrant deletions succeeded - skipping Turso deletes (will retry next run)');
        const { trim, retentionLine } = await finish(archive);
        log.summaryTable('Stage 6: Cleanup', [{
            stage: 'cleanup-non-chart',
            reads: turso.getStats().reads,
            writes: turso.getStats().writes,
            detail: `0 turso deleted (${toDelete.length} pending qdrant), cap-trim ${trim.deletedPoints} pts · ${chartedLine} · ${retentionLine}`,
        }]);
        process.exitCode = 1;
        return;
    }

    if (qdrantOk.length < toDelete.length) {
        log.warn(
            `Partial Qdrant success: Turso-deleting ${qdrantOk.length}/${toDelete.length} shows; remainder stays for next run`
        );
    }

    // --- Turso deletion with trigger workaround (only Qdrant-confirmed ids) ---
    log.group('Turso deletion');
    try {
        log.info('Dropping podcasts_ad trigger (prevents per-row FTS scans)');
        await turso.execute('DROP TRIGGER IF EXISTS podcasts_ad');

        const ids = qdrantOk.map(p => p.id);
        for (let i = 0; i < ids.length; i += DELETE_CHUNK) {
            const chunk = ids.slice(i, i + DELETE_CHUNK);
            const placeholders = chunk.map(() => '?').join(',');
            await turso.execute(`DELETE FROM podcasts WHERE id IN (${placeholders})`, chunk);
        }
        log.info(`Deleted ${log.fmt(ids.length)} podcast rows`);

        log.info('Cleaning tip-queue + handoff for deleted shows');
        await tipQueue.removeMany(turso, ids);
        handoff.removeMany(ids);
        try {
            await handoff.flush();
        } catch {
            // best-effort
        }

        log.info('Cleaning FTS orphans (single scan)');
        await turso.execute('DELETE FROM podcasts_fts WHERE podcast_id NOT IN (SELECT id FROM podcasts)');
    } finally {
        // Always restore the trigger, even if deletion failed midway.
        await turso.execute(`
            CREATE TRIGGER IF NOT EXISTS podcasts_ad AFTER DELETE ON podcasts BEGIN
                DELETE FROM podcasts_fts WHERE podcast_id = old.id;
            END
        `);
        log.info('podcasts_ad trigger restored');
    }
    log.endGroup();

    // --- Prune state for Turso-deleted shows only ---
    const deletedIds = new Set(qdrantOk.map(p => p.id));
    const keepIds = allPods.filter(p => !deletedIds.has(p.id)).map(p => p.id);
    const pruned = state.pruneShows(st, keepIds);
    if (st.candidateIds) {
        st.candidateIds = st.candidateIds.filter(id => !deletedIds.has(id));
    }
    state.save(st);
    log.info(`State pruned: ${pruned} dead entries removed`);

    const { trim, retentionLine } = await finish(archive);

    const stats = turso.getStats();
    log.costFooter('Stage 6 · Cleanup', {
        reads: stats.reads,
        writes: stats.writes,
        detail: `${log.fmt(qdrantOk.length)}/${log.fmt(toDelete.length)} shows deleted · ${log.fmt(inGrace)} in grace · ${pruned} state · cap-trim ${trim.deletedPoints} pts`,
    });
    log.summaryTable('Stage 6: Cleanup', [{
        stage: 'cleanup-non-chart',
        reads: stats.reads,
        writes: stats.writes,
        detail: `${qdrantOk.length}/${toDelete.length} shows deleted, ${inGrace} in grace, ${pruned} state pruned, cap-trim ${trim.deletedPoints} pts${trim.failed || qdrantFailedChunks ? ' (partial/errors)' : ''} · ${chartedLine} · ${retentionLine}`,
    }]);

    if (trim.failed || qdrantFailedChunks > 0) process.exitCode = 1;
}

main()
    .then(() => {
        turso.flushStats();
        attribution.flush();
    })
    .catch(err => {
        log.error(`cleanup-non-chart failed: ${err.message}`);
        turso.flushStats();
        attribution.flush();
        process.exit(1);
    });
