package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast

internal object HomeShowsOrderLogic {
    /**
     * Hybrid session-stable Your Shows order:
     * - first pass: sort by score desc, then title
     * - later: keep prior order, drop removed shows, prepend newly subscribed ids
     */
    fun computeStableShowsOrder(
        previousOrder: List<String>?,
        subs: List<Podcast>,
        scores: Map<String, Double>,
    ): List<String> {
        val currentSubIds = subs.map { it.id }.toSet()
        return if (previousOrder == null) {
            subs
                .map { pod -> pod to (scores[pod.id] ?: 0.0) }
                .sortedWith(
                    compareByDescending<Pair<Podcast, Double>> { it.second }
                        .thenBy { it.first.title },
                ).map { it.first.id }
        } else {
            val existingOrder = previousOrder.filter { it in currentSubIds }
            val newSubscribedIds = currentSubIds.filter { it !in existingOrder.toSet() }
            if (newSubscribedIds.isNotEmpty()) {
                newSubscribedIds + existingOrder
            } else {
                existingOrder
            }
        }
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
