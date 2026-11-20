package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * What the queue held before a list replaced it, so Undo can put it back: its episodes (the one
 * playing and Up next), which one was playing, where, and whether it was playing.
 */
data class UpNextSnapshot(
    val episodes: List<Episode>,
    val currentIndex: Int,
    val positionMs: Long,
    val wasPlaying: Boolean,
    val podcast: Podcast,
) {
    companion object {
        /** The queue in [state], or null when nothing is queued or playing. */
        fun of(state: PlayerState): UpNextSnapshot? {
            val current = state.currentEpisode
            // The episode playing leads the queue when the queue doesn't hold it, so its place goes with it.
            val queue = if (current != null && state.queue.none { it.id == current.id }) listOf(current) + state.queue else state.queue
            if (queue.isEmpty()) return null
            val index = current?.let { playing -> queue.indexOfFirst { it.id == playing.id } }?.takeIf { it >= 0 } ?: 0
            val start = queue[index]
            val podcast = state.currentPodcast ?: Podcast(
                id = start.podcastId.orEmpty(),
                title = start.podcastTitle.orEmpty(),
                artist = start.podcastArtist.orEmpty(),
                imageUrl = start.podcastImageUrl ?: start.imageUrl.orEmpty(),
            )
            return UpNextSnapshot(queue, index, if (current != null) state.position else 0L, state.isPlaying, podcast)
        }
    }
}
