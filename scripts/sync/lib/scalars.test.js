'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
    asScalarString,
    asPositiveInt,
    prepareEpisodePayload,
    prepareShowPayload,
    scrubCorruptStrings,
    payloadLooksCorrupt,
    assertPayloadClean,
} = require('./scalars');

describe('scalars scrub policy', () => {
    it('asScalarString drops objects and [object Object]', () => {
        assert.equal(asScalarString({ a: 1 }, ''), '');
        assert.equal(asScalarString('[object Object]', ''), '');
        assert.equal(asScalarString('News,Comedy', ''), 'News,Comedy');
    });

    it('scrubs optional website_url but keeps the show point', () => {
        const res = prepareShowPayload({
            id: 42,
            title: 'Southeast Asia Radio',
            feed_url: 'https://example.com/feed.xml',
            website_url: '[object Object]',
            description: 'Fine show',
            language: 'en',
        });
        assert.equal(res.ok, true);
        assert.equal(res.payload.website_url, '');
        assert.equal(res.payload.title, 'Southeast Asia Radio');
        assert.ok(res.scrubbed.includes('website_url'));
    });

    it('refuses when podcast title is corrupt', () => {
        const res = prepareShowPayload({
            id: 1,
            title: '[object Object]',
            feed_url: 'https://example.com/feed.xml',
        });
        assert.equal(res.ok, false);
        assert.match(res.reason, /title/);
    });

    it('scrubs optional episode language; refuses bad audio_url', () => {
        const ok = prepareEpisodePayload({
            id: 9,
            title: 'Ep 1',
            podcast_id: 3,
            audio_url: 'https://cdn.example/a.mp3',
            language: '[object Object]',
            podcast_categories: '[object Object]',
        });
        assert.equal(ok.ok, true);
        assert.equal(ok.payload.language, '');
        assert.equal(ok.payload.podcast_categories, '');

        const bad = prepareEpisodePayload({
            id: 9,
            title: 'Ep 1',
            podcast_id: 3,
            audio_url: '[object Object]',
        });
        assert.equal(bad.ok, false);
        assert.match(bad.reason, /audio_url/);
    });

    it('scrubCorruptStrings + assertPayloadClean last line', () => {
        const p = { title: 'ok', website_url: '[object Object]' };
        assert.equal(payloadLooksCorrupt(p), true);
        const scrubbed = scrubCorruptStrings(p);
        assert.equal(scrubbed.website_url, '');
        assertPayloadClean(scrubbed, 'test');
        assert.equal(asPositiveInt('[object Object]', 0), 0);
    });
});
