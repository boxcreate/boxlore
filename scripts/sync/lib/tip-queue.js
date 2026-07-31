'use strict';

/**
 * Durable tip-lane queue in Turso (IDs only). Episode payloads stay in
 * pi-handoff for same-run stage 4; this table survives across runs.
 *
 * Stage 3 upserts on tip change; stage 4 deletes only when a show is fully
 * vectorized. Cleanup purges rows for Turso-deleted podcasts.
 */

const TABLE = 'ep_vec_tip_queue';

async function ensureTable(turso) {
    await turso.execute(`
        CREATE TABLE IF NOT EXISTS ${TABLE} (
            podcast_id INTEGER PRIMARY KEY,
            tip_ep_id TEXT,
            enqueued_at INTEGER NOT NULL
        )
    `);
}

/**
 * @param {object} turso
 * @param {string|number} podcastId
 * @param {string|number|null} tipEpId
 */
async function upsert(turso, podcastId, tipEpId) {
    const id = parseInt(String(podcastId), 10);
    if (!Number.isFinite(id) || id <= 0) return;
    await turso.execute(
        `INSERT OR REPLACE INTO ${TABLE} (podcast_id, tip_ep_id, enqueued_at)
         VALUES (?, ?, ?)`,
        [id, tipEpId != null ? String(tipEpId) : null, Date.now()],
    );
}

/** @returns {Promise<string[]>} podcast ids oldest-enqueued first */
async function listIds(turso) {
    const res = await turso.execute(
        `SELECT podcast_id FROM ${TABLE} ORDER BY enqueued_at ASC, podcast_id ASC`,
    );
    return turso.rows(res).map((r) => String(r[0]));
}

async function remove(turso, podcastId) {
    const id = parseInt(String(podcastId), 10);
    if (!Number.isFinite(id) || id <= 0) return;
    await turso.execute(`DELETE FROM ${TABLE} WHERE podcast_id = ?`, [id]);
}

/**
 * @param {object} turso
 * @param {Array<string|number>} podcastIds
 */
async function removeMany(turso, podcastIds) {
    const ids = [...new Set(
        (podcastIds || [])
            .map((x) => parseInt(String(x), 10))
            .filter((n) => Number.isFinite(n) && n > 0),
    )];
    const CHUNK = 200;
    for (let i = 0; i < ids.length; i += CHUNK) {
        const chunk = ids.slice(i, i + CHUNK);
        const ph = chunk.map(() => '?').join(',');
        await turso.execute(`DELETE FROM ${TABLE} WHERE podcast_id IN (${ph})`, chunk);
    }
}

module.exports = {
    TABLE,
    ensureTable,
    upsert,
    listIds,
    remove,
    removeMany,
};
