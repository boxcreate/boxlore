'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const text = require('./text');

describe('cleanDescription / embed text', () => {
    it('does not truncate long cleaned descriptions by default', () => {
        const long = `Intro. ${'word '.repeat(400)}Outro.`;
        const cleaned = text.cleanDescription(long);
        assert.ok(cleaned.length > 1000);
        assert.equal(cleaned.includes('Intro.'), true);
        assert.equal(cleaned.includes('Outro.'), true);
    });

    it('still truncates when maxLen is provided', () => {
        const cleaned = text.cleanDescription('abcdefghij', 5);
        assert.equal(cleaned, 'abcde');
    });

    it('episodeEmbedText is uncapped', () => {
        const desc = 'x'.repeat(1500);
        const out = text.episodeEmbedText(
            { title: 'Ep', cleanedDescription: desc },
            { title: 'Show', categories: 'News', author: 'Host' },
        );
        assert.ok(out.length > 1000);
        assert.ok(out.includes(desc));
    });

    it('podcastEmbedText is uncapped', () => {
        const desc = `About. ${'detail '.repeat(300)}`;
        const out = text.podcastEmbedText({
            title: 'Show',
            author: 'Host',
            description: desc,
            categories: 'Comedy',
            language: 'en',
        });
        assert.ok(out.length > 1000);
    });

    it('safeTruncate with null/omitted maxLen returns full string', () => {
        const s = 'y'.repeat(1200);
        assert.equal(text.safeTruncate(s, null), s);
        assert.equal(text.safeTruncate(s), s);
    });
});
