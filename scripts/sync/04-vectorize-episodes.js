#!/usr/bin/env node
'use strict';

/**
 * Stage 4: Generate episode embeddings for pending full-tier chart shows and
 * upsert to the Qdrant 'episodes' collection.
 *
 * Drain order (tip-first):
 *   1) Turso ep_vec_tip_queue IDs still on full-tier charts
 *   2) Remaining ep=0 ∧ show=1 (incremental)
 *   3) Cold both=0, oldest last_ep_sync first, leftover budget only
 *
 * Prefers PI handoff from stage 3 (same-run); falls back to PI fetch.
 * Prune-before-insert keeps every show at its country cap.
 * qdrant_vectorized=1 + tip-queue DELETE only when the show fully completes
 * (not on mid-show budget break). Upserts use wait=true.
 */

const log = require('./lib/log');
const turso = require('./lib/turso');
const qdrant = require('./lib/qdrant');
const pi = require('./lib/podcast-index');
const embedder = require('./lib/embedder');
const text = require('./lib/text');
const cfg = require('./lib/config');
const handoff = require('./lib/pi-handoff');
const tipQueue = require('./lib/tip-queue');
const {
    orderPendingForVectorize,
    showVectorizeComplete,
    laneForShow,
} = require('./lib/vectorize-lanes');
const { capsFromCountriesByItunes } = require('./lib/episode-caps');
const {
    loadCountriesByItunesId,
    itunesInCountries,
} = require('./lib/chart-countries');
const scalars = require('./lib/scalars');

const UPSERT_BATCH = 100;

async function ensureIndexes() {
    await turso.execute(`CREATE INDEX IF NOT EXISTS idx_podcasts_pending_ep_vec
                         ON podcasts(qdrant_vectorized) WHERE qdrant_vectorized = 0`);
}

