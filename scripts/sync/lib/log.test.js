'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const log = require('./log');

describe('budgetProgress', () => {
    it('does not throw when done exceeds a tiny budget (repeat clamp)', () => {
        const prog = log.budgetProgress(1, 'embeddings', 50);
        // Simulate embedding more eps in one show than the run budget.
        assert.doesNotThrow(() => {
            for (let i = 0; i < 8; i++) prog.tick();
            prog.flush();
        });
        assert.equal(prog.count, 8);
    });
});
