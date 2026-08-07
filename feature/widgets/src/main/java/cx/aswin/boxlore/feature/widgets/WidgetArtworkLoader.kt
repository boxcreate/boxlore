package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class WidgetArtworkLoader(
    context: Context,
    private val imageLoader: ImageLoader = ImageLoader(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val cacheDir: File =
        File(appContext.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }

    suspend fun load(url: String?): String? =
        withContext(Dispatchers.IO) {
            val normalized = url?.trim().orEmpty()
            if (normalized.isEmpty()) return@withContext null

            val target = cacheFileFor(normalized)
            if (target.exists() && target.length() > 0L) {
                return@withContext target.absolutePath
            }

            val request =
                ImageRequest
                    .Builder(appContext)
                    .data(normalized)
                    .allowHardware(false)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()

            val result = imageLoader.execute(request)
            if (result !is SuccessResult) return@withContext null

            val bitmap = result.drawable.toBitmapOrNull() ?: return@withContext null
            val temp = File(cacheDir, "${target.nameWithoutExtension}.tmp")
            val wrote =
                runCatching {
                    temp.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                    if (temp.length() <= 0L) {
                        temp.delete()
                        return@runCatching false
                    }
                    if (target.exists() && !target.delete()) {
                        temp.delete()
                        return@runCatching false
                    }
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    true
                }.getOrElse {
                    temp.delete()
                    false
                }

            if (!wrote) return@withContext null
            target.takeIf { it.exists() && it.length() > 0L }?.absolutePath
        }

    fun resolveCachedPath(url: String?): String? {
        val normalized = url?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        val file = cacheFileFor(normalized)
        return file.takeIf { it.exists() && it.length() > 0L }?.absolutePath
    }

    private fun cacheFileFor(url: String): File {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.jpg")
    }

    private fun android.graphics.drawable.Drawable.toBitmapOrNull(): Bitmap? =
        when (this) {
            is android.graphics.drawable.BitmapDrawable -> bitmap
            else -> {
                val width = intrinsicWidth.coerceAtLeast(1)
                val height = intrinsicHeight.coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    setBounds(0, 0, width, height)
                    draw(android.graphics.Canvas(bitmap))
                }
            }
        }

    companion object {
        const val CACHE_DIR_NAME = "widget_artwork"
        private const val JPEG_QUALITY = 92
    }
}
