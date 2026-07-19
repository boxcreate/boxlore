package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem

internal object HomeBecauseYouLikeLogic {
    fun toAffinitySignal(item: HomeListeningHistoryItem): PodcastAffinityLogic.HistorySignal =
        PodcastAffinityLogic.HistorySignal(
            podcastId = item.podcastId,
            podcastName = item.podcastName,
            podcastImageUrl = item.podcastImageUrl,
            progressMs = item.progressMs,
            lastPlayedAt = item.lastPlayedAt,
            isCompleted = item.isCompleted,
            isLiked = item.isLiked,
        )

    fun <T> distinctByIdAndTitle(
        items: List<T>,
        id: (T) -> String,
        title: (T) -> String,
    ): List<T> =
        items
            .distinctBy(id)
            .distinctBy { title(it).lowercase().trim() }

    fun sortPodcastsByEpisodeScores(
        podcasts: List<Podcast>,
        scores: Map<String, Double>,
    ): List<Podcast> = podcasts.sortedByDescending { scores[it.latestEpisode?.id] ?: 0.0 }

    fun sortEpisodesByScores(
        episodes: List<Episode>,
        scores: Map<String, Double>,
    ): List<Episode> = episodes.sortedByDescending { scores[it.id] ?: 0.0 }

    fun candidatePodcastsFromHistory(
        subs: List<Podcast>,
        history: List<HomeListeningHistoryItem>,
    ): List<Podcast> {
        val playedPods =
            history
                .distinctBy { it.podcastId }
                .map { h ->
                    Podcast(
                        id = h.podcastId,
                        title = h.podcastName,
                        artist = "",
                        imageUrl = h.podcastImageUrl ?: "",
                        fallbackImageUrl = "",
                        description = "",
                    )
                }.filter { it.id.isNotEmpty() }
        return (subs + playedPods).distinctBy { it.id }
    }

    fun resolveFavoriteFromMaps(
        topPodId: String,
        subscriptions: List<Podcast>,
        podcastNameMap: Map<String, String>,
        podcastImageMap: Map<String, String>,
    ): Podcast {
        val sub = subscriptions.find { it.id == topPodId }
        if (sub != null) return sub
        return Podcast(
            id = topPodId,
            title = podcastNameMap[topPodId] ?: "Podcast",
            artist = "",
            imageUrl = podcastImageMap[topPodId] ?: "",
            fallbackImageUrl = "",
            description = "",
        )
    }
}
