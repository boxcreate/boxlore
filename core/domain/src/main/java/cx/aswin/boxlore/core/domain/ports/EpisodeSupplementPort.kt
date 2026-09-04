package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.Episode

/**
 * PI-owned episode list supplement from a show's public RSS feed.
 *
 * This is **not** [RssSubscriptionPort]: it never creates an `rss:` library row,
 * never migrates/retires a Podcast Index subscription, and never writes FCM topics
 * or autodownload flags. Subscription notification tracking may read
 * [hasDirectFeedOptIn] when attaching `feedUrl` to RTDB rows.
 * Production: `cx.aswin.boxlore.core.rss.EpisodeSupplementRepository`.
 */
interface EpisodeSupplementPort {
    /**
     * Inputs for [refreshFromFeed]. Optional [loadBaseline] starts in parallel with
     * the publisher GET so launch sync does not wait for a 1000-episode PI page
     * before downloading RSS.
     */
    data class RefreshFromFeedRequest(
        val podcastIndexId: String,
        val feedUrl: String,
        val baselineEpisodes: List<Episode> = emptyList(),
        val loadBaseline: (suspend () -> List<Episode>)? = null,
        val podcastTitle: String? = null,
        val podcastImageUrl: String? = null,
        val podcastGenre: String? = null,
        val podcastArtist: String? = null,
    )

    /**
     * Fetches [feedUrl], keeps feed-only episodes not present in [baselineEpisodes],
     * and replaces the cached supplement for [podcastIndexId].
     *
     * On success, [EpisodeSupplementOutcome.Success.newestFeedEpisode] is the tip to
     * write into Room `podcasts.latestEpisode` for Home filter chips / heroes.
     */
    suspend fun refreshFromFeed(
        podcastIndexId: String,
        feedUrl: String,
        baselineEpisodes: List<Episode>,
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): EpisodeSupplementOutcome

    /**
     * Default delegates to the list form and ignores [RefreshFromFeedRequest.loadBaseline].
     * Production overrides this to fetch the feed and PI baseline together.
     */
    suspend fun refreshFromFeed(request: RefreshFromFeedRequest): EpisodeSupplementOutcome = refreshFromFeed(
        podcastIndexId = request.podcastIndexId,
        feedUrl = request.feedUrl,
        baselineEpisodes = request.baselineEpisodes,
        podcastTitle = request.podcastTitle,
        podcastImageUrl = request.podcastImageUrl,
        podcastGenre = request.podcastGenre,
        podcastArtist = request.podcastArtist,
    )

    /**
     * True when the stored supplement ETag / Last-Modified still matches the live feed.
     * Default false (always refresh). Production HEADs the publisher URL.
     */
    suspend fun isPublisherFeedUnchanged(podcastIndexId: String, feedUrl: String,): Boolean = false

    /**
     * Fetches [feedUrl] and **only** persists a supplement (opt-in) when the feed
     * is ahead of [baselineEpisodes] (missing episodes and/or newer tip).
     * Returns [EpisodeSupplementOutcome.NoDisconnect] when the PI list already matches.
     */
    suspend fun optInFromFeedIfDisconnected(
        podcastIndexId: String,
        feedUrl: String,
        baselineEpisodes: List<Episode>,
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): EpisodeSupplementOutcome

    /**
     * True once the user has enabled direct-feed refresh for this PI show
     * (a supplement row exists). Used to hide the opt-in control and refresh on open.
     */
    suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean

    /** Podcast Index ids that have a direct-feed supplement row (user opted in). */
    suspend fun listOptedInPodcastIds(): Set<String>

    /** HTTPS publisher feed the user opted into via Missing episodes?. */
    data class DirectFeedOptIn(val podcastIndexId: String, val feedUrl: String,)

    /**
     * Opted-in PI shows with their stored publisher URLs. Default empty so fakes
     * that only implement [listOptedInPodcastIds] keep compiling.
     */
    suspend fun listDirectFeedOptIns(): List<DirectFeedOptIn> = emptyList()

    /**
     * Restores Missing episodes? after library import by writing a supplement row
     * without a feed GET. Existing extras stay. No-ops for blank / `rss:` ids or
     * non-HTTPS URLs. Default no-op for fakes.
     */
    suspend fun restoreDirectFeedOptIn(podcastIndexId: String, feedUrl: String,) = Unit

    /**
     * Optional identity for a specific feed item (FCM hydration). When set,
     * [resolveNewestTipFromFeed] returns that item instead of whatever is currently newest.
     */
    data class FeedItemMatch(val guid: String? = null, val enclosureUrl: String? = null,)

    /**
     * Inputs for [resolveNewestTipFromFeed]. Bundled so the port stays under
     * detekt's parameter-list threshold.
     */
    data class NewestTipRequest(
        val podcastIndexId: String,
        val feedUrl: String,
        val knownEpisodes: List<Episode> = emptyList(),
        val podcastTitle: String? = null,
        val podcastImageUrl: String? = null,
        val podcastGenre: String? = null,
        val podcastArtist: String? = null,
        val match: FeedItemMatch? = null,
    )

    /**
     * For opted-in library sync: fetch the publisher feed and return a tip
     * under [NewestTipRequest.podcastIndexId] without requiring a full PI episode baseline.
     * Also upserts the tip item when it is feed-only so playback can resolve it.
     *
     * When [NewestTipRequest.match] is set (FCM hydration), only that feed item is
     * returned — not an unrelated newer item.
     */
    suspend fun resolveNewestTipFromFeed(request: NewestTipRequest): Episode?

    suspend fun getEpisodesForPodcast(
        podcastIndexId: String,
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): List<Episode>

    suspend fun getEpisode(
        episodeId: String,
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): Episode?

    suspend fun search(
        podcastIndexId: String,
        query: String,
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): List<Episode>
}

sealed interface EpisodeSupplementOutcome {
    data class Success(
        val addedCount: Int,
        val totalSupplementCount: Int,
        /** Newest item from the live feed, remapped under the PI podcast id. */
        val newestFeedEpisode: Episode?,
    ) : EpisodeSupplementOutcome

    /** Feed matches the PI baseline — no supplement row written. */
    data object NoDisconnect : EpisodeSupplementOutcome

    data class Failure(val message: String,) : EpisodeSupplementOutcome
}
