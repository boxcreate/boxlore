package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/** [unknownTitle] names a show whose title the queued episode doesn't carry. */
internal fun resolveQueuePodcast(episode: Episode, currentPodcast: Podcast, unknownTitle: String): Podcast {
    val podcastId = episode.podcastId
    if (podcastId == null || podcastId == currentPodcast.id) return currentPodcast
    return Podcast(
        id = podcastId,
        title = episode.podcastTitle ?: unknownTitle,
        artist = episode.podcastArtist ?: "",
        imageUrl = episode.podcastImageUrl ?: "",
        description = null,
        genre = episode.podcastGenre ?: ""
    )
}
