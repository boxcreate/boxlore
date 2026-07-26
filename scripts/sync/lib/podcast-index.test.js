'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const pi = require('./podcast-index');

describe('podcast-index failsafes', () => {
    it('parseRetryAfterMs honors numeric Retry-After seconds', () => {
        const ms = pi.parseRetryAfterMs('12', 1000);
        assert.equal(ms, 12_000);
    });

    it('parseRetryAfterMs falls back to floor when header missing', () => {
        const ms = pi.parseRetryAfterMs(null, 1000);
        assert.ok(ms >= 10_000);
        assert.ok(ms <= 120_000);
    });

    it('isTransientHttp includes 429/403/5xx', () => {
        assert.equal(pi.isTransientHttp(429), true);
        assert.equal(pi.isTransientHttp(403), true);
        assert.equal(pi.isTransientHttp(500), true);
        assert.equal(pi.isTransientHttp(404), false);
    });
});
