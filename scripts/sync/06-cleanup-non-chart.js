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
 * FTS safety: the podcasts_ad delete trigger does a full FTS-content scan
 * PER ROW, so we drop it, bulk-delete, clean FTS orphans in one scan, and
 * recreate it.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const state = require('./lib/state');
const cfg = require('./lib/config');
const { trimEpisodeCaps } = require('./lib/trim-episode-caps');
const { CHARTS_ITUNES_FIRST_CURSOR } = require('./lib/chart-countries');

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

async function main() {
    turso.assertEnv();
    qdrant.assertEnv();
    turso.beginStep('cleanup-non-chart');
    await turso.healthCheck();

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

    if (toDelete.length === 0) {
        log.info('Nothing past the grace period - done');
        const trim = await runCapTrim();
        log.summaryTable('Stage 6: Cleanup', [{
            stage: 'cleanup-non-chart',
            reads: turso.getStats().reads,
            writes: turso.getStats().writes,
            detail: `0 deleted, ${inGrace} in grace, cap-trim ${trim.deletedPoints} pts${trim.failed ? ' (trim failed)' : ''}`,
        }]);
        if (trim.failed) process.exitCode = 1;
        return;
    }

    // --- Qdrant deletion first (wait=true); only successful chunks go to Turso ---
    log.group('Qdrant vector deletion');
    const qdrantOk = [];
    let qdrantFailedChunks = 0;
    for (let i = 0; i < toDelete.length; i += QDRANT_CHUNK) {
        const chunk = toDelete.slice(i, i + QDRANT_CHUNK);
        const intIds = chunk.map(p => parseInt(p.id, 10)).filter(n => !isNaN(n));
        const uuids = chunk.map(p => qdrant.stableUUID(p.id));
        try {
            await qdrant.deleteByFilter(cfg.EPISODES_COLLECTION, {
                must: [{ key: 'podcast_id', match: { any: intIds } }],
            }, true);
            await qdrant.deleteByIds(cfg.PODCASTS_COLLECTION, uuids, true);
            qdrantOk.push(...chunk);
            log.info(`Deleted vectors for shows ${i + 1}-${Math.min(i + QDRANT_CHUNK, toDelete.length)} of ${toDelete.length}`);
        } catch (e) {
            qdrantFailedChunks++;
            log.error(`Qdrant deletion failed for chunk at ${i}: ${e.message}`);
        }
    }
    log.info(`Qdrant OK: ${log.fmt(qdrantOk.length)} · failed chunks: ${qdrantFailedChunks}`);
    log.endGroup();

    if (qdrantOk.length === 0) {
        log.error('No Qdrant deletions succeeded - skipping Turso deletes (will retry next run)');
        const trim = await runCapTrim();
        log.summaryTable('Stage 6: Cleanup', [{
            stage: 'cleanup-non-chart',
            reads: turso.getStats().reads,
            writes: turso.getStats().writes,
            detail: `0 turso deleted (${toDelete.length} pending qdrant), cap-trim ${trim.deletedPoints} pts`,
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

    const trim = await runCapTrim();

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
        detail: `${qdrantOk.length}/${toDelete.length} shows deleted, ${inGrace} in grace, ${pruned} state pruned, cap-trim ${trim.deletedPoints} pts${trim.failed || qdrantFailedChunks ? ' (partial/errors)' : ''}`,
    }]);

    if (trim.failed || qdrantFailedChunks > 0) process.exitCode = 1;
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`cleanup-non-chart failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
