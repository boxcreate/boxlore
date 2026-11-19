package cx.aswin.boxlore.feature.library.playlists

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaylistEpisode

/** Pure rules for playing and reordering a playlist, unit-tested without Compose or Room. */
internal object PlaylistPlaybackLogic {
    /**
     * The queue copy for playing [entries] (in playlist order): from the tapped row [startItemId],
     * else from the first unplayed one (the first row when all are played). Played rows after the
     * start are left out, the tapped one never; when nothing from the start onward is unplayed
     * (replaying a finished playlist), every row from the start plays in order. At most [limit]
     * episodes, each once.
     */
    fun playbackEpisodes(entries: List<PlaylistEpisode>, completedIds: Set<String>, startItemId: Long? = null, limit: Int,): List<Episode> {
        if (entries.isEmpty()) return emptyList()
        val start = startItemId?.let { id -> entries.indexOfFirst { it.itemId == id }.takeIf { it >= 0 } }
            ?: entries.indexOfFirst { it.episode.id !in completedIds }.takeIf { it >= 0 }
            ?: 0
        val first = entries[start]
        val following = entries.drop(start + 1)
        val unplayedFollowing = following.filter { it.episode.id !in completedIds }
        val replaying = first.episode.id in completedIds && unplayedFollowing.isEmpty()
        val rest = if (replaying) following else unplayedFollowing
        return (listOf(first) + rest).map { it.episode }.distinctBy { it.id }.take(limit)
    }

    /** [entries] in [pendingOrder] (item IDs), any rows it doesn't name after them in their own order. */
    fun applyOrder(entries: List<PlaylistEpisode>, pendingOrder: List<Long>?): List<PlaylistEpisode> {
        if (pendingOrder.isNullOrEmpty()) return entries
        val rank = pendingOrder.withIndex().associate { (index, id) -> id to index }
        return entries.withIndex()
            .sortedWith(compareBy({ rank[it.value.itemId] ?: (pendingOrder.size + it.index) }, { it.index }))
            .map { it.value }
    }

    /** [order] with the item [fromId] moved to where [toId] is. Unchanged when either is missing. */
    fun move(order: List<Long>, fromId: Long, toId: Long): List<Long> {
        val from = order.indexOf(fromId)
        val to = order.indexOf(toId)
        if (from < 0 || to < 0 || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** [order] with the item at [index] moved one place by [offset] (TalkBack Move up / Move down). */
    fun nudge(order: List<Long>, index: Int, offset: Int): List<Long> {
        val target = index + offset
        if (index !in order.indices || target !in order.indices) return order
        return order.toMutableList().apply { add(target, removeAt(index)) }
    }
}