async function main() {
    turso.assertEnv();
    qdrant.assertEnv();
    pi.assertEnv();
    turso.beginStep('vectorize-episodes');
    await turso.healthCheck();
    await ensureIndexes();
    await tipQueue.ensureTable(turso);
    await qdrant.ensureEpisodesCollection(cfg.VECTOR_DIM, cfg.EPISODES_COLLECTION);

    if (cfg.FULL_TIER_COUNTRIES.length === 0) {
        log.info('No full-tier countries configured - nothing to vectorize');
        return;
    }

    // --- Pending shows (paged); chart membership in JS (no CAST on charts) ---
    const countriesByItunes = await loadCountriesByItunesId(turso);
    const fullTier = new Set(cfg.FULL_TIER_COUNTRIES.map((c) => c.toLowerCase()));
    const pendingRows = await turso.fetchAllPaged({
        pageSize: cfg.TURSO_PAGE_SIZE,
        rowId: (r) => Number(r[0]),
        buildPage: (after, limit) => ({
            sql: `
                SELECT p.id, p.title, p.categories, p.author, p.image_url, p.language,
                       p.last_ep_sync, p.itunes_id, p.qdrant_podcast_vectorized
                FROM podcasts p
                WHERE p.qdrant_vectorized = 0
                  AND p.id > ?
                ORDER BY p.id ASC
                LIMIT ?
            `,
            args: [after == null ? 0 : after, limit],
        }),
    });

    const pending = pendingRows
        .filter((r) => itunesInCountries(countriesByItunes, r[7], fullTier))
        .map((r) => ({
            id: String(r[0]),
            title: r[1] || 'Unknown Show',
            categories: r[2] || 'Podcast',
            author: r[3] || '',
            image_url: r[4] || '',
            language: r[5] || 'en',
            last_ep_sync: r[6] == null ? null : Number(r[6]),
            itunes_id: r[7],
            qdrant_podcast_vectorized: Number(r[8]) || 0,
        }));

    const tipQueueIds = await tipQueue.listIds(turso);
    const {
        ordered: drainOrder,
        tipSet,
        orphanTipIds,
        tipCount,
        incrementalCount,
        coldCount,
    } = orderPendingForVectorize(pending, tipQueueIds);

    if (orphanTipIds.length > 0) {
        await tipQueue.removeMany(turso, orphanTipIds);
        handoff.removeMany(orphanTipIds);
        log.info(
            `Tip queue: dropped ${log.fmt(orphanTipIds.length)} off-chart / already-done id(s)`,
        );
    }

    const caps = capsFromCountriesByItunes(drainOrder, countriesByItunes, fullTier);
    log.banner('Stage 4 · Vectorize Episodes', {
        'Pending shows': log.fmt(pending.length),
        'Tip queue (on charts)': log.fmt(tipCount),
        'Incremental (show=1)': log.fmt(incrementalCount),
        'Cold (both=0)': log.fmt(coldCount),
        'Embedding budget': log.fmt(cfg.MAX_EMBEDDINGS_PER_RUN),
        'Caps': `us/gb ${cfg.EPISODE_CAP_BY_COUNTRY.us} · else ${cfg.EPISODE_CAP_DEFAULT}`,
        'Embed provider': cfg.EMBED_PROVIDER,
        'Handoff': handoff.handoffPath(),
        'Collection': cfg.EPISODES_COLLECTION,
    });
    if (drainOrder.length === 0) {
        log.summaryTable('Stage 4: Vectorize Episodes', [{
            stage: 'vectorize-episodes', reads: turso.getStats().reads,
            writes: turso.getStats().writes, detail: 'queue empty',
        }]);
        return;
    }

    let budget = cfg.MAX_EMBEDDINGS_PER_RUN;
    let embedded = 0;
    let embTip = 0;
    let embCold = 0;
    let showsCompleted = 0;
    let showsFullySkipped = 0;
    let partialShows = 0;
    let handoffHits = 0;
    let piFetches = 0;
    let errors = 0;
    let processedCount = 0;

    // Points buffered for batch upsert; flag + tip-queue clears follow durable writes.
    let pointsQueue = [];
    /** @type {{ id: string, clearTip: boolean }[]} */
    let completeQueue = [];

    async function flush() {
        if (pointsQueue.length > 0) {
            await qdrant.upsert(cfg.EPISODES_COLLECTION, pointsQueue);
            pointsQueue = [];
        }
        if (completeQueue.length > 0) {
            const ids = completeQueue.map((c) => c.id);
            const placeholders = ids.map(() => '?').join(',');
            await turso.execute(
                `UPDATE podcasts SET qdrant_vectorized = 1 WHERE id IN (${placeholders})`,
                ids,
            );
            const tipClear = completeQueue.filter((c) => c.clearTip).map((c) => c.id);
            if (tipClear.length > 0) {
                await tipQueue.removeMany(turso, tipClear);
                handoff.removeMany(tipClear);
            }
            completeQueue = [];
        }
        turso.flushStats();
    }

    const runProg = log.budgetProgress(cfg.MAX_EMBEDDINGS_PER_RUN, 'embeddings', 50);

    function reportBacklogIfDue(force = false) {
        if (force || processedCount % 25 === 0) {
            log.backlogStatus({
                scanned: processedCount,
                pending: drainOrder.length - processedCount,
                budgetLeft: Math.max(0, budget),
                skipped: showsFullySkipped,
            });
        }
    }

    for (const pod of drainOrder) {
        if (budget <= 0) {
            runProg.flush();
            reportBacklogIfDue(true);
            log.info(
                `[BUDGET] Embedding budget exhausted (${embedded}/${cfg.MAX_EMBEDDINGS_PER_RUN}). ` +
                `Remaining backlog: ${drainOrder.length - processedCount} shows`,
            );
            break;
        }
        processedCount++;
        const showCap = caps.get(pod.id) || cfg.EPISODE_CAP_DEFAULT;
        const lane = laneForShow(pod, tipSet);

        let episodes = handoff.take(pod.id);
        if (episodes) {
            handoffHits++;
        } else {
            try {
                episodes = await pi.episodesByFeedId(pod.id, showCap);
                piFetches++;
            } catch (e) {
                errors++;
                log.warn(`Show ${pod.id} (${pod.title.substring(0, 40)}): episode fetch failed: ${e.message}`);
                reportBacklogIfDue();
                continue;
            }
        }

        if (episodes.length === 0) {
            completeQueue.push({ id: pod.id, clearTip: tipSet.has(pod.id) });
            showsCompleted++;
            reportBacklogIfDue();
            continue;
        }

        const eps = episodes.slice(0, showCap).map((ep) => ({
            raw: ep,
            uuid: qdrant.stableUUID(ep.id),
        }));
        const uuids = eps.map((e) => e.uuid);

        // Prune BEFORE insert: hard-cap this show to its country cap (wait=true).
        try {
            await qdrant.deleteByFilter(cfg.EPISODES_COLLECTION, {
                must: [{ key: 'podcast_id', match: { value: parseInt(pod.id, 10) || 0 } }],
                must_not: [{ has_id: uuids }],
            }, true);
        } catch (e) {
            errors++;
            log.warn(`Prune failed for show ${pod.id} (${pod.title.substring(0, 40)}): ${e.message} - skipping upsert`);
            reportBacklogIfDue();
            continue;
        }

        const existing = await qdrant.existingIds(cfg.EPISODES_COLLECTION, uuids);
        const toEmbed = eps.filter((e) => !existing.has(e.uuid));

        if (toEmbed.length === 0) {
            completeQueue.push({ id: pod.id, clearTip: tipSet.has(pod.id) });
            showsCompleted++;
            showsFullySkipped++;
            reportBacklogIfDue();
            continue;
        }

        let showFailed = false;
        let budgetBroke = false;
        let corruptSkipped = 0;
        const showPoints = [];
        for (const item of toEmbed) {
            if (budget - showPoints.length <= 0) {
                budgetBroke = true;
                break;
            }
            const ep = item.raw;
            const epTitle = scalars.asScalarString(ep.title, '');
            const audioUrl = scalars.asScalarString(ep.enclosureUrl, '');
            if (!epTitle || !audioUrl || !scalars.asPositiveInt(ep.id, 0)) {
                errors++;
                corruptSkipped++;
                log.warn(
                    `Skip ep on show ${pod.id}: critical tip fields corrupt/missing ` +
                    `(title=${JSON.stringify(ep.title)} id=${JSON.stringify(ep.id)})`,
                );
                continue;
            }
            const cleaned = text.cleanDescription(
                typeof ep.description === 'string' ? ep.description : '',
            );
            const embedText = text.episodeEmbedText(
                { title: epTitle, cleanedDescription: cleaned },
                {
                    title: scalars.asScalarString(pod.title, 'Unknown Show'),
                    categories: scalars.asScalarString(pod.categories, ''),
                    author: scalars.asScalarString(pod.author, ''),
                },
            );
            try {
                const vector = await embedder.embed(embedText);
                const prepared = scalars.prepareEpisodePayload({
                    id: scalars.asPositiveInt(ep.id, 0),
                    title: text.safeTruncate(epTitle),
                    description: text.safeTruncate(
                        typeof ep.description === 'string' ? ep.description : '',
                        cfg.PAYLOAD_DESCRIPTION_MAX,
                    ),
                    podcast_id: scalars.asPositiveInt(pod.id, 0),
                    podcast_title: text.safeTruncate(pod.title),
                    podcast_author: text.safeTruncate(pod.author),
                    podcast_image_url: scalars.asScalarString(pod.image_url, ''),
                    podcast_categories: scalars.asScalarString(pod.categories, ''),
                    language: scalars.asScalarString(pod.language, 'en') || 'en',
                    audio_url: audioUrl,
                    image_url: scalars.asScalarString(
                        ep.image || ep.feedImage || pod.image_url,
                        '',
                    ),
                    published_date: scalars.asNonNegInt(ep.datePublished, 0),
                    duration: scalars.asNonNegInt(ep.duration, 0),
                });
                if (!prepared.ok) {
                    errors++;
                    corruptSkipped++;
                    log.warn(`Skip ep payload show ${pod.id}: ${prepared.reason}`);
                    continue;
                }
                if (prepared.scrubbed.length) {
                    log.warn(
                        `Scrubbed optional ep fields for show ${pod.id}: ${prepared.scrubbed.join(',')}`,
                    );
                }
                showPoints.push({
                    id: item.uuid,
                    vector,
                    payload: prepared.payload,
                });
                runProg.tick();
            } catch (e) {
                errors++;
                showFailed = true;
                log.warn(`Embedding failed for "${epTitle.substring(0, 40)}" (show ${pod.id}): ${e.message}`);
            }
        }

        embedded += showPoints.length;
        budget -= showPoints.length;
        if (lane === 'tip') embTip += showPoints.length;
        else embCold += showPoints.length;
        pointsQueue.push(...showPoints);

        const complete = showVectorizeComplete({
            showFailed,
            budgetBroke,
            toEmbedCount: toEmbed.length,
            embeddedCount: showPoints.length,
            corruptSkipped,
        });
        if (complete) {
            completeQueue.push({ id: pod.id, clearTip: tipSet.has(pod.id) });
            showsCompleted++;
        } else if (budgetBroke || showPoints.length > 0) {
            partialShows++;
        }

        if (pointsQueue.length >= UPSERT_BATCH) {
            try {
                await flush();
            } catch (e) {
                errors += Math.max(completeQueue.length, 1);
                log.error(
                    `Qdrant upsert batch failed (${pointsQueue.length} points, ` +
                    `${completeQueue.length} shows): ${e.message}`,
                );
                pointsQueue = [];
                completeQueue = [];
            }
        }
        reportBacklogIfDue();
    }

    runProg.flush();

    try {
        await flush();
    } catch (e) {
        errors += Math.max(completeQueue.length, 1);
        log.error(
            `Final Qdrant flush failed (${pointsQueue.length} points, ` +
            `${completeQueue.length} shows): ${e.message}`,
        );
    }

    try {
        await handoff.flush();
    } catch {
        // best-effort
    }

    const backlog = drainOrder.length - processedCount;
    const stats = turso.getStats();
    log.costFooter('Stage 4 · Vectorize Episodes', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail:
            `${log.fmt(embedded)}/${log.fmt(cfg.MAX_EMBEDDINGS_PER_RUN)} budget ` +
            `(tip ${embTip} · cold ${embCold}) · ${showsCompleted} done · ` +
            `${partialShows} partial · skip ${showsFullySkipped} · ` +
            `handoff ${handoffHits} · PI ${piFetches} · backlog ${log.fmt(backlog)} · ${errors} errors`,
    });
    log.summaryTable('Stage 4: Vectorize Episodes', [{
        stage: 'vectorize-episodes',
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail:
            `${embedded}/${cfg.MAX_EMBEDDINGS_PER_RUN} budget tip=${embTip} cold=${embCold}, ` +
            `${showsCompleted} done, partial ${partialShows}, skip ${showsFullySkipped}, ` +
            `handoff ${handoffHits}, PI ${piFetches}, backlog ${backlog}, ${errors} errors`,
    }]);

    if (processedCount > 20 && errors > processedCount) {
        log.error('Error count exceeds processed shows - failing run for visibility');
        process.exit(1);
    }
}

main()
    .then(() => turso.flushStats())
    .catch((err) => {
        log.error(`vectorize-episodes failed: ${err.message}`);
        turso.flushStats();
        process.exit(1);
    });
