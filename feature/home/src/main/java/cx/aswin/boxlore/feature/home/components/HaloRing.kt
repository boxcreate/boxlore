package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.feature.home.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

/**
 * Where a Halo card puts its cover and ring on a [width] × [height] card, in dp. The cookie is three
 * quarters of the card's width, capped so the ring ends by [HaloRingBottom] of the height. With
 * [top] 0 it runs past the top and end edges; otherwise it sits at [top], below a wide context
 * block. [contextWidth] is the room beside the ring for the context block.
 *
 * The ring is the cookie's edge pushed out until its lobe tips reach [ringRadius], so it swings in
 * and out by twice [lobe]. Progress begins at [arcFrom], where the ring meets the top edge, and is
 * drawn from [arcStart], far enough past that edge that its rounded start never shows however the
 * lobes have turned. Full progress runs [arcSweep] (negative: round the start side) to just past the
 * end edge. Angles are degrees clockwise from 3 o'clock.
 */
internal data class HaloGeometry(
    val cover: Float,
    val coverStart: Float,
    val coverTop: Float,
    val centerX: Float,
    val centerY: Float,
    val ringRadius: Float,
    val lobe: Float,
    val contextWidth: Float,
    val arcStart: Float,
    val arcFrom: Float,
    val arcSweep: Float,
)

internal fun haloGeometry(width: Float, height: Float, top: Float = 0f): HaloGeometry {
    val overhang = top <= 0f
    // How far below the cover's top the ring ends, per unit of cover size.
    val ringReach = 0.5f + HaloRingScale
    val coverFromHeight = if (overhang) height * HaloRingBottom / (ringReach - HaloTopOverhang) else (height * HaloRingBottom - top) / ringReach
    val cover = minOf(width * HaloCoverWidth, coverFromHeight).coerceAtLeast(0f)
    val coverTop = if (overhang) -cover * HaloTopOverhang else top
    val coverStart = width - cover * (1f - HaloEndOverhang)
    val centerX = coverStart + cover / 2f
    val centerY = coverTop + cover / 2f
    val radius = cover * HaloRingScale
    val lobe = cover / 2f / (1f + HaloCookieDepth) * HaloCookieDepth
    val mean = radius - lobe
    val inner = radius - lobe * 2f
    // The progress's rounded end reaches half the stroke past its centre; keep it a little further out.
    val beyond = HaloStrokeWidth / 2f + HaloEdgeMargin
    val from = if (centerY < mean) 180f + degrees(asin(centerY / mean)) else 270f
    val start = if (centerY + beyond < inner) 180f + degrees(asin((centerY + beyond) / inner)) else from
    val endRatio = (width + beyond - centerX) / inner
    val end = if (endRatio < 1f) degrees(acos(endRatio.coerceAtLeast(-1f))) else 0f
    val contextWidth = (centerX - radius - HaloInsetDp - HaloContextGap).coerceAtLeast(0f)
    return HaloGeometry(cover, coverStart, coverTop, centerX, centerY, radius, lobe, contextWidth, maxOf(start, from), from, -(from - end).coerceAtLeast(0f))
}

private fun degrees(radians: Float) = Math.toDegrees(radians.toDouble()).toFloat()

/** The nine-lobed cookie's radius at [angle] (radians clockwise from 3 o'clock) for a cookie [size] across, turned by [rotation]. */
internal fun haloCookieRadius(size: Float, angle: Double, rotation: Float): Float {
    val base = size / 2f / (1f + HaloCookieDepth)
    return base * (1f + HaloCookieDepth * cos(HaloLobes * (angle + PI / 2 + rotation)).toFloat())
}

/** A point on the ring, in dp before right-to-left mirroring: the cookie's edge pushed out to the ring. */
internal fun haloRingPoint(geometry: HaloGeometry, degrees: Float, rotation: Float): Offset {
    val angle = Math.toRadians(degrees.toDouble())
    val radius = haloCookieRadius(geometry.cover, angle, rotation) + geometry.ringRadius - geometry.cover / 2f
    return Offset(geometry.centerX + radius * cos(angle).toFloat(), geometry.centerY + radius * sin(angle).toFloat())
}

