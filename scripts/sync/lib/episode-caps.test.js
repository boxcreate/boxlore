'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { episodeCapForCountries, EPISODE_CAP_DEFAULT } = require('./config');

describe('episodeCapForCountries', () => {
    it('defaults to 20', () => {
        assert.equal(episodeCapForCountries([]), EPISODE_CAP_DEFAULT);
        assert.equal(episodeCapForCountries(['in']), 20);
        assert.equal(episodeCapForCountries(['es', 'fr']), 20);
    });

    it('uses 50 when us or gb is present', () => {
        assert.equal(episodeCapForCountries(['us']), 50);
        assert.equal(episodeCapForCountries(['gb']), 50);
        assert.equal(episodeCapForCountries(['in', 'us']), 50);
        assert.equal(episodeCapForCountries(['es', 'gb', 'nl']), 50);
    });
});
