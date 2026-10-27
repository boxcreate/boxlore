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

/**
 * The status line of a feed-first show page, above its episodes.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 */
enum class ShowFeedStatus {
    /** Nothing to say: the list is current, or the page doesn't read the feed. */
    NONE,

    /** The phone's stored copy shows; it is recent, so the feed isn't read again yet. No line. */
    CACHED,

    /** The newest episodes are being read from the feed. */
    READING_NEWEST,

    /** Every episode is being read from the feed (search, oldest first, scrolling on). */
    COMPLETING_ARCHIVE,

    /** The feed couldn't be read: the list is boxlore's list of the show. */
    FALLBACK,

    /** No connection: the list is the stored copy, as it is. */
    OFFLINE,

    /** No connection and no stored copy: nothing saved to show yet. */
    OFFLINE_NO_COPY,
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
        /** The feed-first show page's status line (P4 PR 16). */
        val feedStatus: ShowFeedStatus = ShowFeedStatus.NONE,
    ) : PodcastInfoUiState

    data object Error : PodcastInfoUiState
}
