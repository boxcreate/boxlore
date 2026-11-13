package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.model.Episode

/** Queue entries rebuilt from the player while reconciling the saved queue with it. */
internal object ReconciledQueueEpisode {
    /**
     * A queue entry for a player [item] the UI holds no entry for, or null without a title. Its show
     * comes from the item: the show ID extra and [QueueShowPolicy.showTitle].
     */
    fun fromMediaItem(
        item: MediaItem,
        episodeId: String,
    ): Episode? {
        val metadata = item.mediaMetadata
        val title = CastMediaMetadata.queueTitle(metadata.title) ?: return null
        return Episode(
            id = episodeId,
            title = title,
            description = "",
            audioUrl = item.localConfiguration?.uri?.toString() ?: "",
            imageUrl = PlaybackArtworkMetadata.savedArtworkUrl(metadata),
            podcastImageUrl = PlaybackArtworkMetadata.savedArtworkUrl(metadata),
            podcastTitle = QueueShowPolicy.showTitle(metadata),
            podcastId = QueueShowPolicy.showId(metadata),
            podcastArtist = metadata.artist?.toString(),
            podcastGenre = metadata.genre?.toString(),
            duration = 0,
            publishedDate = 0L,
            // Items we didn't add locally were appended by the service refill path.
            contextType = "AUTO_FILL",
        )
    }
}
