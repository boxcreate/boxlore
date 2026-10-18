'use strict';

/**
 * Vector attribution log: records why the sync spends embeddings.
 *
 * One JSON line per episode point stored or deleted, per show vector stored,
 * and per show imported or deleted. `classify()` (used by
 * sync/attribution-report.js) turns the log into reasons: new episodes, first
 * coverage of a show, returning shows, re-embeds after a cap trim or prune,
 * and Podcast Index episode ID changes.
 *
 * Files are `<dir>/<UTC date>.jsonl`, appended whenever a stage flushes.
 * `dir` is SYNC_ATTRIBUTION_DIR, default `scripts/data/attribution` (gitignored).
 * Older days are gzipped; days past RETENTION_DAYS are deleted. Logging is best
 * effort: a failure warns once and never fails a stage.
 *
 * Event fields (short keys keep ~100k lines a day small):
 *   t    epoch seconds          run  SYNC_STARTED_AT of the run
 *   st   stage ('2','4','5','6')
 *   ev   'ep+' episode point stored, 'ep-' episode point deleted,
 *        'sv+' show vector stored, 'show+' show imported, 'show-' show deleted
 *   pid  podcast ID             eid  Podcast Index episode ID
 *   rid  recipe ID from (pid, catalog key), when the episode has a key
 *   pub  episode publish time (epoch seconds)
 *   ah   first 12 hex of SHA-1(audio URL), to match an episode across ID changes
 *   q    stage 4 lane: 'tip' (tip queue), 'inc' (show already has a vector), 'cold'
 *   why  deletion reason: 'prune' (window prune), 'cap' (cap trim), 'cleanup' (show removed)
 *   n    points deleted with a show      seen  last time the show was on a chart (ms)
 *   it   iTunes ID                       via   import path ('dump' | 'api')
 *
 * Added in P0 of the own-episode-IDs plan (plan section 10b, attribution).
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const log = require('./log');
const recipe = require('./episode-recipe-id');

const RETENTION_DAYS = 60;
const DAY_S = 24 * 60 * 60;
const NEW_EPISODE_WINDOW_S = 3 * DAY_S;

const REASONS = [
    're-embed-cap',
    're-embed-prune',
    'id-change',
    'new-episode',
    'returning-show',
    'first-coverage',
    'backfill',
];

let buffer = [];
let warned = false;
let housekept = false;

function attributionDir() {
    return process.env.SYNC_ATTRIBUTION_DIR || path.resolve(__dirname, '../../data/attribution');
}

function nowS() {
    return Math.floor(Date.now() / 1000);
}

function audioHash(url) {
    if (typeof url !== 'string' || url === '') return undefined;
    return crypto.createHash('sha1').update(url).digest('hex').slice(0, 12);
}

function posInt(value) {
    const n = Number(value);
    return Number.isSafeInteger(n) && n > 0 ? n : undefined;
}

function record(event) {
    const out = { t: nowS(), run: process.env.SYNC_STARTED_AT || undefined, ...event };
    for (const k of Object.keys(out)) if (out[k] === undefined || out[k] === null) delete out[k];
    buffer.push(out);
}

/** A stage 4 episode point that Qdrant confirmed it stored. `ep` is the Podcast Index episode. */
function episodeStored(pid, ep, queue) {
    let rid;
    try {
        rid = recipe.recipeIdForItem(posInt(pid) ?? 0, ep.guid, ep.enclosureUrl) ?? undefined;
    } catch {
        rid = undefined;
    }
    record({
        st: '4',
        ev: 'ep+',
        pid: posInt(pid),
        eid: posInt(ep.id),
        rid,
        pub: posInt(ep.datePublished),
        ah: audioHash(ep.enclosureUrl),
        q: queue,
    });
}

/** An episode point deleted. `point` is { eid, pub, audioUrl } from its payload. */
function episodeDeleted(stage, pid, point, why) {
    record({
        st: stage,
        ev: 'ep-',
        pid: posInt(pid),
        eid: posInt(point.eid),
        pub: posInt(point.pub),
        ah: audioHash(point.audioUrl),
        why,
    });
}

function showVectorStored(pid) {
    record({ st: '5', ev: 'sv+', pid: posInt(pid) });
}

