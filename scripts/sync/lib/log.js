'use strict';

/**
 * GHA-friendly logging: collapsible groups, milestone progress, annotations,
 * and a rich end-of-run Step Summary table.
 */

const fs = require('fs');

const IS_GHA = !!process.env.GITHUB_ACTIONS;

function info(msg) {
    console.log(msg);
}

function warn(msg) {
    if (IS_GHA) console.log(`::warning::${msg}`);
    else console.warn(`[WARN] ${msg}`);
}

function error(msg) {
    if (IS_GHA) console.log(`::error::${msg}`);
    else console.error(`[ERROR] ${msg}`);
}

function group(title) {
    if (IS_GHA) console.log(`::group::${title}`);
    else console.log(`\n=== ${title} ===`);
}

function endGroup() {
    if (IS_GHA) console.log('::endgroup::');
}

/**
 * Boxed stage banner with aligned key-value config lines.
 * banner('Stage 4: Vectorize Episodes', { Budget: '3,000', Pending: '3,586' })
 */
function banner(title, kvPairs = {}) {
    const line = '━'.repeat(64);
    console.log('');
    console.log(line);
    console.log(`  ${title}`);
    const entries = Object.entries(kvPairs);
    if (entries.length > 0) {
        console.log('  ' + '─'.repeat(60));
        for (const [k, v] of entries) {
            console.log(`  ${(k + ':').padEnd(22)} ${v}`);
        }
    }
    console.log(line);
}

/** Consistent end-of-stage cost/result footer. */
function costFooter(stage, { reads, writes, apiCalls, detail } = {}) {
    const line = '─'.repeat(64);
    console.log('');
    console.log(line);
    const parts = [`Turso ${fmt(reads || 0)} reads · ${fmt(writes || 0)} writes`];
    if (apiCalls) parts.push(`API ${fmt(apiCalls)} calls`);
    console.log(`  ${stage} done · ${parts.join(' · ')}`);
    if (detail) console.log(`  ${detail}`);
    console.log(line);
}

/**
 * Milestone progress logger with a text bar. Call tick() per item; prints at
 * most every `intervalPct` percent (default 10%) with rate + ETA.
 */
function progress(total, label, intervalPct = 10) {
    const start = Date.now();
    let lastPct = -1;
    let done = 0;
    return {
        tick(extra = '') {
            done++;
            const pct = total > 0 ? Math.floor((done / total) * 100) : 100;
            if (pct >= lastPct + intervalPct || done === total) {
                lastPct = pct - (pct % intervalPct);
                const elapsed = (Date.now() - start) / 1000;
                const rate = done / Math.max(elapsed, 0.001);
                const eta = done < total ? Math.round((total - done) / Math.max(rate, 0.001)) : 0;
                const filled = Math.round(pct / 10);
                const bar = '▰'.repeat(filled) + '▱'.repeat(10 - filled);
                info(`  ${bar} ${String(pct).padStart(3)}%  ${label} ${done}/${total} · ${rate.toFixed(1)}/s · ETA ${eta}s${extra ? ' · ' + extra : ''}`);
            }
        },
        get count() { return done; },
    };
}

/**
 * Budget-based run progress for embedding stages. Call tick() per successful
 * embed; prints [RUN] bar at every `interval` items or 5% of budget.
 */
function budgetProgress(budget, label, interval = 50) {
    const start = Date.now();
    let done = 0;
    let lastPrinted = 0;
    const milestone = Math.max(1, Math.min(interval, Math.floor(budget * 5 / 100)));

    function print(force = false) {
        if (!force && done < budget && done - lastPrinted < milestone) return;
        lastPrinted = done;
        // Clamp: with tiny budgets a single show can embed past the cap before
        // the outer loop breaks; unclamped pct made '▱'.repeat(10 - filled) throw
        // "Invalid count value: -N" and fail the whole stage.
        const pct = budget > 0
            ? Math.min(100, Math.floor((done / budget) * 100))
            : 100;
        const elapsed = (Date.now() - start) / 1000;
        const rate = done / Math.max(elapsed, 0.001);
        const etaSec = done < budget ? Math.round((budget - done) / Math.max(rate, 0.001)) : 0;
        const filled = Math.max(0, Math.min(10, Math.round(pct / 10)));
        const bar = '▰'.repeat(filled) + '▱'.repeat(10 - filled);
        const etaStr = etaSec >= 60 ? duration(etaSec * 1000) : `${etaSec}s`;
        info(`[RUN]     ${bar} ${fmt(done)}/${fmt(budget)} ${label} · ${rate.toFixed(1)}/s · ETA ${etaStr}`);
    }

    return {
        tick() {
            done++;
            print();
        },
        flush() { print(true); },
        get count() { return done; },
    };
}

/** Overall backlog context line for long-running vectorize stages. */
function backlogStatus({ scanned, pending, budgetLeft, skipped, extra }) {
    const parts = [
        `${fmt(scanned)} shows scanned`,
        `${fmt(pending)} pending`,
        `${fmt(budgetLeft)} budget left`,
        `${fmt(skipped)} skipped (already in Qdrant)`,
    ];
    if (extra) parts.push(extra);
    info(`[BACKLOG] ${parts.join(' · ')}`);
}

/** Append markdown to the GHA step summary (no-op locally). */
function stepSummary(markdown) {
    if (process.env.GITHUB_STEP_SUMMARY) {
        try {
            fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, markdown + '\n');
        } catch (e) {
            warn(`Failed to write step summary: ${e.message}`);
        }
    }
}

/**
 * Render a stage-result row table for the step summary.
 * rows: [{ stage, duration, apiCalls, reads, writes, detail }]
 */
function summaryTable(title, rows) {
    let md = `\n### ${title}\n\n`;
    md += '| Stage | Duration | API calls | DB reads | DB writes | Detail |\n';
    md += '| :--- | ---: | ---: | ---: | ---: | :--- |\n';
    for (const r of rows) {
        md += `| ${r.stage} | ${r.duration || '-'} | ${fmt(r.apiCalls)} | ${fmt(r.reads)} | ${fmt(r.writes)} | ${r.detail || ''} |\n`;
    }
    stepSummary(md);
}

function fmt(n) {
    return typeof n === 'number' ? n.toLocaleString() : (n || '-');
}

function duration(ms) {
    if (ms < 1000) return `${ms}ms`;
    const s = ms / 1000;
    if (s < 60) return `${s.toFixed(1)}s`;
    const m = Math.floor(s / 60);
    return `${m}m ${Math.round(s - m * 60)}s`;
}

module.exports = {
    info, warn, error, group, endGroup, banner, costFooter,
    progress, budgetProgress, backlogStatus,
    stepSummary, summaryTable, duration, fmt,
};
