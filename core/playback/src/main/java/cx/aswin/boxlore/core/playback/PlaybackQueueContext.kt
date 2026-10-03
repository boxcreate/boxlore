package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.model.Episode

/** Persisted provenance distinguishes a chosen list from Smart Queue's mixed continuation. */
internal object PlaybackQueueContext {
    const val NEW_EPISODES = "library_latest_episodes"

    fun episodes(episodes: List<Episode>): List<Episode> = episodes.distinctBy { it.id }.map {
        it.copy(contextType = "MANUAL", contextSourceId = NEW_EPISODES)
    }

    fun extras(episode: Episode, existing: Bundle?): Bundle? {
        if (episode.contextSourceId != NEW_EPISODES) return existing
        return Bundle(existing ?: Bundle()).apply {
            putString("source_entry_point", NEW_EPISODES)
        }
    }

    fun isContextItem(item: MediaItem?): Boolean =
        item?.mediaMetadata?.extras?.getString("source_entry_point") == NEW_EPISODES
}
