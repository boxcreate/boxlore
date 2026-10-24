#!/usr/bin/env node
'use strict';

/**
 * Stage 3: Sync latest-episode metadata for chart shows from the PI API.
 *
 * - Candidate list cached in sync state; re-queried after charts refresh, after 20h, or when the
 *   core countries differ from those its relaxed tags were computed with (lib/staleness.js).
 *   Countries come from a one-shot charts pre-agg (see lib/chart-countries.js),
 *   not a per-podcast correlated subquery.
 * - Staleness: News 8h · core countries 24h · relaxed (secondary) countries 48h
 *   (±25% jitter). Show on both core+relaxed → 24h. News always 8h.
 * - Fetches max=per-show cap (us/gb 50, else 20) once; items[0] updates Turso;
 *   full items[] written to PI handoff for stage 4 (no second PI call same run).
 * - R5 (R5_MODE, lib/stale-tips.js): afterwards, checked shows the registry flags stale take the
 *   live feed's newest episode as their tip when it is newer than Podcast Index's.
 * - Check cadence (CADENCE_MODE, lib/cadence.js): in shadow, reports what a per-show schedule would
 *   have checked; checks still follow the tiers above.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const pi = require('./lib/podcast-index');
const state = require('./lib/state');
const { tipUpdateArgs } = require('./lib/catalog-rows');
const { bookDb, ensureBookSchema } = require('./lib/episode-book');
const { tipIdentity } = require('./lib/episode-identity');
const cfg = require('./lib/config');
const { handoffList } = require('./lib/vector-window');
const handoff = require('./lib/pi-handoff');
const tipQueue = require('./lib/tip-queue');
const { loadCapsByPodcastId, effectiveCap } = require('./lib/episode-caps');
const {
    CHARTS_MATCH_PODCAST,
    loadCountriesByItunesId,
    mergePodcastRowsWithCountries,
} = require('./lib/chart-countries');
const staleness = require('./lib/staleness');
const staleTips = require('./lib/stale-tips');
const showWindow = require('./lib/show-window');
const cadence = require('./lib/cadence');
const runBudget = require('./lib/run-budget');

const CONCURRENCY = 5;

async function refreshCandidates(st, reason) {
    log.info(`[CANDIDATES] Refreshing candidate list from Turso (${reason})`);
    // Pre-aggregate charts once (~one linear scan). Never use a correlated
    // CAST(c.itunes_id AS INTEGER) subquery — that was multi-billion rows_read.
    const countriesByItunes = await loadCountriesByItunesId(turso);
    const podcastRows = await turso.fetchAllPaged({
        pageSize: cfg.TURSO_PAGE_SIZE,
        rowId: (r) => Number(r[0]),
        buildPage: (after, limit) => ({
            sql: `
                SELECT p.id, p.latest_ep_id, p.categories, p.medium, p.itunes_id
                FROM podcasts p
                WHERE p.itunes_id IS NOT NULL
                  AND p.id > ?
                ORDER BY p.id ASC
                LIMIT ?
            `,
            args: [after == null ? 0 : after, limit],
        }),
    });
    const chartShows = mergePodcastRowsWithCountries(podcastRows, countriesByItunes);
    const ids = [];
    let newsCount = 0;
    let relaxedCount = 0;
    for (const { id: podId, latestEpId, categories, medium, itunesId, countryCsv } of chartShows) {
        ids.push(podId);
        const rec = st.shows[podId] || {};
        if (itunesId) rec.i = itunesId;
        if (rec.e === undefined && latestEpId) rec.e = String(latestEpId);
        if ((categories || '').includes('News')) {
            rec.n = 1;
            newsCount++;
        } else {
            delete rec.n;
        }
        staleness.applyCountryCheckFlag(rec, countryCsv);
        if (rec.x === 1) relaxedCount++;
        if (rec.m === undefined && medium) rec.m = medium;
        st.shows[podId] = rec;
    }
    st.candidateIds = ids;
    st.candidatesRefreshedAt = Date.now();
    st.relaxedTagsCore = staleness.relaxedTagsKey();
    const coreRegular = ids.length - newsCount - relaxedCount;
    log.info(
        `[CANDIDATES] ${log.fmt(ids.length)} chart shows cached · ` +
        `news=${log.fmt(newsCount)} · core(non-news)=${log.fmt(coreRegular)} · ` +
        `relaxed-only(48h)=${log.fmt(relaxedCount)}`,
    );
}

/** Country → how many of `ids` chart there (for sync-plan log). */
async function countDueByCountry(ids) {
    const out = {};
    if (!ids.length) return out;
    const countries = cfg.ALL_COUNTRIES;
    const cPh = countries.map(() => '?').join(',');
    const CHUNK = 400;
    for (let i = 0; i < ids.length; i += CHUNK) {
        const slice = ids.slice(i, i + CHUNK);
        const idPh = slice.map(() => '?').join(',');
        const res = await turso.execute(
            `
            SELECT lower(c.country), COUNT(DISTINCT p.id)
            FROM podcasts p
            INNER JOIN charts c ON ${CHARTS_MATCH_PODCAST}
            WHERE c.country IN (${cPh}) AND p.id IN (${idPh})
            GROUP BY lower(c.country)
            `,
            [...countries, ...slice.map(String)],
        );
        for (const [country, n] of turso.rows(res)) {
            const k = String(country || '?');
            out[k] = (out[k] || 0) + Number(n || 0);
        }
    }
    return out;
}

