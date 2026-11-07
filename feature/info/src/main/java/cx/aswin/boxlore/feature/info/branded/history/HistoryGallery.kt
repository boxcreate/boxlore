package cx.aswin.boxlore.feature.info.branded.history

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.awaitPageSettled
import cx.aswin.boxlore.feature.info.branded.rememberBrandedArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The gallery wall in the dark, then its lights coming on, one by one. The lights wait until the
 * page has settled and the frames' pictures have arrived (or a moment has passed), so nothing loads
 * in while they come up: the room warms, the centre frame's spotlight flickers on, the ovals' come
 * on together, then the small frames', the busts are lit from below, and a gilt glint runs across
 * the logo. The first time the page opens; after that, and with animations off, it is simply lit.
 */
@Composable
internal fun GalleryWall(pictures: List<String?>, cover: String?) {
    val art by rememberBrandedArt(R.drawable.trih_velvet, R.drawable.trih_david, R.drawable.trih_churchill, R.drawable.trih_logo)
    val shown = pictures.size.coerceAtMost(HistoryWall.Frames.size)
    // A frame with nothing to hang (no picture and no cover) counts as loaded, so the lights never wait for it.
    val loaded = remember(pictures, cover) { List(shown) { (pictures[it] ?: cover) == null }.toMutableStateList() }
    val clock = rememberLightsClock(ready = art != null, loaded)
    val dark = LocalEffectiveDarkTheme.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val title = stringResource(R.string.trih_logo_description)
    BoxWithConstraints(Modifier.fillMaxWidth().height((HistoryWall.HEIGHT - HistoryWall.STATUS_BAR).dp)) {
        val centre = (maxWidth.value - HistoryWall.WIDTH) / 2f
        Spacer(
            Modifier.matchParentSize().graphicsLayer().drawBehind {
                drawWall(art?.get(0), statusBar, clock.value, dark)
            },
        )
        HistoryWall.Frames.forEachIndexed { i, frame ->
            // An episode without its own picture hangs the show's cover.
            val picture = (if (i < shown) pictures[i] ?: cover else null) ?: return@forEachIndexed
            BronzeFrame(
                picture = picture,
                fallback = cover,
                style = frame.style,
                modifier = Modifier
                    .offset { IntOffset(((centre + frame.x) * density).toInt(), ((frame.y - HistoryWall.STATUS_BAR) * density).toInt()) }
                    .size(frame.width.dp, frame.height.dp)
                    .graphicsLayer(),
                dim = { 1f - HistoryWall.frameBrightness(i, clock.value) },
                onLoaded = { if (i < loaded.size) loaded[i] = true },
            )
        }
        Spacer(
            Modifier
                .matchParentSize()
                .semantics {
                    heading()
                    contentDescription = title
                }
                .graphicsLayer()
                .drawBehind { art?.let { drawFigures(it[1], it[2], it[3], clock.value) } },
        )
        PublisherLine(Modifier.align(Alignment.BottomCenter).padding(bottom = (HistoryWall.HEIGHT - HistoryWall.PUBLISHER_BOTTOM).dp))
    }
}

/**
 * Milliseconds into the lights' sequence: held at 0 in the dark until the art is decoded, the page
 * has settled and the frames' pictures are in (or [HistoryWall.PICTURE_WAIT_MILLIS] has passed),
 * then run once. At rest after the first time and with animations off.
 */
@Composable
private fun rememberLightsClock(ready: Boolean, loaded: SnapshotStateList<Boolean>): State<Float> {
    var played by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !played && ValueAnimator.areAnimatorsEnabled() }
    val clock = remember { Animatable(if (animate) 0f else HistoryWall.LIT) }
    // The pictures can change before the lights start; the wait follows whichever are hung now.
    val current by rememberUpdatedState(loaded)
    LaunchedEffect(ready, animate) {
        if (!ready || !animate) return@LaunchedEffect
        awaitPageSettled()
        withTimeoutOrNull(HistoryWall.PICTURE_WAIT_MILLIS) { snapshotFlow { current.all { it } }.first { it } }
        played = true
        delay(HistoryWall.DARK_BEAT_MILLIS)
        clock.animateTo(HistoryWall.LIT, tween(HistoryWall.LIT.toInt(), easing = LinearEasing))
    }
    return clock.asState()
}

