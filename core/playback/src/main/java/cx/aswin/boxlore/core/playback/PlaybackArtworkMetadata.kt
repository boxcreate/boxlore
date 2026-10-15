package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.core.model.persistableArtworkUrl

/** Additive metadata only; media IDs, audio cache keys and queue provenance remain unchanged. */
internal object PlaybackArtworkMetadata {
    const val SOURCES = "boxlore_artwork_sources"

    fun extras(episode: Episode, podcast: Podcast?, existing: Bundle? = null): Bundle = Bundle(existing ?: Bundle()).apply {
        putStringArrayList(SOURCES, ArrayList(episode.artworkUrls(podcast)))
    }

    /**
     * Keeps [original], the cover's real address, ahead of any sources already in [existing], for
     * items whose artwork the player swaps for an Android Auto / media controls link.
     */
    fun withOriginal(existing: Bundle?, original: String?): Bundle = Bundle(existing ?: Bundle()).apply {
        val sources = (listOfNotNull(persistableArtworkUrl(original)) + getStringArrayList(SOURCES).orEmpty()).distinct()
        if (sources.isNotEmpty()) putStringArrayList(SOURCES, ArrayList(sources))
    }

    /**
     * The artwork address to save for an item (history, queue, restored sessions): its first real
     * source, else its artwork when that is not a player-only link. Never the `.collage` link the
     * player shows Android Auto, which can outlive the cover it points to.
     */
    fun savedArtworkUrl(metadata: MediaMetadata?): String? =
        metadata?.extras?.getStringArrayList(SOURCES)?.firstNotNullOfOrNull(::persistableArtworkUrl)
            ?: persistableArtworkUrl(metadata?.artworkUri?.toString())
}
