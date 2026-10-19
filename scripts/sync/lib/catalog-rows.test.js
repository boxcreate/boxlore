'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { apiImportArgs, tipUpdateArgs, categoryList } = require('./catalog-rows');
const turso = require('./turso');
const cfg = require('./config');

const bindAll = (args) => args.map(turso.mapArgType);

describe('apiImportArgs', () => {
    const feed = {
        id: 101,
        itunesId: 555,
        title: 'Show',
        author: 'Host',
        description: 'About',
        image: 'https://img.example/a.jpg',
        url: 'https://feed.example/rss',
        link: 'https://show.example',
        language: 'en-gb',
        explicit: true,
        itunesType: 'serial',
    };

    it('keeps the import column order', () => {
        const args = apiImportArgs(feed, '999', 'News, Politics');
        assert.equal(args.length, cfg.PODCAST_IMPORT_COLUMNS.length);
        assert.deepEqual(args, [101, 555, 'Show', 'Host', 'About', 'https://img.example/a.jpg',
            'https://feed.example/rss', 'https://show.example', 'News, Politics', 'en-gb', '1', 'serial']);
    });

    it('falls back like before for missing fields', () => {
        const args = apiImportArgs({ id: 7, artwork: 'https://img.example/b.jpg' }, '999', '');
        assert.deepEqual(args, [7, '999', 'Unknown Title', 'Unknown Author', '', 'https://img.example/b.jpg',
            '', '', '', 'en', '0', 'episodic']);
    });

    it('turns objects and stored "[object Object]" into empty fields that bind', () => {
        const args = apiImportArgs({ ...feed, link: { href: 'x' }, description: '[object Object]', title: {} }, '999', '');
        assert.equal(args[7], '');
        assert.equal(args[4], '');
        assert.equal(args[2], 'Unknown Title');
        assert.doesNotThrow(() => bindAll(args));
    });
});

describe('categoryList', () => {
    it('joins scalar categories and drops junk ones', () => {
        assert.equal(categoryList({ 1: 'News', 2: { x: 1 }, 3: '[object Object]', 4: ' Politics ' }), 'News, Politics');
        assert.equal(categoryList(null), '');
        assert.equal(categoryList('News'), '');
    });
});

describe('tipUpdateArgs', () => {
    const latest = {
        id: 9001,
        title: 'Ep 1',
        datePublished: 1790000000,
        duration: 1800,
        enclosureUrl: 'https://cdn.example/1.mp3',
        feedImage: 'https://img.example/f.jpg',
        enclosureType: 'audio/mp4',
        description: '<p>Hi &amp; bye</p>',
        chaptersUrl: 'https://cdn.example/ch.json',
        transcriptUrl: '',
        persons: [{ name: 'Host', role: 'host' }],
        transcripts: null,
    };

    it('builds the UPDATE values in SET order', () => {
        const args = tipUpdateArgs(latest, 'podcast', 1234, '42');
        assert.deepEqual(args, ['9001', 'Ep 1', 1790000000, 1800, 'https://cdn.example/1.mp3',
            'https://img.example/f.jpg', 'audio/mp4', 'Hi & bye', 'https://cdn.example/ch.json', null,
            JSON.stringify([{ name: 'Host', role: 'host' }]), null, 'podcast', 1234, '42']);
    });

    it('fails on an invalid episode id instead of storing it as text', () => {
        for (const id of [undefined, null, '', '  ', { id: 1 }, NaN]) {
            assert.throws(() => tipUpdateArgs({ ...latest, id }, 'podcast', 1, '42'), /Invalid episode id/);
        }
        assert.equal(tipUpdateArgs({ ...latest, id: '61901333872' }, 'podcast', 1, '42')[0], '61901333872');
    });

    it('scrubs objects that used to be stored as "[object Object]"', () => {
        const args = tipUpdateArgs({
            ...latest,
            chaptersUrl: { url: 'x' },
            transcriptUrl: '[object Object]',
            duration: { seconds: 3 },
            title: ['a'],
        }, { kind: 'music' }, 1234, '42');
        assert.equal(args[1], '');
        assert.equal(args[3], 0);
        assert.equal(args[8], null);
        assert.equal(args[9], null);
        assert.equal(args[12], 'podcast');
        assert.doesNotThrow(() => bindAll(args));
        assert.ok(bindAll(args).every((a) => a.value !== '[object Object]'));
    });
});
