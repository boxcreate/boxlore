'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const cfg = require('./config');
const {
    isRelaxedOnly,
    applyCountryCheckFlag,
    relaxedTagsKey,
    candidateRefreshReason,
    CANDIDATE_CACHE_MAX_AGE_MS,
    checkTier,
    thresholdMs,
    planDue,
    policySummary,
} = require('./staleness');

const NOW = 1_792_700_000_000;

/** A freshly refreshed cache: `core` core shows, `news` news shows, `relaxed` relaxed-only shows. */
function cachedState({ core = 0, news = 0, relaxed = 0, tagsKey = relaxedTagsKey() } = {}) {
    const shows = {};
    const ids = [];
    const add = (rec) => {
        const id = String(ids.length + 1);
        ids.push(id);
        shows[id] = { c: NOW - 1000, i: 900000 + ids.length, ...rec };
    };
    for (let k = 0; k < core; k++) add({});
    for (let k = 0; k < news; k++) add({ n: 1 });
    for (let k = 0; k < relaxed; k++) add({ x: 1 });
    return {
        version: 2,
        candidatesRefreshedAt: NOW - 60 * 60 * 1000,
        chartsRefreshedAt: NOW - 2 * 60 * 60 * 1000,
        relaxedTagsCore: tagsKey,
        candidateIds: ids,
        shows,
    };
}

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

test('relaxedTagsKey: the sorted, lowercased core list', () => {
    assert.equal(relaxedTagsKey(['US', 'gb', 'us']), 'gb,us');
    assert.equal(relaxedTagsKey(), relaxedTagsKey([...cfg.CORE_CHECK_COUNTRIES].reverse()));
});

test('candidate refresh: core shows without x keep the cached list (regression: refreshed every run)', () => {
    // Shaped like the runner's cache: about 10,400 core shows, and core shows carry no x.
    const st = cachedState({ core: 10_400, news: 235, relaxed: 15_400 });
    const untagged = st.candidateIds.filter((id) => st.shows[id].x === undefined && st.shows[id].n !== 1).length;
    assert.ok(untagged > st.candidateIds.length * 0.05, 'the old count called these untagged');
    assert.equal(candidateRefreshReason(st, { now: NOW }), null);
    assert.equal(candidateRefreshReason(st, { now: NOW, cadence: true }), null);
});

test('candidate refresh: a cache never tagged, or tagged for other core countries, refreshes once', () => {
    const st = cachedState({ core: 300, relaxed: 40 });
    delete st.relaxedTagsCore;
    assert.equal(candidateRefreshReason(st, { now: NOW }), 'apply relaxed(48h) tags');
    assert.equal(
        candidateRefreshReason(cachedState({ core: 10, tagsKey: 'gb,us' }), { now: NOW, tagsKey: 'fr,gb,us' }),
        'apply relaxed(48h) tags',
    );

    // What the refresh does: tag every candidate and record the key. The next run uses the cache.
    for (const id of st.candidateIds) applyCountryCheckFlag(st.shows[id], st.shows[id].x ? 'de' : 'us,de');
    st.candidatesRefreshedAt = NOW;
    st.relaxedTagsCore = relaxedTagsKey();
    assert.equal(candidateRefreshReason(st, { now: NOW + 1000 }), null);
});

test('candidate refresh: the cadence still refreshes when most candidates lack an iTunes ID', () => {
    const st = cachedState({ core: 1000 });
    for (const id of st.candidateIds.slice(0, 60)) delete st.shows[id].i;
    assert.match(candidateRefreshReason(st, { now: NOW, cadence: true }), /^record iTunes IDs \(60 shows/);
    assert.equal(candidateRefreshReason(st, { now: NOW }), null, 'cadence off: iTunes IDs not needed');
    for (const id of st.candidateIds.slice(0, 10)) st.shows[id].i = 1;
    assert.equal(candidateRefreshReason(st, { now: NOW, cadence: true }), null, '50 or fewer missing: cached');
});

test('candidate refresh: no list, newer charts and an expired cache still refresh', () => {
    const st = cachedState({ core: 5 });
    assert.equal(candidateRefreshReason({ version: 2, shows: {} }, { now: NOW }), 'no cached list');
    assert.equal(candidateRefreshReason({ ...st, chartsRefreshedAt: NOW - 1000 }, { now: NOW }), 'charts refreshed since');
    assert.equal(
        candidateRefreshReason(st, { now: st.candidatesRefreshedAt + CANDIDATE_CACHE_MAX_AGE_MS + 1 }),
        'cache expired',
    );
    // Stage 2 forces a refresh by zeroing the refresh time.
    assert.notEqual(candidateRefreshReason({ ...st, candidatesRefreshedAt: 0 }, { now: NOW }), null);
});
