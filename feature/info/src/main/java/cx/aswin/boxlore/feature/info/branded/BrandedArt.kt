package cx.aswin.boxlore.feature.info.branded

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A page's own artwork, decoded off the main thread and kept for the next visit. Null until every
 * picture is ready, so the page's first frame is never held up by decoding and a header can bring
 * its art in on purpose once it is there.
 */
@Composable
internal fun rememberBrandedArt(@DrawableRes vararg ids: Int): State<List<ImageBitmap>?> {
    val resources = LocalContext.current.resources
    val key = ids.toList()
    val cached = key.map { BrandedArtCache.get(it) }.takeIf { pictures -> pictures.all { it != null } }?.filterNotNull()
    return produceState(initialValue = cached, key) {
        // A new set of pictures starts from the cache or empty, never showing the last set's.
        value = cached
        if (cached == null) value = withContext(Dispatchers.Default) { key.map { BrandedArtCache.decode(resources, it) } }
    }
}

/**
 * A picture needed on the frame it first appears, such as a subscribe moment's. It comes from
 * [BrandedArtCache] when the page has already decoded it off the main thread ([rememberBrandedArt]
 * warms it when the page opens), and is decoded here only when it hasn't.
 */
@Composable
internal fun brandedArtNow(@DrawableRes id: Int): ImageBitmap {
    val resources = LocalContext.current.resources
    return remember(id) { BrandedArtCache.decode(resources, id) }
}

/** Decoded page art, shared by every visit; a few pages' worth, so it stays small. */
internal object BrandedArtCache {
    private val pictures = object : LruCache<Int, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * BYTES_PER_PIXEL
    }

    fun get(@DrawableRes id: Int): ImageBitmap? = pictures.get(id)

    fun decode(resources: Resources, @DrawableRes id: Int): ImageBitmap = pictures.get(id) ?: BitmapFactory.decodeResource(resources, id)
        .asImageBitmap()
        .also {
            it.prepareToDraw()
            pictures.put(id, it)
        }

    private const val BYTES_PER_PIXEL = 4
    private const val MAX_BYTES = 24 * 1024 * 1024
}

/**
 * Waits until the page has finished arriving: at least [minMillis] after it first draws, and then
 * [smoothFrames] frames in a row each within [maxGapMillis] of the last, or [timeoutMillis] at most.
 * A header's motion starts after this, so it never competes with the page loading in.
 */
internal suspend fun awaitPageSettled(
    minMillis: Long = SETTLE_MIN_MILLIS,
    smoothFrames: Int = SETTLE_SMOOTH_FRAMES,
    maxGapMillis: Long = SETTLE_MAX_GAP_MILLIS,
    timeoutMillis: Long = SETTLE_TIMEOUT_MILLIS,
) {
    val start = withFrameMillis { it }
    var last = start
    var smooth = 0
    while (true) {
        val now = withFrameMillis { it }
        smooth = if (now - last <= maxGapMillis) smooth + 1 else 0
        last = now
        if (pageSettled(now - start, smooth, minMillis, smoothFrames, timeoutMillis)) return
    }
}

/** Whether a page [waited] milliseconds in, with [smooth] smooth frames in a row, has settled. */
internal fun pageSettled(
    waited: Long,
    smooth: Int,
    minMillis: Long = SETTLE_MIN_MILLIS,
    smoothFrames: Int = SETTLE_SMOOTH_FRAMES,
    timeoutMillis: Long = SETTLE_TIMEOUT_MILLIS,
): Boolean = waited >= timeoutMillis || (waited >= minMillis && smooth >= smoothFrames)

private const val SETTLE_MIN_MILLIS = 250L
private const val SETTLE_SMOOTH_FRAMES = 6
private const val SETTLE_MAX_GAP_MILLIS = 24L
private const val SETTLE_TIMEOUT_MILLIS = 1_500L
