package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.feature.info.logic.PodcastInfoAsyncResultLogic
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPullRefreshLogic
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The show page's live state, shared by [PodcastInfoViewModel] and the helpers it hands work to
 * (opening a show, the episode list, pull to refresh, the refresh after subscribing and the
 * episode actions). Each helper reads these values when its work runs, never a copy taken
 * earlier, so a result that arrives after the page moved to another show is still dropped.
 */
internal class PodcastInfoPageState {
    val uiState = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Loading)

    /** The show the page lists: the route's show, a followed `rss:` feed linked to it, or the catalog ID from show details. */
    var currentPodcastId: String = ""

    /** [currentPodcastId] for flows; set alongside it. */
    val currentPodcastIdFlow = MutableStateFlow("")

    /** Id passed to the latest [PodcastInfoViewModel.loadPodcast] call, for [PodcastInfoViewModel.retryLoad]. */
    var requestedPodcastId: String? = null

    /** The route this page was opened for and the show it resolved to. */
    val pageShow = MutableStateFlow(PodcastInfoPageShow())

    /** Source episodes loaded so far, the offset of the next page. */
    var currentOffset: Int = 0

    /** Records the show [routePodcastId]'s page resolved to, while that route is still the one requested. */
    fun setShownPodcastId(routePodcastId: String, shownPodcastId: String) {
        if (requestedPodcastId == routePodcastId) pageShow.value = PodcastInfoPageShow(routePodcastId, shownPodcastId)
    }

    /** Clears the loading row when a feed-first read ended without reloading the page (it moved on, or the archive was already whole). */
    fun stopLoadingMore() {
        val state = uiState.value as? PodcastInfoUiState.Success ?: return
        if (state.isLoadingMore) uiState.value = state.copy(isLoadingMore = false)
    }

    /** Ends a pull-to-refresh of [targetPodcastId] while the page still shows that show. */
    fun clearPullRefreshing(targetPodcastId: String) {
        if (!PodcastInfoPullRefreshLogic.shouldApply(currentPodcastId, targetPodcastId)) return
        val latestState = uiState.value as? PodcastInfoUiState.Success ?: return
        uiState.value = latestState.copy(isLoadingMore = false, isRssRefreshing = false)
    }

    /**
     * Shows a feed-extras refresh of [targetPodcastId] when the page still shows that show, keeping
     * its current follow state ([PodcastInfoAsyncResultLogic.preserveCurrentSubscription]). Null when
     * the result was dropped.
     */
    fun applySupplementRefreshState(
        targetPodcastId: String,
        result: PodcastInfoUiState.Success,
    ): PodcastInfoUiState.Success? {
        val current = uiState.value as? PodcastInfoUiState.Success ?: return null
        val applied =
            PodcastInfoAsyncResultLogic.preserveCurrentSubscription(
                current = current,
                result = result,
                targetPodcastId = targetPodcastId,
            ) ?: return null
        uiState.value = applied
        return applied
    }

    companion object {
        /** Logcat tag shared by the ViewModel and its helpers. */
        const val TAG = "PodcastInfoViewModel"
        private const val PAGE_SIZE = 20
        private const val OLDEST_PAGE_SIZE = 50

        /** Episodes per page in [sort]: oldest-first pages are longer. */
        fun pageSizeFor(sort: EpisodeSort): Int = if (sort == EpisodeSort.OLDEST) OLDEST_PAGE_SIZE else PAGE_SIZE

        /** The repository's sort parameter for [sort]. */
        fun sortParamFor(sort: EpisodeSort): String = if (sort == EpisodeSort.OLDEST) "oldest" else "newest"
    }
}
