#!/usr/bin/env node
'use strict';

/**
 * Stage 2: Ensure every chart show exists in the Turso podcasts table.
 *
 * Modes:
 *   --precheck  Compute missing chart shows (union across all countries),
 *               exclude PI-unavailable denylist (TTL), write
 *               missing_itunes_ids.txt for the dump-export step, and
 *               set GHA outputs need_dump / missing_count.
 *   --import    If podcasts_export.csv exists (dump path), bulk-import it;
 *               denylist dump-misses, then try PI API (capped) for them.
 *               Without CSV, fetch missing shows from the PI API (capped).
 *               Fixed column whitelist - NO auto-ALTER.
 */

const fs = require('fs');
const log = require('./lib/log');
const turso = require('./lib/turso');
const pi = require('./lib/podcast-index');
const state = require('./lib/state');
const cfg = require('./lib/config');
const piUnavailable = require('./lib/pi-unavailable');
const { parseCSVLine, parseCSVRecords } = require('./lib/csv');

const MISSING_IDS_FILE = 'missing_itunes_ids.txt';
const CSV_FILE = 'podcasts_export.csv';

const GENRE_PRIORITY = [
    'Technology', 'News', 'Business', 'Science', 'Sports', 'True Crime',
    'History', 'Comedy', 'Arts', 'Fiction', 'Music', 'Religion & Spirituality',
    'Kids & Family', 'Government', 'Health', 'TV & Film', 'Education',
];

function sortCategories(rawCats) {
    if (!rawCats) return rawCats;
    return rawCats.split(',')
        .map(c => c.trim())
        .filter(Boolean)
        .sort((a, b) => {
            const ia = GENRE_PRIORITY.indexOf(a);
            const ib = GENRE_PRIORITY.indexOf(b);
            if (ia !== -1 && ib !== -1) return ia - ib;
            if (ia !== -1) return -1;
            if (ib !== -1) return 1;
            return a.localeCompare(b);
        })
        .join(', ');
}

function loadDenylist() {
    const doc = piUnavailable.load();
    const pruned = piUnavailable.pruneExpired(doc);
    if (pruned > 0) {
        log.info(`PI-unavailable denylist: pruned ${log.fmt(pruned)} expired entr(y/ies)`);
        piUnavailable.save(doc);
    }
    return doc;
}

/** Missing chart itunes ids (string-normalized set difference), minus denylist. */
async function computeMissing({ denylistDoc } = {}) {
    const chartsRes = await turso.execute(
        'SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE itunes_id IS NOT NULL'
    );
    const chartIds = turso.rows(chartsRes).map(r => String(r[0])).filter(Boolean);

    const podsRes = await turso.execute(
        'SELECT DISTINCT itunes_id FROM podcasts WHERE itunes_id IS NOT NULL'
    );
    const existing = new Set(turso.rows(podsRes).map(r => String(r[0])));

    const blocked = piUnavailable.activeIdSet(denylistDoc || loadDenylist());
    const missingRaw = chartIds.filter(id => !existing.has(id));
    const missing = missingRaw.filter(id => !blocked.has(id));
    return {
        chartIds,
        missing,
        missingRawCount: missingRaw.length,
        denylistSuppressed: missingRaw.length - missing.length,
    };
}

function readMissingIdsFile() {
    if (!fs.existsSync(MISSING_IDS_FILE)) return [];
    return fs.readFileSync(MISSING_IDS_FILE, 'utf-8')
        .split(/\r?\n/)
        .map(s => s.trim())
        .filter(Boolean);
}

