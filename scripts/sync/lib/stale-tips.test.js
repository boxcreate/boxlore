'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { runStaleTips, staleShowIds, shouldReplace, summary, CHUNK } = require('./stale-tips');
const { catalogKey, recipeId } = require('./episode-recipe-id');

const OLD = 1_750_000_000;
const NEW = 1_790_000_000;

/** A podcasts table in memory behind stage 3's execute/rows interface; records every write. */
function fakeDb(tips) {
    const writes = [];
    return {
        writes,
        async execute(sql, args) {
            if (sql.trim().startsWith('SELECT')) {
                const rows = args.filter((id) => tips[id]).map((id) => [id, tips[id].id, tips[id].date]);
                return { rows };
            }
            writes.push({ sql, args });
            return { rows: [] };
        },
        rows: (res) => res.rows,
    };
}

function feeds(handler) {
    const calls = [];
    return {
        calls,
        fetchImpl: async (url, init) => {
            const body = JSON.parse(init.body);
            calls.push({ url, body });
            return handler(body);
        },
    };
}

const json = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });
const item = (id, date, extra = {}) => ({
    id, feedId: 7, guid: `guid-${id}`, title: `Episode ${id}`, datePublished: date,
    enclosureUrl: `https://cdn.example/${id}.mp3`, description: '<p>New</p>', ...extra,
});

const base = { feedsUrl: 'http://127.0.0.1:8098', registryPath: '/unused', now: () => 1_792_000_000_000 };

