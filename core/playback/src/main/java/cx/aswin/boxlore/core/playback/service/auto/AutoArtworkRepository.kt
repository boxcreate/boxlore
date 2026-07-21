package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

internal object AutoArtworkRepository {
    fun remoteUri(
        context: Context,
        remoteUrl: String?,
    ): Uri? {
        val source = remoteUrl?.takeIf { it.isNotBlank() } ?: return null
        val localFile =
            when {
                source.startsWith("file://") -> Uri.parse(source).path?.let(::File)
                File(source).isAbsolute -> File(source)
                else -> null
            }
        if (localFile != null) return localUri(context, localFile)

        val normalized = source.replaceFirst("http://", "https://")
        if (!normalized.startsWith("https://")) return null
        val key = normalized.sha256()
        if (!AutoArtworkSourceStore.put(context, key, normalized)) return null
        return Uri
            .Builder()
            .scheme("content")
            .authority("${context.packageName}.collage")
            .appendPath("art")
            .appendPath(key)
            .build()
    }

    private fun localUri(
        context: Context,
        file: File,
    ): Uri? {
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

    fun collageUri(
        context: Context,
        folderId: String,
        version: String? = null,
    ): Uri? {
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
