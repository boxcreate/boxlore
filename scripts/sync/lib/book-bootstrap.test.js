'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { planShow, stampOperations } = require('./book-bootstrap');
const { recipeId } = require('./episode-recipe-id');

const SID = 3391039;
const point = (id, piId) => ({ id, payload: { id: piId, podcast_id: SID } });
const item = (id, guid, enclosureUrl = `https://cdn.example/${id}.mp3`, extra = {}) => ({ id, guid, enclosureUrl, title: `Ep ${id}`, datePublished: 1790000000, duration: 600, ...extra });

describe('planShow', () => {
    it('maps points through the PI list by their payload ID and keeps the PI ID as the served ID', () => {
        const plan = planShow(SID, [point('u1', 61), point('u2', 62)], [item(61, '  https://api.spreaker.com/episode/75827097\n'), item(62, 'g-62')]);
        assert.equal(plan.items.length, 2);
        const [a] = plan.items;
        assert.equal(a.key, 'https://api.spreaker.com/episode/75827097', 'keys are trimmed with the recipe set');
        assert.equal(a.eid, 8825297254050678);
        assert.equal(a.servedId, 61);
        assert.deepEqual(plan.stamps.map((s) => s.pointId), ['u1', 'u2']);
        assert.deepEqual(plan.missing, []);
    });

    it('falls back to the enclosure when the GUID is blank, and never guesses otherwise', () => {
        const plan = planShow(SID, [point('u1', 61), point('u2', 62), point('u3', 99)], [
            item(61, '   ', 'https://cdn.example/a.mp3'),
            item(62, '', ''),
        ]);
        assert.deepEqual(plan.items.map((i) => i.key), ['https://cdn.example/a.mp3']);
        assert.equal(plan.items[0].eid, recipeId(SID, 'https://cdn.example/a.mp3'));
        assert.deepEqual(plan.missing, ['u2', 'u3'], 'no key, or not in the PI list');
    });

    it('folds two points of one episode into one row with an extra PI alias', () => {
        const plan = planShow(SID, [point('u1', 61), point('u2', 70)], [item(61, 'same'), item(70, 'same')]);
        assert.equal(plan.items.length, 1);
        assert.deepEqual(plan.items[0].extraPiIds, [70]);
        assert.equal(plan.duplicates, 1);
        assert.equal(plan.stamps.length, 2, 'both points get the same stamp');
    });

    it('stamps one value set per point and marks unmapped points', () => {
        const plan = planShow(SID, [point('u1', 61), point('u3', 99)], [item(61, 'g')]);
        assert.deepEqual(stampOperations(plan), [
            { set_payload: { payload: { eid: recipeId(SID, 'g'), catalog_key: 'g' }, points: ['u1'] } },
            { delete_payload: { keys: ['eid_missing'], points: ['u1'] } },
            { set_payload: { payload: { eid_missing: true }, points: ['u3'] } },
        ]);
    });

    it('a point mapped on a retry loses its eid_missing mark', () => {
        const plan = planShow(SID, [point('u3', 99)], [item(99, 'late-guid')]);
        assert.deepEqual(stampOperations(plan), [
            { set_payload: { payload: { eid: recipeId(SID, 'late-guid'), catalog_key: 'late-guid' }, points: ['u3'] } },
            { delete_payload: { keys: ['eid_missing'], points: ['u3'] } },
        ]);
    });

    it('stamps a point whose ID another key of the same show serves with that key, without a row', () => {
        const owners = new Map([[61, `${SID}\u0000old-guid`]]);
        const plan = planShow(SID, [point('u1', 61), point('u2', 62)], [item(61, 'new-guid'), item(62, 'g-62')], owners);
        assert.deepEqual(plan.shared, [], 'same show: never eid_shared');
        assert.deepEqual(plan.items.map((x) => x.key), ['g-62'], 'no second row for the served ID');
        assert.deepEqual(plan.stamps.find((x) => x.pointId === 'u1'), { pointId: 'u1', eid: recipeId(SID, 'old-guid'), key: 'old-guid' });
    });

    it('leaves a duplicate feed\'s episodes to the show that holds their IDs, and marks them', () => {
        const owners = new Map([[61, '7650363\u0000g'], [62, `${SID}\u0000g-62`]]);
        const plan = planShow(SID, [point('u1', 61), point('u2', 62), point('u4', 63)], [item(61, 'g'), item(62, 'g-62'), item(63, 'g-63')], owners);
        assert.deepEqual(plan.shared, [{ pointId: 'u1', ownerSid: 7650363 }]);
        assert.deepEqual(plan.items.map((x) => x.key), ['g-62', 'g-63'], 'its own episodes are still recorded');
        assert.deepEqual(stampOperations(plan).at(-1), { set_payload: { payload: { eid_shared: 7650363 }, points: ['u1'] } });
    });
});
