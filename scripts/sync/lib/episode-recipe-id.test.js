'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const recipe = require('./episode-recipe-id');

const FIXTURE_PATH = path.join(__dirname, '../../../core/rss/src/test/resources/fixtures/episode-recipe-vectors.json');
// Copies of the fixture in other repositories must hash to this value too.
const FIXTURE_SHA256 = 'e272d820972c4f9d7cd215c510bfdb7effa382ceb1982617419279189a5ae292';

const fixtureBytes = fs.readFileSync(FIXTURE_PATH);
const fixture = JSON.parse(fixtureBytes.toString('utf8'));

function parseRange(text) {
    const [lo, hi = lo] = text.split('-').map((h) => parseInt(h, 16));
    return [lo, hi];
}

describe('episode-recipe-id', () => {
    it('reads the shared fixture unchanged', () => {
        assert.equal(crypto.createHash('sha256').update(fixtureBytes).digest('hex'), FIXTURE_SHA256);
        assert.equal(fixture.recipe, recipe.RECIPE);
        assert.equal(fixture.recipeIdMin, recipe.RECIPE_ID_MIN);
        assert.equal(fixture.recipeIdMax, recipe.RECIPE_ID_MAX);
        assert.deepEqual(fixture.trimRanges.map(parseRange), recipe.TRIM_RANGES);
    });

    for (const c of fixture.cases) {
        it(`fixture: ${c.name}`, () => {
            assert.equal(recipe.catalogKey(c.guid, c.enclosureUrl), c.catalogKey);
            assert.equal(recipe.recipeIdForItem(c.showId, c.guid, c.enclosureUrl), c.recipeId);
            if (c.recipeId != null) {
                assert.equal(recipe.recipeId(String(c.showId), c.catalogKey), c.recipeId);
                assert.ok(recipe.isRecipeId(c.recipeId));
            }
        });
    }

    it('trims exactly the fixture set and nothing else', () => {
        const ranges = fixture.trimRanges.map(parseRange);
        for (let code = 0; code <= 0xffff; code++) {
            if (code >= 0xd800 && code <= 0xdfff) continue;
            const ch = String.fromCharCode(code);
            const inSet = ranges.some(([lo, hi]) => code >= lo && code <= hi);
            assert.equal(recipe.trimKey(`${ch}x${ch}`), inSet ? 'x' : `${ch}x${ch}`, `U+${code.toString(16)}`);
        }
    });

    it('differs from String#trim where the plan says it must', () => {
        assert.equal(recipe.trimKey('﻿a'), '﻿a');
        assert.equal('﻿a'.trim(), 'a');
        assert.equal(recipe.trimKey('\u001fa'), 'a');
        assert.equal('\u001fa'.trim(), '\u001fa');
    });

    it('refuses keys that are not canonical', () => {
        assert.throws(() => recipe.recipeId(75075, ' a'));
        assert.throws(() => recipe.recipeId(75075, ''));
        assert.throws(() => recipe.recipeId(75075, null));
    });

    it('mints only for positive numeric show IDs', () => {
        for (const bad of ['rss:abc', '0', '-5', '075075', '1.5', 0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1, '99999999999999999', null]) {
            assert.equal(recipe.episodeNamespace(bad), null, String(bad));
            assert.throws(() => recipe.recipeId(bad, 'a'), String(bad));
        }
        assert.equal(recipe.episodeNamespace(75075), '75075');
        assert.equal(recipe.episodeNamespace(75075n), '75075');
        assert.equal(recipe.recipeId(75075, 'a'), recipe.recipeId('75075', 'a'));
    });

    it('keeps legacy IDs out of the recipe range', () => {
        assert.equal(recipe.isRecipeId(4503599627370495), false);
        assert.equal(recipe.isRecipeId(48291034567), false);
        assert.equal(recipe.isRecipeId('-4611686018427387904'), false);
        assert.equal(recipe.isRecipeId('8825297254050678'), true);
        assert.equal(recipe.isRecipeId('08825297254050678'), false);
        assert.equal(recipe.isRecipeId(2 ** 53), false);
    });
});
