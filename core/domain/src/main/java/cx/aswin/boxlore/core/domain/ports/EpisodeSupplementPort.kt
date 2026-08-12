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

    /**
     * For opted-in library sync: fetch the publisher feed and return the newest tip
     * under [podcastIndexId] without requiring a full PI episode baseline.
     * Also upserts the tip item when it is feed-only so playback can resolve it.
     */
    suspend fun resolveNewestTipFromFeed(
        podcastIndexId: String,
        feedUrl: String,
        knownEpisodes: List<Episode> = emptyList(),
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): Episode?

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

    data class Failure(
        val message: String,
    ) : EpisodeSupplementOutcome
}
