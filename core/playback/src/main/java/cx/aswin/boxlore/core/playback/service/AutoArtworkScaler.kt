package cx.aswin.boxlore.core.playback.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import cx.aswin.boxlore.core.playback.AutoArtworkFetchLogic
import java.io.ByteArrayOutputStream

/**
 * Shrinks downloaded covers to the size a car screen shows before they are cached and piped to
 * Android Auto. Podcast feeds commonly publish 3000 px artwork of several megabytes.
 */
internal object AutoArtworkScaler {
    private const val DEFAULT_PX = 512
    private const val MIN_PX = 256
    private const val MAX_PX = 1024
    private const val JPEG_QUALITY = 88

    /** Longest side, in pixels, that cached artwork is reduced to. */
    @Volatile
    var targetPx: Int = DEFAULT_PX
        private set

    /** Takes the artwork size the car asked for, kept within sensible bounds. */
    fun noteHostHint(pixels: Int) {
        if (pixels > 0) targetPx = pixels.coerceIn(MIN_PX, MAX_PX)
    }

    /** The power-of-two decode step that keeps the longest side at or above [target]. */
    fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** [bytes] reduced for the car, or [bytes] unchanged when they are already small or can't be decoded. */
    fun scaleForCar(bytes: ByteArray): ByteArray = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val target = targetPx
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || maxOf(bounds.outWidth, bounds.outHeight) <= target) return bytes
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, target) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes
        val longest = maxOf(decoded.width, decoded.height)
        val scaled = if (longest > target) {
            val ratio = target.toFloat() / longest
            Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1), (decoded.height * ratio).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        val format = if (scaled.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val written = scaled.compress(format, JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        val result = out.toByteArray()
        // Keep the original unless the result is a real, smaller image.
        if (written && result.size < bytes.size && AutoArtworkFetchLogic.looksLikeImage(result.copyOf(minOf(12, result.size)))) result else bytes
    }.getOrDefault(bytes)
}