/** The cookie's outline in a box of [size]; mirrored for right-to-left layouts, like the ring. */
internal fun haloCookiePath(size: Size, rotation: Float, mirror: Boolean): Path = Path().apply {
    val direction = if (mirror) -1f else 1f
    for (step in 0 until HaloOutlineSteps) {
        val angle = 2 * PI * step / HaloOutlineSteps
        val radius = haloCookieRadius(size.minDimension, angle, rotation)
        val x = size.width / 2f + direction * radius * cos(angle).toFloat()
        val y = size.height / 2f + radius * sin(angle).toFloat()
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A still cookie, for collection covers and the loading card. */
internal object HaloCookie : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(haloCookiePath(size, 0f, layoutDirection == LayoutDirection.Rtl))
}

/**
 * Clips to the cookie turned by [rotation], read while drawing so the lobes move without
 * recomposing or relaying out; the image itself stays upright, as on the compact player.
 */
internal fun Modifier.haloCookieClip(rotation: State<Float>): Modifier = drawWithContent {
    clipPath(haloCookiePath(size, rotation.value, layoutDirection == LayoutDirection.Rtl)) {
        this@drawWithContent.drawContent()
    }
}

/** The ring around the cover and, for Resume, its progress; both turn with the cover's [rotation]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HaloRing(geometry: HaloGeometry, progress: HeroProgressState?, rotation: State<Float>, track: Color, color: Color, modifier: Modifier) {
    val description = stringResource(R.string.home_hero_listening_progress)
    val animated by animateFloatAsState(progress?.value ?: 0f, WavyProgressIndicatorDefaults.ProgressAnimationSpec, label = "halo progress")
    val semantics = if (progress != null) Modifier.testTag("home_hero_progress").semantics { contentDescription = description } else Modifier
    Canvas(modifier.then(semantics)) {
        val turn = rotation.value
        drawHaloRing(geometry, track, turn)
        if (progress != null && animated > 0f) drawHaloProgress(geometry, animated, color, turn)
    }
}

/** The ring's whole track, mirrored for right-to-left layouts with the cover. Shared with the loading card. */
internal fun DrawScope.drawHaloRing(geometry: HaloGeometry, track: Color, rotation: Float = 0f) = mirrored {
    drawPath(ringPath(geometry, 0f, 360f, rotation, close = true), track, style = Stroke(HaloStrokeWidth.dp.toPx()))
}

private fun DrawScope.drawHaloProgress(geometry: HaloGeometry, progress: Float, color: Color, rotation: Float) = mirrored {
    val end = geometry.arcFrom + geometry.arcSweep * progress.coerceIn(0f, 1f)
    val stroke = Stroke(HaloStrokeWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(ringPath(geometry, geometry.arcStart, end, rotation, close = false), color, style = stroke)
}

private fun DrawScope.ringPath(geometry: HaloGeometry, from: Float, to: Float, rotation: Float, close: Boolean): Path = Path().apply {
    val steps = (abs(to - from) * 2).toInt().coerceAtLeast(2)
    for (step in 0..steps) {
        val point = haloRingPoint(geometry, from + (to - from) * step / steps, rotation)
        val x = point.x.dp.toPx()
        val y = point.y.dp.toPx()
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
    if (close) close()
}

private inline fun DrawScope.mirrored(crossinline block: DrawScope.() -> Unit) {
    if (layoutDirection == LayoutDirection.Rtl) scale(-1f, 1f) { block() } else block()
}

/** Turns [rotation] on by [elapsedNanos]: one lobe every 2.4 seconds, the compact player's pace. */
internal fun advanceHaloRotation(rotation: Float, elapsedNanos: Long): Float {
    val initial = rotation.takeIf { it.isFinite() } ?: 0f
    val advance = elapsedNanos.coerceAtLeast(0L).toDouble() / 1_000_000_000.0 * HaloLobeTurn / HaloLobeSeconds
    return ((initial.toDouble() + advance) % HaloLobeTurn).toFloat()
}

/**
 * The compact player's clock for a Halo cover: while [running], the cookie and its ring turn one lobe
 * every 2.4 seconds at up to 30fps, paused while Home is not on screen. Read it while drawing.
 */
@Composable
internal fun rememberHaloRotation(key: String, running: Boolean): State<Float> {
    val rotation = remember(key) { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(rotation, running, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (last == 0L) {
                        last = now
                    } else if (now - last >= HaloFrameNanos) {
                        rotation.floatValue = advanceHaloRotation(rotation.floatValue, (now - last).coerceAtMost(100_000_000L))
                        last = now
                    }
                }
            }
        }
    }
    return rotation
}

private const val HaloLobes = 9
private const val HaloCookieDepth = 0.07f
private const val HaloOutlineSteps = 180
internal val HaloLobeTurn = (2 * PI / HaloLobes).toFloat()
private const val HaloLobeSeconds = 2.4
private const val HaloFrameNanos = 33_333_333L
internal const val HaloStrokeWidth = 4f
private const val HaloEdgeMargin = 2f
private const val HaloInsetDp = 20f
private const val HaloCoverWidth = 0.755f
private const val HaloEndOverhang = 0.228f
private const val HaloTopOverhang = 0.079f
private const val HaloRingScale = 0.5526f
private const val HaloRingBottom = 0.55f
private const val HaloContextGap = 8f
