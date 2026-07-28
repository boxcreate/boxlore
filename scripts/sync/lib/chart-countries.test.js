'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
    CHARTS_MATCH_PODCAST,
    CHARTS_ITUNES_FIRST_CURSOR,
    mergePodcastRowsWithCountries,
    itunesInCountries,
    countriesForItunes,
} = require('./chart-countries');

describe('chart-countries', () => {
    it('uses TEXT cast on podcast side (index-friendly)', () => {
        assert.match(CHARTS_MATCH_PODCAST, /c\.itunes_id\s*=\s*CAST\(p\.itunes_id AS TEXT\)/);
        assert.doesNotMatch(CHARTS_MATCH_PODCAST, /CAST\(c\.itunes_id/);
    });

    it('merges only chart podcasts and keeps country csv', () => {
        const map = new Map([
            ['111', 'us,gb'],
            ['222', 'de'],
        ]);
        const rows = [
            [10, 'e1', 'News', 'podcast', 111],
            [20, 'e2', 'Comedy', 'podcast', 999], // not on charts
            [30, null, '', 'podcast', '222'],
            [40, 'e4', 'Tech', 'podcast', null],
        ];
        const out = mergePodcastRowsWithCountries(rows, map);
        assert.deepEqual(out, [
            {
                id: '10',
                latestEpId: 'e1',
                categories: 'News',
                medium: 'podcast',
                countryCsv: 'us,gb',
            },
            {
                id: '30',
                latestEpId: null,
                categories: '',
                medium: 'podcast',
                countryCsv: 'de',
            },
        ]);
    });

    it('filters itunes ids by country allowlist', () => {
        const map = new Map([
            ['111', 'us,gb'],
            ['222', 'de'],
            ['333', 'in,fr'],
        ]);
        assert.equal(itunesInCountries(map, 111, ['us', 'in']), true);
        assert.equal(itunesInCountries(map, '222', ['us', 'in']), false);
        assert.equal(itunesInCountries(map, 333, ['us', 'in']), true);
        assert.equal(itunesInCountries(map, 999, ['us']), false);
        assert.equal(itunesInCountries(map, 222, null), true); // any chart country
        assert.deepEqual(countriesForItunes(map, 111, ['gb', 'de']), ['gb']);
        assert.deepEqual(countriesForItunes(map, '333', ['in', 'us']), ['in']);
    });

    it('documents empty-string cursor hazard (mapArgType → SQL NULL)', () => {
        // Regression note: first-page cursor must not be '' — turso.mapArgType
        // coerces '' to null and `itunes_id > NULL` returns zero rows.
        assert.equal(CHARTS_ITUNES_FIRST_CURSOR, '!');
        assert.notEqual(CHARTS_ITUNES_FIRST_CURSOR, '');
        assert.ok('1000199274' > CHARTS_ITUNES_FIRST_CURSOR);
    });
});
