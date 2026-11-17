package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.V2EpisodesPaging
import java.util.Locale

/**
 * In-show search's answer ([PodcastRepository.searchShowEpisodes]): the matching episodes, and
 * [searchedNewest], how many of the newest episodes were searched when the show lists more than the
 * server can page through (null when every episode was searched).
 */
data class ShowEpisodeSearch(
    val episodes: List<Episode>,
    val searchedNewest: Int? = null,
)

/**
 * How far in-show search has read a show's server's list, newest first, page by page. The newest
 * [SEARCH_FULL_ROWS] rows are kept as full episodes ([full]); older rows only as [SlimRow]s, so a
 * long list holds its IDs and a short search key rather than every episode's notes. Reading
 * carries on at [nextOffset] (a search that already found enough matches stops early); the list
 * was read to its end once [complete], or as far as the route pages once [cutShort] (more episodes
 * are left past its last offset).
 */
internal data class ShowSearchScan(
    val full: List<Episode> = emptyList(),
    val slim: List<SlimRow> = emptyList(),
    val nextOffset: Int = 0,
    val complete: Boolean = false,
    val cutShort: Boolean = false,
) {
    /**
     * An older row: its ID, the offset of the page it came from (read again for a match), and its
     * title and the first [SEARCH_SLIM_NOTES_CHARS] characters of its notes, tags removed, lowercased.
     */
    data class SlimRow(val id: String, val pageOffset: Int, val key: String)

    /** Nothing more to read: the list's end, or the route's last offset. */
    val done: Boolean get() = complete || cutShort

    /** Rows read so far, without repeats. */
    val rowCount: Int get() = full.size + slim.size

    /** This scan with [page], read at [offset], added: rows already held are skipped. */
    fun withPage(offset: Int, page: ShowEpisodeWindowSearch.Page): ShowSearchScan {
        val known = HashSet<String>(rowCount * 2)
        full.forEach { known += it.id }
        slim.forEach { known += it.id }
        val fresh = page.episodes.filter { known.add(it.id) }
        val room = (SEARCH_FULL_ROWS - full.size).coerceAtLeast(0)
        val next = offset + page.sourceCount
        val more = page.hasMore && page.sourceCount > 0
        return copy(
            full = full + fresh.take(room),
            slim = slim + fresh.drop(room).map { SlimRow(it.id, offset, slimKey(it)) },
            nextOffset = next,
            complete = !more,
            cutShort = more && next > V2EpisodesPaging.OFFSET_MAX,
        )
    }

    /**
     * The first [limit] rows matching [query] (by title or notes, ignoring case), newest first: the
     * full episodes that match, then the slim rows that do, whose episodes are read again.
     */
    fun matches(query: String, limit: Int): Matches {
        val needle = query.lowercase(Locale.ROOT)
        val fullMatches = full.filter { it.matches(needle) }.take(limit)
        val slimMatches = slim.filter { needle in it.key }.take((limit - fullMatches.size).coerceAtLeast(0))
        return Matches(fullMatches, slimMatches)
    }

    /** True when [matches] already finds [limit] rows for [query], so reading older pages can't change them. */
    fun answers(query: String, limit: Int): Boolean = matches(query, limit).let { it.full.size + it.slim.size >= limit }

    /** [matches]' answer. */
    data class Matches(val full: List<Episode>, val slim: List<SlimRow>)

    companion object {
        private val TAG = Regex("<[^>]*>")

        fun slimKey(episode: Episode): String =
            (episode.title + " " + episode.description.replace(TAG, " ").take(SEARCH_SLIM_NOTES_CHARS)).lowercase(Locale.ROOT)

        private fun Episode.matches(needle: String): Boolean =
            title.lowercase(Locale.ROOT).contains(needle) || description.lowercase(Locale.ROOT).contains(needle)
    }
}

private val searchMatchesLock = Any()

/**
 * Adds [episodes] to the show's kept matches as the most recent, dropping the oldest past
 * [SEARCH_MATCH_EPISODES]; nothing once the show's search list was dropped (a refresh, another show).
 */
internal fun rememberSearchMatches(resolvedId: String, episodes: List<Episode>) {
    if (episodes.isEmpty()) return
    synchronized(searchMatchesLock) {
        if (PodcastRepository.showSearchCache[resolvedId] == null) return
        val kept = LinkedHashMap(PodcastRepository.showSearchMatches[resolvedId].orEmpty())
        episodes.forEach { episode ->
            kept.remove(episode.id)
            kept[episode.id] = episode
        }
        val iterator = kept.keys.iterator()
        while (kept.size > SEARCH_MATCH_EPISODES && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
        PodcastRepository.showSearchMatches[resolvedId] = kept
    }
}
