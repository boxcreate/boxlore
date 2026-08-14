package cx.aswin.boxlore.feature.library.logic

import cx.aswin.boxlore.core.model.Podcast

internal object SubscriptionManualOrderLogic {
    fun apply(
        order: List<String>,
        podcasts: List<Podcast>,
    ): List<Podcast> {
        val byId = podcasts.associateBy { it.id }
        val seen = LinkedHashSet<String>()
        val result = ArrayList<Podcast>(podcasts.size)
        for (id in order) {
            val podcast = byId[id] ?: continue
            if (seen.add(id)) {
                result.add(podcast)
            }
        }
        podcasts
            .filter { it.id !in seen }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .forEach { result.add(it) }
        return result
    }

    fun move(
        ids: List<String>,
        fromId: String,
        toId: String,
    ): List<String> {
        if (fromId == toId) return ids
        val from = ids.indexOf(fromId)
        val to = ids.indexOf(toId)
        if (from < 0 || to < 0) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun orderAfterDrag(
        visibleIds: List<String>,
        fromId: String,
        toId: String,
    ): List<String> = move(visibleIds, fromId, toId)

    fun drop(
        order: List<String>,
        podcastId: String,
    ): List<String> = order.filter { it != podcastId }
}