async function precheck() {
    const denylistDoc = loadDenylist();
    const { chartIds, missing, missingRawCount, denylistSuppressed } = await computeMissing({ denylistDoc });
    log.info(`Chart shows (union all countries): ${log.fmt(chartIds.length)}`);
    log.info(`Missing from podcasts table:       ${log.fmt(missingRawCount)}`);
    log.info(`Suppressed by PI-unavailable list: ${log.fmt(denylistSuppressed)}`);
    log.info(`Actionable missing (after denylist): ${log.fmt(missing.length)}`);

    const needDump = missing.length > cfg.DUMP_THRESHOLD;
    fs.writeFileSync(MISSING_IDS_FILE, missing.join('\n') + (missing.length ? '\n' : ''));
    log.info(`Decision: need_dump=${needDump} (threshold ${cfg.DUMP_THRESHOLD})`);

    if (process.env.GITHUB_OUTPUT) {
        fs.appendFileSync(process.env.GITHUB_OUTPUT, `need_dump=${needDump}\n`);
        fs.appendFileSync(process.env.GITHUB_OUTPUT, `missing_count=${missing.length}\n`);
    }
}

async function importFromCSV() {
    const content = fs.readFileSync(CSV_FILE, 'utf-8');
    // Quote-aware record split: unquoted mid-field newlines (legacy dumps)
    // must not create short rows that bind the wrong number of Turso args.
    const records = parseCSVRecords(content);
    if (records.length < 2) {
        log.info('CSV contains no data rows - nothing to import');
        return { count: 0, ids: [], itunesIds: new Set() };
    }

    const headers = parseCSVLine(records[0]);
    // Enforce the fixed whitelist: every CSV header must be a known column.
    const unknown = headers.filter(h => !cfg.PODCAST_IMPORT_COLUMNS.includes(h));
    if (unknown.length > 0) {
        throw new Error(`CSV contains non-whitelisted columns: ${unknown.join(', ')} - refusing to import (no auto-ALTER)`);
    }

    const idIdx = headers.indexOf('id');
    const itunesIdx = headers.indexOf('itunes_id');
    const catIdx = headers.indexOf('categories');
    const dataRecords = records.slice(1);
    log.info(`Importing ${log.fmt(dataRecords.length)} rows from ${CSV_FILE}`);

    const BATCH = 50;
    let imported = 0;
    let skipped = 0;
    const ids = [];
    const itunesIds = new Set();
    const placeholders = headers.map(() => '?').join(',');
    const insertSql = `INSERT OR IGNORE INTO podcasts (${headers.join(',')}, qdrant_vectorized, qdrant_podcast_vectorized)
                      VALUES (${placeholders}, 0, 0)`;
    const prog = log.progress(dataRecords.length, 'csv-import');

    for (let i = 0; i < dataRecords.length; i += BATCH) {
        const batchRecords = dataRecords.slice(i, i + BATCH);
        const statements = [];
        for (const record of batchRecords) {
            const values = parseCSVLine(record);
            if (values.length !== headers.length) {
                skipped++;
                log.warn(
                    `Skipping CSV row with ${values.length} fields (expected ${headers.length}): `
                    + `${record.slice(0, 80)}${record.length > 80 ? '…' : ''}`
                );
                prog.tick();
                continue;
            }
            if (idIdx !== -1 && values[idIdx]) ids.push(String(values[idIdx]));
            if (itunesIdx !== -1 && values[itunesIdx]) itunesIds.add(String(values[itunesIdx]));
            if (catIdx !== -1 && values[catIdx]) {
                values[catIdx] = sortCategories(values[catIdx]);
            }
            statements.push({ sql: insertSql, args: values });
            prog.tick();
        }
        if (statements.length > 0) {
            await turso.batch(statements);
            imported += statements.length;
        }
    }
    if (skipped > 0) {
        log.warn(`Skipped ${log.fmt(skipped)} malformed CSV row(s); imported ${log.fmt(imported)}`);
    }
    return { count: imported, ids, itunesIds };
}

/**
 * @param {string[]|null} itunesIds  If set, import these; else actionable missing from Turso.
 * @returns {{ count: number, ids: string[], foundItunesIds: string[], notFoundIds: string[] }}
 */
