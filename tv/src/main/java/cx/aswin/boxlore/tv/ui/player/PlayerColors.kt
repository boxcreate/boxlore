package cx.aswin.boxlore.tv.ui.player

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Covers already read this session, so reopening the player shows its colours from the first frame. */
private val seedCache = LruCache<String, Int>(32)
private const val SAMPLE_PX = 48
private const val COLOR_FADE_MS = 450

/**
 * The player's colours for the cover at [artworkUrl]. Starts in boxlore's iris (or the colours
 * already read for this cover) and fades to the cover's colours once a small copy of it is read.
 */
@Composable
fun rememberPlayerPalette(artworkUrl: String?): PlayerPalette {
    val context = LocalContext.current
    // A new episode fades from the last cover's colours straight to its own.
    var seed by remember { mutableIntStateOf(artworkUrl?.let { seedCache[it] } ?: PlayerColorRules.IRIS) }
    LaunchedEffect(artworkUrl) {
        seed = artworkUrl?.let { seedCache[it] ?: readSeed(context, it) } ?: PlayerColorRules.IRIS
    }
    val shown by animateColorAsState(Color(seed), tween(COLOR_FADE_MS), label = "player seed")
    val argb = shown.toArgb()
    return remember(argb) { PlayerColorRules.palette(argb) }
}

private suspend fun readSeed(context: Context, url: String): Int? = withContext(Dispatchers.Default) {
    val request = ImageRequest.Builder(context).data(url).size(SAMPLE_PX).allowHardware(false).build()
    val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    (PlayerColorRules.seed(pixels) ?: PlayerColorRules.IRIS).also { seedCache.put(url, it) }
}

/**
 * The player's backdrop, as on the phone: a solid gradient in the cover's hue with a soft glow behind
 * the art. It is painted once into a small picture and stretched over the screen, because redrawing
 * two full-screen gradients every frame is more than many TV graphics chips can keep up with.
 */
fun Modifier.playerBackdrop(palette: PlayerPalette): Modifier = drawWithCache {
    val backdrop = ImageBitmap(BACKDROP_WIDTH, BACKDROP_HEIGHT)
    CanvasDrawScope().draw(this, layoutDirection, Canvas(backdrop), Size(BACKDROP_WIDTH.toFloat(), BACKDROP_HEIGHT.toFloat())) {
        drawRect(Brush.verticalGradient(listOf(Color(palette.backgroundTop), Color(palette.backgroundBottom))))
        val centre = Offset(size.width * GLOW_X, size.height * GLOW_Y)
        val radius = size.width * GLOW_RADIUS
        drawCircle(
            Brush.radialGradient(listOf(Color(palette.glow).copy(alpha = GLOW_ALPHA), Color.Transparent), centre, radius),
            radius,
            centre,
        )
    }
    val target = IntSize(size.width.roundToInt(), size.height.roundToInt())
    onDrawBehind { drawImage(backdrop, dstSize = target, filterQuality = FilterQuality.Low) }
}

private const val BACKDROP_WIDTH = 192
private const val BACKDROP_HEIGHT = 108
private const val GLOW_X = 0.26f
private const val GLOW_Y = 0.42f
private const val GLOW_RADIUS = 0.42f
private const val GLOW_ALPHA = 0.55f
