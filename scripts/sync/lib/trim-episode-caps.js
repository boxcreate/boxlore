'use strict';

/**
 * Enforce EPISODES_PER_SHOW across the episodes collection: for every
 * podcast_id, keep only the newest N points (by published_date) and delete
 * the rest. Shared by the daily cleanup job and one-off remediation.
 */

const log = require('./log');
const qdrant = require('./qdrant');
const cfg = require('./config');

const DELETE_CHUNK = 500;

/**
 * @param {{ dryRun?: boolean }} [opts]
 * @returns {Promise<{ showsScanned: number, trimmedShows: number, deletedPoints: number }>}
 */
async function trimEpisodeCaps(opts = {}) {
    const dryRun = Boolean(opts.dryRun);
    const cap = cfg.EPISODES_PER_SHOW;

    log.group(`Trim episode caps (max ${cap}/show)`);
    const scanStart = Date.now();
    const allPoints = await qdrant.scrollAll(
        cfg.EPISODES_COLLECTION,
        null,
        ['podcast_id', 'published_date'],
        1000
    );
    log.info(`Scanned ${log.fmt(allPoints.length)} points in ${log.duration(Date.now() - scanStart)}`);

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

    const trimDeleteIds = [];
    let trimmedShows = 0;
    for (const points of byShow.values()) {
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
