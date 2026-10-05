package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.Episode

/**
 * First-class local episode catalog for a subscribed show, routed by show source.
 *
 * Production: catalog and RSS adapters composed by `SubscribedEpisodeCatalog`.
 * Subscription creation remains owned by [RssSubscriptionPort]; this is not
 * [EpisodeSupplementPort] (feed-only extras). Features must not touch DAOs.
 */
@Suppress("TooManyFunctions") // Paging, identity and freshness belong to the same catalog contract.
interface LocalEpisodeCatalogPort {
    data class PodcastMeta(val title: String? = null, val imageUrl: String? = null, val genre: String? = null, val artist: String? = null,)

    enum class RefreshReason { NORMAL, AUTO_DOWNLOAD, NEW_RELEASE, MANUAL }

    data class RefreshRequest(
        /** Stable show id, including rss: IDs when using the RSS adapter. */
        val podcastIndexId: String,
        val feedUrl: String,
        val meta: PodcastMeta = PodcastMeta(),
        /**
         * One-time PI rematch on first persist only. Later refreshes ignore this.
         * A throwing loader fails the refresh and keeps last-good rows.
         */
        val loadPiBaseline: (suspend () -> List<Episode>)? = null,
        val reason: RefreshReason = RefreshReason.NORMAL,
        /** Rechecked after waiting for a fetch slot and before network/persistence. */
        val canProceed: suspend () -> Boolean = { true },
        /** Background callers own gated release admission rather than the ordinary ingest callback. */
        val runPostPersistCallback: Boolean = true,
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
     * With [aroundEpisodeId], includes the anchor and the following chronological
     * episodes for playback continuation. Without an anchor, follows [sort].
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

    /** Shared per-show automatic freshness gate; manual refresh and pushes can bypass it. */
    suspend fun isRefreshDue(podcastId: String, feedUrl: String, nowMillis: Long): Boolean = true

    suspend fun isPublisherFeedUnchanged(podcastId: String, feedUrl: String,): Boolean

    /** Record a GET /podcast feedUrl lookup time for the daily retry. */
    suspend fun markFeedUrlLookup(podcastId: String, atMillis: Long,)

    suspend fun lastFeedUrlLookupAt(podcastId: String): Long

    suspend fun setUnsubscribedTtl(podcastId: String, ttlExpiresAt: Long?,)

    suspend fun sweepExpired(nowMillis: Long)
}
