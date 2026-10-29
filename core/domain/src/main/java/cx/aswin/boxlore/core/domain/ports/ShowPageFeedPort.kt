package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.Episode

/**
 * The phone's stored copy of a Podcast Index show's feed for its show page, followed or not, and
 * the reads that fill it. Pages read the copy through [LocalEpisodeCatalogPort.getPage]; this port
 * says what the copy holds, adds to it and searches it ([search], bounded).
 *
 * A read never makes an unfollowed show's copy the followed live list ([LocalEpisodeCatalogPort.isReady]
 * stays false), and it never runs the post-write work of followed shows (auto-download, new-episode
 * delivery). New items take the same IDs a followed show's refresh gives them; stored rows keep theirs.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 * COMPAT(own-ids): show pages read the show's own feed. Permanent: never remove (MIGRATION.md §5).
 */
interface ShowPageFeedPort {
    /**
     * What the phone holds for a show.
     *
     * - [itemCount]: stored episodes.
     * - [archiveComplete]: every item of the feed has been read, not only the newest part.
     * - [fetchedAt]: when the copy was last read or confirmed current (epoch ms).
     * - [oldestFirst]: the feed lists its oldest items first, so a read of its start is no use.
     */
    data class CachedShow(
        val podcastId: String,
        val feedUrl: String,
        val fetchedAt: Long,
        val itemCount: Int,
        val archiveComplete: Boolean,
        val oldestFirst: Boolean,
    )

    /** How much of the feed a read takes: its newest items, or the whole document. */
    enum class Scope { NEWEST, ARCHIVE }

    /**
     * One read of [feedUrl] (HTTPS; for [readTransport] the publisher's http address, if known) for show [podcastId]. [serverEpNs] is only compared with the
     * stored namespace (decision D12).
     */
    data class ReadRequest(
        val podcastId: String,
        val feedUrl: String,
        val scope: Scope,
        val meta: LocalEpisodeCatalogPort.PodcastMeta = LocalEpisodeCatalogPort.PodcastMeta(),
        val serverEpNs: Long? = null,
    )

    /**
     * One answer of boxlore's transport (`/v2/feed/<id>`), the only way to read an http-only feed.
     * Transport documents are always the whole feed.
     */
    sealed interface Transported {
        /** The feed document; [etag] answers the next read's `If-None-Match`. */
        class Document(val bytes: ByteArray, val etag: String?) : Transported

        /** The stored copy is current (304 to the ETag the read sent). */
        data object NotModified : Transported

        /** The show's address is HTTPS after all (`404 not_proxied`): read [url] directly. */
        data class ReadDirect(val url: String) : Transported

        /** The transport couldn't answer; [reportStatus] as in [ReadOutcome.Failed]. */
        data class Failed(val reportStatus: String) : Transported
    }

    sealed interface ReadOutcome {
        /** The read was stored; [complete] when it held the whole feed. */
        data class Stored(val itemCount: Int, val complete: Boolean) : ReadOutcome

        /** The publisher said the copy is current (304). */
        data object NotModified : ReadOutcome

        /**
         * The read failed. [reportStatus] is what it saw, in the words `/v2/feed-report` takes
         * (`http_404`, `timeout`, `dns`, `tls`, `parse`, `network`, `other`).
         */
        data class Failed(val reportStatus: String) : ReadOutcome
    }

    suspend fun cached(podcastId: String): CachedShow?

    /** Reads [ReadRequest.feedUrl] and stores what it holds. Never throws for a failed read. */
    suspend fun read(request: ReadRequest): ReadOutcome

    /**
     * Reads [request]'s show through boxlore's transport: [fetch] is given the stored ETag (only
     * when the copy is the whole archive) and answers. It runs under the same per-show lock and
     * download gate as [read]. Neither the transport address nor [ReadRequest.feedUrl] (the
     * publisher's own http address) is recorded as the copy's address. A [Transported.ReadDirect]
     * answer is read directly ([read]).
     */
    suspend fun readTransport(request: ReadRequest, fetch: suspend (ifNoneMatch: String?) -> Transported): ReadOutcome

    /**
     * In-show search over [podcastId]'s copy: titles and descriptions containing [query], newest
     * first, at most [limit] episodes.
     */
    suspend fun search(
        podcastId: String,
        query: String,
        limit: Int,
        meta: LocalEpisodeCatalogPort.PodcastMeta = LocalEpisodeCatalogPort.PodcastMeta(),
    ): List<Episode>

    /** Notes that the show page opened this show now, which keeps its copy from the automatic cleanup. */
    suspend fun markOpened(podcastId: String, atMillis: Long)

    /**
     * Removes the copies of unfollowed shows not opened in 30 days, then the least recently opened
     * ones over the size cap, never [keepPodcastId]. Only `local_episodes` and `local_episode_feeds`
     * rows go: listening history, downloads, the queue and episode aliases stay. Returns how many
     * shows were removed.
     */
    suspend fun cleanUp(nowMillis: Long, keepPodcastId: String? = null): Int

    /** How many unfollowed shows have a saved copy (Settings › Clear saved shows). P4 PR 20. */
    suspend fun unfollowedCount(): Int = 0

    /**
     * Removes every unfollowed show's saved copy, whatever its age (Settings › Clear saved shows).
     * The same rows as [cleanUp]: listening history, downloads, the queue and episode aliases stay.
     * Returns how many shows were removed. P4 PR 20 (decision D10).
     */
    suspend fun clearUnfollowed(): Int = 0
}
