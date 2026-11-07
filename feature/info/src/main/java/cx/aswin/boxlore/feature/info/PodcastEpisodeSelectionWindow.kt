package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.model.Episode

internal data class PodcastEpisodeSelectionWindow(
    val episodes: List<Episode>,
    val newestFirst: Boolean,
)

/** Episodes asked for per request while reading a whole show: the most one request returns. */
internal const val SELECTION_PAGE_SIZE = 100

/**
 * Every episode of the show, for range selection (Select all / older / newer) and for looking a
 * saved selection up again after recreation: page after page in [sort] from [loadPage] (limit,
 * offset and sort parameter) until the show has no more, then the episodes [state] already lists
 * that the pages didn't, without repeats, in date order. [sort] is the order the selection was
 * made in; null uses the page's current sort. Without a loaded page the window is empty. A failed
 * page throws, so a selection never acts on part of a show as if it were all of it.
 */
internal suspend fun loadPodcastEpisodeSelectionWindow(
    state: PodcastInfoUiState.Success?,
    sort: EpisodeSort?,
    loadPage: suspend (limit: Int, offset: Int, sort: String) -> PodcastRepository.EpisodePage,
): PodcastEpisodeSelectionWindow {
    val newestFirst = (sort ?: state?.currentSort) != EpisodeSort.OLDEST
    if (state == null) {
        return PodcastEpisodeSelectionWindow(emptyList(), newestFirst)
    }
    val sortParam = if (newestFirst) "newest" else "oldest"
    val episodes = LinkedHashMap<String, Episode>()
    var offset = 0
    do {
        val page = loadPage(SELECTION_PAGE_SIZE, offset, sortParam)
        val before = episodes.size
        page.episodes.forEach { episodes.putIfAbsent(it.id, it) }
        offset += page.sourceCount
        // A page that adds nothing new ends the read, so a source that keeps saying "more" can't loop.
    } while (page.hasMore && page.sourceCount > 0 && episodes.size > before)
    state.episodes.forEach { episodes.putIfAbsent(it.id, it) }
    val byDate = compareBy<Episode> { it.publishedDate }
    return PodcastEpisodeSelectionWindow(
        episodes = episodes.values.sortedWith(if (newestFirst) byDate.reversed() else byDate),
        newestFirst = newestFirst,
    )
}
