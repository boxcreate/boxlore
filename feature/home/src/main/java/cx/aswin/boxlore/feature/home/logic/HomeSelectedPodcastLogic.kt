package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.listenerShowIds
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import cx.aswin.boxlore.feature.home.SelectedPodcastSignal

internal object HomeSelectedPodcastLogic {
    fun buildSignal(
        podcastId: String?,
        allHistory: List<HomeListeningHistoryItem>,
        subs: List<Podcast>,
        rssRefreshVersion: Long,
    ): SelectedPodcastSignal? {
        if (podcastId == null) return null
        val podcast = subs.find { it.id == podcastId }
        // A show switched to its feed reads the listening its old Podcast Index show keeps.
        val showIds = podcast?.listenerShowIds ?: setOf(podcastId)
        val lastPlayed =
            allHistory
                .filter { it.podcastId in showIds }
                .maxByOrNull { it.lastPlayedAt }
        val preferredSort = podcast?.preferredSort ?: "newest"
        return SelectedPodcastSignal(
            podcastId = podcastId,
            lastPlayedEpisodeId = lastPlayed?.episodeId,
            sort = preferredSort,
            rssRefreshVersion = rssRefreshVersion,
            latestEpisodeId = podcast?.latestEpisode?.id,
            rssHasNewEpisodes = podcast?.rssHasNewEpisodes == true,
        )
    }

    fun oldestSortWindow(
        allEpisodes: List<Episode>,
        lastPlayedEpisodeId: String?,
        windowSize: Int = 15,
        lookback: Int = 2,
    ): List<Episode> {
        val lastPlayedIndex =
            if (lastPlayedEpisodeId != null) {
                allEpisodes.indexOfFirst { it.id == lastPlayedEpisodeId }
            } else {
                -1
            }
        val offset =
            if (lastPlayedIndex != -1) {
                (lastPlayedIndex - lookback).coerceAtLeast(0)
            } else {
                0
            }
        return allEpisodes.drop(offset).take(windowSize)
    }

    fun filterCompletedIfNeeded(
        episodes: List<Episode>,
        hideCompleted: Boolean,
        completedIds: Set<String>,
    ): List<Episode> = if (hideCompleted) {
        episodes.filter { it.id !in completedIds }
    } else {
        episodes
    }

    /**
     * Skeleton only when switching chips or the stream is still empty.
     * Same-chip tip / extras refresh must swap in place.
     */
    fun shouldShowLoading(
        switchingPodcast: Boolean,
        listEmpty: Boolean,
    ): Boolean = switchingPodcast || listEmpty
}
