'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { STAGE3_DEADLINE, RUN_DEADLINE, deadlineFrom, stageDeadline, timeUp, recordMetrics } = require('./run-budget');

test('deadlines come from epoch seconds; unset or malformed means no limit', () => {
    assert.equal(deadlineFrom(RUN_DEADLINE, { [RUN_DEADLINE]: '1792800000' }), 1_792_800_000_000);
    assert.equal(deadlineFrom(RUN_DEADLINE, { [RUN_DEADLINE]: ' 1792800000 ' }), 1_792_800_000_000);
    for (const bad of [undefined, '', '0', '1.5e9', 'soon', '-5']) {
        assert.equal(deadlineFrom(RUN_DEADLINE, { [RUN_DEADLINE]: bad }), Infinity, String(bad));
    }
});

test('a stage stops at the earlier of its own deadline and the run\'s', () => {
    assert.equal(stageDeadline(STAGE3_DEADLINE, { [STAGE3_DEADLINE]: '100', [RUN_DEADLINE]: '200' }), 100_000);
    assert.equal(stageDeadline(STAGE3_DEADLINE, { [STAGE3_DEADLINE]: '300', [RUN_DEADLINE]: '200' }), 200_000);
    assert.equal(stageDeadline(STAGE3_DEADLINE, { [RUN_DEADLINE]: '200' }), 200_000);
    assert.equal(stageDeadline(STAGE3_DEADLINE, {}), Infinity);
});

test('timeUp at and after the deadline only', () => {
    assert.equal(timeUp(1000, 999), false);
    assert.equal(timeUp(1000, 1000), true);
    assert.equal(timeUp(Infinity), false);
});

test('metrics: one JSON line per call; no file configured means nothing written', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'run-metrics-'));
    const file = path.join(dir, 'm.jsonl');
    try {
        assert.equal(recordMetrics('03-sync-episodes', { checked: 5, timeUp: true }, { SYNC_METRICS_FILE: file }), true);
        assert.equal(recordMetrics('04-vectorize-episodes', { embedded: 2 }, { SYNC_METRICS_FILE: file }), true);
        const lines = fs.readFileSync(file, 'utf8').trim().split('\n').map((l) => JSON.parse(l));
        assert.deepEqual(lines.map((l) => [l.stage, l.checked ?? l.embedded]), [['03-sync-episodes', 5], ['04-vectorize-episodes', 2]]);
        assert.equal(typeof lines[0].t, 'number');
        assert.equal(recordMetrics('x', {}, {}), false);
        assert.equal(recordMetrics('x', {}, { SYNC_METRICS_FILE: path.join(dir, 'missing', 'm.jsonl') }), false, 'never throws');
    } finally {
        fs.rmSync(dir, { recursive: true, force: true });
    }
});
