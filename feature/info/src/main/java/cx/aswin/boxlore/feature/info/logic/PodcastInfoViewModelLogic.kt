package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.EpisodeSort

object PodcastInfoPlaybackSourceLogic {
    const val BULK_PLAY_ENTRY_POINT = "podcast_detail"
    const val BULK_PLAY_SOURCE_ENTRY_POINT = "podcast_detail"

    fun retainedEntryPoint(entryPoint: String?): String? = entryPoint?.takeIf {
        it == cx.aswin.boxlore.core.analytics.AnalyticsGlossary.VIDEO_SPOTLIGHT_ENTRY_POINT
    }

    fun resolvedPlaybackEntryPoints(entryPoint: String?): Pair<String, String> {
        val retained = retainedEntryPoint(entryPoint)
        return (retained ?: "podcast_detail") to "podcast_detail"
    }
}

object PodcastInfoSortLogic {
    fun resolveInitialSort(
        preferredSort: String?,
        initialType: String,
    ): EpisodeSort = when (preferredSort) {
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
    ): Podcast {
        val effectiveSubscribed = (currentPodcast?.subscribedAt ?: 0L) > 0L || (localPodcast?.subscribedAt ?: 0L) > 0L
        val (skipBeginning, skipEnding) = resolveSkips(currentPodcast ?: apiPodcast, localPodcast, apiPodcast)
        return apiPodcast.copy(
            fallbackImageUrl = resolveEnrichedFallbackImage(currentPodcast, localPodcast, apiPodcast, pageEpisodes),
            subscribedAt = resolveEnrichedSubscribedAt(currentPodcast, localPodcast),
            notificationsEnabled = resolveNotifications(effectiveSubscribed, currentPodcast ?: apiPodcast, localPodcast),
            autoDownloadEnabled = resolveAutoDownload(effectiveSubscribed, currentPodcast ?: apiPodcast, localPodcast),
            skipBeginningOverrideMs = skipBeginning,
            skipEndingOverrideMs = skipEnding,
            preferredSort = resolvePreferredSort(currentPodcast ?: apiPodcast, localPodcast, apiPodcast),
            feedUrl = resolveFeedUrl(apiPodcast, currentPodcast, localPodcast),
            latestEpisode = resolveEnrichedLatestEpisode(localPodcast, currentPodcast, apiPodcast, pageEpisodes, sortParam),
        )
    }

    private fun resolveEnrichedFallbackImage(
        current: Podcast?,
        local: Podcast?,
        api: Podcast,
        pageEpisodes: List<Episode>,
    ): String? = api.fallbackImageUrl?.takeIf { it.isNotBlank() }
        ?: current?.fallbackImageUrl?.takeIf { it.isNotBlank() }
        ?: local?.fallbackImageUrl?.takeIf { it.isNotBlank() }
        ?: pageEpisodes.firstOrNull()?.imageUrl

    private fun resolveEnrichedSubscribedAt(
        current: Podcast?,
        local: Podcast?,
    ): Long = current?.subscribedAt?.takeIf { it > 0L } ?: local?.subscribedAt ?: 0L

    private fun resolveEnrichedLatestEpisode(
        local: Podcast?,
        current: Podcast?,
        api: Podcast,
        pageEpisodes: List<Episode>,
        sortParam: String,
    ): Episode? = listOfNotNull(local?.latestEpisode, current?.latestEpisode, api.latestEpisode)
        .maxByOrNull { it.publishedDate }
        ?: resolveLatestFromPage(pageEpisodes, sortParam)

    fun resolveLatestFromPage(episodes: List<Episode>, sortParam: String): Episode? =
        if (sortParam == "newest") {
            episodes.firstOrNull()
        } else {
            episodes.maxByOrNull { it.publishedDate }
        }

