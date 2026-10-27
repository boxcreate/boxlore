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
        val reason: RefreshReason = RefreshReason.NORMAL,
        /** Rechecked after waiting for a fetch slot and before network/persistence. */
        val canProceed: suspend () -> Boolean = { true },
        /** Background callers own gated release admission rather than the ordinary ingest callback. */
        val runPostPersistCallback: Boolean = true,
        /**
         * Check whether the feed moved for good ([RefreshOutcome.Success.movedTo]): every redirect
         * permanent, or a declared `itunes:new-feed-url` that serves the same show. Needs
         * [PodcastMeta.title] to compare with.
         */
        val detectMove: Boolean = false,
        /**
         * The episode namespace the server reported for this show (`epNs` from `/v2/podcast` or
         * `/v2/shows/feeds`), when the caller has one. It is only compared with the namespace the
         * phone keeps: the phone never adopts a different value and never re-keys, it logs the
         * difference (decision D12). New episodes are always minted in the stored namespace.
         *
         * Added in P4 PR 9 (own-IDs plan section 4, recipe IDs; decision D12).
         * COMPAT(own-ids): issued recipe IDs depend on the stored namespace. Permanent: never remove (MIGRATION.md §5).
         */
        val serverEpNs: Long? = null,
    )

    sealed interface RefreshOutcome {
        /** [movedTo]: the feed's confirmed new HTTPS address, when [RefreshRequest.detectMove] found one. */
        data class Success(val newest: Episode?, val itemCount: Int, val ready: Boolean, val movedTo: String? = null,) : RefreshOutcome

        data class Unchanged(val newest: Episode?,) : RefreshOutcome

        /**
         * [addressGone]: the publisher answered in a way that says the saved address no longer
         * works (gone or refused, no such host, a failing certificate, or not a feed), so it is
         * worth asking the server for a newer one. False for timeouts, server errors and offline.
         *
         * [reportStatus]: what the read saw, in the words `/v2/feed-report` takes (`http_404`,
         * `timeout`, `dns`, `tls`, `parse`, `network`, `other`); null when no read was made (a gate
         * said no, or there was no address to read), so there is nothing to report.
         * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: reports).
         */
        data class Failure(val message: String, val addressGone: Boolean = false, val reportStatus: String? = null,) : RefreshOutcome
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
