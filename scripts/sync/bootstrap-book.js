#!/usr/bin/env node
'use strict';

/**
 * Book bootstrap (own-episode-IDs plan P1, section 8): one resumable pass over every show with
 * episode points. Per show: read its points' payload IDs, fetch PI's episode list once, write its
 * book rows in one transaction (served ID = the PI ID the point already has), then stamp `eid` and
 * `catalog_key` on each point (`eid_missing` when unmapped, `eid_shared` when a duplicate feed
 * already holds its PI ID as a served ID). Point IDs and vectors never change.
 *
 *   node sync/bootstrap-book.js [--minutes N] [--dry] [--limit-shows N] [--catch-up | --retry-missing]
 *
 * --catch-up only takes shows that still have points with none of `eid`, `eid_missing` and `eid_shared` (added
 * between the main pass and stage 4 writing identities itself, or by a run whose book write failed).
 * It keeps its own state file, and can be run again any time.
 *
 * --retry-missing (own-episode-IDs P3, monthly) only takes shows with `eid_missing` points and runs the
 * same pass again: Podcast Index or the book may know those episodes by now. A point mapped this time
 * gets its stamp and loses the mark; the rest keep it. Own state file; a finished pass starts over.
 *
 * Progress lives in SYNC_BOOTSTRAP_STATE (default scripts/data/book-bootstrap.json); a show is
 * checkpointed once both writes finished, so a stopped run repeats at most one show (idempotent).
 * Run it under the shared job lock, in chunks (netcup-panel stack/boxlore-sync/run-book-bootstrap.sh).
 */

const fs = require('node:fs');
const path = require('node:path');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const pi = require('./lib/podcast-index');
const log = require('./lib/log');
const cfg = require('./lib/config');
const { ensureBookSchema, bootstrapShow, bookDb } = require('./lib/episode-book');
const { planShow, stampOperations } = require('./lib/book-bootstrap');
const { loadServedOwners } = require('./lib/episode-identity');

const args = process.argv.slice(2);
const opt = (name, def) => {
    const i = args.indexOf(name);
    return i === -1 ? def : args[i + 1];
};
const DRY = args.includes('--dry');
const CATCH_UP = args.includes('--catch-up');
const RETRY = args.includes('--retry-missing');
const MINUTES = Number(opt('--minutes', '20'));
const LIMIT = Number(opt('--limit-shows', '0'));
const STATE = process.env.SYNC_BOOTSTRAP_STATE || path.resolve(__dirname, '../data/book-bootstrap.json');
const STATE_FILE = STATE.replace(/\.json$/, `${CATCH_UP ? '.catchup' : ''}${RETRY ? '.retry' : ''}${DRY ? '.dry' : ''}.json`);
const PI_MAX = 1000;

function loadState() {
    try {
        return JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
    } catch {
        return null;
    }
}

