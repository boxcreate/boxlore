package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.Episode

/**
 * PI-owned episode list supplement from a show's public RSS feed.
 *
 * This is **not** [RssSubscriptionPort]: it never creates an `rss:` library row,
 * never migrates/retires a Podcast Index subscription, and never touches FCM /
 * autodownload. Production: `cx.aswin.boxlore.core.rss.EpisodeSupplementRepository`.
 */
interface EpisodeSupplementPort {
    /**
     * Fetches [feedUrl], keeps feed-only episodes not present in [baselineEpisodes],
     * and replaces the cached supplement for [podcastIndexId].
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
     * True once the user has enabled direct-feed refresh for this PI show
     * (a supplement row exists). Used to hide the opt-in control and refresh on open.
     */
    suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean

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
    ) : EpisodeSupplementOutcome

    data class Failure(
        val message: String,
    ) : EpisodeSupplementOutcome
}
