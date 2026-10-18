'use strict';

/**
 * Enforce per-show episode caps across the episodes collection: for every
 * podcast_id, keep only the newest N points (by published_date) and delete
 * the rest. N = the larger of today's chart cap (us/gb 50, else 20) and the
 * cap the show earned while its vectors were kept (episode-caps.js).
 * Shared by the daily cleanup job and one-off remediation.
 *
 * Fails closed: if the chart cap lookup fails, nothing is trimmed (it used to
 * fall back to 20 for every show and delete 30 vectors from each US/GB show).
 * Changed in P1a of the own-episode-IDs plan (plan section 10b).
 */

const log = require('./log');
const qdrant = require('./qdrant');
const turso = require('./turso');
const cfg = require('./config');
const { loadCapsByPodcastId, rememberEarnedCaps, effectiveCap } = require('./episode-caps');
const state = require('./state');
const attribution = require('./attribution');

const DELETE_CHUNK = 500;

/**
 * @param {{ dryRun?: boolean, loadCaps?: Function }} [opts] loadCaps is for tests
 * @returns {Promise<{ showsScanned: number, trimmedShows: number, deletedPoints: number, keptEarned?: number, failed?: boolean }>}
 */
async function trimEpisodeCaps(opts = {}) {
    const dryRun = Boolean(opts.dryRun);
    const loadCaps = opts.loadCaps || ((ids) => {
        turso.assertEnv();
        return loadCapsByPodcastId(ids);
    });

    log.group(`Trim episode caps (us/gb ${cfg.EPISODE_CAP_BY_COUNTRY.us} · else ${cfg.EPISODE_CAP_DEFAULT})`);

    let allPoints;
    try {
        const scanStart = Date.now();
        allPoints = await qdrant.scrollAll(
            cfg.EPISODES_COLLECTION,
            null,
            ['podcast_id', 'published_date', 'id'],
            1000
        );
        log.info(`Scanned ${log.fmt(allPoints.length)} points in ${log.duration(Date.now() - scanStart)}`);
    } catch (e) {
        if (/404|not found|doesn't exist|does not exist/i.test(e.message)) {
            log.warn(`Episodes collection missing - nothing to trim: ${e.message}`);
            log.endGroup();
            return { showsScanned: 0, trimmedShows: 0, deletedPoints: 0 };
        }
        throw e;
    }

    const byShow = new Map();
    let unknownPodcastId = 0;
    for (const pt of allPoints) {
        const pid = pt.payload?.podcast_id;
        if (pid === undefined || pid === null) {
            unknownPodcastId++;
            continue;
        }
        const key = String(pid);
        if (!byShow.has(key)) byShow.set(key, []);
        byShow.get(key).push({ id: pt.id, date: pt.payload?.published_date || 0, eid: pt.payload?.id });
    }
    log.info(`Distinct shows: ${log.fmt(byShow.size)} (${unknownPodcastId} points without podcast_id)`);

    let caps;
    try {
        caps = await loadCaps([...byShow.keys()]);
        log.info(`Loaded caps for ${log.fmt(caps.size)} chart shows`);
    } catch (e) {
        log.warn(`Cap lookup failed (${e.message}) — skipping the trim (fail closed)`);
        log.endGroup();
        return { showsScanned: byShow.size, trimmedShows: 0, deletedPoints: 0, failed: true };
    }

    const st = state.load();
    const raised = rememberEarnedCaps(st, caps);
    const trimDeleteIds = [];
    const trimDeleted = [];
    let trimmedShows = 0;
    let keptEarned = 0;
    for (const [podId, points] of byShow.entries()) {
        const cap = effectiveCap(podId, caps, st);
        if (cap > (caps.get(podId) || cfg.EPISODE_CAP_DEFAULT) && points.length > (caps.get(podId) || cfg.EPISODE_CAP_DEFAULT)) {
            keptEarned++;
        }
        if (points.length <= cap) continue;
        points.sort((a, b) => b.date - a.date);
        const excess = points.slice(cap);
        trimDeleteIds.push(...excess.map(p => p.id));
        trimDeleted.push(...excess.map(p => ({ podId, ...p })));
        trimmedShows++;
    }
    log.info(`Shows over cap: ${log.fmt(trimmedShows)} · excess points: ${log.fmt(trimDeleteIds.length)}`);
    log.info(`Earned caps: ${log.fmt(raised)} raised · ${log.fmt(keptEarned)} shows kept above today's chart cap`);
    if (!dryRun) state.save(st);

    if (!dryRun && trimDeleteIds.length > 0) {
        // Audio URLs of the points being trimmed, for the attribution log only.
        let audio = new Map();
        try {
            audio = await qdrant.payloadsByIds(cfg.EPISODES_COLLECTION, trimDeleteIds, ['audio_url']);
        } catch {
            audio = new Map();
        }
        for (let i = 0; i < trimDeleteIds.length; i += DELETE_CHUNK) {
            await qdrant.deleteByIds(
                cfg.EPISODES_COLLECTION,
                trimDeleteIds.slice(i, i + DELETE_CHUNK),
                true
            );
            for (const p of trimDeleted.slice(i, i + DELETE_CHUNK)) {
                attribution.episodeDeleted('6', p.podId, {
                    eid: p.eid,
                    pub: p.date,
                    audioUrl: audio.get(String(p.id))?.audio_url,
                }, 'cap');
            }
            attribution.flush();
        }
        log.info(`Deleted ${log.fmt(trimDeleteIds.length)} excess episode points (wait=true)`);
    } else if (dryRun) {
        log.info('Dry run - no deletes');
    }

    log.endGroup();
    return {
        showsScanned: byShow.size,
        trimmedShows,
        deletedPoints: dryRun ? 0 : trimDeleteIds.length,
        excessPoints: trimDeleteIds.length,
        keptEarned,
    };
}

module.exports = { trimEpisodeCaps };
