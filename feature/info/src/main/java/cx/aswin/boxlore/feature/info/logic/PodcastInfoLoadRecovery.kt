package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.PodcastInfoUiState

/**
 * Podcast Info recovery when show details (`/podcast`) fail but a local row is already on screen.
 */
internal object PodcastInfoLoadRecovery {
    /**
     * Keeps the local podcast and applies the episode page that loaded alongside the failed
     * details request (it may come from the subscribed RSS catalog). With no fetched episodes,
     * existing episodes stay as they are. Loading always stops, so the list never spins forever.
     */
    fun withoutPodcastDetails(
        state: PodcastInfoUiState.Success,
        pageEpisodes: List<Episode>,
        pageHasMore: Boolean,
        sort: EpisodeSort,
    ): PodcastInfoUiState.Success =
        if (pageEpisodes.isNotEmpty()) {
            state.copy(
                episodes = pageEpisodes,
                piEpisodes = pageEpisodes,
                hasMoreEpisodes = pageHasMore,
                currentSort = sort,
                isLoadingMore = false,
            )
        } else {
            state.copy(
                hasMoreEpisodes = state.episodes.isNotEmpty() && state.hasMoreEpisodes,
                isLoadingMore = false,
            )
        }
}
