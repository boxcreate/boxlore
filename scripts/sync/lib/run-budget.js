'use strict';

/**
 * Per-run time budget and metrics (own-episode-IDs P3b, hourly runs).
 *
 * The VPS runner (netcup-panel `boxlore-sync/run-sync.sh`) gives each run a time budget once it
 * holds the shared job lock: 30 minutes for hourly runs, 45 for the nightly chart and cleanup
 * runs. It passes two deadlines as epoch seconds:
 *   SYNC_STAGE3_DEADLINE_AT — stage 3 stops checking shows (half the budget);
 *   SYNC_DEADLINE_AT        — stage 4 stops embedding.
 * Work left over (shows still due, backlog) waits for the next run, so runs stay even instead of
 * one long run after a quiet one. Unset deadlines mean no limit (manual runs, CI).
 *
 * Stages append one JSON line of counts to SYNC_METRICS_FILE for the runner's usage log.
 */

const fs = require('fs');

const STAGE3_DEADLINE = 'SYNC_STAGE3_DEADLINE_AT';
const RUN_DEADLINE = 'SYNC_DEADLINE_AT';

/** Deadline in epoch ms from an env var holding epoch seconds; Infinity when unset or invalid. */
function deadlineFrom(name, env = process.env) {
    const raw = String(env[name] ?? '').trim();
    const s = /^[0-9]+$/.test(raw) ? Number(raw) : 0;
    return s > 0 ? s * 1000 : Infinity;
}

/** The earlier of the stage's own deadline and the run's. */
function stageDeadline(stageVar, env = process.env) {
    return Math.min(deadlineFrom(stageVar, env), deadlineFrom(RUN_DEADLINE, env));
}

function timeUp(deadlineMs, now = Date.now()) {
    return now >= deadlineMs;
}

/** Appends one metrics line for the runner's usage log. Never throws. */
function recordMetrics(stage, data, env = process.env) {
    const file = env.SYNC_METRICS_FILE;
    if (!file) return false;
    try {
        fs.appendFileSync(file, `${JSON.stringify({ stage, t: Math.floor(Date.now() / 1000), ...data })}\n`);
        return true;
    } catch {
        return false;
    }
}

module.exports = { STAGE3_DEADLINE, RUN_DEADLINE, deadlineFrom, stageDeadline, timeUp, recordMetrics };
