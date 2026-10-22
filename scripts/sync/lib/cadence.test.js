'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const staleness = require('./staleness');
const {
    paceFromDates, paceFromSpan, notePace, noteNewest, intervalFor, appleForces,
    shadowPlan, compare, seedFromBook, openApple, summary, HOUR_MS,
} = require('./cadence');

const NOW = 1_792_700_000_000;
const NOW_S = NOW / 1000;
const DAY_S = 86_400;
const jit = (id) => staleness.jitterFactor(id);

test('pace: the average gap over the newest 11 dated episodes; null under 3', () => {
    const weekly = Array.from({ length: 20 }, (_, i) => NOW_S - i * 7 * DAY_S);
    assert.equal(paceFromDates(weekly), 168);
    // Only the newest 11 count: an old daily stretch doesn't pull a weekly show down.
    const mixed = [...weekly.slice(0, 11), ...Array.from({ length: 30 }, (_, i) => NOW_S - 80 * 7 * DAY_S - i * DAY_S)];
    assert.equal(paceFromDates(mixed), 168);
    assert.equal(paceFromDates([NOW_S, NOW_S - DAY_S, 0, null]), null, 'undated episodes are ignored');
    assert.equal(paceFromDates([NOW_S, NOW_S - DAY_S, NOW_S - 2 * DAY_S]), 24);
    assert.equal(paceFromDates(null), null);
    assert.equal(paceFromSpan(11, NOW_S - 10 * DAY_S, NOW_S), 24, 'the book seed uses the same rule');
    assert.equal(paceFromSpan(2, 0, NOW_S), null);
});

test('notePace and noteNewest keep the newest date we know', () => {
    const rec = notePace({ p: NOW_S + 5 }, [NOW_S, NOW_S - DAY_S, NOW_S - 2 * DAY_S]);
    assert.deepEqual(rec, { p: NOW_S + 5, g: 24 });
    assert.equal(noteNewest({}, String(NOW_S)).p, NOW_S);
    assert.deepEqual(noteNewest({ p: 5 }, undefined), { p: 5 });
    assert.deepEqual(notePace({}, [NOW_S]), { p: NOW_S }, 'one episode: date but no pace');
});

test('interval: news hourly, a quarter of the gap within 3–48 h, tiers when the pace is unknown', () => {
    const ctx = { nowS: NOW_S };
    assert.deepEqual(intervalFor({ n: 1, g: 500 }, 'a', ctx), { ms: HOUR_MS * jit('a'), reason: 'news' });
    assert.deepEqual(intervalFor({ g: 24 }, 'b', ctx), { ms: 6 * HOUR_MS * jit('b'), reason: 'pace' });
    assert.equal(intervalFor({ g: 2 }, 'c', ctx).ms, 3 * HOUR_MS * jit('c'), 'never under 3 h');
    assert.equal(intervalFor({ g: 24 * 30 }, 'd', ctx).ms, 48 * HOUR_MS * jit('d'), 'never over 48 h while active');
    assert.equal(intervalFor({ g: 0 }, 'e', ctx).ms, 3 * HOUR_MS * jit('e'), 'a burst show is still a pace');
    assert.deepEqual(intervalFor({ x: 1 }, 'f', ctx), { ms: staleness.thresholdMs({ x: 1 }, 'f'), reason: 'tier' });
});

test('dormant: weekly only when both our newest date and a fresh Apple answer are over 180 days old', () => {
    const rec = { g: 24 * 30, p: NOW_S - 200 * DAY_S };
    const quiet = { releaseDate: NOW_S - 190 * DAY_S, verifiedAt: NOW_S - 2 * DAY_S };
    assert.deepEqual(intervalFor(rec, 'g', { nowS: NOW_S, apple: quiet }), { ms: 7 * 24 * HOUR_MS * jit('g'), reason: 'dormant' });
    assert.equal(intervalFor(rec, 'g', { nowS: NOW_S }).reason, 'pace', 'no Apple answer: not dormant');
    assert.equal(intervalFor(rec, 'g', { nowS: NOW_S, apple: { ...quiet, verifiedAt: NOW_S - 40 * DAY_S } }).reason, 'pace', 'stale Apple answer');
    assert.equal(intervalFor(rec, 'g', { nowS: NOW_S, apple: { ...quiet, releaseDate: NOW_S - 3 * DAY_S } }).reason, 'pace', 'Apple has a recent episode');
    assert.equal(intervalFor({ ...rec, p: NOW_S - 100 * DAY_S }, 'g', { nowS: NOW_S, apple: quiet }).reason, 'pace');
    assert.equal(intervalFor({ g: 24 * 30 }, 'g', { nowS: NOW_S, apple: quiet }).reason, 'pace', 'no newest date: not dormant');
});

test('Apple forces a check for a newer episode until we have checked hours past its date', () => {
    const apple = { releaseDate: NOW_S - 2 * 3600, verifiedAt: NOW_S };
    assert.equal(appleForces({ p: NOW_S - 7 * DAY_S, c: NOW - 3 * HOUR_MS }, apple), true);
    assert.equal(appleForces({ p: NOW_S - 7 * DAY_S, c: (apple.releaseDate + 7 * 3600) * 1000 }, apple), false, 'checked well after it');
    assert.equal(appleForces({ p: apple.releaseDate - 600, c: 0 }, apple), false, 'within an hour of ours: same episode');
    assert.equal(appleForces({ p: NOW_S, c: 0 }, apple), false);
    assert.equal(appleForces({ c: 0 }, null), false);
    assert.equal(appleForces({ c: 0 }, { releaseDate: 0 }), false);
});

