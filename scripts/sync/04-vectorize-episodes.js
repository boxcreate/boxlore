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
 * Prune-before-insert keeps every show at its cap, but only against a full window.
 * Caps are retention-aware (the larger of today's chart cap and the cap a show earned).
 * Shows with no vectors get their newest episodes first and the rest after a few days.
 * A failed upload is spooled to disk and uploaded first by the next run.
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
const { capsFromCountriesByItunes, rememberEarnedCaps, effectiveCap } = require('./lib/episode-caps');
const { planWindow, dueForDepth, clearDepth, pruneIsSafe } = require('./lib/vector-window');
const vectorSpool = require('./lib/vector-spool');
const { bookDb, bootstrapShow, ensureBookSchema } = require('./lib/episode-book');
const { loadServedIds, loadServedOwners, resolveIdentities } = require('./lib/episode-identity');
const { catalogKey } = require('./lib/episode-recipe-id');
const state = require('./lib/state');
const {
    loadCountriesByItunesId,
    itunesInCountries,
} = require('./lib/chart-countries');
const scalars = require('./lib/scalars');
const attribution = require('./lib/attribution');

const UPSERT_BATCH = 100;

async function ensureIndexes() {
    await turso.execute(`CREATE INDEX IF NOT EXISTS idx_podcasts_pending_ep_vec
                         ON podcasts(qdrant_vectorized) WHERE qdrant_vectorized = 0`);
}

async function main() {
    const startedS = Math.floor(Date.now() / 1000);
    turso.assertEnv();
    qdrant.assertEnv();
    pi.assertEnv();
    turso.beginStep('vectorize-episodes');
    await turso.healthCheck();
    await ensureIndexes();
    const book = bookDb(turso);
    await ensureBookSchema(book);
    await tipQueue.ensureTable(turso);
    await qdrant.ensureEpisodesCollection(cfg.VECTOR_DIM, cfg.EPISODES_COLLECTION);

    if (cfg.FULL_TIER_COUNTRIES.length === 0) {
        log.info('No full-tier countries configured - nothing to vectorize');
        return;
    }

    const st = state.load();
    stateRef = st;
    const nowMs = Date.now();
    const storedPids = new Set();

    // Vectors from an upload that failed in an earlier run go up first, so the existence
    // checks below see them and nothing is embedded twice.
    const replayed = await vectorSpool.replay(
        (points) => qdrant.upsert(cfg.EPISODES_COLLECTION, points),
        (metas) => {
            for (const m of metas) {
                attribution.episodeStored(m.pid, m.ep, m.queue);
                storedPids.add(Number(m.pid));
            }
        },
    );
    if (replayed.batches || replayed.dropped || replayed.left) {
        log.info(
            `Spool: ${log.fmt(replayed.points)} vectors re-uploaded from ${replayed.batches} batch(es)` +
            ` · ${replayed.dropped} dropped · ${replayed.left} still waiting`,
        );
    }

    // Shows whose newest-first phase is over get one more pass at their full cap.
    // A show's depth date is cleared only once its flag is stored; if an UPDATE fails, the run's
    // state save keeps the date and the next run tries again.
    const depthDue = dueForDepth(st, nowMs);
    for (let i = 0; i < depthDue.length; i += 200) {
        const chunk = depthDue.slice(i, i + 200);
        await turso.execute(
            `UPDATE podcasts SET qdrant_vectorized = 0 WHERE id IN (${chunk.map(() => '?').join(',')})`,
            chunk,
        );
        clearDepth(st, chunk);
    }
    if (depthDue.length) log.info(`Depth: ${log.fmt(depthDue.length)} show(s) flagged for their full window`);

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
    rememberEarnedCaps(st, caps);
    log.banner('Stage 4 · Vectorize Episodes', {
        'Pending shows': log.fmt(pending.length),
        'Tip queue (on charts)': log.fmt(tipCount),
        'Incremental (show=1)': log.fmt(incrementalCount),
        'Cold (both=0)': log.fmt(coldCount),
        'Embedding budget': log.fmt(cfg.MAX_EMBEDDINGS_PER_RUN),
        'Caps': `us/gb ${cfg.EPISODE_CAP_BY_COUNTRY.us} · else ${cfg.EPISODE_CAP_DEFAULT} · or earned`,
        'New shows': cfg.NEW_ENTRANT_DEPTH_DAYS > 0
            ? `newest ${cfg.NEW_ENTRANT_EPISODES} first, rest after ${cfg.NEW_ENTRANT_DEPTH_DAYS} d`
            : 'full window',
        'Embed provider': cfg.EMBED_PROVIDER,
        'Handoff': handoff.handoffPath(),
        'Collection': cfg.EPISODES_COLLECTION,
    });
    if (drainOrder.length === 0) {
        state.save(st);
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
    let stored = 0;
    let pruned = 0;
    let pruneSkipped = 0;
    let newestShows = 0;
    let spooledPoints = 0;
    let flushes = 0;
    let bookRows = 0;
    let bookErrors = 0;
    let renamedEps = 0;
    let deferredEps = 0;

    // Points buffered for batch upsert; flag + tip-queue clears follow durable writes.
    let pointsQueue = [];
    // Parallel to pointsQueue: what each point is, for the attribution log.
    let metaQueue = [];
    /** @type {{ id: string, clearTip: boolean }[]} */
    let completeQueue = [];

    async function flush() {
        if (pointsQueue.length > 0) {
            await qdrant.upsert(cfg.EPISODES_COLLECTION, pointsQueue);
            stored += pointsQueue.length;
            for (const m of metaQueue) {
                attribution.episodeStored(m.pid, m.ep, m.queue);
                storedPids.add(Number(m.pid));
            }
            pointsQueue = [];
            metaQueue = [];
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
        attribution.flush();
        if (++flushes % 25 === 0) state.save(st);
    }

    /** A failed upload: keep the computed vectors for the next run instead of dropping them. */
    function spoolFailedBatch() {
        if (pointsQueue.length && vectorSpool.spool(pointsQueue, metaQueue)) spooledPoints += pointsQueue.length;
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
        const showCap = effectiveCap(pod.id, caps, st);
        const lane = laneForShow(pod, tipSet);
        // Newest first for shows with no vectors yet; the count is skipped once a phase is known.
        const rec = st.shows[pod.id] || (st.shows[pod.id] = {});
        let existingCount = -1;
        if (rec.d == null && cfg.NEW_ENTRANT_DEPTH_DAYS > 0) {
            try {
                existingCount = await qdrant.countPoints(cfg.EPISODES_COLLECTION, {
                    must: [{ key: 'podcast_id', match: { value: parseInt(pod.id, 10) || 0 } }],
                });
            } catch {
                existingCount = -1; // unknown: treat as an ordinary show
            }
        }
        const window = planWindow(rec, existingCount, showCap, {
            now: nowMs,
            newestEpisodes: cfg.NEW_ENTRANT_EPISODES,
            depthDays: cfg.NEW_ENTRANT_DEPTH_DAYS,
        });
        if (window.phase === 'newest') newestShows++;
        let queue = 'cold';
        if (tipSet.has(pod.id)) queue = 'tip';
        else if (pod.qdrant_podcast_vectorized === 1) queue = 'inc';

        let episodes = handoff.take(pod.id);
        if (episodes) {
            handoffHits++;
        } else {
            try {
                episodes = await pi.episodesByFeedId(pod.id, window.cap);
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

        // Identity first (P1 handover, lib/episode-identity.js): the book's served ID names each point,
        // and new episodes get their book row before any vector. On a book failure this show keeps
        // today's naming for this run, unstamped, and the bootstrap's catch-up pass records it later.
        const windowEps = episodes.slice(0, window.cap);
        let ident = null;
        try {
            const sid = Number(pod.id);
            const keys = [...new Set(windowEps.map((ep) => catalogKey(ep.guid, ep.enclosureUrl)).filter(Boolean))];
            const piIds = windowEps.map((ep) => Number(ep.id)).filter((n) => Number.isSafeInteger(n) && n > 0);
            ident = resolveIdentities(sid, windowEps, await loadServedIds(book, sid, keys), await loadServedOwners(book, piIds));
            const writes = [...ident.newRows, ...ident.aliases.map((a) => ({ key: a.key, eid: a.eid, piId: a.piId, servedId: null }))];
            if (writes.length) {
                const res = await bootstrapShow(book, sid, writes, { now: Math.floor(Date.now() / 1000), source: 'pi' });
                if (!res.ok) throw new Error(res.error || 'book write failed');
                bookRows += ident.newRows.length;
            }
            renamedEps += ident.renamed;
            deferredEps += ident.deferred;
        } catch (e) {
            bookErrors++;
            ident = null;
            log.warn(`Book identity failed for show ${pod.id}: ${e.message} - unstamped this run`);
        }
        const eps = ident
            ? ident.items
            : windowEps.map((ep) => ({ raw: ep, uuid: qdrant.stableUUID(ep.id), servedId: ep.id, stamps: null }));
        const uuids = eps.map((e) => e.uuid);

        // Prune BEFORE insert: hard-cap this show to its window (wait=true), but only when the
        // list fills the window; a shorter list may be partial and must never delete vectors.
        const pruneFilter = {
            must: [{ key: 'podcast_id', match: { value: parseInt(pod.id, 10) || 0 } }],
            must_not: [{ has_id: uuids }],
        };
        // The fetched list decides whether pruning is safe; folding duplicates must not make it look partial.
        const pruneSafe = pruneIsSafe(windowEps.length, window.cap);
        if (!pruneSafe) pruneSkipped++;
        // Read what the prune will remove, for the attribution log only.
        let pruning = [];
        try {
            if (pruneSafe) {
                pruning = await qdrant.scrollAll(
                    cfg.EPISODES_COLLECTION, pruneFilter, ['id', 'published_date', 'audio_url'], 256,
                );
            }
        } catch {
            pruning = [];
        }
        try {
            if (pruneSafe) await qdrant.deleteByFilter(cfg.EPISODES_COLLECTION, pruneFilter, true);
            pruned += pruning.length;
            for (const p of pruning) {
                attribution.episodeDeleted('4', pod.id, {
                    eid: p.payload?.id,
                    pub: p.payload?.published_date,
                    audioUrl: p.payload?.audio_url,
                }, 'prune');
            }
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
        const showMeta = [];
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
                    id: scalars.asPositiveInt(item.servedId ?? ep.id, 0),
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
                if (item.stamps) Object.assign(prepared.payload, item.stamps);
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
                showMeta.push({ pid: pod.id, ep, queue });
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
        metaQueue.push(...showMeta);

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
                spoolFailedBatch();
                pointsQueue = [];
                metaQueue = [];
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
        spoolFailedBatch();
        metaQueue = [];
    }
    attribution.flush();
    state.save(st);

    try {
        await handoff.flush();
    } catch {
        // best-effort
    }

    let reasons = '';
    try {
        reasons = attribution.runReasons({ since: startedS, pids: storedPids }).text;
    } catch (e) {
        log.warn(`Attribution reasons unavailable (non-fatal): ${e.message}`);
    }

    const backlog = drainOrder.length - processedCount;
    const stats = turso.getStats();
    log.costFooter('Stage 4 · Vectorize Episodes', {
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail:
            `${log.fmt(embedded)}/${log.fmt(cfg.MAX_EMBEDDINGS_PER_RUN)} budget ` +
            `(tip ${embTip} · cold ${embCold}) · stored ${log.fmt(stored)} · pruned ${log.fmt(pruned)} · ` +
            `prune skipped ${pruneSkipped} · newest-first ${newestShows} · depth ${depthDue.length} · ` +
            `spooled ${spooledPoints} · replayed ${replayed.points} · ` +
            `${showsCompleted} done · ` +
            `${partialShows} partial · skip ${showsFullySkipped} · ` +
            `handoff ${handoffHits} · PI ${piFetches} · backlog ${log.fmt(backlog)} · ${errors} errors · ` +
            `book ${bookRows} rows · renamed ${renamedEps} · deferred ${deferredEps} · book errors ${bookErrors}` +
            (reasons ? `\n  ${reasons}` : ''),
    });
    log.summaryTable('Stage 4: Vectorize Episodes', [{
        stage: 'vectorize-episodes',
        reads: stats.reads,
        writes: stats.writes,
        apiCalls: pi.getApiCallCount(),
        detail:
            `${embedded}/${cfg.MAX_EMBEDDINGS_PER_RUN} budget tip=${embTip} cold=${embCold}, ` +
            `stored ${stored}, pruned ${pruned}, prune skipped ${pruneSkipped}, newest-first ${newestShows}, ` +
            `depth ${depthDue.length}, spooled ${spooledPoints}, replayed ${replayed.points}, ` +
            `${showsCompleted} done, partial ${partialShows}, skip ${showsFullySkipped}, ` +
            `handoff ${handoffHits}, PI ${piFetches}, backlog ${backlog}, ${errors} errors` +
            (reasons ? `, ${reasons}` : ''),
    }]);

    if (processedCount > 20 && errors > processedCount) {
        log.error('Error count exceeds processed shows - failing run for visibility');
        process.exit(1);
    }
}

/** The run's sync state, so a crash still saves earned caps and newest-first dates. */
let stateRef = null;

main()
    .then(() => turso.flushStats())
    .catch((err) => {
        log.error(`vectorize-episodes failed: ${err.message}`);
        turso.flushStats();
        attribution.flush();
        if (stateRef) state.save(stateRef);
        process.exit(1);
    });
