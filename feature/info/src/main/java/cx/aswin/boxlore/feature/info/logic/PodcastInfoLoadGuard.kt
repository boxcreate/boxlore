package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.feature.info.PodcastInfoUiState

/**
 * Decides whether [cx.aswin.boxlore.feature.info.PodcastInfoViewModel.loadPodcast] can skip a call
 * (P4, adaptive show page).
 *
 * The screen calls `loadPodcast(routeId)` again whenever it is recomposed from saved state: after
 * activity recreation (a fold density change) and on a return from the back stack. The page's
 * current ID can differ from the route ID: a followed `rss:` feed linked to the show replaces it,
 * and so can the catalog ID that show details return. Comparing only the current ID would reload
 * those shows from scratch and lose the search query, loaded pages and scroll position, so the
 * requested route ID counts as a match too.
 */
internal object PodcastInfoLoadGuard {
    fun shouldSkipLoad(
        routePodcastId: String,
        requestedPodcastId: String?,
        currentPodcastId: String,
        state: PodcastInfoUiState,
    ): Boolean {
        val settled = state is PodcastInfoUiState.Success || state is PodcastInfoUiState.Error
        val sameShow = routePodcastId == requestedPodcastId || routePodcastId == currentPodcastId
        return settled && sameShow
    }
}