function showImported(pid, { itunesId, via } = {}) {
    record({ st: '2', ev: 'show+', pid: posInt(pid), it: posInt(itunesId), via });
}

function showDeleted(pid, { points, lastSeen, itunesId } = {}) {
    record({
        st: '6',
        ev: 'show-',
        pid: posInt(pid),
        n: Number.isSafeInteger(points) ? points : undefined,
        seen: posInt(lastSeen),
        it: posInt(itunesId),
    });
}

function utcDay(tSeconds) {
    return new Date(tSeconds * 1000).toISOString().slice(0, 10);
}

/** gzip finished days and delete expired ones; at most once per process. */
function housekeep(dir, today) {
    if (housekept) return;
    housekept = true;
    const cutoff = utcDay(nowS() - RETENTION_DAYS * DAY_S);
    for (const name of fs.readdirSync(dir)) {
        const m = /^(\d{4}-\d{2}-\d{2})\.jsonl(\.gz)?$/.exec(name);
        if (!m) continue;
        const file = path.join(dir, name);
        if (m[1] < cutoff) {
            fs.rmSync(file, { force: true });
        } else if (!m[2] && m[1] < today && !fs.existsSync(`${file}.gz`)) {
            fs.writeFileSync(`${file}.gz`, zlib.gzipSync(fs.readFileSync(file)));
            fs.rmSync(file, { force: true });
        }
    }
}

/** Appends buffered events to their day files. Safe to call often. */
function flush() {
    if (buffer.length === 0) return 0;
    const events = buffer;
    buffer = [];
    try {
        const dir = attributionDir();
        fs.mkdirSync(dir, { recursive: true });
        const byDay = new Map();
        for (const e of events) {
            const day = utcDay(e.t);
            if (!byDay.has(day)) byDay.set(day, []);
            byDay.get(day).push(JSON.stringify(e));
        }
        for (const [day, lines] of byDay) {
            fs.appendFileSync(path.join(dir, `${day}.jsonl`), `${lines.join('\n')}\n`);
        }
        housekeep(dir, utcDay(nowS()));
        return events.length;
    } catch (e) {
        if (!warned) {
            warned = true;
            log.warn(`Attribution log write failed (non-fatal): ${e.message}`);
        }
        return 0;
    }
}

/**
 * Reads retained day files, oldest first.
 * @param {{ dir?: string, fromDay?: string, pids?: Set<number> }} [opts]
 *   fromDay: skip files before this UTC date; pids: keep only these shows' events
 */
function readAll({ dir = attributionDir(), fromDay = '', pids = null } = {}) {
    if (!fs.existsSync(dir)) return [];
    const names = fs.readdirSync(dir)
        .filter((n) => /^\d{4}-\d{2}-\d{2}\.jsonl(\.gz)?$/.test(n) && n.slice(0, 10) >= fromDay)
        .sort();
    const events = [];
    for (const name of names) {
        const raw = fs.readFileSync(path.join(dir, name));
        const textData = name.endsWith('.gz') ? zlib.gunzipSync(raw).toString('utf8') : raw.toString('utf8');
        for (const line of textData.split('\n')) {
            if (!line) continue;
            if (pids) {
                const m = /"pid":(\d+)/.exec(line);
                if (!m || !pids.has(Number(m[1]))) continue;
            }
            try {
                events.push(JSON.parse(line));
            } catch {
                // a torn line from a crashed append
            }
        }
    }
    events.sort((a, b) => a.t - b.t);
    return events;
}

/**
 * Classifies every episode point stored in [from, to) using everything logged before it.
 *
 * Order matters: a re-embed of the same Podcast Index ID beats everything; then an ID
 * change (same show and audio URL, or the same publish time when the deleted point's
 * audio wasn't logged, under a new ID); then a genuinely new episode; then a show that
 * was deleted earlier; then a show's first coverage; else backfill. Returning shows
 * deleted before logging started count as first coverage.
 *
 * @returns {{ stored: object, byDay: object, deleted: object, shows: object, showVectors: number }}
 */