async function importFromAPI(itunesIds = null) {
    pi.assertEnv();
    let missing;
    if (itunesIds) {
        missing = itunesIds.map(String).filter(Boolean);
    } else {
        const computed = await computeMissing();
        missing = computed.missing;
        if (computed.denylistSuppressed > 0) {
            log.info(`Skipping ${log.fmt(computed.denylistSuppressed)} denylisted iTunes ID(s)`);
        }
    }
    if (missing.length === 0) {
        log.info('All chart shows already present - nothing to import');
        return { count: 0, ids: [], foundItunesIds: [], notFoundIds: [] };
    }

    const toImport = missing.slice(0, cfg.API_IMPORT_CAP);
    if (missing.length > cfg.API_IMPORT_CAP) {
        log.warn(`${missing.length} shows missing; importing first ${cfg.API_IMPORT_CAP} via API (rest handled by denylist / next run)`);
    }
    log.info(`Fetching ${toImport.length} shows from the PI API`);

    const cols = cfg.PODCAST_IMPORT_COLUMNS;
    const placeholders = cols.map(() => '?').join(',');
    let imported = 0;
    const ids = [];
    const foundItunesIds = [];
    const notFoundIds = [];
    const statements = [];
    const prog = log.progress(toImport.length, 'api-import');

    for (const itunesId of toImport) {
        let feed = null;
        try {
            feed = await pi.podcastByItunesId(itunesId);
        } catch (e) {
            log.warn(`PI lookup failed for itunes_id=${itunesId}: ${e.message}`);
        }
        prog.tick();
        if (!feed) {
            notFoundIds.push(String(itunesId));
            continue;
        }

        ids.push(String(feed.id));
        foundItunesIds.push(String(itunesId));
        const categoriesStr = feed.categories
            ? sortCategories(Object.values(feed.categories).join(', '))
            : '';
        statements.push({
            sql: `INSERT INTO podcasts (${cols.join(',')}, qdrant_vectorized, qdrant_podcast_vectorized)
                  VALUES (${placeholders}, 0, 0)
                  ON CONFLICT(id) DO UPDATE SET itunes_id = excluded.itunes_id
                  WHERE itunes_id IS NULL OR itunes_id != excluded.itunes_id`,
            args: [
                feed.id,
                feed.itunesId || itunesId,
                feed.title || 'Unknown Title',
                feed.author || 'Unknown Author',
                (feed.description || '').substring(0, 1000),
                feed.image || feed.artwork || '',
                feed.url || '',
                feed.link || '',
                categoriesStr,
                feed.language || 'en',
                feed.explicit ? '1' : '0',
                feed.itunesType || 'episodic',
            ],
        });
        imported++;

        if (statements.length >= 50) {
            await turso.batch(statements.splice(0));
        }
    }
    if (statements.length > 0) await turso.batch(statements);

    log.info(`API import done: ${imported} imported, ${notFoundIds.length} not found on PI`);
    return { count: imported, ids, foundItunesIds, notFoundIds };
}

/**
 * Dump-misses: denylist so need_dump stops thrashing, then try API (capped).
 * API hits remove from denylist; confirmed misses upgrade reason to api_not_found.
 */
async function handleDumpUnmatched(unmatched) {
    if (unmatched.length === 0) return { count: 0, ids: [] };

    const doc = loadDenylist();
    const added = piUnavailable.add(doc, unmatched, 'not_in_dump');
    log.info(
        `Denylisted ${log.fmt(unmatched.length)} dump-miss iTunes ID(s) `
        + `(${log.fmt(added)} new) — will not count toward need_dump until TTL`
    );

    const apiResult = await importFromAPI(unmatched);
    if (apiResult.foundItunesIds.length > 0) {
        piUnavailable.remove(doc, apiResult.foundItunesIds);
        log.info(`Removed ${log.fmt(apiResult.foundItunesIds.length)} denylist entr(y/ies) after API hit`);
    }
    if (apiResult.notFoundIds.length > 0) {
        piUnavailable.add(doc, apiResult.notFoundIds, 'api_not_found');
    }
    // Over-cap dump-misses stay as not_in_dump (already added above).
    piUnavailable.save(doc);
    log.info(`PI-unavailable denylist size: ${log.fmt(Object.keys(doc.ids).length)}`);
    return { count: apiResult.count, ids: apiResult.ids };
}

