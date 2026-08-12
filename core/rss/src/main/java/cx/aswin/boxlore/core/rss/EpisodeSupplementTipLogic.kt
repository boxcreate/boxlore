package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Episode

/**
 * Picks a Room / Home library tip from a parsed feed under a Podcast Index owner id.
 *
 * Prefer a matching PI baseline episode id when possible so history / deep links stay
 * stable; otherwise use the feed's negative supplement episode id with [podcastIndexId]
 * as [Episode.podcastId].
 */
internal object EpisodeSupplementTipLogic {
    fun resolveNewestTip(
        newestFeedEpisode: RssEpisodeEntity,
        podcastIndexId: String,
        knownEpisodes: List<Episode>,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): Episode {
        val matched = EpisodeSupplementMatcher.findMatchingBaseline(newestFeedEpisode, knownEpisodes)
        if (matched != null) {
            return matched.copy(
                podcastId = podcastIndexId,
                podcastTitle = podcastTitle?.takeIf(String::isNotBlank) ?: matched.podcastTitle,
                podcastImageUrl = podcastImageUrl?.takeIf(String::isNotBlank) ?: matched.podcastImageUrl,
                podcastGenre = podcastGenre?.takeIf(String::isNotBlank) ?: matched.podcastGenre,
                podcastArtist = podcastArtist?.takeIf(String::isNotBlank) ?: matched.podcastArtist,
            )
        }
        return newestFeedEpisode
            .copy(podcastId = podcastIndexId)
            .toEpisode(
                podcastTitle = podcastTitle,
                podcastImageUrl = podcastImageUrl,
                podcastGenre = podcastGenre,
                podcastArtist = podcastArtist,
            )
    }
}
