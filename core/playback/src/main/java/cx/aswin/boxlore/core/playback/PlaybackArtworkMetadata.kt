package cx.aswin.boxlore.core.playback

import android.os.Bundle
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls

/** Additive metadata only; media IDs, audio cache keys and queue provenance remain unchanged. */
internal object PlaybackArtworkMetadata {
    const val SOURCES = "boxlore_artwork_sources"

    fun extras(episode: Episode, podcast: Podcast?, existing: Bundle? = null): Bundle = Bundle(existing ?: Bundle()).apply {
        putStringArrayList(SOURCES, ArrayList(episode.artworkUrls(podcast)))
    }
}
