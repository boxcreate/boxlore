package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

internal enum class EpisodeSelectionRange {
    ALL,
    OLDER,
    NEWER,
}

/** A selection rebuilt from saved IDs: the episodes found and the IDs that are still selected. */
internal data class RestoredEpisodeSelection(
    val pool: List<Episode>,
    val selectedIds: List<String>,
)

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

    /** Selected IDs that none of [available] carries, in selection order. */
    fun unresolvedIds(
        selectedIds: List<String>,
        available: List<Episode>,
    ): List<String> {
        val availableIds = available.mapTo(HashSet(), Episode::id)
        return selectedIds.filterNot { it in availableIds }
    }

    /**
     * Rebuilds a selection whose episode pool was lost when the show page was recreated (P4,
     * adaptive show page). The pool keeps the order of the freshly loaded [window], which is read
     * in the sort the selection was made in, so actions such as Play keep the listener's order;
     * episodes only the page has follow. Each episode is the page's own copy from [available] when
     * it has one. IDs that neither supplies are dropped, so the selected count always matches the
     * episodes the toolbar actions receive.
     */
    fun restoreSelection(
        selectedIds: List<String>,
        available: List<Episode>,
        window: List<Episode>,
    ): RestoredEpisodeSelection {
        val wanted = selectedIds.toSet()
        val loadedById = LinkedHashMap<String, Episode>()
        available.forEach { episode -> if (episode.id in wanted) loadedById.putIfAbsent(episode.id, episode) }
        val pool =
            (window + available)
                .filter { it.id in wanted }
                .distinctBy(Episode::id)
                .map { loadedById[it.id] ?: it }
        val resolvedIds = pool.mapTo(HashSet(), Episode::id)
        return RestoredEpisodeSelection(
            pool = pool,
            selectedIds = selectedIds.filter { it in resolvedIds }.distinct(),
        )
    }

    /**
     * Whether a recreated page may look its saved selection up now (P4, adaptive show page): only
     * once the first page has finished loading (its sort and episodes are real), while no range
     * lookup is running, and not again on the page state a lookup already failed on.
     */
    fun canRestoreSelection(
        restorePending: Boolean,
        pageLoading: Boolean,
        rangeLoading: Boolean,
        failedOnCurrentState: Boolean,
    ): Boolean = restorePending && !pageLoading && !rangeLoading && !failedOnCurrentState

    fun shouldMarkUnplayed(
        selectedEpisodes: List<Episode>,
        completedEpisodeIds: Set<String>,
    ): Boolean = selectedEpisodes.isNotEmpty() && selectedEpisodes.all { it.id in completedEpisodeIds }
}
