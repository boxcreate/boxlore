package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

/** Artwork URL for Podcast Info episode rows (feed extras often omit item art). */
object EpisodeArtworkLogic {
    fun listUrl(
        episode: Episode,
        podcastImageUrl: String? = null,
    ): String? =
        episode.imageUrl?.takeIf { it.isNotBlank() }
            ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() }
            ?: podcastImageUrl?.takeIf { it.isNotBlank() }
}
