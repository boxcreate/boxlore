package cx.aswin.boxlore.feature.info

import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

enum class EpisodeSort { NEWEST, OLDEST }

@Immutable
sealed interface PodcastInfoUiState {
    data object Loading : PodcastInfoUiState

    data class Success(
        val podcast: Podcast,
        val episodes: List<Episode>,
        val isSubscribed: Boolean,
        val isLoadingMore: Boolean = false,
        val isRssRefreshing: Boolean = false,
        val hasMoreEpisodes: Boolean = true,
        val currentSort: EpisodeSort = EpisodeSort.NEWEST,
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val searchResults: List<Episode>? = null, // null = not searching, empty = no results
    ) : PodcastInfoUiState

    data object Error : PodcastInfoUiState
}