/** The velvet, drawn up behind the status bar, with the room's light and each frame's spotlight on it. */
private fun DrawScope.drawWall(velvet: ImageBitmap?, statusBar: Dp, t: Float, dark: Boolean) {
    val unit = 1.dp.toPx()
    val top = -maxOf(statusBar.toPx(), HistoryWall.STATUS_BAR * unit)
    val bottom = size.height
    clipRect(top = top, bottom = bottom) {
        if (velvet == null) {
            drawRect(HistoryColors.WallInTheDark, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
        } else {
            val scale = maxOf(size.width / velvet.width, (bottom - top) / velvet.height)
            val w = velvet.width * scale
            val h = velvet.height * scale
            drawImage(
                velvet,
                dstOffset = IntOffset(((size.width - w) / 2f).toInt(), (top + (bottom - top - h) / 2f).toInt()),
                dstSize = IntSize(w.toInt() + 1, h.toInt() + 1),
                colorFilter = brightness(HistoryWall.wallBrightness(t)),
            )
        }
        val centre = (size.width / unit - HistoryWall.WIDTH) / 2f
        val wash = HistoryWall.washStrength(t)
        if (wash > 0f) glow(Offset(size.width / 2f, (160f - HistoryWall.STATUS_BAR) * unit), 200f * unit, 110f * unit, 0x29, wash)
        HistoryWall.Frames.forEachIndexed { i, frame ->
            val pool = HistoryWall.spotlight(i, t)
            if (pool <= 0f) return@forEachIndexed
            val at = Offset((centre + frame.x + frame.width / 2f) * unit, (frame.y - HistoryWall.STATUS_BAR + frame.height * 0.42f) * unit)
            glow(at, frame.width * 1.05f * unit, frame.height * 1.05f * unit, 0x4D, pool)
        }
    }
    // By day the wall ends in a fine claret line over the ivory page.
    if (!dark) drawRect(HistoryColors.WallEdge, topLeft = Offset(0f, size.height - unit), size = Size(size.width, unit))
}

/** A warm elliptical pool of light, [alpha255] strong at its middle. */
private fun DrawScope.glow(centre: Offset, radiusX: Float, radiusY: Float, alpha255: Int, strength: Float) {
    val warm = Color(0xFFFFE2D6)
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = centre) {
        drawCircle(
            Brush.radialGradient(
                0f to warm.copy(alpha = alpha255 / 255f),
                0.55f to warm.copy(alpha = alpha255 / 255f * 0.32f),
                1f to warm.copy(alpha = 0f),
                center = centre,
                radius = radiusX,
            ),
            radius = radiusX,
            center = centre,
            alpha = strength.coerceIn(0f, 1f),
        )
    }
}

/** David and Churchill at the window's edges, lit from below, and the logo between them with its glint. */
private fun DrawScope.drawFigures(david: ImageBitmap, churchill: ImageBitmap, logo: ImageBitmap, t: Float) {
    val unit = 1.dp.toPx()
    val busts = brightness(HistoryWall.bustBrightness(t))
    val bustTop = (HistoryWall.BUST_TOP - HistoryWall.STATUS_BAR) * unit
    val bustHeight = HistoryWall.BUST_HEIGHT * unit
    val davidLeft = HistoryWall.DAVID_X * unit
    val churchillLeft = size.width + HistoryWall.CHURCHILL_FROM_RIGHT * unit
    drawImage(david, dstOffset = IntOffset(davidLeft.toInt(), bustTop.toInt()), dstSize = IntSize((HistoryWall.DAVID_WIDTH * unit).toInt(), bustHeight.toInt()), colorFilter = busts)
    drawImage(
        churchill,
        dstOffset = IntOffset(churchillLeft.toInt(), bustTop.toInt()),
        dstSize = IntSize((HistoryWall.CHURCHILL_WIDTH * unit).toInt(), bustHeight.toInt()),
        colorFilter = busts,
    )
    val uplight = HistoryWall.uplight(t)
    if (uplight > 0f) {
        glow(Offset(davidLeft + HistoryWall.DAVID_WIDTH * unit * 0.55f, size.height), 80f * unit, 60f * unit, 0x40, uplight)
        glow(Offset(churchillLeft + HistoryWall.CHURCHILL_WIDTH * unit * 0.45f, size.height), 80f * unit, 60f * unit, 0x40, uplight)
    }
    val logoWidth = HistoryWall.LOGO_WIDTH * unit
    val logoRect = Rect(
        Offset((size.width - logoWidth) / 2f, (HistoryWall.LOGO_TOP - HistoryWall.STATUS_BAR) * unit),
        Size(logoWidth, HistoryWall.LOGO_HEIGHT * unit),
    )
    drawLogo(logo, logoRect, HistoryWall.logoAlpha(t), HistoryWall.glint(t))
}

