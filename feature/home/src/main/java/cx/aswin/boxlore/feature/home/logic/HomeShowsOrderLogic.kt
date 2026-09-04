package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HomePinnedShows

internal object HomeShowsOrderLogic {
    const val SCORE_MOVE_THRESHOLD = 0.05

    /**
     * Hybrid session-stable Your Shows order:
     * - pinned ids (still subscribed, max 5) stay first in pin order
     * - first pass of the rest: sort by score desc, then title
     * - later: keep prior unpinned order, drop removed shows, prepend newly subscribed ids
     */
    fun computeStableShowsOrder(
        previousOrder: List<String>?,
        subs: List<Podcast>,
        scores: Map<String, Double>,
        pinnedIds: List<String> = emptyList(),
        refreshFromScores: Boolean = false,
    ): List<String> {
        val currentSubIds = subs.map { it.id }.toSet()
        val pins =
            HomePinnedShows.sanitize(pinnedIds).filter { it in currentSubIds }
        val pinSet = pins.toSet()
        val unpinnedSubs = subs.filter { it.id !in pinSet }
        val previousUnpinned = previousOrder?.filter { it in currentSubIds && it !in pinSet }
        val rest =
            computeUnpinnedOrder(
                previousOrder = previousUnpinned,
                unpinnedSubs = unpinnedSubs,
                scores = scores,
                refreshFromScores = refreshFromScores,
            )
        return pins + rest
    }

    private fun computeUnpinnedOrder(
        previousOrder: List<String>?,
        unpinnedSubs: List<Podcast>,
        scores: Map<String, Double>,
        refreshFromScores: Boolean,
    ): List<String> {
        val currentSubIds = unpinnedSubs.map { it.id }.toSet()
        return if (previousOrder == null) {
            unpinnedSubs
                .map { pod -> pod to (scores[pod.id] ?: 0.0) }
                .sortedWith(
                    compareByDescending<Pair<Podcast, Double>> { it.second }
                        .thenBy { it.first.title },
                ).map { it.first.id }
        } else {
            val existingOrder = previousOrder.filter { it in currentSubIds }
            val newSubscribedIds = currentSubIds.filter { it !in existingOrder.toSet() }
            val refreshedOrder =
                if (refreshFromScores) {
                    reorderWithHysteresis(existingOrder, scores)
                } else {
                    existingOrder
                }
            if (newSubscribedIds.isNotEmpty()) {
                newSubscribedIds + refreshedOrder
            } else {
                refreshedOrder
            }
        }
    }

    private fun reorderWithHysteresis(
        previousOrder: List<String>,
        scores: Map<String, Double>,
    ): List<String> {
        val result = previousOrder.toMutableList()
        var moved: Boolean
        do {
            moved = false
            for (index in 0 until result.lastIndex) {
                val leadingScore = scores[result[index]]
                val trailingScore = scores[result[index + 1]]
                if (
                    leadingScore != null &&
                    trailingScore != null &&
                    trailingScore > leadingScore + SCORE_MOVE_THRESHOLD
                ) {
                    val leading = result[index]
                    result[index] = result[index + 1]
                    result[index + 1] = leading
                    moved = true
                }
            }
        } while (moved)
        return result
    }

    fun orderedSubs(
        order: List<String>,
        subs: List<Podcast>,
    ): List<Podcast> {
        val subsMap = subs.associateBy { it.id }
        return order.mapNotNull { subsMap[it] }
    }

    fun shouldInvalidateMixtapeCache(
        previousSignature: Set<String>?,
        currentSignature: Set<String>,
    ): Boolean = previousSignature != null && previousSignature != currentSignature
}

internal object HomeShowsRefreshPolicy {
    const val MIN_SNAPSHOT_AGE_MS = 30L * 60L * 1_000L

    fun shouldRequestRefresh(
        hasStableOrder: Boolean,
        hasPendingRequest: Boolean,
        snapshotCreatedAtMs: Long,
        nowMs: Long,
    ): Boolean = hasStableOrder &&
        !hasPendingRequest &&
        nowMs - snapshotCreatedAtMs >= MIN_SNAPSHOT_AGE_MS
}
