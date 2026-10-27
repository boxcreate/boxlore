package cx.aswin.boxlore.core.domain.ports

/**
 * The phone's stored copy of a Podcast Index show's feed for its show page, followed or not, and
 * the reads that fill it. Pages and search read the copy through [LocalEpisodeCatalogPort.getPage]
 * and [LocalEpisodeCatalogPort.search]; this port says what the copy holds and adds to it.
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
     * One read of [feedUrl] (HTTPS) for show [podcastId]. [serverEpNs] is only compared with the
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
     * A feed document read some other way (boxlore's transport address for an http-only feed).
     * [complete] is false when it holds only the start of the feed.
     */
    class Document(val bytes: ByteArray, val finalUrl: String, val etag: String?, val complete: Boolean,)

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

    /** Stores [document] as a read of [request]'s show; a document that isn't a feed is [ReadOutcome.Failed]. */
    suspend fun store(request: ReadRequest, document: Document): ReadOutcome

    /** Notes that the show page opened this show now, which keeps its copy from the automatic cleanup. */
    suspend fun markOpened(podcastId: String, atMillis: Long)

    /**
     * Removes the copies of unfollowed shows not opened in 30 days, then the least recently opened
     * ones over the size cap, never [keepPodcastId]. Only `local_episodes` and `local_episode_feeds`
     * rows go: listening history, downloads, the queue and episode aliases stay. Returns how many
     * shows were removed.
     */
    suspend fun cleanUp(nowMillis: Long, keepPodcastId: String? = null): Int
}
