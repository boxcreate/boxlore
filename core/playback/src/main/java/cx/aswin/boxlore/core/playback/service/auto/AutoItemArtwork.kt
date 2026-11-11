package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.core.playback.PlaybackArtworkMetadata

/**
 * Makes a player item's cover work on both screens.
 *
 * The car draws a queue entry's picture from the item's artwork link and can only open links into
 * this app, so an item carrying a web address is a blank square there. The phone's media controls
 * are the other way round: they can't use the in-app link and read the cover's real address from
 * the sources saved on the item.
 */
internal object AutoItemArtwork {
    private val CAR_SCHEMES = setOf("content", "android.resource")

    /**
     * [item] with a cover link the car can open, keeping its real address in the saved sources. The
     * same instance comes back when the item already has such a link, or has no cover to offer.
     */
    fun forCar(context: Context, item: MediaItem): MediaItem {
        val metadata = item.mediaMetadata
        val art = metadata.artworkUri
        if (art != null && art.scheme in CAR_SCHEMES) return item
        val saved = metadata.extras?.getStringArrayList(PlaybackArtworkMetadata.SOURCES).orEmpty()
        val web = artworkUrls(art?.toString(), *saved.toTypedArray()).filter { it.startsWith("https://") }
        val carLink = AutoArtworkRepository.remoteUri(context, web.firstOrNull(), web.getOrNull(1)) ?: return item
        // Sources already saved keep their order: they say which address is the episode's own cover.
        val extras = PlaybackArtworkMetadata.withOriginal(metadata.extras, art?.toString(), episodeCover = saved.isEmpty())
        return item.buildUpon().setMediaMetadata(metadata.buildUpon().setArtworkUri(carLink).setExtras(extras).build()).build()
    }

    /**
     * [item] with the real addresses behind its in-app cover link saved on it. Without them the
     * phone's media controls have no picture for it and the episode is saved with no image.
     */
    fun withSources(context: Context, item: MediaItem): MediaItem {
        val metadata = item.mediaMetadata
        if (!metadata.extras?.getStringArrayList(PlaybackArtworkMetadata.SOURCES).isNullOrEmpty()) return item
        val originals = AutoArtworkRepository.originalsOf(context, metadata.artworkUri)
        if (originals.isEmpty()) return item
        val extras = originals.fold(metadata.extras) { saved, original ->
            PlaybackArtworkMetadata.withOriginal(saved, original, episodeCover = false)
        }
        return item.buildUpon().setMediaMetadata(metadata.buildUpon().setExtras(extras).build()).build()
    }
}
