package cx.aswin.boxlore.feature.info

import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

enum class EpisodeSort { NEWEST, OLDEST }

/** Top-bar pill for PI direct-feed supplement. */
enum class DirectFeedChipState {
    /** Not shown. */
    Hidden,

    /** User can opt in. */
    Offer,

    /** Fetch in progress (first time or on open). */
    Fetching,

    /** Opted in; idle. */
    Updated,
}

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
        /** Accumulated repository page list (offset-0 pages already include cached feed extras). */
        val piEpisodes: List<Episode> = emptyList(),
        val directFeedChip: DirectFeedChipState = DirectFeedChipState.Hidden,
        /** One-shot toast/snackbar text; cleared via [PodcastInfoViewModel.consumeUserMessage]. */
        val userMessage: String? = null,
    ) : PodcastInfoUiState

    data object Error : PodcastInfoUiState
}
