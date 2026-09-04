package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.Episode

/**
 * First-class local episode catalog for a subscribed Podcast Index show.
 *
 * Production: `cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository`.
 * This is **not** [RssSubscriptionPort] (true `rss:` library) and **not**
 * [EpisodeSupplementPort] (feed-only extras). Features must not touch DAOs.
 */
interface LocalEpisodeCatalogPort {
    data class PodcastMeta(val title: String? = null, val imageUrl: String? = null, val genre: String? = null, val artist: String? = null,)

    data class RefreshRequest(
        val podcastIndexId: String,
        val feedUrl: String,
        val meta: PodcastMeta = PodcastMeta(),
        /**
         * One-time PI rematch on first persist only. Later refreshes ignore this.
         * A throwing loader fails the refresh and keeps last-good rows.
         */
        val loadPiBaseline: (suspend () -> List<Episode>)? = null,
    )

    sealed interface RefreshOutcome {
        data class Success(val newest: Episode?, val itemCount: Int, val ready: Boolean,) : RefreshOutcome

        data class Unchanged(val newest: Episode?,) : RefreshOutcome

        data class Failure(val message: String,) : RefreshOutcome
    }

    /** True when this show must be served from Room only (no PI ∪ extras merge). */
    suspend fun isReady(podcastId: String): Boolean

    suspend fun getPage(podcastId: String, limit: Int, offset: Int, sort: String, meta: PodcastMeta = PodcastMeta(),): List<Episode>

    /**
     * Bounded window for Smart Queue / Download / Auto.
     * When [aroundEpisodeId] is set, returns the next [bound] episodes in [sort]
     * order from that id (inclusive of a small lookback so the current item is found).
     */
    suspend fun getWindow(
        podcastId: String,
        sort: String,
        bound: Int,
        aroundEpisodeId: String?,
        meta: PodcastMeta = PodcastMeta(),
    ): List<Episode>

    suspend fun getEpisode(episodeId: String, meta: PodcastMeta = PodcastMeta(),): Episode?

    /** Guid, else enclosure. Used by FCM hydration — no newest-in-feed fallback. */
    suspend fun findByCatalogKey(podcastId: String, guid: String?, enclosureUrl: String?, meta: PodcastMeta = PodcastMeta(),): Episode?

    suspend fun search(podcastId: String, query: String, meta: PodcastMeta = PodcastMeta(),): List<Episode>

    suspend fun newest(podcastId: String, meta: PodcastMeta = PodcastMeta(),): Episode?

    suspend fun count(podcastId: String): Int

    suspend fun refresh(request: RefreshRequest): RefreshOutcome

    suspend fun isPublisherFeedUnchanged(podcastId: String, feedUrl: String,): Boolean

    /** Record a GET /podcast feedUrl lookup time for the daily retry. */
    suspend fun markFeedUrlLookup(podcastId: String, atMillis: Long,)

    suspend fun lastFeedUrlLookupAt(podcastId: String): Long

    suspend fun setUnsubscribedTtl(podcastId: String, ttlExpiresAt: Long?,)

    suspend fun sweepExpired(nowMillis: Long)
}
