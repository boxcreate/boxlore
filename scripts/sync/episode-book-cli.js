#!/usr/bin/env node
'use strict';

/**
 * Episode book maintenance (own-episode-IDs plan P1, section 8).
 *   node sync/episode-book-cli.js ensure   create the book tables and add missing columns (idempotent)
 *   node sync/episode-book-cli.js stats    row counts, served IDs, clashes and PI aliases
 */

const turso = require('./lib/turso');
const log = require('./lib/log');
const { ensureBookSchema, bookDb } = require('./lib/episode-book');

async function main() {
    const cmd = process.argv[2] || 'stats';
    turso.assertEnv();
    turso.beginStep('episode-book');
    const db = bookDb(turso);
    if (cmd === 'ensure') {
        const { added } = await ensureBookSchema(db);
        log.info(`Book tables ready${added.length ? `; added ${added.join(', ')}` : ''}`);
    } else if (cmd === 'stats') {
        const [[rows, shows, served, clashes]] = await db.query(
            'SELECT COUNT(*), COUNT(DISTINCT sid), COUNT(served_id), SUM(eid_clash) FROM episode_book');
        const [[aliases]] = await db.query('SELECT COUNT(*) FROM episode_pi_alias');
        const [[servedShows]] = await db.query('SELECT COUNT(*) FROM served_show');
        console.log(JSON.stringify({ rows, shows, served, clashes: clashes || 0, aliases, servedShows }));
    } else {
        console.error('usage: episode-book-cli.js ensure | stats');
        process.exitCode = 2;
    }
    turso.flushStats();
}

main().catch((e) => {
    log.error(e.message);
    process.exitCode = 1;
});
