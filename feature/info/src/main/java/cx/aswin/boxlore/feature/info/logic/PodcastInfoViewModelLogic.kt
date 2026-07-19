package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.EpisodeSort

object PodcastInfoSortLogic {
    fun resolveInitialSort(
        preferredSort: String?,
        initialType: String,
    ): EpisodeSort =
        when (preferredSort) {
            "oldest" -> EpisodeSort.OLDEST
            "newest" -> EpisodeSort.NEWEST
            else -> if (initialType == "serial") EpisodeSort.OLDEST else EpisodeSort.NEWEST
        }
}

object PodcastInfoEnrichLogic {
    fun enrichPodcastWithFallback(
        apiPodcast: Podcast,
        currentPodcast: Podcast?,
        localPodcast: Podcast?,
        pageEpisodes: List<Episode>,
        sortParam: String,
    ): Podcast =
        apiPodcast.copy(
            fallbackImageUrl =
                apiPodcast.fallbackImageUrl.takeIf { !it.isNullOrBlank() }
                    ?: currentPodcast?.fallbackImageUrl
                    ?: pageEpisodes.firstOrNull()?.imageUrl,
            subscribedAt = currentPodcast?.subscribedAt ?: 0L,
            notificationsEnabled = localPodcast?.notificationsEnabled ?: false,
            autoDownloadEnabled = localPodcast?.autoDownloadEnabled ?: false,
            skipBeginningOverrideMs = localPodcast?.skipBeginningOverrideMs,
            skipEndingOverrideMs = localPodcast?.skipEndingOverrideMs,
            latestEpisode =
                apiPodcast.latestEpisode
                    ?: currentPodcast?.latestEpisode
                    ?: (if (sortParam == "newest") pageEpisodes.firstOrNull() else pageEpisodes.maxByOrNull { it.publishedDate }),
        )
}