    fun preserveSubscriptionProperties(
        refreshedPodcast: Podcast,
        latestPodcast: Podcast,
        localPodcast: Podcast? = null,
        isSubscribed: Boolean? = null,
    ): Podcast {
        val effectiveSubscribed =
            isSubscribed ?: (latestPodcast.subscribedAt > 0L || (localPodcast?.subscribedAt ?: 0L) > 0L)
        val (skipBeginning, skipEnding) = resolveSkips(latestPodcast, localPodcast, refreshedPodcast)
        return refreshedPodcast.copy(
            notificationsEnabled = resolveNotifications(effectiveSubscribed, latestPodcast, localPodcast),
            autoDownloadEnabled = resolveAutoDownload(effectiveSubscribed, latestPodcast, localPodcast),
            subscribedAt = resolveSubscribedAt(effectiveSubscribed, latestPodcast, localPodcast, refreshedPodcast),
            skipBeginningOverrideMs = skipBeginning,
            skipEndingOverrideMs = skipEnding,
            fallbackImageUrl = resolveFallbackImage(latestPodcast, localPodcast, refreshedPodcast),
            preferredSort = resolvePreferredSort(latestPodcast, localPodcast, refreshedPodcast),
            feedUrl = resolveFeedUrl(refreshedPodcast, latestPodcast, localPodcast),
            latestEpisode = resolveLatestEpisode(refreshedPodcast, latestPodcast, localPodcast),
        )
    }

    private fun resolveNotifications(
        effectiveSubscribed: Boolean,
        latest: Podcast,
        local: Podcast?,
    ): Boolean = effectiveSubscribed && (latest.notificationsEnabled || (local?.notificationsEnabled == true))

    private fun resolveAutoDownload(
        effectiveSubscribed: Boolean,
        latest: Podcast,
        local: Podcast?,
    ): Boolean = effectiveSubscribed && (latest.autoDownloadEnabled || (local?.autoDownloadEnabled == true))

    private fun resolveSubscribedAt(
        effectiveSubscribed: Boolean,
        latest: Podcast,
        local: Podcast?,
        refreshed: Podcast,
    ): Long = if (effectiveSubscribed) {
        latest.subscribedAt.takeIf { it > 0L }
            ?: local?.subscribedAt?.takeIf { it > 0L }
            ?: refreshed.subscribedAt.takeIf { it > 0L }
            ?: 0L
    } else {
        0L
    }

    private fun resolveSkips(
        latest: Podcast,
        local: Podcast?,
        refreshed: Podcast,
    ): Pair<Long?, Long?> {
        val beginning =
            latest.skipBeginningOverrideMs
                ?: local?.skipBeginningOverrideMs
                ?: refreshed.skipBeginningOverrideMs
        val ending =
            latest.skipEndingOverrideMs
                ?: local?.skipEndingOverrideMs
                ?: refreshed.skipEndingOverrideMs
        return beginning to ending
    }

    private fun resolveFallbackImage(
        latest: Podcast,
        local: Podcast?,
        refreshed: Podcast,
    ): String? = latest.fallbackImageUrl?.takeIf { it.isNotBlank() }
        ?: local?.fallbackImageUrl?.takeIf { it.isNotBlank() }
        ?: refreshed.fallbackImageUrl

    private fun resolvePreferredSort(
        latest: Podcast,
        local: Podcast?,
        refreshed: Podcast,
    ): String? = latest.preferredSort?.takeIf { it.isNotBlank() }
        ?: local?.preferredSort?.takeIf { it.isNotBlank() }
        ?: refreshed.preferredSort

    private fun resolveFeedUrl(
        refreshed: Podcast,
        latest: Podcast?,
        local: Podcast?,
    ): String? = refreshed.feedUrl?.takeIf { it.isNotBlank() }
        ?: latest?.feedUrl?.takeIf { it.isNotBlank() }
        ?: local?.feedUrl?.takeIf { it.isNotBlank() }

    private fun resolveLatestEpisode(
        refreshed: Podcast,
        latest: Podcast,
        local: Podcast?,
    ): Episode? {
        val localEp = local?.latestEpisode
        val latestEp = latest.latestEpisode
        val refreshedEp = refreshed.latestEpisode
        return listOfNotNull(localEp, latestEp, refreshedEp).maxByOrNull { it.publishedDate }
    }
}
