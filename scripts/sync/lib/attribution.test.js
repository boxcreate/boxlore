'use strict';

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const attribution = require('./attribution');
const recipe = require('./episode-recipe-id');

const DAY = 24 * 60 * 60;
// The regex the ops panel uses to read reasons from a run report line.
const PANEL_REASONS = /reasons((?:\s+[a-z_-]+=\d+)+)/i;

function dayName(tSeconds) {
    return new Date(tSeconds * 1000).toISOString().slice(0, 10);
}

describe('attribution log', () => {
    let dir;

    beforeEach(() => {
        dir = fs.mkdtempSync(path.join(os.tmpdir(), 'attr-'));
        process.env.SYNC_ATTRIBUTION_DIR = dir;
        attribution.resetForTests();
    });

    afterEach(() => {
        delete process.env.SYNC_ATTRIBUTION_DIR;
        fs.rmSync(dir, { recursive: true, force: true });
    });

    it('writes compact lines with recipe IDs and audio hashes', () => {
        attribution.episodeStored('75075', {
            id: 41000000001,
            guid: ' Buzzsprout-19865045 ',
            enclosureUrl: 'https://cdn.example/a.mp3',
            datePublished: 1760000000,
        }, 'cold');
        attribution.episodeStored('75075', { id: 41000000002, guid: '', enclosureUrl: '' }, 'tip');
        assert.equal(attribution.flush(), 2);
        const events = attribution.readAll();
        assert.equal(events.length, 2);
        assert.equal(events[0].ev, 'ep+');
        assert.equal(events[0].pid, 75075);
        assert.equal(events[0].rid, 8665876719383229);
        assert.equal(events[0].rid, recipe.recipeIdForItem(75075, 'Buzzsprout-19865045', null));
        assert.match(events[0].ah, /^[0-9a-f]{12}$/);
        assert.equal(events[0].q, 'cold');
        assert.equal('rid' in events[1], false, 'no catalog key, no recipe ID');
        assert.equal('ah' in events[1], false);
    });

    it('never throws when the directory is unwritable', () => {
        process.env.SYNC_ATTRIBUTION_DIR = path.join(dir, 'file-not-dir');
        fs.writeFileSync(process.env.SYNC_ATTRIBUTION_DIR, 'x');
        attribution.showImported(1, { via: 'api' });
        assert.equal(attribution.flush(), 0);
    });

    it('gzips finished days and drops expired ones', () => {
        const now = Math.floor(Date.now() / 1000);
        const old = dayName(now - (attribution.RETENTION_DAYS + 2) * DAY);
        const yesterday = dayName(now - DAY);
        fs.writeFileSync(path.join(dir, `${old}.jsonl`), '{"t":1,"ev":"show+","pid":1}\n');
        fs.writeFileSync(path.join(dir, `${yesterday}.jsonl`), `{"t":${now - DAY},"ev":"show+","pid":2}\n`);
        attribution.showImported(3, { via: 'dump' });
        attribution.flush();
        const names = fs.readdirSync(dir).sort();
        assert.deepEqual(names, [`${yesterday}.jsonl.gz`, `${dayName(now)}.jsonl`].sort());
        const gz = zlib.gunzipSync(fs.readFileSync(path.join(dir, `${yesterday}.jsonl.gz`))).toString();
        assert.match(gz, /"pid":2/);
        assert.deepEqual(attribution.readAll().map((e) => e.pid), [2, 3]);
        assert.deepEqual(attribution.readAll({ pids: new Set([3]) }).map((e) => e.pid), [3]);
    });
});

