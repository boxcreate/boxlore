#!/usr/bin/env node
'use strict';

/**
 * Stage 3: Sync latest-episode metadata for chart shows from the PI API.
 *
 * - Candidate list cached in sync state; re-queried after charts refresh or 20h.
 * - Staleness: News 8h · core countries 24h · relaxed (secondary) countries 48h
 *   (±10% jitter). Show on both core+relaxed → 24h. News always 8h.
 * - Fetches max=per-show cap (us/gb 50, else 20) once; items[0] updates Turso;
 *   full items[] written to PI handoff for stage 4 (no second PI call same run).
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const pi = require('./lib/podcast-index');
const state = require('./lib/state');
const text = require('./lib/text');
const cfg = require('./lib/config');
const handoff = require('./lib/pi-handoff');
const { loadCapsByPodcastId } = require('./lib/episode-caps');
const staleness = require('./lib/staleness');

const CANDIDATE_CACHE_MAX_AGE_MS = 20 * 60 * 60 * 1000;
const CONCURRENCY = 5;

async function refreshCandidates(st) {
    log.info('[CANDIDATES] Refreshing candidate list from Turso');
    // Keyset-page podcasts on charts; keep per-show country CSV for core/relaxed staleness.
    const pendingRows = await turso.fetchAllPaged({
        pageSize: cfg.TURSO_PAGE_SIZE,
        rowId: (r) => Number(r[0]),
        buildPage: (after, limit) => ({
            sql: `
                SELECT p.id, p.latest_ep_id, p.categories, p.medium,
                       (SELECT GROUP_CONCAT(DISTINCT lower(c.country))
                        FROM charts c
                        WHERE CAST(c.itunes_id AS INTEGER) = p.itunes_id)
                FROM podcasts p
                WHERE p.itunes_id IN (
                    SELECT DISTINCT CAST(itunes_id AS INTEGER) FROM charts WHERE itunes_id IS NOT NULL
                )
                  AND p.id > ?
                ORDER BY p.id ASC
                LIMIT ?
            `,
            args: [after == null ? 0 : after, limit],
        }),
    });
    const ids = [];
    let newsCount = 0;
    let relaxedCount = 0;
    for (const [id, latestEpId, categories, medium, countryCsv] of pendingRows) {
        const podId = String(id);
        ids.push(podId);
        const rec = st.shows[podId] || {};
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
            INNER JOIN charts c ON CAST(c.itunes_id AS INTEGER) = p.itunes_id
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
    turso.assertEnv();
    pi.assertEnv();
    turso.beginStep('sync-episodes');
    await turso.healthCheck();

    const st = state.load();
    handoff.clear();

    const policy = staleness.policySummary();

    log.banner('Stage 3 · Sync Episodes', {
        'Staleness tiers': policy.label,
        'Core countries (24h)': policy.coreCountries.join(',') || '(none)',
        'Relaxed countries (48h)': policy.relaxedCountries.join(',') || '(none)',
        'Per-run check cap': log.fmt(cfg.MAX_CHECKS_PER_RUN),
        'Episode fetch max': `us/gb ${cfg.EPISODE_CAP_BY_COUNTRY.us} · else ${cfg.EPISODE_CAP_DEFAULT}`,
        'Handoff file': handoff.handoffPath(),
        'Concurrency': String(CONCURRENCY),
    });

    const cacheAge = Date.now() - (st.candidatesRefreshedAt || 0);
    const chartsNewerThanCache = (st.chartsRefreshedAt || 0) > (st.candidatesRefreshedAt || 0);
    let needRefresh = !st.candidateIds || chartsNewerThanCache || cacheAge > CANDIDATE_CACHE_MAX_AGE_MS;
    if (!needRefresh && st.candidateIds && cfg.RELAXED_CHECK_COUNTRIES.length > 0) {
        let missingX = 0;
        for (const id of st.candidateIds) {
            const rec = st.shows[id];
            if (rec && rec.x === undefined && rec.n !== 1) missingX++;
        }
        // One-shot: apply relaxed(48h) tags without waiting for charts / 20h cache expiry
        if (missingX > Math.max(50, st.candidateIds.length * 0.05)) {
            log.info(
                `[CANDIDATES] Refreshing to apply relaxed(48h) tags ` +
                `(${log.fmt(missingX)} non-news shows untagged)`,
            );
            needRefresh = true;
        }
    }
    if (needRefresh) {
        await refreshCandidates(st);
    } else {
        log.info(`[CANDIDATES] Using cached list (${log.fmt(st.candidateIds.length)} shows, age ${Math.round(cacheAge / 3600000)}h) - 0 Turso reads`);
    }

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

    let updated = 0, unchanged = 0, empty = 0, errors = 0;
    const prog = log.progress(due.length, 'episode-sync', 5);

    for (let i = 0; i < due.length; i += CONCURRENCY) {
        const batch = due.slice(i, i + CONCURRENCY);
        await Promise.all(batch.map(async (podId) => {
            const rec = st.shows[podId] || {};
            const maxEps = caps.get(podId) || cfg.EPISODE_CAP_DEFAULT;
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
                    state.recordCheck(st, podId);
                    return;
                }

                // New/changed: pull full cap once for Turso + stage-4 handoff
                let episodes = tip;
                if (maxEps > 1) {
                    episodes = await pi.episodesByFeedId(podId, maxEps);
                    if (episodes.length === 0) episodes = tip;
                }
                handoff.put(podId, episodes);
                const latest = episodes[0] || latestTip;
                // Prefer cached medium from import/prior sync; never spend a PI call on it.
                const medium = rec.m || 'podcast';

                await turso.execute(`
                    UPDATE podcasts SET
                        latest_ep_id = ?, latest_ep_title = ?, latest_ep_date = ?,
                        latest_ep_duration = ?, latest_ep_url = ?, latest_ep_image = ?,
                        latest_ep_type = ?, latest_ep_description = ?,
                        latest_ep_chapters_url = ?, latest_ep_transcript_url = ?,
                        latest_ep_persons = ?, latest_ep_transcripts = ?,
                        medium = ?, last_ep_sync = ?, qdrant_vectorized = 0
                    WHERE id = ?
                `, [
                    String(latest.id),
                    latest.title || '',
                    latest.datePublished || 0,
                    latest.duration || 0,
                    latest.enclosureUrl || '',
                    latest.image || latest.feedImage || '',
                    latest.enclosureType || 'audio/mpeg',
                    text.cleanDescription(latest.description),
                    latest.chaptersUrl || null,
                    latest.transcriptUrl || null,
                    latest.persons ? JSON.stringify(latest.persons) : null,
                    latest.transcripts ? JSON.stringify(latest.transcripts) : null,
                    medium,
                    Date.now(),
                    podId,
                ]);
                updated++;
                state.recordCheck(st, podId, { latestEpId: latest.id }).m = medium;
            } catch (e) {
                errors++;
                log.warn(`Show ${podId}: ${e.message}`);
            }
        }));
        for (let k = 0; k < batch.length; k++) prog.tick(`new ${updated} / same ${unchanged}`);
        turso.flushStats();
        // sync_cache is ~1.5MB — do not rewrite every 5-show batch
        if ((i / CONCURRENCY) % 20 === 0) state.save(st);
    }

    state.save(st);
    await handoff.flush();
    const stats = turso.getStats();
    log.costFooter('Stage 3 · Sync Episodes', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail:
            `${log.fmt(due.length)} checked (${log.fmt(deferred)} deferred) · ` +
            `${updated} new · ${unchanged} unchanged · ${empty} empty · ${errors} errors · ` +
            `tiers news/${policy.news} core/${policy.core} relaxed/${policy.relaxed} · ` +
            `handoff ${handoff.handoffPath()}`,
    });
    log.summaryTable('Stage 3: Sync Episodes', [{
        stage: 'sync-episodes',
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail:
            `${due.length} checked (${deferred} deferred): ${updated} new, ${unchanged} unchanged, ` +
            `${empty} empty, ${errors} errors · due news=${staleNews} core=${staleCore} relaxed=${staleRelaxed}`,
    }]);

    if (due.length > 20 && errors > due.length / 2) {
        log.error(`More than half of episode checks failed (${errors}/${due.length})`);
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