function saveState(st) {
    st.updatedAt = new Date().toISOString();
    fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true });
    const tmp = `${STATE_FILE}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(st));
    fs.renameSync(tmp, STATE_FILE);
}

const UNSTAMPED = { must: [{ is_empty: { key: 'eid' } }, { is_empty: { key: 'eid_missing' } }, { is_empty: { key: 'eid_shared' } }] };
const MISSING = { must: [{ key: 'eid_missing', match: { value: true } }] };

async function listShows() {
    const filter = CATCH_UP ? UNSTAMPED : RETRY ? MISSING : null;
    const points = await qdrant.scrollAll(cfg.EPISODES_COLLECTION, filter, ['podcast_id'], 5000);
    return [...new Set(points.map((p) => Number(p.payload?.podcast_id)).filter((n) => Number.isSafeInteger(n) && n > 0))]
        .sort((a, b) => a - b);
}

async function main() {
    turso.assertEnv();
    qdrant.assertEnv();
    pi.assertEnv();
    turso.beginStep('book-bootstrap');
    const db = bookDb(turso);
    const deadline = Date.now() + MINUTES * 60_000;

    let st = loadState();
    // A finished catch-up or retry pass is history: list its shows again.
    if (st && (CATCH_UP || RETRY) && st.idx >= st.shows.length) st = null;
    if (!st) {
        if (!DRY) await ensureBookSchema(db);
        const shows = await listShows();
        st = {
            startedAt: new Date().toISOString(), shows, idx: 0,
            showsDone: 0, points: 0, stamped: 0, missing: 0, duplicates: 0, rows: 0,
            shared: 0, piErrors: 0, bookErrors: 0, failedShows: [],
        };
        saveState(st);
        const what = CATCH_UP ? 'with unstamped points' : RETRY ? 'with eid_missing points' : 'with episode points';
        log.info(`Bootstrap${CATCH_UP ? ' catch-up' : RETRY ? ' retry' : ''}: ${shows.length} shows ${what}`);
    }
    if (!DRY) await qdrant.ensurePayloadIndex(cfg.EPISODES_COLLECTION, 'eid', 'integer');

    const end = LIMIT ? Math.min(st.shows.length, st.idx + LIMIT) : st.shows.length;
    const from = st.idx;
    while (st.idx < end && Date.now() < deadline) {
        const sid = st.shows[st.idx];
        const points = await qdrant.scrollAll(cfg.EPISODES_COLLECTION,
            { must: [{ key: 'podcast_id', match: { value: sid } }] }, ['id'], 500);
        let items;
        try {
            items = await pi.episodesByFeedId(sid, PI_MAX);
        } catch (e) {
            st.piErrors++;
            st.failedShows.push({ sid, why: `pi: ${String(e.message).slice(0, 120)}` });
            st.idx++;
            saveState(st);
            continue;
        }
        // PI IDs another show already serves (duplicate feeds) are left to that show. A dry run on a
        // database without the book yet has no owners.
        let owners = new Map();
        try {
            owners = await loadServedOwners(db, points.map((p) => Number(p.payload?.id)).filter((n) => Number.isSafeInteger(n) && n > 0));
        } catch (e) {
            if (!(DRY && /no such table: episode_book/i.test(String(e.message)))) throw e;
        }
        const plan = planShow(sid, points, items, owners);
        if (!DRY && points.length) {
            const res = await bootstrapShow(db, sid, plan.items, { now: Math.floor(Date.now() / 1000) });
            if (!res.ok) {
                st.bookErrors++;
                st.failedShows.push({ sid, why: `book: ${String(res.error).slice(0, 120)}` });
                st.idx++;
                saveState(st);
                continue;
            }
            const ops = stampOperations(plan);
            for (let i = 0; i < ops.length; i += 500) {
                await qdrant.request(`/collections/${cfg.EPISODES_COLLECTION}/points/batch?wait=true`, {
                    method: 'POST',
                    body: JSON.stringify({ operations: ops.slice(i, i + 500) }),
                });
            }
        }
        st.showsDone++;
        st.points += points.length;
        st.stamped += plan.stamps.length;
        st.missing += plan.missing.length;
        st.duplicates += plan.duplicates;
        st.shared = (st.shared || 0) + plan.shared.length;
        st.rows += plan.items.length;
        st.idx++;
        saveState(st);
    }
    turso.flushStats();
    const pct = st.points ? ((100 * st.stamped) / st.points).toFixed(2) : '0';
    log.info(`${DRY ? 'DRY ' : ''}Bootstrap shows ${from}..${st.idx} of ${st.shows.length}: ` +
        `${st.points} points, ${st.stamped} mapped (${pct}%), ${st.missing} missing, ${st.duplicates} duplicates, ` +
        `${st.shared || 0} shared with a duplicate feed, ` +
        `${st.rows} rows, PI errors ${st.piErrors}, book errors ${st.bookErrors}, PI calls ${pi.getApiCallCount()}`);
    if (st.idx >= st.shows.length) log.info(CATCH_UP ? 'Bootstrap catch-up complete' : RETRY ? 'Bootstrap retry complete' : 'Bootstrap complete');
}

main().catch((e) => {
    log.error(e.stack || e.message);
    process.exitCode = 1;
});
