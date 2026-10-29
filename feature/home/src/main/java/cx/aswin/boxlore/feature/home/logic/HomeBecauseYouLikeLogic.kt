package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal object HomeBecauseYouLikeLogic {
    /** What the Because you like row loads for: the picked show, region and content languages. */
    data class Request(val overriddenId: String?, val region: String, val languages: List<String>,)

    /** Emits again when any of the three changes, so a language change alone refetches the row. */
    fun requests(
        overriddenId: Flow<String?>,
        region: Flow<String>,
        languages: Flow<List<String>>,
    ): Flow<Request> = combine(overriddenId, region, languages) { id, r, l -> Request(id, r, l) }.distinctUntilChanged()

    /** Saved rows were fetched for [fetched] languages; once the languages differ they must not be reused. */
    fun skipsSavedRows(fetched: List<String>?, current: List<String>): Boolean = fetched != null && fetched != current

    fun toAffinitySignal(item: HomeListeningHistoryItem): PodcastAffinityLogic.HistorySignal = PodcastAffinityLogic.HistorySignal(
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
    ): List<T> = items
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

    fun filterRailPodcasts(podcasts: List<Podcast>): List<Podcast> =
        cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy.deduplicateById(podcasts) { it.id }

    fun filterRailEpisodes(episodes: List<Episode>): List<Episode> =
        cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy.deduplicateById(episodes) { it.id }

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