/** The logo, and while [glint] is between 0 and 1 a bright band sweeping across its letters only. */
private fun DrawScope.drawLogo(logo: ImageBitmap, rect: Rect, alpha: Float, glint: Float) {
    val dst = IntOffset(rect.left.toInt(), rect.top.toInt())
    val dstSize = IntSize(rect.width.toInt(), rect.height.toInt())
    if (glint <= 0f || glint >= 1f) {
        drawImage(logo, dstOffset = dst, dstSize = dstSize, alpha = alpha)
        return
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(rect, Paint())
        drawImage(logo, dstOffset = dst, dstSize = dstSize, alpha = alpha)
        val band = rect.width * 0.35f
        val x = rect.left - band + (rect.width + band * 2f) * glint
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color(0xD9FFF4DC),
                1f to Color.Transparent,
                start = Offset(x - band / 2f, rect.top),
                end = Offset(x + band / 2f, rect.bottom),
            ),
            topLeft = rect.topLeft,
            size = rect.size,
            blendMode = BlendMode.SrcAtop,
        )
        canvas.restore()
    }
}

private fun brightness(b: Float): ColorFilter? = if (b == 1f) {
    null
} else {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(b, b, b, 1f) })
}

/**
 * The wall's layout in dp on the design's 369dp-wide board, whose top is the top of the screen under
 * a 43dp status bar, and the lights' choreography in milliseconds from when they start. The frames
 * and logo stay centred on wider windows; the busts keep to the window's edges.
 */
internal object HistoryWall {
    const val WIDTH = 369f
    const val STATUS_BAR = 43f
    const val HEIGHT = 344f
    const val PUBLISHER_BOTTOM = 318f

    /** One frame on the wall, the newest episode first, and when its spotlight comes on. */
    data class Frame(val x: Float, val y: Float, val width: Float, val height: Float, val style: FrameStyle, val lightsAt: Float)

    private fun big() = FrameStyle(oval = false, moulding = 9.dp, mount = 6.dp, shadows = WallShadows)
    private fun oval() = FrameStyle(oval = true, moulding = 6.dp, mount = 4.dp, shadows = WallShadows)
    private fun small() = FrameStyle(oval = false, moulding = 4.dp, mount = 0.dp, shadows = WallShadows)

    val Frames = listOf(
        Frame(128.5f, 104f, 112f, 112f, big(), 180f),
        Frame(64f, 104f, 58f, 82f, oval(), 460f),
        Frame(246.5f, 104f, 58f, 82f, oval(), 460f),
        Frame(12f, 104f, 46f, 38f, small(), 620f),
        Frame(310.5f, 104f, 46f, 38f, small(), 620f),
        Frame(12f, 148f, 46f, 38f, small(), 720f),
        Frame(310.5f, 148f, 46f, 38f, small(), 720f),
    )

    /** The subscribe moment runs this many of the latest episodes past. */
    const val MOMENT_FRAMES = 9

    const val BUST_TOP = 192f
    const val BUST_HEIGHT = 152f
    const val DAVID_X = -28f
    const val DAVID_WIDTH = 119.7f

