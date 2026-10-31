package cx.aswin.boxlore.feature.info.branded.waveform

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.LocalBrandedHeaderMotion

/**
 * The logo across the top of the page. It rests as traced; while one of the show's episodes plays,
 * its bars breathe; and when you subscribe it replays its intro on the Subscribe button's clock.
 */
@Composable
internal fun WaveformLogo(modifier: Modifier = Modifier) {
    val motion = LocalBrandedHeaderMotion.current
    val playing = motion?.showPlaying == true && remember { ValueAnimator.areAnimatorsEnabled() }
    val live = animateFloatAsState(if (playing) 1f else 0f, tween(LIVE_FADE_MILLIS), label = "waveform_logo_live")
    val clock = rememberLiveClock(playing, live)
    val intro = motion?.subscribeMomentMillis
    Spacer(
        modifier.fillMaxWidth().height(WaveformLogoShape.HEIGHT.dp).drawWithCache {
            val unit = 1.dp.toPx()
            val left = (size.width - WaveformLogoShape.WIDTH * unit) / 2f
            val shapes = LogoPaths(WaveformLogoShape.barsFor(size.width / unit), unit, left)
            onDrawBehind {
                val millis = intro?.invoke()
                if (millis != null) drawIntro(shapes, millis) else drawLive(shapes, clock.value, live.value)
            }
        },
    )
}

/** The bars' clock while an episode plays, and for the fade back to rest after it stops. */
@Composable
private fun rememberLiveClock(playing: Boolean, live: State<Float>): State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        while (playing || live.value > 0f) {
            withFrameMillis { clock.longValue = it }
        }
    }
    return clock
}

private const val LIVE_FADE_MILLIS = 400

/** The logo's shapes for one size, in pixels, centred in the width. */
private class LogoPaths(val bars: List<LogoBar>, val unit: Float, val left: Float) {
    val block = polygon(WaveformLogoShape.Block)
    val overClip = polygon(WaveformLogoShape.OverClip)
    val underClip = polygon(WaveformLogoShape.UnderClip)
    val blockOrigin = Offset(left + WaveformLogoShape.BlockOrigin.x * unit, WaveformLogoShape.BlockOrigin.y * unit)

    private fun polygon(points: List<Offset>) = Path().apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(left + p.x * unit, p.y * unit) else lineTo(left + p.x * unit, p.y * unit) }
        close()
    }
}

private fun DrawScope.drawLive(shapes: LogoPaths, clock: Long, live: Float) {
    if (live <= 0f) {
        drawLogo(shapes, barColor = WaveformColors.Text, blockColor = WaveformColors.Red)
        return
    }
    drawLogo(shapes, barColor = WaveformColors.Text, blockColor = WaveformColors.Red, scaleOf = { bar ->
        1f + (WaveformMotion.liveScale(clock, bar.phase) - 1f) * live
    })
}

private fun DrawScope.drawIntro(shapes: LogoPaths, millis: Float) {
    val frame = WaveformMotion.glitchFrame(millis)
    if (frame >= 0) {
        drawGlitch(shapes, GlitchRed[frame], WaveformColors.GlitchRed)
        drawGlitch(shapes, GlitchCyan[frame], WaveformColors.GlitchCyan)
    }
    val (jitterX, jitterY) = WaveformMotion.introJitter(millis)
    translate(jitterX * shapes.unit, jitterY * shapes.unit) {
        scale(scaleX = 1f, scaleY = WaveformMotion.introBeat(millis), pivot = Offset(size.width / 2f, WaveformLogoShape.HEIGHT / 2f * shapes.unit)) {
            drawLogo(
                shapes,
                barColor = WaveformColors.Text,
                blockColor = WaveformColors.Red,
                scaleOf = { bar -> WaveformMotion.introBarScale(millis, bar.introDelay) },
                blockScale = WaveformMotion.introBlockScale(millis),
                flash = WaveformMotion.introFlash(millis),
            )
        }
    }
    if (frame >= 0) {
        val scan = ScanLines[frame]
        drawRect(Color.White.copy(alpha = scan.second), topLeft = Offset(0f, scan.first * shapes.unit), size = Size(size.width, 2 * shapes.unit))
    }
}

/** One glitch copy of the logo: a horizontal band of it, shifted, in one colour. */
private fun DrawScope.drawGlitch(shapes: LogoPaths, frame: GlitchFrame, color: Color) {
    val height = WaveformLogoShape.HEIGHT * shapes.unit
    clipRect(top = frame.top * height, bottom = height * (1f - frame.bottom)) {
        translate(frame.dx * shapes.unit, frame.dy * shapes.unit) {
            drawLogo(shapes, barColor = color, blockColor = color)
        }
    }
}

private fun DrawScope.drawLogo(
    shapes: LogoPaths,
    barColor: Color,
    blockColor: Color,
    scaleOf: (LogoBar) -> Float = { 1f },
    blockScale: Float = 1f,
    flash: Float = 0f,
) {
    fun drawBar(bar: LogoBar) {
        val scale = scaleOf(bar)
        val height = bar.height * scale * shapes.unit
        val centre = (bar.y + bar.height / 2f) * shapes.unit
        drawRect(
            barColor,
            topLeft = Offset(shapes.left + bar.x * shapes.unit, centre - height / 2f),
            size = Size(WaveformLogoShape.BAR_WIDTH * shapes.unit, height),
        )
    }
    shapes.bars.filter { it.group == LogoGroup.Plain }.forEach(::drawBar)
    clipPath(shapes.underClip) { shapes.bars.filter { it.group == LogoGroup.UnderBlock }.forEach(::drawBar) }
    if (blockScale > 0f) {
        scale(scaleX = blockScale, scaleY = 1f, pivot = shapes.blockOrigin) {
            drawPath(shapes.block, blockColor)
            if (flash > 0f) drawPath(shapes.block, Color.White, alpha = flash)
        }
    }
    clipPath(shapes.overClip) { shapes.bars.filter { it.group == LogoGroup.OverBlock }.forEach(::drawBar) }
}

/** A glitch frame: its shift in dp and the band it shows, as fractions cut from the top and bottom. */
private data class GlitchFrame(val dx: Float, val dy: Float, val top: Float, val bottom: Float)

private val GlitchRed = listOf(
    GlitchFrame(-6f, 0f, 0f, 0.64f),
    GlitchFrame(5f, -1f, 0.38f, 0.34f),
    GlitchFrame(-4f, 1f, 0.7f, 0f),
    GlitchFrame(6f, 0f, 0.12f, 0.5f),
)

private val GlitchCyan = listOf(
    GlitchFrame(6f, 0f, 0.3f, 0.4f),
    GlitchFrame(-5f, 1f, 0f, 0.72f),
    GlitchFrame(4f, 0f, 0.52f, 0.14f),
    GlitchFrame(-6f, -1f, 0.76f, 0f),
)

/** The scanline's place in the logo box (dp) and its opacity, one per glitch frame. */
private val ScanLines = listOf(28f to 0.5f, 74f to 0.35f, 46f to 0.5f, 92f to 0.3f)
