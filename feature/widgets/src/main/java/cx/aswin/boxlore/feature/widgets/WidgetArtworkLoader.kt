package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetArtworkLoader(
    context: Context,
    private val imageLoader: ImageLoader = coil.Coil.imageLoader(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val cacheDir: File =
        File(appContext.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }

    suspend fun load(url: String?, fallbackUrls: List<String?> = emptyList()): String? = withContext(Dispatchers.IO) {
        val primary = url?.trim().orEmpty()
        // Keep the existing filename identity even when transport upgrades HTTP to HTTPS.
        val normalized = primary.takeIf { cx.aswin.boxlore.core.model.artworkUrls(it).isNotEmpty() }
            ?: cx.aswin.boxlore.core.model.artworkUrls(*fallbackUrls.toTypedArray()).firstOrNull().orEmpty()
        if (normalized.isEmpty()) return@withContext null

        val target = cacheFileFor(normalized)
        if (target.exists() && target.length() > 0L) {
            return@withContext target.absolutePath
        }

        val bitmap = cx.aswin.boxlore.core.designsystem.components.loadArtworkBitmap(
            appContext,
            listOfNotNull(url) + fallbackUrls.filterNotNull(),
            width = 512,
            loader = imageLoader,
        ) ?: return@withContext null
        if (!publishAtomically(bitmap, target)) return@withContext null
        target.takeIf { it.exists() && it.length() > 0L }?.absolutePath
    }

    fun resolveCachedPath(url: String?): String? {
        val normalized = url?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        val file = cacheFileFor(normalized)
        return file.takeIf { it.exists() && it.length() > 0L }?.absolutePath
    }

    private fun publishAtomically(
        bitmap: Bitmap,
        target: File,
    ): Boolean {
        val temp = File(cacheDir, "${target.nameWithoutExtension}.tmp")
        return runCatching {
            writeJpeg(temp, bitmap) && replaceTarget(temp, target)
        }.getOrElse {
            deleteQuietly(temp)
            false
        }
    }

    private fun writeJpeg(
        temp: File,
        bitmap: Bitmap,
    ): Boolean {
        temp.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (temp.length() > 0L) return true
        deleteQuietly(temp)
        return false
    }

    private fun replaceTarget(
        temp: File,
        target: File,
    ): Boolean {
        if (target.exists()) {
            deleteQuietly(target)
            if (target.exists()) {
                deleteQuietly(temp)
                return false
            }
        }
        if (temp.renameTo(target)) return true
        temp.copyTo(target, overwrite = true)
        deleteQuietly(temp)
        return target.exists() && target.length() > 0L
    }

    private fun deleteQuietly(file: File) {
        if (!file.exists()) return
        // Best-effort cleanup; ignore races where another writer already removed the file.
        if (!file.delete() && file.exists()) return
    }

    private fun cacheFileFor(url: String): File {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.jpg")
    }

    companion object {
        const val CACHE_DIR_NAME = "widget_artwork"
        private const val JPEG_QUALITY = 92
    }
}
