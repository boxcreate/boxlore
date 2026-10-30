package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

private const val UNKNOWN_SHOW = "Unknown Podcast"

/**
 * Every queue entry plays under its own show. An episode can reach the queue without its show (a
 * show-page episode without feed fields, a Smart Queue pick rebuilt from its media item); it then
 * must never take the show of the episode that played before it.
 */
internal object QueueShowPolicy {
    /** Media item extra with the item's show ID, for items the UI only knows from the player (Smart Queue refill). */
    const val SHOW_ID = "boxlore_podcast_id"

    /**
     * [episode] with [podcast]'s ID, title, host, genre and artwork where it carries none of its own.
     * An episode that names another show (another ID, or another title without an ID) is returned as
     * it is, so two shows' fields never mix.
     */
    fun withShow(episode: Episode, podcast: Podcast): Episode {
        val ownId = episode.podcastId.nonBlank()
        val ownTitle = episode.podcastTitle.knownTitle()
        val namesAnotherShow = if (ownId != null) ownId != podcast.id else ownTitle != null && ownTitle != podcast.title
        if (namesAnotherShow) return episode
        return episode.copy(
            podcastId = ownId ?: podcast.id.nonBlank(),
            podcastTitle = ownTitle ?: podcast.title.knownTitle(),
            podcastArtist = episode.podcastArtist.nonBlank() ?: podcast.artist.nonBlank(),
            podcastGenre = episode.podcastGenre.nonBlank() ?: podcast.genre.nonBlank(),
            podcastImageUrl = episode.podcastImageUrl.nonBlank() ?: podcast.imageUrl.nonBlank(),
        )
    }

    /** [existing] with [podcastId] as the item's show ID; unchanged without one. */
    fun extras(podcastId: String?, existing: Bundle? = null): Bundle? {
        val id = podcastId.nonBlank() ?: return existing
        return Bundle(existing ?: Bundle()).apply { putString(SHOW_ID, id) }
    }

    /** The show ID a media item carries, if any. */
    fun showId(metadata: MediaMetadata?): String? = metadata?.extras?.getString(SHOW_ID).nonBlank()

    /**
     * The show title a media item carries: its album title (Android Auto items), else its subtitle
     * (queue and Smart Queue items), else its artist. Android Auto subtitles add the duration and
     * Smart Queue artists are the host, so neither comes first.
     */
    fun showTitle(metadata: MediaMetadata?): String? = metadata?.albumTitle?.toString().knownTitle()
        ?: metadata?.subtitle?.toString().knownTitle()
        ?: metadata?.artist?.toString().knownTitle()

    /**
     * The show for [episode] when it becomes the current item. [stored] is the show saved under the
     * episode's show ID; [queueShow] is the show the queue was started from. An episode with no show
     * of its own plays under [queueShow] (a list played from one show's page), else under [previous].
     * [previous] and [queueShow] are kept for an episode with a show only when they have its ID, or
     * its title when it has no ID. Otherwise the show is built from the episode's own fields, never
     * from another show's.
     */
    fun currentShow(episode: Episode, previous: Podcast?, stored: Podcast?, queueShow: Podcast? = null): Podcast? {
        val showId = episode.podcastId.nonBlank()
        val ownTitle = episode.podcastTitle.knownTitle()
        if (showId == null) {
            if (ownTitle == null) return queueShow ?: previous
            return listOfNotNull(queueShow, previous).firstOrNull { it.title == ownTitle } ?: showFrom(episode, "", ownTitle)
        }
        return listOfNotNull(stored, previous, queueShow).firstOrNull { it.id == showId && it.title.knownTitle() != null }
            ?: showFrom(episode, showId, ownTitle ?: UNKNOWN_SHOW)
    }

    private fun showFrom(episode: Episode, id: String, title: String) = Podcast(
        id = id,
        title = title,
        artist = episode.podcastArtist.orEmpty(),
        imageUrl = episode.podcastImageUrl.orEmpty(),
        description = null,
        genre = episode.podcastGenre.nonBlank() ?: "Podcast",
    )

    private fun String?.nonBlank(): String? = this?.takeIf(String::isNotBlank)

    private fun String?.knownTitle(): String? = nonBlank()?.takeIf { it != UNKNOWN_SHOW }
}
