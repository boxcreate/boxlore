package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

internal object AutoArtworkRepository {
    /**
     * A link the car can load [remoteUrl] from. [fallbackUrl] (usually the show's cover) is served
     * instead when the first address fails, so a broken episode image never leaves an empty tile.
     */
    fun remoteUri(context: Context, remoteUrl: String?, fallbackUrl: String? = null,): Uri? {
        val source = remoteUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl?.takeIf { it.isNotBlank() } ?: return null
        // A cover bundled with the app (The boxlore brief's) or already served by it needs no fetching.
        if (CAR_READY_PREFIXES.any(source::startsWith)) return Uri.parse(source)
        val localFile =
            when {
                source.startsWith("file://") -> Uri.parse(source).path?.let(::File)
                File(source).isAbsolute -> File(source)
                else -> null
            }
        if (localFile != null) return localUri(context, localFile)

        val normalized = source.toHttps() ?: return null
        val fallback = fallbackUrl?.takeIf { it.isNotBlank() }?.toHttps()?.takeIf { it != normalized }
        val sources = listOfNotNull(normalized, fallback).joinToString(SOURCE_SEPARATOR)
        val key = sources.sha256()
        if (!AutoArtworkSourceStore.put(context, key, sources)) return null
        return Uri
            .Builder()
            .scheme("content")
            .authority("${context.packageName}.collage")
            .appendPath("art")
            .appendPath(key)
            .build()
    }

    /** The addresses saved for an artwork key, in the order to try them. */
    fun sourcesOf(saved: String): List<String> = saved.split(SOURCE_SEPARATOR).filter { it.isNotBlank() }

    /** The real addresses behind [link] when it is one of this app's cover links, in the order to try them. */
    fun originalsOf(context: Context, link: Uri?): List<String> {
        if (link?.scheme != "content" || link.authority != "${context.packageName}.collage") return emptyList()
        val segments = link.pathSegments
        if (segments.size != 2 || segments[0] != "art") return emptyList()
        return sourcesOf(AutoArtworkSourceStore.get(context, segments[1]).orEmpty())
    }

    private fun String.toHttps(): String? = replaceFirst("http://", "https://").takeIf { it.startsWith("https://") }

    private const val SOURCE_SEPARATOR = "\n"
    private val CAR_READY_PREFIXES = listOf("android.resource://", "content://")

    private fun localUri(context: Context, file: File,): Uri? {
        val canonicalFile = runCatching(file::getCanonicalFile).getOrNull() ?: return null
        val allowedRoots =
            listOfNotNull(
                context.cacheDir,
                context.filesDir,
                context.getExternalFilesDir(null),
                context.externalCacheDir,
            ).mapNotNull { runCatching(it::getCanonicalFile).getOrNull() }
        if (allowedRoots.none { canonicalFile.isInside(it) } || !canonicalFile.isFile) return null
        val key = canonicalFile.path.sha256()
        if (!AutoArtworkSourceStore.put(context, key, canonicalFile.path)) return null
        return Uri
            .Builder()
            .scheme("content")
            .authority("${context.packageName}.collage")
            .appendPath("local")
            .appendPath(key)
            .build()
    }

    fun collageUri(context: Context, folderId: String, version: String? = null,): Uri? {
        val filename = "${folderId.safeFileName()}.png"
        val collageDir = File(context.cacheDir, "auto_collages")
        val file = File(collageDir, filename)
        if (!file.exists()) return null
        val cacheBuster =
            version?.takeIf { it.isNotBlank() }
                ?: File(collageDir, "${folderId.safeFileName()}.signature")
                    .takeIf { it.isFile }
                    ?.readText()
                    ?.takeIf { it.isNotBlank() }
        val builder =
            Uri
                .Builder()
                .scheme("content")
                .authority("${context.packageName}.collage")
                .appendPath("collage")
                .appendPath(filename)
        if (!cacheBuster.isNullOrBlank()) {
            builder.appendQueryParameter("v", cacheBuster)
        }
        return builder.build()
    }

    private fun String.safeFileName(): String = replace(Regex("[^a-zA-Z0-9_]"), "_")

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun File.isInside(root: File): Boolean = path == root.path || path.startsWith("${root.path}${File.separator}")
}