function classify(events, { from = 0, to = Infinity } = {}) {
    const deletedEp = new Map(); // pid:eid -> why
    const deletedAudio = new Set(); // pid:ah
    const deletedPubNoAudio = new Set(); // pid:pub, for deletions logged without audio
    const showDeletedAt = new Map(); // pid -> t
    const showImportedAt = new Map(); // pid -> t

    const stored = Object.fromEntries(REASONS.map((r) => [r, 0]));
    const byDay = {};
    const deleted = { prune: 0, cap: 0, cleanup: 0 };
    const shows = { imported: 0, returning: 0, deleted: 0, staysDays: [] };
    let showVectors = 0;

    for (const e of events) {
        const inWindow = e.t >= from && e.t < to;
        if (e.ev === 'ep+') {
            if (inWindow) {
                let why;
                const prior = deletedEp.get(`${e.pid}:${e.eid}`);
                if (prior) why = `re-embed-${prior}`;
                else if ((e.ah && deletedAudio.has(`${e.pid}:${e.ah}`)) || (e.pub && deletedPubNoAudio.has(`${e.pid}:${e.pub}`))) why = 'id-change';
                else if (e.pub && e.t - e.pub <= NEW_EPISODE_WINDOW_S) why = 'new-episode';
                else if (showDeletedAt.has(e.pid)) why = 'returning-show';
                else if (e.q === 'cold') why = 'first-coverage';
                else why = 'backfill';
                stored[why]++;
                const day = utcDay(e.t);
                byDay[day] ??= Object.fromEntries(REASONS.map((r) => [r, 0]));
                byDay[day][why]++;
            }
            deletedEp.delete(`${e.pid}:${e.eid}`);
            if (e.ah) deletedAudio.delete(`${e.pid}:${e.ah}`);
            if (e.pub) deletedPubNoAudio.delete(`${e.pid}:${e.pub}`);
        } else if (e.ev === 'ep-') {
            if (inWindow && e.why in deleted) deleted[e.why]++;
            deletedEp.set(`${e.pid}:${e.eid}`, e.why);
            if (e.ah) deletedAudio.add(`${e.pid}:${e.ah}`);
            else if (e.pub) deletedPubNoAudio.add(`${e.pid}:${e.pub}`);
        } else if (e.ev === 'show-') {
            if (inWindow) {
                shows.deleted++;
                deleted.cleanup += e.n || 0;
                if (showImportedAt.has(e.pid)) shows.staysDays.push((e.t - showImportedAt.get(e.pid)) / DAY_S);
            }
            showDeletedAt.set(e.pid, e.t);
            showImportedAt.delete(e.pid);
        } else if (e.ev === 'show+') {
            if (inWindow) {
                shows.imported++;
                if (showDeletedAt.has(e.pid)) shows.returning++;
            }
            showImportedAt.set(e.pid, e.t);
        } else if (e.ev === 'sv+' && inWindow) {
            showVectors++;
        }
    }
    return { stored, byDay, deleted, shows, showVectors };
}

/**
 * Reasons for the episode points stored since `since` (this run), as the report line
 * "reasons new-episode=12 first-coverage=40". Reads only the last `lookbackDays` of
 * the log, and only events for `pids`, so it stays cheap inside a sync stage.
 * @returns {{ counts: Record<string, number>, text: string }}
 */
function runReasons({ since, pids, lookbackDays = 30, dir } = {}) {
    if (!pids || pids.size === 0) return { counts: {}, text: '' };
    const events = readAll({ dir, fromDay: utcDay(since - lookbackDays * DAY_S), pids });
    const { stored } = classify(events, { from: since });
    const counts = Object.fromEntries(Object.entries(stored).filter(([, n]) => n > 0));
    const pairs = Object.entries(counts).map(([k, n]) => `${k}=${n}`);
    return { counts, text: pairs.length ? `reasons ${pairs.join(' ')}` : '' };
}

function resetForTests() {
    buffer = [];
    warned = false;
    housekept = false;
}

module.exports = {
    RETENTION_DAYS,
    REASONS,
    attributionDir,
    episodeStored,
    episodeDeleted,
    showVectorStored,
    showImported,
    showDeleted,
    flush,
    readAll,
    classify,
    runReasons,
    resetForTests,
};