async function main() {
    const startedMs = Date.now();
    // Hourly runs: stop starting checks at the runner's deadline (lib/run-budget.js).
    const deadline = runBudget.stageDeadline(runBudget.STAGE3_DEADLINE);
    turso.assertEnv();
    const book = bookDb(turso);
    pi.assertEnv();
    turso.beginStep('sync-episodes');
    await turso.healthCheck();

    const st = state.load();
    // Same-run episode payloads only — durable tip lane lives in Turso tip_queue.
    handoff.clear();
    await tipQueue.ensureTable(turso);
    // Book tables and the tip queue's generation column (stage 4 also runs this).
    await ensureBookSchema(book);

    const policy = staleness.policySummary();

    log.banner('Stage 3 · Sync Episodes', {
        'Staleness tiers': policy.label,
        'Core countries (24h)': policy.coreCountries.join(',') || '(none)',
        'Relaxed countries (48h)': policy.relaxedCountries.join(',') || '(none)',
        'Per-run check cap': log.fmt(cfg.MAX_CHECKS_PER_RUN),
        'Episode fetch max': `us/gb ${cfg.EPISODE_CAP_BY_COUNTRY.us} · else ${cfg.EPISODE_CAP_DEFAULT}`,
        'Handoff file': handoff.handoffPath(),
        'Tip queue': tipQueue.TABLE,
        'Concurrency': String(CONCURRENCY),
        'Stale tips (R5)': cfg.R5_MODE,
        'Saved windows': cfg.WINDOWS_MODE,
        'Check cadence': cfg.CADENCE_MODE,
    });

    const cacheAge = Date.now() - (st.candidatesRefreshedAt || 0);
    const refreshReason = staleness.candidateRefreshReason(st, { cadence: cfg.CADENCE_MODE !== 'off' });
    const needRefresh = refreshReason !== null;
    if (needRefresh) {
        await refreshCandidates(st, refreshReason);
    } else {
        log.info(`[CANDIDATES] Using cached list (${log.fmt(st.candidateIds.length)} shows, age ${Math.round(cacheAge / 3600000)}h) - 0 Turso reads`);
    }

    // Cadence shadow: pace from the book about daily, then the plan from the state as it was before
    // this run's checks. Never fails the stage.
    let shadow = null;
    let appleError = null;
    if (cfg.CADENCE_MODE === 'shadow') {
        if (needRefresh || Date.now() - (st.cadenceSeededAt || 0) > staleness.CANDIDATE_CACHE_MAX_AGE_MS) {
            try {
                const seeded = await cadence.seedFromBook(book, st.shows, st.candidateIds);
                st.cadenceSeededAt = Date.now();
                log.info(`[CADENCE] pace seeded from the book for ${log.fmt(seeded)} shows`);
            } catch (e) {
                log.warn(`[CADENCE] pace seed skipped: ${e.message}`);
            }
        }
        const apple = cadence.openApple(cfg.APPLE_LATEST_DB);
        appleError = apple.error;
        if (appleError) log.warn(`[CADENCE] Apple snapshot unread: ${appleError}`);
        try {
            shadow = cadence.shadowPlan(st.candidateIds, st.shows, { appleOf: apple.lookup });
        } catch (e) {
            log.warn(`[CADENCE] shadow plan skipped: ${e.message}`);
        } finally {
            apple.close();
        }
    }
    const newTipIds = [];

    const plan = staleness.planDue(st.candidateIds, st.shows);
    const { allDue, neverChecked, staleNews, staleCore, staleRelaxed,
        poolNews, poolCore, poolRelaxed } = plan;
    const due = allDue.slice(0, cfg.MAX_CHECKS_PER_RUN);
    const deferred = allDue.length - due.length;

    const caps = await loadCapsByPodcastId(due);
    let dueByCountry = {};
    try {
        dueByCountry = await countDueByCountry(due);
    } catch (e) {
        log.warn(`[CANDIDATES] due-by-country breakdown skipped: ${e.message}`);
    }

    log.group('Sync plan');
    log.info(`Check policy:       ${policy.label}`);
    log.info(
        `Chart shows:        ${log.fmt(st.candidateIds.length)} ` +
        `(pool news=${log.fmt(poolNews)} · core=${log.fmt(poolCore)} · relaxed=${log.fmt(poolRelaxed)})`,
    );
    log.info(`Due for check:      ${log.fmt(allDue.length)}`);
    log.info(
        `Checking this run:  ${log.fmt(due.length)} ` +
        `(cap ${log.fmt(cfg.MAX_CHECKS_PER_RUN)}, ${log.fmt(deferred)} deferred to next run)`,
    );
    log.info(`- never checked:           ${log.fmt(neverChecked)}`);
    log.info(`- stale news (${policy.news}):         ${log.fmt(staleNews)}`);
    log.info(`- stale core (${policy.core}):         ${log.fmt(staleCore)}`);
    log.info(`- stale relaxed (${policy.relaxed}):     ${log.fmt(staleRelaxed)}`);
    if (Object.keys(dueByCountry).length) {
        const parts = Object.entries(dueByCountry)
            .sort((a, b) => a[0].localeCompare(b[0]))
            .map(([c, n]) => {
                const cadence = policy.coreCountries.includes(c) ? '24h' : '48h';
                return `${c}=${log.fmt(n)}(${cadence})`;
            });
        log.info(`Due by country (this run): ${parts.join(' · ')}`);
    }
    log.endGroup();

    let updated = 0, unchanged = 0, empty = 0, errors = 0, tipReflags = 0;
    // Podcast Index paused (lib/podcast-index.js): the rest of this run's checks wait for the next run.
    let piPaused = null;
    let checkedBeforePause = 0;
    const windows = { usable: 0, unusable: 0, failed: 0, feed: 0 };

    /** Saves a fetched list as the show's window (WINDOWS_MODE shadow/on). Never fails the show. */
    async function saveWindow(podId, list, cap, source) {
        if (cfg.WINDOWS_MODE === 'off' || !list || !list.length) return false;
        try {
            const r = await showWindow.recordWindow(book, Number(podId), list, {
                cap, gen: Date.now(), now: Math.floor(Date.now() / 1000), source,
            });
            if (r.usable) windows.usable++;
            else windows.unusable++;
            return r.usable;
        } catch (e) {
            windows.failed++;
            log.warn(`Show ${podId}: window not saved (${e.message})`);
            return false;
        }
    }
    const prog = log.progress(due.length, 'episode-sync', 5);

    let timeLeftOver = 0;
    for (let i = 0; i < due.length; i += CONCURRENCY) {
        if (runBudget.timeUp(deadline)) {
            timeLeftOver = due.length - i;
            log.warn(`[BUDGET] Time budget reached: ${log.fmt(timeLeftOver)} checks wait for the next run`);
            break;
        }
        const batch = due.slice(i, i + CONCURRENCY);
        let tipUpdatedThisBatch = false;
        await Promise.all(batch.map(async (podId) => {
            const rec = st.shows[podId] || {};
            // The same cap stage 4 enforces (chart or earned), so the list fills its window.
            const maxEps = effectiveCap(podId, caps, st);
            try {
                // Fast path: most shows are unchanged — probe latest only (max=1).
                // Full cap fetch + handoff only when the tip episode changed.
                const tip = await pi.episodesByFeedId(podId, 1);
                if (tip.length === 0) {
                    empty++;
                    state.recordCheck(st, podId);
                    return;
                }
                const latestTip = tip[0];
                if (rec.e && String(latestTip.id) === String(rec.e)) {
                    unchanged++;
                    const checked = state.recordCheck(st, podId);
                    if (shadow) cadence.noteNewest(checked, latestTip.datePublished);
                    return;
                }

                // New/changed: pull full cap once for Turso + stage-4 handoff
                const full = maxEps > 1 ? await pi.episodesByFeedId(podId, maxEps) : null;
                // Hand stage 4 only a complete list, never the one-item tip (handoffList).
                const window = handoffList(full, maxEps);
                if (window) {
                    handoff.put(podId, window);
                    await saveWindow(podId, window, maxEps, 'pi');
                }
                const episodes = window || tip;
                const latest = episodes[0] || latestTip;
                // Prefer cached medium from import/prior sync; never spend a PI call on it.
                const medium = rec.m || 'podcast';

                // The tip carries the episode's served ID (own-episode-IDs P2); change detection and the
                // tip queue keep PI's own ID.
                let identity = null;
                try {
                    identity = await tipIdentity(book, Number(podId), latest);
                } catch (e) {
                    log.warn(`Show ${podId}: tip identity lookup failed (${e.message}); using its PI ID`);
                }
                await turso.execute(`
                    UPDATE podcasts SET
                        latest_ep_id = ?, latest_ep_title = ?, latest_ep_date = ?,
                        latest_ep_duration = ?, latest_ep_url = ?, latest_ep_image = ?,
                        latest_ep_type = ?, latest_ep_description = ?,
                        latest_ep_chapters_url = ?, latest_ep_transcript_url = ?,
                        latest_ep_persons = ?, latest_ep_transcripts = ?,
                        medium = ?, last_ep_sync = ?, latest_ep_key = ?, latest_ep_eid = ?,
                        qdrant_vectorized = 0
                    WHERE id = ?
                `, tipUpdateArgs(latest, medium, Date.now(), podId, identity));
                await tipQueue.upsert(turso, podId, latest.id);
                updated++;
                tipReflags++;
                tipUpdatedThisBatch = true;
                const checked = state.recordCheck(st, podId, { latestEpId: latest.id });
                checked.m = medium;
                if (shadow) {
                    const knewNewest = checked.p;
                    cadence.notePace(checked, (full || tip).map((ep) => ep.datePublished));
                    if (cadence.isNewEpisode(knewNewest, latest.datePublished)) newTipIds.push(podId);
                }
            } catch (e) {
                if (pi.isSuspended(e)) {
                    piPaused = piPaused || e;
                    return;
                }
                errors++;
                log.warn(`Show ${podId}: ${e.message}`);
            }
        }));
        for (let k = 0; k < batch.length; k++) prog.tick(`new ${updated} / same ${unchanged}`);
        turso.flushStats();
        // Persist tip ids soon after successful UPDATEs so a crash does not re-flag.
        if (tipUpdatedThisBatch || (i / CONCURRENCY) % 20 === 0) state.save(st);
        if (piPaused) {
            // Exact: shows whose check finished (the paused ones stay due).
            checkedBeforePause = updated + unchanged + empty + errors;
            log.warn(`[PI] ${piPaused.message}: ${log.fmt(due.length - checkedBeforePause)} checks wait for the next run`);
            break;
        }
    }

    state.save(st);
    await handoff.flush();

    // Checks that ran this run. When time ran out, R5 reads feeds only for those, until the run's
    // deadline (not stage 3's half: a checked stale show skipped here would wait a whole tier). When
    // Podcast Index paused, R5 still covers every due show, as the feed is then the only source.
    const attempted = piPaused ? checkedBeforePause : due.length - timeLeftOver;
    const r5 = await staleTips.runStaleTips({
        mode: cfg.R5_MODE,
        due: timeLeftOver ? due.slice(0, attempted) : due,
        deadlineMs: runBudget.deadlineFrom(runBudget.RUN_DEADLINE),
        db: turso,
        feedsUrl: cfg.FEEDS_URL,
        registryPath: cfg.REGISTRY_DB,
        mediumOf: (id) => st.shows[id]?.m || 'podcast',
        warn: (msg) => log.warn(msg),
    });
    const r5Line = staleTips.summary(r5);
    if (cfg.R5_MODE !== 'off') log.info(`[R5] ${r5Line}`);

    let cadenceLine = '';
    if (shadow) {
        for (const [id, date] of Object.entries(r5.tipDates || {})) {
            const rec = st.shows[id] || (st.shows[id] = {});
            const knewNewest = rec.p;
            cadence.noteNewest(rec, date);
            if (cadence.isNewEpisode(knewNewest, date)) newTipIds.push(id);
        }
        state.save(st);
        const paceShows = st.candidateIds.filter((id) => st.shows[id]?.g != null).length;
        cadenceLine = cadence.summary(cfg.CADENCE_MODE, shadow, cadence.compare(shadow, allDue, newTipIds), {
            appleError, paceShows, candidates: st.candidateIds.length,
        });
        log.info(`[CADENCE] ${cadenceLine}`);
    }

    // R5 shows whose tip came from the feed also save the feed's merged list as their window, so
    // stage 4 embeds the feed-only episodes (WINDOWS_MODE on flags them; shadow only records).
    if (cfg.WINDOWS_MODE !== 'off' && r5.updatedIds?.length) {
        const flagged = [];
        for (let i = 0; i < r5.updatedIds.length; i += 4) {
            await Promise.all(r5.updatedIds.slice(i, i + 4).map(async (id) => {
                const cap = effectiveCap(id, caps, st);
                let list;
                try {
                    list = await staleTips.feedWindow({ baseUrl: cfg.FEEDS_URL, sid: id, cap });
                } catch (e) {
                    windows.failed++;
                    log.warn(`Show ${id}: feed window not read (${e.message})`);
                    return;
                }
                if (!(await saveWindow(id, list, cap, 'pi'))) return;
                windows.feed++;
                if (cfg.WINDOWS_MODE !== 'on') return;
                try {
                    await turso.execute('UPDATE podcasts SET qdrant_vectorized = 0 WHERE id = ?', [id]);
                    await tipQueue.upsert(turso, id, list[0]?.id ?? null);
                    flagged.push(id);
                } catch (e) {
                    log.warn(`Show ${id}: not flagged for vectors (${e.message})`);
                }
            }));
        }
        // Stage 4 must read the merged window, not this run's Podcast Index list.
        if (flagged.length) {
            handoff.removeMany(flagged);
            await handoff.flush();
        }
    }
    const windowLine = cfg.WINDOWS_MODE === 'off' ? '' :
        `windows ${cfg.WINDOWS_MODE}: ${windows.usable} saved (${windows.feed} from feeds), ${windows.unusable} unusable, ${windows.failed} failed`;
    if (windowLine) log.info(`[WINDOWS] ${windowLine}`);
    turso.flushStats();
    const stats = turso.getStats();
    log.costFooter('Stage 3 · Sync Episodes', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail:
            `${log.fmt(due.length)} checked (${log.fmt(deferred)} deferred) · ` +
            `${updated} new · tip_reflags ${tipReflags} · ${unchanged} unchanged · ${empty} empty · ${errors} errors · ` +
            `tiers news/${policy.news} core/${policy.core} relaxed/${policy.relaxed} · ` +
            `handoff ${handoff.handoffPath()}`,
    });
    log.summaryTable('Stage 3: Sync Episodes', [{
        stage: 'sync-episodes',
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail:
            `${due.length} checked (${deferred} deferred): ${updated} new (tip_reflags=${tipReflags}), ${unchanged} unchanged, ` +
            `${empty} empty, ${errors} errors · due news=${staleNews} core=${staleCore} relaxed=${staleRelaxed}` +
            ` · ${pi.summary()}` +
            (piPaused ? ` · ${due.length - checkedBeforePause} checks left for the next run` : '') +
            (timeLeftOver ? ` · time budget: ${timeLeftOver} checks left for the next run` : '') +
            (cfg.R5_MODE !== 'off' ? ` · ${r5Line}` : '') +
            (windowLine ? ` · ${windowLine}` : '') +
            (cadenceLine ? ` · ${cadenceLine}` : ''),
    }]);

    runBudget.recordMetrics('03-sync-episodes', {
        due: allDue.length,
        checked: updated + unchanged + empty + errors,
        newTips: updated,
        errors,
        leftForNextRun: deferred + timeLeftOver + (piPaused ? due.length - checkedBeforePause : 0),
        timeUp: timeLeftOver > 0,
        ms: Date.now() - startedMs,
    });

    // A pause is "try later", not a failure; only real errors over half the checks fail the stage.
    if (attempted > 20 && errors > attempted / 2) {
        log.error(`More than half of episode checks failed (${errors}/${attempted})`);
        process.exit(1);
    }
}

main()
    .then(() => turso.flushStats())
    .catch(err => {
        log.error(`sync-episodes failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
