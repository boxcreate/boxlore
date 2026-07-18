package cx.aswin.boxlore.core.data.playback

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * Pure episode artwork URL resolution (episode art → podcast art fallback).
 * Extracted from [cx.aswin.boxlore.core.data.PlaybackRepository].
 */
object PlaybackArtworkResolver {
    fun resolveEpisodeImageUrl(
        episodeImageUrl: String?,
        episodePodcastImageUrl: String?,
        podcastImageUrl: String?,
    ): String? =
        episodeImageUrl?.takeIf { it.isNotBlank() }
            ?: episodePodcastImageUrl?.takeIf { it.isNotBlank() }
            ?: podcastImageUrl?.takeIf { it.isNotBlank() }

    fun resolveEpisodeImageUrl(
        episode: Episode,
        podcast: Podcast,
    ): String? =
        resolveEpisodeImageUrl(
            episodeImageUrl = episode.imageUrl,
            episodePodcastImageUrl = episode.podcastImageUrl,
            podcastImageUrl = podcast.imageUrl,
        )
}
