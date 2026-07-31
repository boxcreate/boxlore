'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const cfg = require('./config');
const {
    isRelaxedOnly,
    applyCountryCheckFlag,
    checkTier,
    thresholdMs,
    planDue,
    policySummary,
} = require('./staleness');

test('core vs relaxed country sets are disjoint and cover ALL', () => {
    const core = new Set(cfg.CORE_CHECK_COUNTRIES);
    const relaxed = new Set(cfg.RELAXED_CHECK_COUNTRIES);
    for (const c of core) assert.equal(relaxed.has(c), false);
    for (const c of cfg.ALL_COUNTRIES) {
        assert.ok(core.has(c) || relaxed.has(c), `${c} missing check cadence`);
    }
});

test('isRelaxedOnly: core presence wins', () => {
    assert.equal(isRelaxedOnly('de,nl'), true);
    assert.equal(isRelaxedOnly('us,de'), false);
    assert.equal(isRelaxedOnly(['ES', 'br']), true);
    assert.equal(isRelaxedOnly(''), false);
});

test('checkTier: news > relaxed > core', () => {
    assert.equal(checkTier({ n: 1, x: 1 }), 'news');
    assert.equal(checkTier({ x: 1 }), 'relaxed');
    assert.equal(checkTier({}), 'core');
});

test('thresholdMs uses 8h / 24h / 48h bases', () => {
    const id = 'feed-123';
    const news = thresholdMs({ n: 1 }, id);
    const core = thresholdMs({}, id);
    const relaxed = thresholdMs({ x: 1 }, id);
    // same jitter factor for same id → exact ratios
    assert.ok(Math.abs(news / cfg.NEWS_STALE_MS - core / cfg.REGULAR_STALE_MS) < 1e-9);
    assert.ok(Math.abs(core / cfg.REGULAR_STALE_MS - relaxed / cfg.RELAXED_STALE_MS) < 1e-9);
    assert.ok(relaxed > core);
    assert.ok(core > news);
});

test('jitter spreads core thresholds across ±STALENESS_JITTER', () => {
    const { jitterFactor } = require('./staleness');
    const factors = ['1', '2', '99', 'abc', 'feed-9', 'feed-42'].map(jitterFactor);
    const lo = Math.min(...factors);
    const hi = Math.max(...factors);
    assert.ok(lo >= 1 - cfg.STALENESS_JITTER - 1e-9);
    assert.ok(hi <= 1 + cfg.STALENESS_JITTER + 1e-9);
    assert.ok(hi - lo > cfg.STALENESS_JITTER, 'ids should not all cluster at one extreme');
    assert.equal(policySummary().jitterPct, Math.round(cfg.STALENESS_JITTER * 100));
});

test('applyCountryCheckFlag sets x only for relaxed-only', () => {
    const a = applyCountryCheckFlag({}, 'de');
    assert.equal(a.x, 1);
    const b = applyCountryCheckFlag({ x: 1 }, 'us,de');
    assert.equal(b.x, undefined);
});

test('planDue splits never / news / core / relaxed', () => {
    const now = 1_000_000_000_000;
    const shows = {
        a: {}, // never
        b: { n: 1, c: now - cfg.NEWS_STALE_MS * 2 },
        c: { c: now - cfg.REGULAR_STALE_MS * 2 },
        d: { x: 1, c: now - cfg.RELAXED_STALE_MS * 2 },
        e: { x: 1, c: now - 1000 }, // fresh relaxed
    };
    const plan = planDue(['a', 'b', 'c', 'd', 'e'], shows, now);
    assert.equal(plan.neverChecked, 1);
    assert.equal(plan.staleNews, 1);
    assert.equal(plan.staleCore, 1);
    assert.equal(plan.staleRelaxed, 1);
    assert.equal(plan.poolNews, 1);
    assert.equal(plan.poolCore, 2); // a (default) + c
    assert.equal(plan.poolRelaxed, 2);
    assert.deepEqual(plan.allDue.sort(), ['a', 'b', 'c', 'd']);
});

test('policySummary mentions all three windows', () => {
    const p = policySummary();
    assert.match(p.label, /News 8h/);
    assert.match(p.label, /24h/);
    assert.match(p.label, /48h/);
    assert.ok(p.coreCountries.includes('us'));
    assert.ok(p.relaxedCountries.includes('de'));
});