describe('stale tips (R5)', () => {
    it('off: no registry read, no feeds call, no write', async () => {
        const db = fakeDb({ 7: { id: '1', date: OLD } });
        const f = feeds(() => assert.fail('no call when off'));
        const r = await runStaleTips({ ...base, mode: 'off', due: ['7'], db, fetchImpl: f.fetchImpl, staleIds: () => assert.fail('no read') });
        assert.equal(summary(r), 'R5 off');
        assert.equal(db.writes.length, 0);
    });

    it('on: a newer feed episode becomes the tip under its served ID, key and recipe ID; nothing else moves', async () => {
        const db = fakeDb({ 7: { id: '500', date: OLD }, 8: { id: '600', date: NEW + 10 }, 9: { id: '700', date: OLD } });
        const recipe = 4503599627370777;
        const f = feeds((b) => json({ results: b.ids.map((id) => ({
            id, status: 200,
            item: id === 7 ? item(recipe, NEW) : id === 8 ? item(601, NEW) : null,
        })) }));
        const r = await runStaleTips({ ...base, mode: 'on', due: ['7', '8', '9', '10'], db, fetchImpl: f.fetchImpl, staleIds: () => new Set([7, 8, 9]), mediumOf: () => 'music' });
        assert.deepEqual(f.calls.map((c) => c.body), [{ ids: [7, 8, 9], episodes: true, mode: 'live' }], 'only flagged shows that were checked');
        assert.deepEqual([r.stale, r.answered, r.newer, r.updated, r.noItem], [3, 3, 1, 1, 1]);
        assert.equal(db.writes.length, 1);
        const { sql, args } = db.writes[0];
        assert.doesNotMatch(sql, /qdrant_vectorized/, 'stage 4 keeps following the Podcast Index pass');
        assert.equal(args[0], String(recipe), 'latest_ep_id is the served ID');
        assert.equal(args[2], NEW);
        assert.equal(args[12], 'music');
        const key = catalogKey(`guid-${recipe}`, `https://cdn.example/${recipe}.mp3`);
        assert.equal(args[14].value, key, 'key bound as text');
        assert.equal(args[15], recipeId(7, key));
        assert.equal(args[16], 7);
        assert.match(summary(r), /R5 on: 3 stale due, 3 answered, 1 tips from the feed, 1 without a served ID yet/);
    });

    it('shadow: counts newer feed episodes from the date summary, never asks for IDs, never writes', async () => {
        const db = fakeDb({ 7: { id: '500', date: OLD }, 8: { id: '600', date: NEW } });
        const f = feeds((b) => json({ results: b.ids.map((id) => ({ id, newest: { publishedAt: id === 7 ? NEW : NEW - 5 } })) }));
        const r = await runStaleTips({ ...base, mode: 'shadow', due: [7, 8], db, fetchImpl: f.fetchImpl, staleIds: () => new Set([7, 8]) });
        assert.deepEqual(f.calls[0].body, { ids: [7, 8], maxAgeMinutes: 1440 });
        assert.deepEqual([r.answered, r.newer, r.updated], [2, 1, 0]);
        assert.equal(db.writes.length, 0);
    });

    it('chunks of 40; a failed chunk is skipped and counted, the others still run', async () => {
        const ids = Array.from({ length: 85 }, (_, i) => 1000 + i);
        const tips = Object.fromEntries(ids.map((id) => [id, { id: '1', date: OLD }]));
        const db = fakeDb(tips);
        let n = 0;
        const f = feeds((b) => (++n === 2 ? json({ error: 'busy' }, 503) : json({ results: b.ids.map((id) => ({ id, item: item(id * 10, NEW) })) })));
        const warnings = [];
        const r = await runStaleTips({ ...base, mode: 'on', due: ids, db, fetchImpl: f.fetchImpl, staleIds: () => new Set(ids), warn: (m) => warnings.push(m) });
        assert.deepEqual(f.calls.map((c) => c.body.ids.length), [CHUNK, CHUNK, 5]);
        assert.equal(r.failedChunks, 1);
        assert.equal(r.updated, 45);
        assert.match(warnings[0], /HTTP 503/);
    });

    it('a failed tip read skips R5 for the run without failing the stage', async () => {
        const db = { execute: async () => { throw new Error('sqld HTTP 500'); }, rows: (r) => r.rows };
        const warnings = [];
        const r = await runStaleTips({ ...base, mode: 'on', due: [7], db, staleIds: () => new Set([7]), fetchImpl: () => assert.fail('no call'), warn: (m) => warnings.push(m) });
        assert.match(r.error, /tip read failed: sqld HTTP 500/);
        assert.match(warnings[0], /skipped this run/);
    });

    it('a registry it cannot read skips R5 for the run without failing it', async () => {
        const db = fakeDb({});
        const r = await runStaleTips({ ...base, mode: 'on', due: [7], db, registryPath: '/nonexistent/registry.db', fetchImpl: () => assert.fail('no call') });
        assert.match(r.error, /registry unreadable/);
        assert.match(summary(r), /skipped/);
    });

    it('replace only a playable, different and newer episode', () => {
        const tip = { id: '500', date: OLD };
        assert.equal(shouldReplace(tip, item(501, NEW)), true);
        assert.equal(shouldReplace(tip, item(500, NEW)), false, 'same episode');
        assert.equal(shouldReplace(tip, item(501, OLD)), false, 'not newer');
        assert.equal(shouldReplace(tip, item(501, NEW, { enclosureUrl: '' })), false, 'old builds drop items without audio');
        assert.equal(shouldReplace(undefined, item(501, NEW)), true, 'a show without a tip takes one');
        assert.equal(shouldReplace(tip, null), false);
    });

    it('reads stale flags from the published registry', () => {
        const { DatabaseSync } = require('node:sqlite');
        const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'r5-registry-'));
        try {
            const file = path.join(dir, 'registry.db');
            const db = new DatabaseSync(file);
            db.exec('CREATE TABLE show_flags (id INTEGER PRIMARY KEY, address_repair INTEGER, episode_stale INTEGER, verified_at INTEGER)');
            db.exec('INSERT INTO show_flags VALUES (1, 0, 1, 1), (2, 1, 0, 1), (3, 0, 1, 1)');
            db.close();
            assert.deepEqual([...staleShowIds(file)].sort(), [1, 3]);
        } finally {
            fs.rmSync(dir, { recursive: true, force: true });
        }
    });
});