function applyApiNotFoundDenylist(notFoundIds) {
    if (notFoundIds.length === 0) return;
    const doc = loadDenylist();
    const added = piUnavailable.add(doc, notFoundIds, 'api_not_found');
    piUnavailable.save(doc);
    log.info(
        `Denylisted ${log.fmt(notFoundIds.length)} API-not-found iTunes ID(s) `
        + `(${log.fmt(added)} new); denylist size ${log.fmt(Object.keys(doc.ids).length)}`
    );
}

/**
 * Append newly imported podcast IDs to stage 3's candidate list.
 * Per-show sync history (last check time, latest ep id, etc.) is untouched.
 */
function registerImportedCandidates(importedIds) {
    if (importedIds.length === 0) return;

    const st = state.load();
    if (!st.candidateIds) {
        // First run or legacy state — let stage 3 build the full list from Turso.
        st.candidatesRefreshedAt = 0;
        state.save(st);
        log.info('[CANDIDATES] No candidate list yet — stage 3 will refresh from Turso');
        return;
    }

    st.candidateIds = (st.candidateIds || []).map(String);
    const known = new Set(st.candidateIds);
    let added = 0;
    for (const podId of importedIds) {
        const id = String(podId);
        if (known.has(id)) continue;
        st.candidateIds.push(id);
        known.add(id);
        if (!st.shows[id]) st.shows[id] = {};
        added++;
    }
    if (added === 0) return;

    st.candidatesRefreshedAt = 0; // Force Stage 3 to refresh from Turso and seed 'n' (News) flags
    state.save(st);
    log.info(`[CANDIDATES] Registered ${added} newly imported shows for episode sync (existing check history unchanged)`);
}

async function main() {
    turso.assertEnv();
    turso.beginStep('import-podcasts');
    await turso.healthCheck();

    if (process.argv.includes('--precheck')) {
        log.banner('Stage 2a · Pre-check Missing Shows', {
            'Dump threshold': String(cfg.DUMP_THRESHOLD),
            'Denylist TTL days': String(Math.round(cfg.PI_UNAVAILABLE_TTL_MS / (24 * 60 * 60 * 1000))),
        });
        await precheck();
        return;
    }

    let imported = 0;
    let importedIds = [];
    let mode;
    if (fs.existsSync(CSV_FILE)) {
        mode = 'dump';
        log.banner('Stage 2 · Import Podcasts', { 'Mode': 'bulk (PI dump CSV)' });
        log.group('Bulk import from PI dump CSV');
        const result = await importFromCSV();
        imported = result.count;
        importedIds = result.ids;
        log.endGroup();

        const requested = readMissingIdsFile();
        const unmatched = requested.filter(id => !result.itunesIds.has(String(id)));
        if (unmatched.length > 0) {
            log.group('Dump-miss follow-up (denylist + API)');
            log.info(
                `${log.fmt(unmatched.length)} of ${log.fmt(requested.length)} requested `
                + 'iTunes ID(s) were not in the dump export'
            );
            const follow = await handleDumpUnmatched(unmatched);
            imported += follow.count;
            importedIds = importedIds.concat(follow.ids);
            log.endGroup();
        }
    } else {
        mode = 'api';
        log.banner('Stage 2 · Import Podcasts', {
            'Mode': 'incremental (PI API)',
            'Per-run cap': String(cfg.API_IMPORT_CAP),
        });
        log.group('Incremental import via PI API');
        const result = await importFromAPI();
        imported = result.count;
        importedIds = result.ids;
        applyApiNotFoundDenylist(result.notFoundIds);
        log.endGroup();
    }

    registerImportedCandidates(importedIds);

    const stats = turso.getStats();
    log.costFooter('Stage 2 · Import Podcasts', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail: `${log.fmt(imported)} shows imported (${mode})`,
    });
    log.summaryTable('Stage 2: Import Podcasts', [{
        stage: `import-podcasts (${mode})`,
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail: `${imported} shows imported`,
    }]);
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`import-podcasts failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
