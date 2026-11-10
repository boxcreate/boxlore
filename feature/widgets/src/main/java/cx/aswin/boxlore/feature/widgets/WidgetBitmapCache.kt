package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansTypefaces
import cx.aswin.boxlore.feature.widgets.logic.WidgetProgress
import java.io.File

/**
 * Keeps the pictures a widget is drawn from, so a redraw after a tap or a progress step reuses them
 * instead of decoding the cover and drawing every shape again. That is what makes a widget answer a
 * tap at once. Entries are keyed by everything that changes how they look, and the oldest are dropped
 * once the cache is full.
 */
internal object WidgetBitmapCache {
    private val bitmaps =
        object : LruCache<String, Bitmap>(MAX_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }

    /** The cover at [path] cut to [shape], or null when the file cannot be read. */
    fun artwork(path: String, sizePx: Int, shape: WidgetArtShape, insetFraction: Float = 0f): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        // A cover that is replaced on disk keeps its path, so its change time is part of the key.
        val key = "art|$path|${file.lastModified()}|$sizePx|$shape|$insetFraction"
        bitmaps.get(key)?.let { return it }
        val source = WidgetShapes.decodeSampled(path, sizePx) ?: return null
        return WidgetShapes.shapeArtwork(source, sizePx, shape, insetFraction).also {
            bitmaps.put(key, it)
            source.recycle()
        }
    }

    fun mask(shape: WidgetArtShape, sizePx: Int, insetFraction: Float = 0f): Bitmap = cached("mask|$sizePx|$shape|$insetFraction") { WidgetShapes.mask(shape, sizePx, insetFraction) }

    /** The ring at one of its [WidgetProgress.RING_STEPS] steps. Either arc is null when it has nothing to draw. */
    fun progressRing(sizePx: Int, step: Int, strokeFraction: Float): WidgetShapes.ProgressRing {
        val key = "ring|$sizePx|$step|$strokeFraction"
        val played = bitmaps.get("$key|played")
        val remaining = bitmaps.get("$key|remaining")
        // A step with only one arc is cheap to draw again, so only complete pairs are served from here.
        if (played != null && remaining != null) return WidgetShapes.ProgressRing(played, remaining)
        return WidgetShapes.progressRing(sizePx, step.toFloat() / WidgetProgress.RING_STEPS, strokeFraction).also { ring ->
            ring.played?.let { bitmaps.put("$key|played", it) }
            ring.remaining?.let { bitmaps.put("$key|remaining", it) }
        }
    }

    fun seekIcon(context: Context, seconds: Int, forward: Boolean): Bitmap {
        // The number is drawn in the listener's lettering, so a change of lettering draws it again.
        val roundness = GoogleSansTypefaces.cachedRoundness(context)
        return cached("seek|$seconds|$forward|$roundness") { WidgetSeekIcon.mask(context, seconds, forward, SEEK_ICON_PX) }
    }

    private inline fun cached(key: String, draw: () -> Bitmap): Bitmap = bitmaps.get(key) ?: draw().also { bitmaps.put(key, it) }

    private const val MAX_BYTES = 12 * 1024 * 1024

    /** Sharp enough for the largest seek button; the layout scales it down. */
    private const val SEEK_ICON_PX = 120
}
