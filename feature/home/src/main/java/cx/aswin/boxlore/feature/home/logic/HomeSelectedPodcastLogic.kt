package cx.aswin.boxlore.feature.home.logic

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
        retryVersion: Long = 0L,
    ): SelectedPodcastSignal? {
        if (podcastId == null) return null
        val podcast = subs.find { it.id == podcastId }
        // A show switched to its feed reads the listening its old Podcast Index show keeps.
        val showIds = podcast?.listenerShowIds ?: setOf(podcastId)
        val preferredSort = podcast?.preferredSort ?: "newest"
        // Only a show listened to in order lists from the listener's place, so only it reloads when the place moves.
        val place = if (preferredSort == "oldest") HomeShowEpisodesLogic.placeHistoryId(allHistory, showIds) else null
        return SelectedPodcastSignal(
            podcastId = podcastId,
            placeHistoryId = place,
            sort = preferredSort,
            rssRefreshVersion = rssRefreshVersion,
            latestEpisodeId = podcast?.latestEpisode?.id,
            rssHasNewEpisodes = podcast?.rssHasNewEpisodes == true,
            retryVersion = retryVersion,
        )
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
