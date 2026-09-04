package cx.aswin.boxlore.core.downloads

import java.io.File

/** Picks a still-loadable artwork URL after backup restore or a failed local copy. */
internal object DownloadArtworkUrls {
    fun resolve(stored: String?, fallback: String? = null, fileExists: (String) -> Boolean = { path -> File(path).isFile },): String? = usable(stored, fileExists) ?: usable(fallback, fileExists)

    fun remoteUrl(stored: String?): String? {
        val cleaned = stored?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        val withScheme = if (cleaned.startsWith("//")) "https:$cleaned" else cleaned
        return withScheme.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun usable(stored: String?, fileExists: (String) -> Boolean,): String? {
        val cleaned = stored?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        val path = cleaned.removePrefix("file://")
        return if (path.startsWith("/")) {
            cleaned.takeIf { fileExists(path) }
        } else {
            cleaned
        }
    }
}
