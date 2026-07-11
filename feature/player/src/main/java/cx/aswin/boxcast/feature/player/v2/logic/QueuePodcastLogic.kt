package cx.aswin.boxcast.feature.player.v2.logic

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast

internal fun resolveQueuePodcast(episode: Episode, currentPodcast: Podcast): Podcast {
    val podcastId = episode.podcastId
    if (podcastId == null || podcastId == currentPodcast.id) return currentPodcast
    return Podcast(
        id = podcastId,
        title = episode.podcastTitle ?: "Unknown",
        artist = episode.podcastArtist ?: "",
        imageUrl = episode.podcastImageUrl ?: "",
        description = null,
        genre = episode.podcastGenre ?: ""
    )
}
