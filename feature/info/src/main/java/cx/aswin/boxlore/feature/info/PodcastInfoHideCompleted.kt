package cx.aswin.boxlore.feature.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import cx.aswin.boxlore.feature.info.logic.HideCompletedFill

/**
 * With completed episodes hidden, keeps asking for the next page while too few unfinished episodes
 * show ([HideCompletedFill]), and returns what the list should say meanwhile or once none are left.
 */
@Composable
internal fun rememberHideCompletedFill(
    hideCompleted: Boolean,
    searching: Boolean,
    visibleCount: Int,
    state: PodcastInfoUiState.Success,
    onLoadMore: () -> Unit,
): HideCompletedFill.State {
    val loadsMore = HideCompletedFill.loadsMore(hideCompleted, searching, visibleCount, state.hasMoreEpisodes, state.isLoadingMore)
    val load by rememberUpdatedState(onLoadMore)
    // Keyed on the loaded count too, so each page that still leaves too few asks for the next.
    LaunchedEffect(loadsMore, state.episodes.size) {
        if (loadsMore) load()
    }
    return HideCompletedFill.state(hideCompleted, searching, visibleCount, state.episodes.size, state.hasMoreEpisodes, state.isLoadingMore)
}
