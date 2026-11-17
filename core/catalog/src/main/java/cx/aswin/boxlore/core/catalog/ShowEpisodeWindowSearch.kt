package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode

/**
 * Finds the window same-show continuation reads around an episode (the anchor): the anchor and the
 * episodes after it in time, as a followed show's catalog answers
 * ([cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.getWindow] with an anchor). The
 * show's list is read page by page in one sort until the anchor turns up or the list ends, so an
 * episode far past the newest few still continues from its own place.
 */
internal object ShowEpisodeWindowSearch {
    /** One page of the show's list; [sourceCount] is how far the next page's offset moves. */
    data class Page(
        val episodes: List<Episode>,
        val hasMore: Boolean,
        val sourceCount: Int = episodes.size,
    )

    /**
     * The anchor [anchorId] and up to [bound] - 1 episodes published after it, oldest first.
     * [firstPage] is the list at [startOffset] in its sort (newest first when [newestFirst]);
     * [loadPage] reads the page at an offset, null when it can't. [onFound] hears the anchor's row
     * in the list. Empty when the list was read from [startOffset] to its end without the anchor;
     * null when a page it needed couldn't be read, before the anchor turned up or after it (oldest
     * first, the next page that fills the window), so a cut-short window never passes for the end.
     */
    suspend fun around(
        anchorId: String,
        newestFirst: Boolean,
        bound: Int,
        firstPage: Page,
        startOffset: Int = 0,
        onFound: (row: Int) -> Unit = {},
        loadPage: suspend (offset: Int) -> Page?,
    ): List<Episode>? {
        val rest = (bound - 1).coerceAtLeast(0)
        // Newest first, the episodes after the anchor are the ones read before it: keep the closest.
        val newer = ArrayDeque<Episode>()
        var page = firstPage
        var offset = startOffset
        while (true) {
            val index = page.episodes.indexOfFirst { it.id == anchorId }
            if (index >= 0) {
                onFound(offset + index)
                val anchor = page.episodes[index]
                val after =
                    if (newestFirst) {
                        newer.keepLast(page.episodes.take(index), rest)
                        newer.reversed()
                    } else {
                        oldestAfter(page, index, offset, rest, loadPage) ?: return null
                    }
                return (listOf(anchor) + after).distinctBy(Episode::id).take(bound.coerceAtLeast(1))
            }
            if (!page.hasMore || page.sourceCount <= 0) return emptyList()
            if (newestFirst) newer.keepLast(page.episodes, rest)
            offset += page.sourceCount
            page = loadPage(offset) ?: return null
        }
    }

    /** [around] over one list already in hand, in any order: empty when it doesn't hold [anchorId]. */
    fun inList(anchorId: String, episodes: List<Episode>, bound: Int): List<Episode> {
        val chronological = episodes.sortedBy(Episode::publishedDate)
        val index = chronological.indexOfFirst { it.id == anchorId }
        if (index < 0) return emptyList()
        return chronological.drop(index).distinctBy(Episode::id).take(bound.coerceAtLeast(1))
    }

    /** Oldest first: the rest of the anchor's page, and the next page when that is short; null when that page fails. */
    private suspend fun oldestAfter(
        page: Page,
        index: Int,
        offset: Int,
        rest: Int,
        loadPage: suspend (offset: Int) -> Page?,
    ): List<Episode>? {
        val after = page.episodes.drop(index + 1)
        if (after.size >= rest || !page.hasMore) return after.take(rest)
        val next = loadPage(offset + page.sourceCount) ?: return null
        return (after + next.episodes).take(rest)
    }

    private fun ArrayDeque<Episode>.keepLast(episodes: List<Episode>, count: Int) {
        addAll(episodes)
        while (size > count) removeFirst()
    }
}
