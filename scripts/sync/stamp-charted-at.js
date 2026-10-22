#!/usr/bin/env node
'use strict';

/**
 * One-off and catch-up: stamps every Qdrant point with its show's last chart day (`charted_at`,
 * lib/charted-at.js, own-episode-IDs P3b). Stage 6 keeps the dates current afterwards.
 *
 * Reads the shows in the catalog and the cleanup's chart clock (state `shows[id].s`); never
 * changes the state, the catalog or any vector. Points that already have a date of the show's day
 * are skipped, so it can be stopped and run again. Run it under the shared job lock.
 *
 *   node sync/stamp-charted-at.js --dry   # count the points that need a date, write nothing
 *   node sync/stamp-charted-at.js         # stamp them
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const state = require('./lib/state');
const cfg = require('./lib/config');
const chartedAt = require('./lib/charted-at');

async function main() {
    const dry = process.argv.includes('--dry');
    turso.assertEnv();
    qdrant.assertEnv();
    turso.beginStep('stamp-charted-at');
    log.banner('Stamp last-charted dates', { Mode: dry ? 'dry run (counts only)' : 'stamp' });

    const rows = await turso.fetchAllPaged({
        pageSize: cfg.TURSO_PAGE_SIZE,
        rowId: (r) => Number(r[0]),
        buildPage: (after, limit) => ({
            sql: 'SELECT id FROM podcasts WHERE id > ? ORDER BY id ASC LIMIT ?',
            args: [after == null ? 0 : after, limit],
        }),
    });
    const st = state.load();
    const pods = rows.map((r) => ({ id: String(r[0]) }));
    const batches = chartedAt.planBatches(pods, st.shows);
    const withClock = pods.filter((p) => st.shows[p.id]?.s > 0).length;
    log.info(`Shows: ${log.fmt(pods.length)} in the catalog, ${log.fmt(withClock)} with a chart day, ${batches.length} batches`);

    if (!dry) {
        for (const c of chartedAt.COLLECTIONS) await qdrant.ensurePayloadIndex(c.name, chartedAt.FIELD, 'integer');
    } else {
        // A dry run creates nothing: without the index, counts filter unindexed, which a
        // collection in strict mode refuses (each refused call is counted as failed).
        for (const c of chartedAt.COLLECTIONS) {
            const schema = await qdrant.payloadSchema(c.name);
            if (schema && !schema[chartedAt.FIELD]) {
                log.warn(`[CHARTED_AT] ${c.name} has no ${chartedAt.FIELD} index yet (the stamp run creates it); counting without it`);
            }
        }
    }
    const started = Date.now();
    const r = await chartedAt.stamp(qdrant, batches, {
        collections: chartedAt.COLLECTIONS,
        refreshSec: cfg.CHARTED_AT_REFRESH_DAYS * chartedAt.DAY_S,
        dry,
        warn: (m) => log.warn(m),
    });
    log.info(`[CHARTED_AT] ${chartedAt.summary(r)} (${Math.round((Date.now() - started) / 1000)} s)`);
    turso.flushStats();
    if (r.failed) process.exitCode = 1;
}

main().catch((e) => {
    log.error(`stamp-charted-at failed: ${e.message}`);
    process.exit(1);
});
