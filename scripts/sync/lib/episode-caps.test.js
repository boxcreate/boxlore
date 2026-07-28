'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { episodeCapForCountries, EPISODE_CAP_DEFAULT } = require('./config');
const { capsFromCountriesByItunes, ID_CHUNK } = require('./episode-caps');

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

describe('capsFromCountriesByItunes', () => {
    it('derives caps from preloaded chart map without Turso', () => {
        const map = new Map([
            ['111', 'us,in'],
            ['222', 'fr'],
        ]);
        const caps = capsFromCountriesByItunes(
            [
                { id: 10, itunesId: 111 },
                { id: '20', itunes_id: '222' },
                { id: 30, itunesId: 999 }, // not on charts
            ],
            map,
            ['us', 'in', 'gb', 'fr'],
        );
        assert.equal(caps.get('10'), 50);
        assert.equal(caps.get('20'), 20);
        assert.equal(caps.has('30'), false);
    });

    it('respects allowlist (excludes non-tier countries)', () => {
        const map = new Map([['111', 'de,nl']]);
        const caps = capsFromCountriesByItunes(
            [{ id: 1, itunesId: '111' }],
            map,
            ['us', 'gb'], // de/nl not allowed → no cap entry
        );
        assert.equal(caps.has('1'), false);
    });

    it('keeps IN-list chunk size bounded', () => {
        assert.ok(ID_CHUNK >= 50 && ID_CHUNK <= 1000);
    });
});
