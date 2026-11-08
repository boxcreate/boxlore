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

/** The selected show's load state: the episodes loaded for it, whether the list shows a skeleton, and whether the last load failed with nothing to show. */
internal data class SelectedShowLoadState(
    val pool: ShowEpisodesPool? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** How the selected show's load state moves as loads start, succeed and fail. */
internal object SelectedShowLoadTransitions {
    val cleared = SelectedShowLoadState()

    /**
     * A load starts. Switching shows drops the old show's episodes. The skeleton shows only when
     * nothing is on screen, and then an earlier failure is cleared, so Try again shows the skeleton.
     */
    fun started(
        state: SelectedShowLoadState,
        switchingPodcast: Boolean,
    ): SelectedShowLoadState {
        val pool = if (switchingPodcast) null else state.pool
        val loading = HomeSelectedPodcastLogic.shouldShowLoading(switchingPodcast, listEmpty = pool == null)
        return SelectedShowLoadState(pool = pool, loading = loading, failed = state.failed && !loading)
    }

    fun succeeded(pool: ShowEpisodesPool): SelectedShowLoadState = SelectedShowLoadState(pool = pool)

    /** A failed refresh keeps the list on screen; only a show with nothing to show says it failed. */
    fun failed(state: SelectedShowLoadState): SelectedShowLoadState = state.copy(loading = false, failed = state.pool == null)
}
