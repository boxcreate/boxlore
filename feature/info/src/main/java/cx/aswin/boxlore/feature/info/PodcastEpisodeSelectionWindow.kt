package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.model.Episode

internal data class PodcastEpisodeSelectionWindow(
    val episodes: List<Episode>,
    val newestFirst: Boolean,
    val isTruncated: Boolean,
)

/** The most episodes range selection (Select all / older / newer) acts on. */
internal const val MAX_SELECTION_EPISODES = 100

/**
 * Up to [MAX_SELECTION_EPISODES] episodes for range selection and for looking a saved selection up
 * again after recreation: the first page in [sort] from [loadPage] (limit and sort parameter), then
 * the episodes [state] already lists, without repeats. [sort] is the order the selection was made
 * in; null uses the page's current sort. Without a loaded page the window is empty.
 */
internal suspend fun loadPodcastEpisodeSelectionWindow(
    state: PodcastInfoUiState.Success?,
    sort: EpisodeSort?,
    loadPage: suspend (limit: Int, sort: String) -> PodcastRepository.EpisodePage,
): PodcastEpisodeSelectionWindow {
    val newestFirst = (sort ?: state?.currentSort) != EpisodeSort.OLDEST
    if (state == null) {
        return PodcastEpisodeSelectionWindow(emptyList(), newestFirst, isTruncated = false)
    }
    val sortParam = if (newestFirst) "newest" else "oldest"
    val page = loadPage(MAX_SELECTION_EPISODES, sortParam)
    val episodes =
        (page.episodes + state.episodes)
            .distinctBy(Episode::id)
            .take(MAX_SELECTION_EPISODES)
    return PodcastEpisodeSelectionWindow(
        episodes = episodes,
        newestFirst = newestFirst,
        isTruncated = page.hasMore,
    )
}