    /** Churchill's left edge, from the window's right edge. */
    const val CHURCHILL_FROM_RIGHT = -92f
    const val CHURCHILL_WIDTH = 125.7f
    const val LOGO_TOP = 236f
    const val LOGO_WIDTH = 160f
    const val LOGO_HEIGHT = 49f

    /** The longest the lights wait for the frames' pictures, and the dark beat before they come on. */
    const val PICTURE_WAIT_MILLIS = 1_200L
    const val DARK_BEAT_MILLIS = 120L

    const val BUSTS_AT = 860f
    const val LOGO_AT = 1_040f
    const val GLINT_AT = 1_260f
    const val GLINT_MILLIS = 560f

    /** By now every light is on and the glint has passed. */
    const val LIT = GLINT_AT + GLINT_MILLIS

    /** In the dark, before the lights. */
    const val WALL_DARK = 0.3f
    const val FRAME_DARK = 0.22f
    const val BUST_DARK = 0.2f
    const val LOGO_DARK = 0.1f

    private val Out = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    private fun progress(t: Float, start: Float, duration: Float) = ((t - start) / duration).coerceIn(0f, 1f)

    /** The room warms first, then rises with the spotlights to its full light. */
    fun wallBrightness(t: Float): Float {
        val warm = Out.transform(progress(t, 0f, 180f))
        val rest = Out.transform(progress(t, 180f, 820f))
        return WALL_DARK + (0.55f - WALL_DARK) * warm + (0.95f - 0.55f) * rest
    }

    fun washStrength(t: Float): Float = Out.transform(progress(t, 180f, 600f))

    /**
     * A frame's brightness as its spotlight comes on: the centre frame flickers like a lamp catching
     * (on, a dip, on again); the others snap on just past full and settle.
     */
    fun frameBrightness(index: Int, t: Float): Float {
        val frame = Frames[index]
        if (index == 0) {
            val p = progress(t, frame.lightsAt, 280f)
            return when {
                p <= 0f -> FRAME_DARK
                p < 0.22f -> FRAME_DARK + (1.08f - FRAME_DARK) * (p / 0.22f)
                p < 0.4f -> 1.08f + (0.5f - 1.08f) * ((p - 0.22f) / 0.18f)
                p < 0.62f -> 0.5f + (1.05f - 0.5f) * ((p - 0.4f) / 0.22f)
                else -> 1.05f - 0.05f * ((p - 0.62f) / 0.38f)
            }
        }
        val p = progress(t, frame.lightsAt, 200f)
        return when {
            p <= 0f -> FRAME_DARK
            p < 0.55f -> FRAME_DARK + (1.06f - FRAME_DARK) * Out.transform(p / 0.55f)
            else -> 1.06f - 0.06f * ((p - 0.55f) / 0.45f)
        }
    }

    /** Each frame's spotlight pool on the wall: it comes on with its frame, bright, then settles. */
    fun spotlight(index: Int, t: Float): Float {
        val p = progress(t, Frames[index].lightsAt, 420f)
        return when {
            p <= 0f -> 0f
            p < 0.3f -> p / 0.3f
            else -> 1f - 0.55f * Out.transform((p - 0.3f) / 0.7f)
        }
    }

    fun bustBrightness(t: Float): Float = BUST_DARK + (1f - BUST_DARK) * Out.transform(progress(t, BUSTS_AT, 360f))

    /** The busts' uplights: on with them, then settling low. */
    fun uplight(t: Float): Float {
        val p = progress(t, BUSTS_AT, 520f)
        return when {
            p <= 0f -> 0f
            p < 0.35f -> p / 0.35f
            else -> 1f - 0.6f * Out.transform((p - 0.35f) / 0.65f)
        }
    }

    fun logoAlpha(t: Float): Float = LOGO_DARK + (1f - LOGO_DARK) * Out.transform(progress(t, LOGO_AT, 280f))

    /** How far the glint is across the logo, 0 to 1; outside its run there is none. */
    fun glint(t: Float): Float = ((t - GLINT_AT) / GLINT_MILLIS).let { if (it <= 0f || it >= 1f) -1f else GlintEasing.transform(it) }

    private val GlintEasing = CubicBezierEasing(0.45f, 0f, 0.35f, 1f)
}
