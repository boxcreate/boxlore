package cx.aswin.boxlore.core.model

import java.net.URI

/** Ordered, distinct sources. Preserve signed query parameters and local Coil sources. */
fun artworkUrls(vararg urls: String?): List<String> = urls.mapNotNull(::usableArtworkUrl).distinct()

fun Episode.artworkUrls(podcast: Podcast? = null): List<String> = artworkUrls(
    imageUrl,
    podcastImageUrl,
    podcast?.imageUrl,
    podcast?.fallbackImageUrl,
)

/** A show-focused surface always starts with show artwork. */
fun Podcast.artworkUrls(): List<String> = artworkUrls(imageUrl, fallbackImageUrl, latestEpisode?.imageUrl)

private const val PLAYBACK_ARTWORK_AUTHORITY_SUFFIX = ".collage"

/**
 * True for the links the player hands Android Auto and the media controls
 * (`content://<app>.collage/...`). They point into an in-app registry of covers that is cleared
 * with downloads and app data, so they are never saved, synced or shown as a card's image.
 */
fun isPlaybackArtworkLink(value: String?): Boolean {
    val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return false
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
    return uri.scheme.equals("content", ignoreCase = true) && uri.authority?.endsWith(PLAYBACK_ARTWORK_AUTHORITY_SUFFIX, ignoreCase = true) == true
}

/** [value] when it can be saved as artwork: not blank and not a player-only link ([isPlaybackArtworkLink]). */
fun persistableArtworkUrl(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && !isPlaybackArtworkLink(it) }

private fun usableArtworkUrl(value: String?): String? {
    val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (isPlaybackArtworkLink(trimmed)) return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "http" -> trimmed.takeIf { !uri.host.isNullOrBlank() }?.replaceFirst("http://", "https://", ignoreCase = true)
        "https" -> trimmed.takeIf { !uri.host.isNullOrBlank() }
        "content", "android.resource" -> trimmed.takeIf { !uri.authority.isNullOrBlank() }
        "file" -> trimmed.takeIf { !uri.path.isNullOrBlank() }
        null -> trimmed.takeIf { it.startsWith('/') }
        else -> null
    }
}