describe('attribution classify', () => {
    const t0 = 1_760_000_000;
    const ep = (t, pid, eid, extra = {}) => ({ t, ev: 'ep+', pid, eid, ...extra });
    const del = (t, pid, eid, why, extra = {}) => ({ t, ev: 'ep-', pid, eid, why, ...extra });

    it('names each reason', () => {
        const events = [
            del(t0, 1, 11, 'cap'),
            del(t0, 1, 12, 'prune'),
            del(t0, 2, 21, 'cap', { ah: 'aaaaaaaaaaaa', pub: t0 - 100 * DAY }),
            del(t0, 2, 22, 'cap', { pub: t0 - 200 * DAY }),
            { t: t0, ev: 'show-', pid: 3, n: 15 },
            { t: t0 + DAY, ev: 'show+', pid: 3, via: 'dump' },
            ep(t0 + 2 * DAY, 1, 11, { pub: t0 - 50 * DAY }),
            ep(t0 + 2 * DAY, 1, 12, { pub: t0 - 50 * DAY }),
            ep(t0 + 2 * DAY, 2, 91, { ah: 'aaaaaaaaaaaa', pub: t0 - 100 * DAY }),
            ep(t0 + 2 * DAY, 2, 92, { pub: t0 - 200 * DAY }),
            ep(t0 + 2 * DAY, 4, 41, { pub: t0 + 2 * DAY - 3600 }),
            ep(t0 + 2 * DAY, 3, 31, { pub: t0 - 30 * DAY, q: 'cold' }),
            ep(t0 + 2 * DAY, 5, 51, { pub: t0 - 30 * DAY, q: 'cold' }),
            ep(t0 + 2 * DAY, 6, 61, { pub: t0 - 30 * DAY, q: 'inc' }),
        ];
        const { stored, shows, deleted } = attribution.classify(events, { from: t0 + DAY });
        assert.deepEqual(stored, {
            're-embed-cap': 1,
            're-embed-prune': 1,
            'id-change': 2,
            'new-episode': 1,
            'returning-show': 1,
            'first-coverage': 1,
            backfill: 1,
        });
        assert.equal(shows.imported, 1);
        assert.equal(shows.returning, 1);
        assert.deepEqual(deleted, { prune: 0, cap: 0, cleanup: 0, retention: 0 }, 'deletions before the window are history only');
    });

    it('matches ID changes by audio before publish time', () => {
        const events = [
            del(t0, 2, 21, 'cap', { ah: 'aaaaaaaaaaaa', pub: t0 - 9 * DAY }),
            // Same publish second, different audio: two episodes, not an ID change.
            ep(t0 + DAY, 2, 99, { ah: 'bbbbbbbbbbbb', pub: t0 - 9 * DAY }),
        ];
        assert.equal(attribution.classify(events).stored.backfill, 1);
    });

    it('consumes a deletion once it is re-embedded', () => {
        const events = [
            del(t0, 1, 11, 'cap'),
            ep(t0 + 1, 1, 11, { pub: t0 - 99 * DAY }),
            ep(t0 + 2, 1, 11, { pub: t0 - 99 * DAY }),
        ];
        const { stored } = attribution.classify(events);
        assert.equal(stored['re-embed-cap'], 1);
        assert.equal(stored.backfill, 1);
    });

    it('measures how long shows stay and counts cleanup points', () => {
        const events = [
            { t: t0, ev: 'show+', pid: 7 },
            { t: t0 + 10 * DAY, ev: 'show-', pid: 7, n: 16 },
            { t: t0 + 11 * DAY, ev: 'show+', pid: 7 },
        ];
        const { shows, deleted } = attribution.classify(events);
        assert.deepEqual(shows.staysDays, [10]);
        assert.equal(shows.returning, 1);
        assert.equal(deleted.cleanup, 16);
    });

    it('counts a kept show\'s deleted episodes as cleanup and expired points as retention', () => {
        const events = [
            { t: t0, ev: 'show-', pid: 7, n: 15, kept: 5 },
            { t: t0 + 90 * DAY, ev: 'exp', pid: 7, n: 5 },
            { t: t0 + 91 * DAY, ev: 'show+', pid: 7 },
        ];
        const { shows, deleted } = attribution.classify(events);
        assert.deepEqual([deleted.cleanup, deleted.retention, shows.deleted, shows.returning], [15, 5, 1, 1]);
    });

    it('formats run reasons the way the ops panel reads them', () => {
        const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'attr-run-'));
        try {
            const now = Math.floor(Date.now() / 1000);
            const lines = [
                del(now - 10, 1, 11, 'cap'),
                ep(now - 5, 1, 11, { pub: now - 99 * DAY }),
                ep(now - 5, 1, 12, { pub: now - 3600 }),
                ep(now - 5, 2, 21, { pub: now - 3600 }),
            ];
            fs.writeFileSync(path.join(dir, `${dayName(now)}.jsonl`), `${lines.map((l) => JSON.stringify(l)).join('\n')}\n`);
            const { counts, text } = attribution.runReasons({ since: now - 6, pids: new Set([1]), dir });
            assert.deepEqual(counts, { 're-embed-cap': 1, 'new-episode': 1 });
            assert.equal(text, 'reasons re-embed-cap=1 new-episode=1');
            assert.match(`2 done, partial 0, 0 errors, ${text}`, PANEL_REASONS);
            assert.equal(attribution.runReasons({ since: now, pids: new Set(), dir }).text, '');
        } finally {
            fs.rmSync(dir, { recursive: true, force: true });
        }
    });
});
