package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

sealed class FeedItem {
    abstract val id: String

    data class NormalEpisode(
        val episode: Episode,
        val globalIndex: Int,
    ) : FeedItem() {
        override val id: String = episode.id
    }

    data class SingleTrailer(
        val episode: Episode,
        val globalIndex: Int,
    ) : FeedItem() {
        override val id: String = episode.id
    }

    data class TrailerGroup(
        val trailers: List<Pair<Episode, Int>>,
    ) : FeedItem() {
        override val id: String = "trailer_group_${trailers.firstOrNull()?.first?.id ?: hashCode()}"
    }
}

fun groupEpisodes(episodes: List<Episode>): List<FeedItem> {
    val result = mutableListOf<FeedItem>()
    val currentTrailers = mutableListOf<Pair<Episode, Int>>()

    episodes.forEachIndexed { index, episode ->
        if (episode.episodeType == "trailer") {
            currentTrailers.add(episode to index)
        } else {
            if (currentTrailers.isNotEmpty()) {
                if (currentTrailers.size == 1) {
                    val (tEp, tIdx) = currentTrailers.first()
                    result.add(FeedItem.SingleTrailer(tEp, tIdx))
                } else {
                    result.add(FeedItem.TrailerGroup(currentTrailers.toList()))
                }
                currentTrailers.clear()
            }
            result.add(FeedItem.NormalEpisode(episode, index))
        }
    }

    if (currentTrailers.isNotEmpty()) {
        if (currentTrailers.size == 1) {
            val (tEp, tIdx) = currentTrailers.first()
            result.add(FeedItem.SingleTrailer(tEp, tIdx))
        } else {
            result.add(FeedItem.TrailerGroup(currentTrailers.toList()))
        }
    }

    return result
}

data class AutoScrollTarget(
    val jumpIndex: Int,
    val isOngoing: Boolean,
    val jumpEpisode: Episode?,
    val badgeEpisodeId: String?,
)

fun resolveAutoScrollTarget(
    feedItems: List<FeedItem>,
    completedEpisodeIds: Set<String>,
    ongoingEpisodeIds: Set<String>,
): AutoScrollTarget {
    fun isCompleted(item: FeedItem): Boolean =
        when (item) {
            is FeedItem.NormalEpisode -> completedEpisodeIds.contains(item.episode.id)
            is FeedItem.SingleTrailer -> completedEpisodeIds.contains(item.episode.id)
            is FeedItem.TrailerGroup -> item.trailers.any { completedEpisodeIds.contains(it.first.id) }
        }

    fun isOngoing(item: FeedItem): Boolean =
        when (item) {
            is FeedItem.NormalEpisode -> ongoingEpisodeIds.contains(item.episode.id)
            is FeedItem.SingleTrailer -> ongoingEpisodeIds.contains(item.episode.id)
            // Match any trailer in the group, not only the first.
            is FeedItem.TrailerGroup -> item.trailers.any { ongoingEpisodeIds.contains(it.first.id) }
        }

    fun episodeAt(item: FeedItem): Episode? =
        when (item) {
            is FeedItem.NormalEpisode -> item.episode
            is FeedItem.SingleTrailer -> item.episode
            is FeedItem.TrailerGroup ->
                item.trailers.firstOrNull { ongoingEpisodeIds.contains(it.first.id) }?.first
                    ?: item.trailers.firstOrNull { !completedEpisodeIds.contains(it.first.id) }?.first
        }

    // 1. Look for an in-progress/ongoing episode first
    var targetIndex = feedItems.indexOfFirst { isOngoing(it) }
    val isOngoingMatched = targetIndex != -1

    // 2. If nothing is ongoing, look for the episode just after the last completed one
    if (targetIndex == -1) {
        val lastCompletedIndex = feedItems.indexOfLast { isCompleted(it) }
        if (lastCompletedIndex != -1) {
            targetIndex = lastCompletedIndex + 1
        }
    }

    // 3. Fallback to first episode (index 0) if nothing completed or ongoing
    if (targetIndex == -1) {
        targetIndex = 0
    }

    // Coerce to ensure we stay inside bounds
    val resolvedIndex = targetIndex.coerceIn(0, feedItems.size - 1)

    // UP NEXT tag should go to the episode immediately following the ongoing/in-progress one
    val badgeIndex =
        if (isOngoingMatched && resolvedIndex < feedItems.size - 1) {
            resolvedIndex + 1
        } else {
            resolvedIndex
        }

    val jumpEpisode = feedItems.getOrNull(resolvedIndex)?.let { episodeAt(it) }
    val badgeEpisode = feedItems.getOrNull(badgeIndex)?.let { episodeAt(it) }

    return AutoScrollTarget(
        jumpIndex = resolvedIndex,
        isOngoing = isOngoingMatched,
        jumpEpisode = jumpEpisode,
        badgeEpisodeId = badgeEpisode?.id,
    )
}
