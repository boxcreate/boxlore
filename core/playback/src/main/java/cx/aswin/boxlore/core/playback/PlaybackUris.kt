package cx.aswin.boxlore.core.playback

import android.net.Uri

/**
 * Null-safe URI parsing for playback metadata. Catalog fields declared non-null can still arrive
 * null from Gson or blank from feeds, and [Uri.parse] throws on null.
 */
internal object PlaybackUris {
    fun parseOrNull(value: String?): Uri? = value?.trim()?.takeIf { it.isNotEmpty() }?.let(Uri::parse)
}