test('shadow plan: never-checked, Apple-forced and interval-due shows; daily check estimate', () => {
    const shows = {
        news: { n: 1, c: NOW - 2 * HOUR_MS },
        daily: { g: 24, c: NOW - 12 * HOUR_MS, p: NOW_S - DAY_S },
        weekly: { g: 168, c: NOW - 12 * HOUR_MS, p: NOW_S - DAY_S, i: 11 },
        forced: { g: 168, c: NOW - 12 * HOUR_MS, p: NOW_S - 7 * DAY_S, i: 22 },
        fresh: {},
    };
    const apple = { 11: { releaseDate: NOW_S - DAY_S, verifiedAt: NOW_S }, 22: { releaseDate: NOW_S - 3600, verifiedAt: NOW_S } };
    const plan = shadowPlan(Object.keys(shows), shows, { now: NOW, appleOf: (i) => apple[i] || null });
    assert.deepEqual([...plan.due].sort(), ['daily', 'forced', 'fresh', 'news']);
    assert.deepEqual(plan.counts, { never: 1, appleForced: 1, news: 1, dormant: 0, pace: 3, tier: 1 });
    assert.equal(plan.dueAt.get('weekly'), shows.weekly.c + 42 * HOUR_MS * jit('weekly'));
    assert.equal(plan.dueAt.has('fresh'), false);
    const perDay = 24 / jit('news') + 24 / (6 * jit('daily')) + 24 / (42 * jit('weekly')) + 24 / (42 * jit('forced')) +
        24 / (staleness.thresholdMs({}, 'fresh') / HOUR_MS);
    assert.equal(plan.perDay, Math.round(perDay));
    assert.ok(plan.perDayNow > 0);
    assert.deepEqual(shadowPlan(null, {}).due.size, 0);
});

test('compare: skipped, earlier, and new tips the cadence would have found later', () => {
    const plan = {
        due: new Set(['a', 'b', 'c']),
        dueAt: new Map([['d', NOW + 5 * HOUR_MS], ['e', NOW + 1 * HOUR_MS], ['f', NOW + 30 * HOUR_MS]]),
    };
    const cmp = compare(plan, ['a', 'd', 'e', 'f', 'g'], ['a', 'd', 'e', 'f', 'f'], NOW);
    assert.deepEqual(cmp, { wouldCheck: 3, current: 5, skipped: 4, earlier: 2, late: 3, lateMedianH: 5, lateMaxH: 30 });
    assert.deepEqual(compare(plan, [], [], NOW), { wouldCheck: 3, current: 0, skipped: 0, earlier: 3, late: 0, lateMedianH: 0, lateMaxH: 0 });
});

test('seed from the book: candidate shows get pace and newest date; others are left alone', async () => {
    const book = { query: async (sql) => {
        assert.match(sql, /ROW_NUMBER\(\) OVER \(PARTITION BY sid ORDER BY published_at DESC\)/);
        return [['1', '11', String(NOW_S - 10 * DAY_S), String(NOW_S)], ['2', '2', '100', '200'], ['3', '11', '0', '1']];
    } };
    const shows = { 2: { p: 500, g: 7 } };
    assert.equal(await seedFromBook(book, shows, ['1', '2']), 2);
    assert.deepEqual(shows, { 1: { g: 24, p: NOW_S }, 2: { p: 500, g: 7 } }, 'too few episodes keep the old pace; dates never go back');
});

test('Apple snapshot: read-only lookups; a missing file means no Apple, not a failure', () => {
    const missing = openApple(path.join(os.tmpdir(), 'no-such-dir', 'apple_latest.db'));
    assert.ok(missing.error);
    assert.equal(missing.lookup(1), null);
    missing.close();

    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'cadence-'));
    const file = path.join(dir, 'apple_latest.db');
    const { DatabaseSync } = require('node:sqlite');
    const db = new DatabaseSync(file);
    db.exec('CREATE TABLE apple_latest (itunes_id INTEGER PRIMARY KEY, release_date INTEGER, track_count INTEGER, verified_at INTEGER)');
    db.prepare('INSERT INTO apple_latest VALUES (?, ?, ?, ?)').run(1200361736, 1792403100, 2754, 1792428098);
    db.close();
    const apple = openApple(file);
    assert.equal(apple.error, null);
    assert.deepEqual(apple.lookup(1200361736), { releaseDate: 1792403100, verifiedAt: 1792428098 });
    assert.equal(apple.lookup(5), null);
    assert.throws(() => new DatabaseSync(file, { readOnly: true }).exec('DELETE FROM apple_latest'));
    apple.close();
    fs.rmSync(dir, { recursive: true, force: true });
});

test('summary: one report line', () => {
    const plan = { counts: { never: 2, appleForced: 3, news: 4, dormant: 5, pace: 6, tier: 7 }, perDay: 900, perDayNow: 1000 };
    const cmp = { wouldCheck: 10, current: 12, skipped: 4, earlier: 2, late: 2, lateMedianH: 2.25, lateMaxH: 30.4 };
    assert.equal(summary('shadow', plan, cmp, { paceShows: 6, candidates: 24 }),
        'cadence shadow: would check 10 (today 12; 4 skipped, 2 earlier, 3 Apple-forced, 2 never checked) · ' +
        '2 new tips not yet due (median 2.3 h, max 30 h late) · checks/day 900 vs 1000 · ' +
        'news 4, dormant 5, pace 6, tier 7 · pace known 25%');
    assert.match(summary('shadow', plan, { ...cmp, late: 0 }, { appleError: 'gone' }), /0 new tips not yet due · .* · Apple unread \(gone\)$/);
    assert.equal(summary('off', null, null), 'cadence off');
});
