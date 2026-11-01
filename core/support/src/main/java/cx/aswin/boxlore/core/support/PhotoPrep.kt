package cx.aswin.boxlore.core.support

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Shrinks a picked photo or screenshot before upload: the long edge to at most [LONG_EDGE] px,
 * re-encoded as JPEG, so a screenshot is a few hundred KB instead of several MB.
 */
object PhotoPrep {
    const val LONG_EDGE = 1600
    private const val JPEG_QUALITY = 82

    /** Target size for an image of [width] x [height] (never upscaled). */
    fun targetSize(width: Int, height: Int, longEdge: Int = LONG_EDGE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val longest = maxOf(width, height)
        if (longest <= longEdge) return width to height
        val scale = longEdge.toDouble() / longest
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /** Decodes [uri] (from the photo picker), scales it down and returns JPEG bytes, or null. */
    fun prepare(contentResolver: android.content.ContentResolver, uri: Uri): ByteArray? = runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (w, h) = targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }.getOrNull()
}
