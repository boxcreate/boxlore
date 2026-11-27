package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shows and episodes as boxlore's API and the shows' own feeds describe them, remembered for the
 * screens. Episodes come from each show's own feed, as on the phone; the API's list stands in only
 * when a feed cannot be read.
 */
internal class TvShowSource(
    private val store: TvLibraryStore,
    private val catalog: TvCatalog,
    private val feeds: TvFeedReader?,
    private val country: () -> String,
) {
    private val described = MutableStateFlow<Map<String, TvShow>>(emptyMap())
    private val unknown = MutableStateFlow<Set<String>>(emptySet())
    private val charted = MutableStateFlow<Map<String, List<TvShow>>>(emptyMap())

    // Charts being loaded, by genre ("" for all genres).
    private val chartLoads: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** Shows the API has described, by ID. */
    val shows: StateFlow<Map<String, TvShow>> = described.asStateFlow()

    /** Shows the API could not describe: their pages say so instead of staying blank. */
    val unknownShows: StateFlow<Set<String>> = unknown.asStateFlow()

    /** Charts loaded so far: shows popular now in the TV's country, by genre ("" for all genres). */
    val charts: StateFlow<Map<String, List<TvShow>>> = charted.asStateFlow()

    class Read(val episodes: List<TvEpisode>, val fromFeed: Boolean)

    /** The show as the listener follows it, else as the API last described it. */
    fun named(showId: String): TvShow? = store.state.value.shows.firstOrNull { it.id == showId } ?: described.value[showId]

    /** Keeps [show] as last seen, so a page left open after unfollowing can still draw it. */
    fun remember(show: TvShow) = described.update { it + (show.id to show) }

    /** Shows matching [query], or null when the API could not be reached. */
    suspend fun search(query: String): List<TvShow>? {
        val found = catalog.search(query) ?: return null
        val state = store.state.value
        return found.map { TvMapping.show(it, state.isSubscribed(it.id.toString())) }.also { shows -> described.update { it + shows.associateBy(TvShow::id) } }
    }

    /**
     * Loads the chart for [genre] (null for all genres) unless it is loaded or already loading, so
     * two asks for one chart load it once; a failed load leaves no chart, so the next ask tries again.
     */
    suspend fun loadChart(genre: String?) {
        val key = genre.orEmpty()
        if (charted.value[key] != null || !chartLoads.add(key)) return
        try {
            val found = catalog.trending(country(), genre) ?: return
            val state = store.state.value
            val shows = found.map { TvMapping.show(it, state.isSubscribed(it.id.toString())) }
            described.update { known -> known + shows.filterNot { it.id in known }.associateBy(TvShow::id) }
            charted.update { it + (key to shows) }
        } finally {
            chartLoads.remove(key)
        }
    }

    /** Up to [limit] of a show's newest episodes: from its feed, else from the API's list, which may name them by other IDs. */
    suspend fun episodes(showId: String, limit: Int): Read? {
        val id = showId.toLongOrNull() ?: return null
        val details = details(showId)
        val show = named(showId)
        val fromFeed = details?.let { feeds?.newest(id, it, limit) }?.takeIf { it.isNotEmpty() }
        if (fromFeed != null) return Read(fromFeed.map { TvMapping.episode(it, showId, show) }, fromFeed = true)
        return catalog.episodes(id)?.mapNotNull { TvMapping.episode(it, show) }?.take(limit)?.let { Read(it, fromFeed = false) }
    }

    /** A show's details from the API, remembering the show itself. A show nobody can name is noted as unknown. */
    suspend fun details(showId: String): V2PodcastResponse? {
        val details = showId.toLongOrNull()?.let { catalog.show(it) }
        val feed = details?.feed
        if (feed != null) {
            described.update { it + (showId to TvMapping.show(feed, subscribed = false)) }
            unknown.update { it - showId }
        } else if (named(showId) == null) {
            unknown.update { it + showId }
        }
        return details
    }
}
