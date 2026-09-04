package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

internal enum class EpisodeSelectionRange {
    ALL,
    OLDER,
    NEWER,
}

internal object PodcastEpisodeSelectionLogic {
    fun toggle(
        selectedIds: Set<String>,
        episodeId: String,
    ): Set<String> = if (episodeId in selectedIds) {
        selectedIds - episodeId
    } else {
        selectedIds + episodeId
    }

    /**
     * Adds a chronological range from a complete fetched episode window.
     * OLDER and NEWER include the anchor, which is already selected in normal use.
     */
    fun addRange(
        selectedIds: Set<String>,
        episodes: List<Episode>,
        anchorEpisodeId: String?,
        range: EpisodeSelectionRange,
        newestFirst: Boolean,
    ): Set<String> {
        val orderedIds = episodes.map(Episode::id)
        if (orderedIds.isEmpty()) return selectedIds
        if (range == EpisodeSelectionRange.ALL) return orderedIds.toSet()

        val anchorIndex = orderedIds.indexOf(anchorEpisodeId)
        if (anchorIndex < 0) return selectedIds
        val rangeIds =
            when (range) {
                EpisodeSelectionRange.ALL -> orderedIds
                EpisodeSelectionRange.OLDER ->
                    if (newestFirst) {
                        orderedIds.drop(anchorIndex)
                    } else {
                        orderedIds.take(anchorIndex + 1)
                    }
                EpisodeSelectionRange.NEWER ->
                    if (newestFirst) {
                        orderedIds.take(anchorIndex + 1)
                    } else {
                        orderedIds.drop(anchorIndex)
                    }
            }
        return selectedIds + rangeIds
    }

    fun visibleEpisodes(
        feedItems: List<FeedItem>,
        visibleItemKeys: Set<String>,
    ): List<Episode> = feedItems
        .asSequence()
        .filter { it.id in visibleItemKeys }
        .flatMap { feedItem ->
            when (feedItem) {
                is FeedItem.NormalEpisode -> sequenceOf(feedItem.episode)
                is FeedItem.SingleTrailer -> sequenceOf(feedItem.episode)
                is FeedItem.TrailerGroup -> feedItem.trailers.asSequence().map { it.first }
            }
        }.distinctBy(Episode::id)
        .toList()

    fun selectedEpisodes(
        episodes: List<Episode>,
        selectedIds: Set<String>,
    ): List<Episode> = episodes.filter { it.id in selectedIds }.distinctBy(Episode::id)

    fun shouldMarkUnplayed(
        selectedEpisodes: List<Episode>,
        completedEpisodeIds: Set<String>,
    ): Boolean = selectedEpisodes.isNotEmpty() && selectedEpisodes.all { it.id in completedEpisodeIds }
}
