'use strict';

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');

const piUnavailable = require('./pi-unavailable');

describe('pi-unavailable denylist', () => {
    let tmpFile;

    beforeEach(() => {
        tmpFile = path.join(os.tmpdir(), `pi-unavail-${Date.now()}-${Math.random().toString(16).slice(2)}.json`);
    });

    afterEach(() => {
        try { fs.unlinkSync(tmpFile); } catch { /* ignore */ }
    });

    it('load returns empty doc when file missing', () => {
        const doc = piUnavailable.load(tmpFile);
        assert.deepEqual(doc, { updatedAt: 0, ids: {} });
    });

    it('add / activeIdSet / remove round-trip', () => {
        const doc = piUnavailable.emptyDoc();
        const now = 1_000_000;
        const added = piUnavailable.add(doc, ['111', '222'], 'not_in_dump', { now });
        assert.equal(added, 2);
        assert.equal(piUnavailable.add(doc, ['111'], 'api_not_found', { now: now + 1 }), 0);
        assert.equal(doc.ids['111'].reason, 'api_not_found');

        const active = piUnavailable.activeIdSet(doc, { now: now + 1, ttlMs: 60_000 });
        assert.ok(active.has('111'));
        assert.ok(active.has('222'));

        assert.equal(piUnavailable.remove(doc, ['111']), 1);
        assert.equal(piUnavailable.activeIdSet(doc, { now: now + 1, ttlMs: 60_000 }).has('111'), false);
    });

    it('pruneExpired drops stale entries and keeps fresh ones', () => {
        const doc = piUnavailable.emptyDoc();
        const now = 10_000_000;
        piUnavailable.add(doc, ['old'], 'not_in_dump', { now: now - 100 });
        piUnavailable.add(doc, ['fresh'], 'api_not_found', { now: now - 10 });

        const pruned = piUnavailable.pruneExpired(doc, { now, ttlMs: 50 });
        assert.equal(pruned, 1);
        assert.equal(doc.ids.old, undefined);
        assert.ok(doc.ids.fresh);

        const active = piUnavailable.activeIdSet(doc, { now, ttlMs: 50 });
        assert.deepEqual([...active], ['fresh']);
    });

    it('save then load preserves entries', () => {
        const doc = piUnavailable.emptyDoc();
        piUnavailable.add(doc, ['42'], 'api_not_found', { now: 123 });
        piUnavailable.save(doc, tmpFile);

        const loaded = piUnavailable.load(tmpFile);
        assert.equal(loaded.ids['42'].reason, 'api_not_found');
        assert.equal(loaded.ids['42'].seenAt, 123);
    });

    it('filters missing lists the way precheck should', () => {
        const doc = piUnavailable.emptyDoc();
        const now = Date.now();
        piUnavailable.add(doc, ['9', '8'], 'not_in_dump', { now });
        const blocked = piUnavailable.activeIdSet(doc, { now, ttlMs: 60_000 });
        const missingRaw = ['1', '8', '2', '9'];
        const missing = missingRaw.filter(id => !blocked.has(id));
        assert.deepEqual(missing, ['1', '2']);
    });
});
