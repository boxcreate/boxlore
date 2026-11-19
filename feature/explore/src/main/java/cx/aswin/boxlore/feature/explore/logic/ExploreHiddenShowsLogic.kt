package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.model.withoutHiddenShowEpisodes
import cx.aswin.boxlore.core.model.withoutHiddenShows
import cx.aswin.boxlore.feature.explore.ExploreUiState

/**
 * Explore without the shows the listener hid: For you, the Top chart and genres, mood topics and
 * Ask anything. Find a show keeps them, so a hidden show can be found and unhidden.
 */
internal object ExploreHiddenShowsLogic {
    fun withoutHiddenShows(state: ExploreUiState.Success, hiddenIds: Set<String>): ExploreUiState.Success {
        if (hiddenIds.isEmpty()) return state
        return state.copy(
            trending = state.trending.withoutHiddenShows(hiddenIds),
            recommendations = state.recommendations.withoutHiddenShowEpisodes(hiddenIds),
            // With a mood topic open, the results are its curated shows; otherwise a show search.
            searchResults = if (state.currentVibe != null) state.searchResults.withoutHiddenShows(hiddenIds) else state.searchResults,
            semanticSearchResults = state.semanticSearchResults.withoutHiddenShowEpisodes(hiddenIds),
            semanticPodcastResults = state.semanticPodcastResults.withoutHiddenShows(hiddenIds),
        )
    }
}
