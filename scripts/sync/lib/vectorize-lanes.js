'use strict';

/**
 * Pure helpers for stage-4 tip/cold drain order and partial-complete flags.
 */

/**
 * Order pending shows: tip-queue IDs (enqueued order) → incremental
 * (ep=0 ∧ show=1) → cold (both=0) oldest last_ep_sync first.
 *
 * @param {Array<{id: string, last_ep_sync?: number|null, qdrant_podcast_vectorized?: number|boolean}>} pending
 * @param {string[]} tipQueueIdsOrdered  oldest enqueued first
 * @returns {{ ordered: typeof pending, tipSet: Set<string>, orphanTipIds: string[], tipCount: number, incrementalCount: number, coldCount: number }}
 */
function orderPendingForVectorize(pending, tipQueueIdsOrdered) {
    const byId = new Map((pending || []).map((p) => [String(p.id), p]));
    const tipSet = new Set();
    const tipOrdered = [];
    const orphanTipIds = [];

    for (const rawId of tipQueueIdsOrdered || []) {
        const id = String(rawId);
        const pod = byId.get(id);
        if (pod) {
            tipOrdered.push(pod);
            tipSet.add(id);
        } else {
            orphanTipIds.push(id);
        }
    }

    const incremental = [];
    const cold = [];
    for (const pod of pending || []) {
        const id = String(pod.id);
        if (tipSet.has(id)) continue;
        if (Number(pod.qdrant_podcast_vectorized) === 1) incremental.push(pod);
        else cold.push(pod);
    }

    cold.sort((a, b) => {
        if (a.last_ep_sync == null && b.last_ep_sync != null) return -1;
        if (a.last_ep_sync != null && b.last_ep_sync == null) return 1;
        return (a.last_ep_sync || 0) - (b.last_ep_sync || 0);
    });

    return {
        ordered: [...tipOrdered, ...incremental, ...cold],
        tipSet,
        orphanTipIds,
        tipCount: tipOrdered.length,
        incrementalCount: incremental.length,
        coldCount: cold.length,
    };
}

/**
 * Whether a show may flip qdrant_vectorized=1 and leave the tip queue.
 * Budget mid-show or embed failure → incomplete (partial upsert OK, flag stays 0).
 *
 * @param {{ showFailed: boolean, budgetBroke: boolean, toEmbedCount: number, embeddedCount: number, corruptSkipped: number }}
 */
function showVectorizeComplete({
    showFailed,
    budgetBroke,
    toEmbedCount,
    embeddedCount,
    corruptSkipped = 0,
}) {
    if (showFailed || budgetBroke) return false;
    const accounted = embeddedCount + corruptSkipped;
    return accounted >= toEmbedCount;
}

/**
 * Lane label for metrics: tip queue membership wins, else show-vec done → tip lane.
 * @param {{ id: string, qdrant_podcast_vectorized?: number|boolean }} pod
 * @param {Set<string>} tipSet
 * @returns {'tip'|'cold'}
 */
function laneForShow(pod, tipSet) {
    const id = String(pod.id);
    if (tipSet && tipSet.has(id)) return 'tip';
    if (Number(pod.qdrant_podcast_vectorized) === 1) return 'tip';
    return 'cold';
}

module.exports = {
    orderPendingForVectorize,
    showVectorizeComplete,
    laneForShow,
};
