package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

private const val UNKNOWN_SHOW = "Unknown Podcast"
private const val UNKNOWN_SHOW_ID = "unknown"

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
     * it is, so two shows' fields never mix, and so is every episode when [podcast] has no real ID
     * (an episode page that doesn't know its show passes the "unknown" placeholder).
     */
    fun withShow(episode: Episode, podcast: Podcast): Episode {
        val podcastId = podcast.id.knownId() ?: return episode
        if (namesAnotherShow(episode, podcast)) return episode
        return episode.copy(
            podcastId = episode.podcastId.knownId() ?: podcastId,
            podcastTitle = episode.podcastTitle.knownTitle() ?: podcast.title.knownTitle(),
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
    fun showId(metadata: MediaMetadata?): String? = metadata?.extras?.getString(SHOW_ID).knownId()

    /** The show ID of a queue entry: its own, else the one its media item carries. */
    fun entryShowId(episode: Episode, metadata: MediaMetadata?): String? = episode.podcastId.knownId() ?: showId(metadata)

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
        val showId = episode.podcastId.knownId()
        val ownTitle = episode.podcastTitle.knownTitle()
        if (showId == null) {
            if (ownTitle == null) return queueShow ?: previous
            return listOfNotNull(queueShow, previous).firstOrNull { it.title == ownTitle } ?: showFrom(episode, "", ownTitle)
        }
        return listOfNotNull(stored, previous, queueShow).firstOrNull { it.id == showId && it.title.knownTitle() != null }
            ?: showFrom(episode, showId, ownTitle ?: UNKNOWN_SHOW)
    }

    /**
     * The show a queue starts with for its first [episode]: [podcast], unless the episode names
     * another show (a mixed list started under a placeholder such as All Downloads), which it then
     * plays under, built from its own fields.
     */
    fun startShow(episode: Episode, podcast: Podcast): Podcast =
        if (namesAnotherShow(episode, podcast)) currentShow(episode, previous = null, stored = null) ?: podcast else podcast

    /** True when [episode] carries a show other than [podcast]: another ID, or another title without an ID. */
    private fun namesAnotherShow(episode: Episode, podcast: Podcast): Boolean {
        val ownId = episode.podcastId.knownId()
        if (ownId != null) return ownId != podcast.id
        val ownTitle = episode.podcastTitle.knownTitle()
        return ownTitle != null && ownTitle != podcast.title
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

    /** A show ID, never blank or the "unknown" placeholder older code stored for an episode with no show. */
    private fun String?.knownId(): String? = nonBlank()?.takeIf { it != UNKNOWN_SHOW_ID }

    private fun String?.knownTitle(): String? = nonBlank()?.takeIf { it != UNKNOWN_SHOW }
}
