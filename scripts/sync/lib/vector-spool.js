'use strict';

/**
 * Spool for computed vectors whose upload failed.
 *
 * Embedding is the expensive part of stage 4; a failed Qdrant upsert used to throw the
 * whole batch away, so the next run embedded it again. Now the batch (points with their
 * vectors, plus what each point is for the attribution log) is written to one file, and
 * the next stage 4 uploads it before embedding anything.
 *
 * Files: VECTOR_SPOOL_DIR/<ms>-<random>.json, root-only. Batches older than MAX_AGE_DAYS
 * are dropped (their shows will have been re-processed by then). A replay stops at the
 * first failure and keeps the rest for later.
 *
 * Added in P1a of the own-episode-IDs plan (plan section 10b, "lost flushes").
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const MAX_AGE_DAYS = 7;

function spoolDir(dir) {
    return dir || require('./config').VECTOR_SPOOL_DIR;
}

/**
 * Saves a batch. Returns the file path, or null if writing failed.
 * @param {object[]} points Qdrant points ({ id, vector, payload })
 * @param {object[]} metas  parallel attribution metas
 */
function spool(points, metas, dir) {
    if (!points || points.length === 0) return null;
    try {
        const folder = spoolDir(dir);
        fs.mkdirSync(folder, { recursive: true, mode: 0o700 });
        const file = path.join(folder, `${Date.now()}-${crypto.randomBytes(4).toString('hex')}.json`);
        fs.writeFileSync(`${file}.tmp`, JSON.stringify({ points, metas: metas || [] }), { mode: 0o600 });
        fs.renameSync(`${file}.tmp`, file);
        return file;
    } catch {
        return null;
    }
}

/**
 * Uploads spooled batches oldest first.
 * @param {(points: object[]) => Promise<void>} upsert
 * @param {(metas: object[]) => void} [onStored]
 * @returns {Promise<{ batches: number, points: number, dropped: number, left: number }>}
 */
async function replay(upsert, onStored, { dir, now = Date.now() } = {}) {
    const folder = spoolDir(dir);
    const out = { batches: 0, points: 0, dropped: 0, left: 0 };
    let names;
    try {
        names = fs.readdirSync(folder).filter((n) => /^\d+-[0-9a-f]+\.json$/.test(n)).sort();
    } catch {
        return out;
    }
    for (let i = 0; i < names.length; i++) {
        const file = path.join(folder, names[i]);
        const savedAt = Number(names[i].split('-')[0]);
        if (now - savedAt > MAX_AGE_DAYS * 24 * 60 * 60 * 1000) {
            fs.rmSync(file, { force: true });
            out.dropped++;
            continue;
        }
        let batch;
        try {
            batch = JSON.parse(fs.readFileSync(file, 'utf8'));
        } catch {
            fs.rmSync(file, { force: true });
            out.dropped++;
            continue;
        }
        try {
            await upsert(batch.points || []);
        } catch {
            out.left = names.length - i;
            break;
        }
        if (onStored) onStored(batch.metas || []);
        fs.rmSync(file, { force: true });
        out.batches++;
        out.points += (batch.points || []).length;
    }
    return out;
}

module.exports = { spool, replay, MAX_AGE_DAYS };
