package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.model.Episode

/**
 * Persisted provenance distinguishes a chosen list from Smart Queue's mixed continuation. A chosen
 * list is New Episodes ([NEW_EPISODES]) or a playlist (`playlist:<id>` rows, [PLAYLIST] on the media
 * item): Smart Queue leaves it alone until its last episode, then continues as after New Episodes.
 */
internal object PlaybackQueueContext {
    const val NEW_EPISODES = "library_latest_episodes"

    /** The media items' `source_entry_point` while a playlist plays; one value for every playlist. */
    const val PLAYLIST = "playlist"

    /** Queue rows' `contextSourceId` prefix for a playlist, followed by its ID. */
    const val PLAYLIST_SOURCE_PREFIX = "playlist:"

    private val CONTEXT_ENTRY_POINTS = setOf(NEW_EPISODES, PLAYLIST)

    fun episodes(episodes: List<Episode>): List<Episode> = tagged(episodes, NEW_EPISODES)

    /** [episodes] tagged as [playlistId]'s, in order, each once. */
    fun playlistEpisodes(episodes: List<Episode>, playlistId: String): List<Episode> =
        tagged(episodes, PLAYLIST_SOURCE_PREFIX + playlistId)

    /** The `source_entry_point` a queue row with [contextSourceId] plays under, or null when it isn't a chosen list. */
    fun entryPointFor(contextSourceId: String?): String? = when {
        contextSourceId == NEW_EPISODES -> NEW_EPISODES
        contextSourceId?.startsWith(PLAYLIST_SOURCE_PREFIX) == true -> PLAYLIST
        else -> null
    }

    /** A known chosen-list entry point, or null. Cast restores only these. */
    fun knownEntryPoint(value: String?): String? = value?.takeIf { it in CONTEXT_ENTRY_POINTS }

    fun extras(episode: Episode, existing: Bundle?): Bundle? {
        val entryPoint = entryPointFor(episode.contextSourceId) ?: return existing
        return Bundle(existing ?: Bundle()).apply {
            putString("source_entry_point", entryPoint)
        }
    }

    fun isContextItem(item: MediaItem?): Boolean = contextEntryPoint(item) != null

    /** The chosen list [item] belongs to ([NEW_EPISODES] or [PLAYLIST]), or null. */
    fun contextEntryPoint(item: MediaItem?): String? =
        knownEntryPoint(item?.mediaMetadata?.extras?.getString("source_entry_point"))

    private fun tagged(episodes: List<Episode>, sourceId: String): List<Episode> = episodes.distinctBy { it.id }.map {
        it.copy(contextType = "MANUAL", contextSourceId = sourceId)
    }
}
