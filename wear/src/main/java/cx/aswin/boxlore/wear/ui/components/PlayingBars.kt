package cx.aswin.boxlore.wear.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import androidx.wear.compose.foundation.LocalReduceMotion
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The episode in a player right now, so any list can mark its row. Null when nothing is loaded. */
internal data class PlayingEpisode(val id: String, val isPlaying: Boolean)

internal val LocalPlayingEpisode = compositionLocalOf<PlayingEpisode?> { null }

/** The heights of the three bars that say "this is playing". */
internal object PlayingBarsMath {
    const val BARS = 3
    private const val LOWEST = 0.3f

    /** How the bars stand when nothing moves: uneven, so they still read as sound. */
    val resting: List<Float> = listOf(0.55f, 1f, 0.7f)

    /** Each bar's height from [LOWEST] to 1 at [phase] (0 to 1, then round again); the bars are out of step. */
    fun heights(phase: Float): List<Float> =
        List(BARS) { bar ->
            val wave = sin(2.0 * PI * (phase + bar / BARS.toFloat())).toFloat()
            LOWEST + (1f - LOWEST) * (wave + 1f) / 2f
        }
}

/**
 * Three bars that rise and fall while [moving]. With motion reduced in the watch's settings, or
 * when not moving, they stand still.
 */
@Composable
internal fun PlayingBars(moving: Boolean, color: Color, modifier: Modifier = Modifier) {
    val animate = moving && !LocalReduceMotion.current
    val heights =
        if (animate) {
            val phase by rememberInfiniteTransition(label = "playing bars").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
                label = "phase",
            )
            PlayingBarsMath.heights(phase)
        } else {
            PlayingBarsMath.resting
        }
    Canvas(modifier = modifier) {
        val slot = size.width / (PlayingBarsMath.BARS * 2 - 1)
        heights.forEachIndexed { index, height ->
            val barHeight = size.height * height
            drawRoundRect(
                color = color,
                topLeft = Offset(slot * index * 2, size.height - barHeight),
                size = Size(slot, barHeight),
                cornerRadius = CornerRadius(slot / 2f),
            )
        }
    }
}

/** Artwork with the playing bars over it when [playing] is not null: the mark of the episode in the player. */
@Composable
internal fun ArtworkWithPlaying(url: String?, playing: PlayingEpisode?, modifier: Modifier = Modifier, shape: Shape? = null) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (shape == null) Artwork(url = url, modifier = Modifier.fillMaxSize()) else Artwork(url = url, modifier = Modifier.fillMaxSize(), shape = shape)
        if (playing != null) {
            val clip = shape ?: androidx.compose.foundation.shape.RoundedCornerShape(SCRIM_CORNER)
            Box(modifier = Modifier.fillMaxSize().clip(clip).background(Color.Black.copy(alpha = SCRIM)))
            PlayingBars(moving = playing.isPlaying, color = Color.White, modifier = Modifier.fillMaxSize(BARS_SHARE))
        }
    }
}

private const val SCRIM = 0.55f
private const val BARS_SHARE = 0.46f
private val SCRIM_CORNER = androidx.compose.ui.unit.Dp(9f)

/** One colour picked from the artwork, or null until it loads or when there is none. */
@Composable
internal fun rememberArtworkSeed(url: String?): State<Int?> {
    val context = LocalContext.current
    return produceState<Int?>(initialValue = null, url) {
        value = null
        if (url == null) return@produceState
        value =
            withContext(Dispatchers.Default) {
                val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(SEED_IMAGE_PX).build()
                val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { Palette.from(it).generate() }?.let { it.vibrantSwatch ?: it.dominantSwatch }?.rgb
            }
    }
}

private const val SEED_IMAGE_PX = 96
