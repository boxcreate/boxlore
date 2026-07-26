'use strict';

/**
 * Enforce per-show episode caps across the episodes collection: for every
 * podcast_id, keep only the newest N points (by published_date) and delete
 * the rest. N = max country cap for that show (us/gb 50, else 20).
 * Shared by the daily cleanup job and one-off remediation.
 */

const log = require('./log');
const qdrant = require('./qdrant');
const turso = require('./turso');
const cfg = require('./config');
const { loadCapsByPodcastId } = require('./episode-caps');

const DELETE_CHUNK = 500;

/**
 * @param {{ dryRun?: boolean }} [opts]
 * @returns {Promise<{ showsScanned: number, trimmedShows: number, deletedPoints: number }>}
 */
async function trimEpisodeCaps(opts = {}) {
    const dryRun = Boolean(opts.dryRun);

    log.group(`Trim episode caps (us/gb ${cfg.EPISODE_CAP_BY_COUNTRY.us} · else ${cfg.EPISODE_CAP_DEFAULT})`);

    let allPoints;
    try {
        const scanStart = Date.now();
        allPoints = await qdrant.scrollAll(
            cfg.EPISODES_COLLECTION,
            null,
            ['podcast_id', 'published_date'],
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
        byShow.get(key).push({ id: pt.id, date: pt.payload?.published_date || 0 });
    }
    log.info(`Distinct shows: ${log.fmt(byShow.size)} (${unknownPodcastId} points without podcast_id)`);

    let caps = new Map();
    try {
        turso.assertEnv();
        caps = await loadCapsByPodcastId([...byShow.keys()]);
        log.info(`Loaded caps for ${log.fmt(caps.size)} chart shows`);
    } catch (e) {
        log.warn(`Cap lookup failed (${e.message}) — using default ${cfg.EPISODE_CAP_DEFAULT}`);
    }

    const trimDeleteIds = [];
    let trimmedShows = 0;
    for (const [podId, points] of byShow.entries()) {
        const cap = caps.get(podId) || cfg.EPISODE_CAP_DEFAULT;
        if (points.length <= cap) continue;
        points.sort((a, b) => b.date - a.date);
        const excess = points.slice(cap);
        trimDeleteIds.push(...excess.map(p => p.id));
        trimmedShows++;
    }
    log.info(`Shows over cap: ${log.fmt(trimmedShows)} · excess points: ${log.fmt(trimDeleteIds.length)}`);

    if (!dryRun && trimDeleteIds.length > 0) {
        for (let i = 0; i < trimDeleteIds.length; i += DELETE_CHUNK) {
            await qdrant.deleteByIds(
                cfg.EPISODES_COLLECTION,
                trimDeleteIds.slice(i, i + DELETE_CHUNK),
                true
            );
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
    };
}

module.exports = { trimEpisodeCaps };
