package cx.aswin.boxlore.wear.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.wear.protolayout.ResourceBuilders
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The episode's artwork for the tile. A tile cannot load pictures itself, so the picture is read
 * here, from the app's own picture store when it has it, and handed over as plain pixels.
 */
internal object TileArtwork {
    /** A small square picture, ready to travel inside the tile. */
    class Pixels(val bytes: ByteArray, val sidePx: Int) {
        fun resource(): ResourceBuilders.ImageResource =
            ResourceBuilders.ImageResource.Builder()
                .setInlineResource(
                    ResourceBuilders.InlineImageResource.Builder()
                        .setData(bytes)
                        .setWidthPx(sidePx)
                        .setHeightPx(sidePx)
                        .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                        .build(),
                ).build()
    }

    private const val SIDE_PX = 144

    /** The tile is asked for quickly; a slow picture is left out rather than holding it up. */
    private const val WAIT_MS = 2_500L

    /** The last picture read, kept so the watch's second question (for the picture itself) is answered at once. */
    @Volatile
    private var held: Pair<String, Pixels>? = null

    /**
     * The picture named [id]: the one held when it is that one, otherwise read from [url]. Null
     * when there is no [url] or it cannot be read in time; the tile then goes without artwork.
     */
    suspend fun of(id: String, url: String?, context: Context): Pixels? {
        held?.takeIf { it.first == id }?.let { return it.second }
        val pixels = url?.let { load(context, it) } ?: return null
        held = id to pixels
        return pixels
    }

    private suspend fun load(context: Context, url: String): Pixels? =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(WAIT_MS) {
                    val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(SIDE_PX).scale(Scale.FILL).build()
                    (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap?.let(::pixels)
                }
            }.getOrNull()
        }

    private fun pixels(source: Bitmap): Pixels {
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val sized = Bitmap.createScaledBitmap(square, SIDE_PX, SIDE_PX, true).copy(Bitmap.Config.RGB_565, false)
        val buffer = ByteBuffer.allocate(sized.byteCount)
        sized.copyPixelsToBuffer(buffer)
        return Pixels(buffer.array(), SIDE_PX)
    }
}
