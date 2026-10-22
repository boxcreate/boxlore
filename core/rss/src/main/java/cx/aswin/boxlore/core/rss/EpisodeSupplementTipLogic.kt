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
            // A match baseline carries only the ID and match fields: the feed fills the rest.
            val feed = newestFeedEpisode.toEpisode()
            return matched.withBlanksFrom(feed).copy(
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

/**
 * This episode with every empty field taken from [other], so a match-only baseline row (ID, title,
 * audio, date) still yields a tip with description, artwork, length and Podcast 2.0 links. A field
 * this episode has always wins, so a full Podcast Index row is unchanged.
 */
internal fun Episode.withBlanksFrom(other: Episode): Episode = copy(
    title = title.ifBlank { other.title },
    description = description.ifBlank { other.description },
    audioUrl = audioUrl.ifBlank { other.audioUrl },
    imageUrl = imageUrl?.takeIf(String::isNotBlank) ?: other.imageUrl,
    duration = duration.takeIf { it > 0 } ?: other.duration,
    publishedDate = publishedDate.takeIf { it > 0L } ?: other.publishedDate,
    chaptersUrl = chaptersUrl ?: other.chaptersUrl,
    transcriptUrl = transcriptUrl ?: other.transcriptUrl,
    transcripts = transcripts ?: other.transcripts,
    persons = persons ?: other.persons,
    seasonNumber = seasonNumber ?: other.seasonNumber,
    episodeNumber = episodeNumber ?: other.episodeNumber,
    episodeType = episodeType ?: other.episodeType,
    enclosureType = enclosureType ?: other.enclosureType,
)
