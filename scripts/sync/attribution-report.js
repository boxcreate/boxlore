#!/usr/bin/env node
'use strict';

/**
 * Report on the vector attribution log (sync/lib/attribution.js): why the sync
 * embedded what it embedded, what it deleted, and how chart entrants churn.
 *
 *   node sync/attribution-report.js [--days 7] [--json]
 *
 * Read-only. Uses the whole retained log as history and reports on the last
 * `--days` days. Returning shows can only be recognised once their deletion is
 * in the log, so that share is low for the first weeks of logging.
 *
 * Added in P0 of the own-episode-IDs plan (plan section 10b and section 16, item 2).
 */

const attribution = require('./lib/attribution');

function arg(name, fallback) {
    const i = process.argv.indexOf(name);
    return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

function pct(n, total) {
    return total > 0 ? `${((100 * n) / total).toFixed(1)}%` : '-';
}

function median(values) {
    if (values.length === 0) return null;
    const sorted = [...values].sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

function main() {
    const days = Math.max(1, Number(arg('--days', '7')) || 7);
    const to = Math.floor(Date.now() / 1000);
    const from = to - days * 24 * 60 * 60;
    const events = attribution.readAll();
    const first = events.length ? new Date(events[0].t * 1000).toISOString() : null;
    const result = attribution.classify(events, { from, to });
    const totalStored = Object.values(result.stored).reduce((a, b) => a + b, 0);
    const summary = {
        dir: attribution.attributionDir(),
        loggingSince: first,
        windowDays: days,
        episodePointsStored: totalStored,
        storedByReason: result.stored,
        storedByDay: result.byDay,
        episodePointsDeleted: result.deleted,
        showVectorsStored: result.showVectors,
        shows: {
            imported: result.shows.imported,
            returning: result.shows.returning,
            deleted: result.shows.deleted,
            medianStayDays: median(result.shows.staysDays),
        },
    };

    if (process.argv.includes('--json')) {
        console.log(JSON.stringify(summary, null, 2));
        return;
    }

    console.log(`Attribution, last ${days} day(s). Logging since ${first || 'never'}.`);
    console.log(`\nEpisode points stored: ${totalStored.toLocaleString()}`);
    for (const reason of attribution.REASONS) {
        const n = result.stored[reason];
        console.log(`  ${reason.padEnd(16)} ${String(n.toLocaleString()).padStart(9)}  ${pct(n, totalStored)}`);
    }
    console.log('\nEpisode points deleted:');
    for (const [why, n] of Object.entries(result.deleted)) {
        console.log(`  ${why.padEnd(16)} ${String(n.toLocaleString()).padStart(9)}`);
    }
    console.log(`\nShow vectors stored: ${result.showVectors.toLocaleString()}`);
    const s = summary.shows;
    console.log(
        `Shows imported: ${s.imported.toLocaleString()} (returning ${s.returning.toLocaleString()}, ` +
        `${pct(s.returning, s.imported)}) · deleted: ${s.deleted.toLocaleString()} · ` +
        `median stay: ${s.medianStayDays == null ? '-' : `${s.medianStayDays.toFixed(1)} d`}`,
    );
}

main();
