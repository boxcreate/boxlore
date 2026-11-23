package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.tv.model.ShowSort
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow

/** Where loading a show's episodes stands. LoadingMore: the newest are in, the rest of the feed is on its way. */
internal enum class EpisodeLoad { Loading, LoadingMore, Loaded, Failed }

/** How the TV puts its lists together: pure rules for [LiveTvLibrary]. */
internal object TvLibraryRules {
    /** A followed show's feed is read again for Daily Mix at most this often. */
    const val REFRESH_AFTER_MS = 6L * 60L * 60L * 1_000L

    /** A show's whole feed is read for its page at most this often. */
    const val WHOLE_FEED_AGAIN_MS = 30L * 60L * 1_000L

    /**
     * Whether a show page reads the show's whole feed after its newest [read] episodes: only a feed
     * that filled the first [page], so more may follow, and not again soon after the last time.
     */
    fun wantsWholeFeed(fromFeed: Boolean, read: Int, page: Int, lastReadAtMillis: Long?, nowMillis: Long): Boolean =
        fromFeed && read >= page && (lastReadAtMillis == null || nowMillis - lastReadAtMillis >= WHOLE_FEED_AGAIN_MS)

    /** Whether a feed read at [readAtMillis] is old enough to read again. */
    fun wantsRefresh(readAtMillis: Long?, nowMillis: Long): Boolean = readAtMillis == null || nowMillis - readAtMillis >= REFRESH_AFTER_MS

    /**
     * The episodes just read ([incoming]) in place of what the page [shown] from the oldest dated
     * incoming episode on, so episodes taken off the feed drop off. Older shown episodes stay, as a
     * feed read covers only the newest ones.
     */
    fun replaced(shown: List<TvEpisode>, incoming: List<TvEpisode>): List<TvEpisode> {
        val oldest = incoming.mapNotNull(TvEpisode::releasedAtMillis).minOrNull() ?: return newestFirst(incoming)
        val kept = shown.filter { old -> old.releasedAtMillis.let { it != null && it < oldest } && incoming.none { it.sameEpisode(old) } }
        return newestFirst(incoming + kept)
    }

    /** What the page shows. A list on screen stays there even when a later read fails. */
    fun state(load: EpisodeLoad?, episodes: List<TvEpisode>, showUnknown: Boolean): ShowEpisodes =
        when {
            episodes.isNotEmpty() || load == EpisodeLoad.Loaded -> ShowEpisodes.Loaded(episodes, more = load == EpisodeLoad.LoadingMore)
            showUnknown || load == EpisodeLoad.Failed -> ShowEpisodes.Failed
            else -> ShowEpisodes.Loading
        }

    /**
     * Your shows in one of the phone's orders. Smart puts the shows listened to most recently
     * first, then the rest as followed; Manual keeps the order they were followed in.
     */
    fun sorted(shows: List<TvShow>, state: TvLibraryState, sort: ShowSort): List<TvShow> =
        when (sort) {
            ShowSort.Smart -> {
                val lastHeard = state.history.mapNotNull { state.episodes[it]?.showId }.distinct()
                shows.sortedBy { show -> lastHeard.indexOf(show.id).let { if (it < 0) Int.MAX_VALUE else it } }
            }
            ShowSort.RecentlyUpdated -> shows.sortedByDescending { show -> state.latest[show.id]?.let(state.episodes::get)?.releasedAtMillis ?: 0L }
            ShowSort.AToZ -> shows.sortedBy { it.title.lowercase() }
            ShowSort.MostListened -> shows.sortedByDescending { state.listens[it.id] ?: 0 }
            ShowSort.Manual -> shows
        }

    /** How many of [showId]'s episodes are new to the listener: its newest one, until started or played. */
    fun newEpisodes(state: TvLibraryState, showId: String): Int {
        val newest = state.latest[showId] ?: return 0
        return if (newest in state.played || newest in state.recent) 0 else 1
    }

    // The sort is stable: episodes without a date keep their order, after the dated ones.
    private fun newestFirst(episodes: List<TvEpisode>): List<TvEpisode> = episodes.sortedByDescending { it.releasedAtMillis ?: Long.MIN_VALUE }

    private fun TvEpisode.sameEpisode(other: TvEpisode): Boolean =
        id == other.id ||
            (catalogKey != null && catalogKey == other.catalogKey) ||
            (audioUrl != null && audioUrl == other.audioUrl)
}
