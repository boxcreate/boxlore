package cx.aswin.boxlore.core.playback.service.auto

import androidx.media3.common.MediaItem

/**
 * Remembers the lists most recently sent to the car, so a play request can be matched to the list
 * it was tapped in.
 *
 * Android Auto asks to play with the media ID alone: the extras a browse item carried never come
 * back. Without this, every tapped episode looked like it came from nowhere, so it was labelled
 * with a catch-all source and never continued down its list.
 */
internal class AutoPlayContextTracker(private val maxLists: Int = MAX_LISTS) {
    /** What a tapped episode should do: its source label, and the episodes to play when its list continues. */
    data class Match(val source: String, val continues: Boolean, val itemsFromHere: List<MediaItem>)

    private data class Served(val source: String, val continues: Boolean, val items: List<MediaItem>)

    // Insertion order is recency: a list sent again moves to the end.
    private val lists = LinkedHashMap<String, Served>()

    /**
     * Records [items] as the content of [parentId]. Only playable episodes are kept. [continues]
     * says whether tapping one plays on through the rest of the list.
     */
    @Synchronized
    fun record(parentId: String, source: String, continues: Boolean, items: List<MediaItem>) {
        val playable = items.filter { it.mediaMetadata.isPlayable == true && it.isEpisode() }
        lists.remove(parentId)
        if (playable.isEmpty()) return
        lists[parentId] = Served(source, continues, playable)
        while (lists.size > maxLists) lists.remove(lists.keys.first())
    }

    /** The most recently sent list holding [episodeId], or null when no list sent to the car has it. */
    @Synchronized
    fun find(episodeId: String): Match? {
        for (served in lists.values.toList().asReversed()) {
            val index = served.items.indexOfFirst { it.mediaId.stripEpisodePrefix() == episodeId }
            if (index < 0) continue
            val fromHere = if (served.continues) served.items.drop(index) else listOf(served.items[index])
            return Match(served.source, served.continues, fromHere)
        }
        return null
    }

    @Synchronized
    fun clear() = lists.clear()

    private fun MediaItem.isEpisode(): Boolean =
        mediaId.startsWith(AutoBrowseContract.EPISODE_PREFIX) || mediaId.startsWith(AutoBrowseContract.LEARN_PREFIX)

    private companion object {
        const val MAX_LISTS = 16
    }
}
