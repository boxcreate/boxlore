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

private fun usableArtworkUrl(value: String?): String? {
    val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
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
