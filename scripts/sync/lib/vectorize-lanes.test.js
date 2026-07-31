'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
    orderPendingForVectorize,
    showVectorizeComplete,
    laneForShow,
} = require('./vectorize-lanes');

describe('orderPendingForVectorize', () => {
    it('drains tip-queue before incremental before cold', () => {
        const pending = [
            { id: '1', last_ep_sync: 100, qdrant_podcast_vectorized: 0 },
            { id: '2', last_ep_sync: 50, qdrant_podcast_vectorized: 1 },
            { id: '3', last_ep_sync: 200, qdrant_podcast_vectorized: 0 },
            { id: '4', last_ep_sync: null, qdrant_podcast_vectorized: 0 },
            { id: '5', last_ep_sync: 300, qdrant_podcast_vectorized: 1 },
        ];
        const { ordered, tipCount, incrementalCount, coldCount, orphanTipIds } =
            orderPendingForVectorize(pending, ['5', '99', '2']);
        assert.deepEqual(ordered.map((p) => p.id), ['5', '2', '4', '1', '3']);
        assert.equal(tipCount, 2);
        assert.equal(incrementalCount, 0); // 2 and 5 already in tip
        assert.equal(coldCount, 3);
        assert.deepEqual(orphanTipIds, ['99']);
    });

    it('puts remaining show=1 after tip queue, before cold', () => {
        const pending = [
            { id: 'a', last_ep_sync: 1, qdrant_podcast_vectorized: 0 },
            { id: 'b', last_ep_sync: 2, qdrant_podcast_vectorized: 1 },
        ];
        const { ordered } = orderPendingForVectorize(pending, []);
        assert.deepEqual(ordered.map((p) => p.id), ['b', 'a']);
    });
});

describe('showVectorizeComplete', () => {
    it('false when budget breaks mid-show', () => {
        assert.equal(
            showVectorizeComplete({
                showFailed: false,
                budgetBroke: true,
                toEmbedCount: 5,
                embeddedCount: 2,
                corruptSkipped: 0,
            }),
            false,
        );
    });

    it('true when all toEmbed accounted (embed + corrupt skip)', () => {
        assert.equal(
            showVectorizeComplete({
                showFailed: false,
                budgetBroke: false,
                toEmbedCount: 3,
                embeddedCount: 2,
                corruptSkipped: 1,
            }),
            true,
        );
    });

    it('false on embed failure', () => {
        assert.equal(
            showVectorizeComplete({
                showFailed: true,
                budgetBroke: false,
                toEmbedCount: 1,
                embeddedCount: 0,
                corruptSkipped: 0,
            }),
            false,
        );
    });
});

describe('laneForShow', () => {
    it('tip queue wins over cold flags', () => {
        assert.equal(
            laneForShow({ id: '1', qdrant_podcast_vectorized: 0 }, new Set(['1'])),
            'tip',
        );
    });

    it('show=1 without tip queue is tip lane', () => {
        assert.equal(
            laneForShow({ id: '1', qdrant_podcast_vectorized: 1 }, new Set()),
            'tip',
        );
    });

    it('both=0 is cold', () => {
        assert.equal(
            laneForShow({ id: '1', qdrant_podcast_vectorized: 0 }, new Set()),
            'cold',
        );
    });
});
