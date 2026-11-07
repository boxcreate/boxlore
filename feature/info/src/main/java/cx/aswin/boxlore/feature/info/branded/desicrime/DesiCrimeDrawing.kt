package cx.aswin.boxlore.feature.info.branded.desicrime

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.Keyframe
import cx.aswin.boxlore.feature.info.branded.introProgress
import cx.aswin.boxlore.feature.info.branded.keyframes
import kotlin.math.roundToInt

// The crime board's drawing: the cork, the studio light, the hosts, pins, tape and the torn print's edge and shadow.

/** The torn print's shadow on the cork: high and soft while it drops, close and darker once it lands. */
internal fun DrawScope.drawTornShadow(lift: Float) {
    val unit = 1.dp.toPx()
    val drop = (22f - 17f * lift) * unit
    val blur = (16f - 10f * lift) * unit
    val paint = android.graphics.Paint().apply {
        color = DesiCrimeColors.Shadow.toArgb()
        alpha = ((0.45f + 0.15f * lift) * 255).roundToInt()
        maskFilter = BlurMaskFilter(blur * 0.75f, BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.apply {
        save()
        translate(0f, drop)
        drawPath(tearPath(size.width).asAndroidPath(), paint)
        restore()
    }
}

/** The cork fills the board and reaches up behind the status bar, cropped to cover. */
internal fun DrawScope.drawCork(cork: ImageBitmap?, statusBar: Float) {
    val unit = 1.dp.toPx()
    val top = -maxOf(statusBar, DesiCrimeArt.STATUS_BAR * unit)
    clipRect(top = top, bottom = size.height) {
        drawRect(DesiCrimeColors.Cork, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        if (cork == null) return@clipRect
        val height = size.height - top
        val scale = maxOf(size.width / cork.width, height / cork.height)
        val srcWidth = (size.width / scale).roundToInt().coerceAtMost(cork.width)
        val srcHeight = (height / scale).roundToInt().coerceAtMost(cork.height)
        drawImage(
            cork,
            srcOffset = IntOffset((cork.width - srcWidth) / 2, 0),
            srcSize = IntSize(srcWidth, srcHeight),
            dstOffset = IntOffset(0, top.roundToInt()),
            dstSize = IntSize(size.width.roundToInt(), height.roundToInt()),
        )
    }
}

/**
 * The studio light over the board: the lamp's warm pool (flickering on as the board opens), the red
 * wall's glow at the left, shadow at the edges, and the dark under the top bar.
 */
internal fun DrawScope.drawStudioLight(t: Float, statusBar: Float) {
    val unit = 1.dp.toPx()
    val top = -maxOf(statusBar, DesiCrimeArt.STATUS_BAR * unit)
    val height = size.height - top
    val lamp = keyframes(introProgress(t, 0f, 900f), Keyframe(0f, 0f, LampEase), Keyframe(0.12f, 0.8f, LampEase), Keyframe(0.18f, 0.2f, LampEase), Keyframe(0.3f, 1f, LampEase), Keyframe(1f, 1f))
    clipRect(top = top, bottom = size.height) {
        ellipse(Offset(size.width * 0.54f, top + height * 0.44f), size.width * 0.62f, height * 0.52f, top, lamp, BlendMode.Screen, 0f to LampLight, 0.45f to LampLight.copy(alpha = 0.10f), 0.72f to Color.Transparent)
        drawRect(Brush.horizontalGradient(0f to WallGlow, 0.22f to Color.Transparent, endX = size.width), topLeft = Offset(0f, top), size = Size(size.width, height), blendMode = BlendMode.Multiply)
        ellipse(Offset(size.width * 0.54f, top + height * 0.5f), size.width * 0.95f, height * 0.78f, top, 1f, BlendMode.SrcOver, 0.42f to Color.Transparent, 1f to EdgeShadow)
        val dark = 0.35f * (1f - keyframes(introProgress(t, 0f, 900f), Keyframe(0f, 0f, LampEase), Keyframe(0.3f, 1f), Keyframe(1f, 1f)))
        if (dark > 0f) drawRect(Studio, topLeft = Offset(0f, top), size = Size(size.width, height), alpha = dark)
        val shade = (DesiCrimeArt.TOP_SHADE - DesiCrimeArt.STATUS_BAR) * unit - top
        drawRect(
            Brush.verticalGradient(0f to TopShade, 0.5f to TopShade.copy(alpha = 0.55f), 1f to Color.Transparent, startY = top, endY = top + shade),
            topLeft = Offset(0f, top),
            size = Size(size.width, shade),
        )
    }
}

/** An elliptical radial gradient ([rx] by [ry] round [centre]) over the whole board from [top] down, like CSS's radial-gradient. */
private fun DrawScope.ellipse(centre: Offset, rx: Float, ry: Float, top: Float, alpha: Float, blendMode: BlendMode, vararg stops: Pair<Float, Color>) {
    if (alpha <= 0f) return
    val squash = ry / rx
    scale(scaleX = 1f, scaleY = squash, pivot = centre) {
        val from = centre.y + (top - centre.y) / squash
        val to = centre.y + (size.height - centre.y) / squash
        drawRect(Brush.radialGradient(*stops, center = centre, radius = rx), topLeft = Offset(0f, from), size = Size(size.width, to - from), alpha = alpha, blendMode = blendMode)
    }
}

/** Aishwarya held to the window's left edge and Aryaan to its right, peeking up over their arms. */
internal fun DrawScope.drawHosts(her: ImageBitmap, him: ImageBitmap) {
    val unit = 1.dp.toPx()
    val top = (DesiCrimeArt.HOSTS_Y - DesiCrimeArt.STATUS_BAR) * unit
    val height = DesiCrimeArt.HOSTS_HEIGHT * unit
    val herWidth = height * her.width / her.height
    val himWidth = height * him.width / him.height
    drawImage(her, dstOffset = IntOffset(0, top.roundToInt()), dstSize = IntSize(herWidth.roundToInt(), height.roundToInt()))
    drawImage(him, dstOffset = IntOffset((size.width - himWidth).roundToInt(), top.roundToInt()), dstSize = IntSize(himWidth.roundToInt(), height.roundToInt()))
}

/** A flat brass drawing pin, the kind every stationery shop sells by the box. */
internal fun DrawScope.drawBrassPin(centre: Offset, size: Float) {
    val r = size / 2f
    drawCircle(Brush.radialGradient(0f to PinShadow, 1f to Color.Transparent, center = centre + Offset(r * 0.3f, r * 0.55f), radius = r * 1.35f), r * 1.35f, centre + Offset(r * 0.3f, r * 0.55f))
    drawCircle(
        Brush.radialGradient(0f to Color(0xFFFFF3C4), 0.26f to Color(0xFFEDC45A), 0.62f to Color(0xFFB9832A), 1f to Color(0xFF714A12), center = centre + Offset(-0.32f * r, -0.4f * r), radius = r * 1.5f),
        r,
        centre,
    )
    drawCircle(PinRing, r - 0.35f, centre, style = Stroke(0.7f * density))
    drawCircle(PinDot, 1.3f * density, centre)
}

/** A saffron plastic push pin, seen from above. */
internal fun DrawScope.drawPushPin(centre: Offset, size: Float) {
    val r = size / 2f
    drawCircle(Brush.radialGradient(0f to PinShadow, 1f to Color.Transparent, center = centre + Offset(r * 0.35f, r * 0.6f), radius = r * 1.4f), r * 1.4f, centre + Offset(r * 0.35f, r * 0.6f))
    drawCircle(
        Brush.radialGradient(0f to Color(0xFFFFD3A8), 0.3f to Color(0xFFF58A33), 0.64f to Color(0xFFD2600C), 1f to Color(0xFF8C3A04), center = centre + Offset(-0.28f * r, -0.4f * r), radius = r * 1.5f),
        r,
        centre,
    )
}

/** Two strips of tape across a print's top corners. */
internal fun DrawScope.drawTape() {
    val unit = 1.dp.toPx()
    val strip = Size(26f * unit, 10f * unit)
    rotate(-30f, pivot = Offset(6f * unit, 1f * unit)) { drawRect(DesiCrimeColors.Tape, Offset(-7f * unit, -4f * unit), strip) }
    rotate(30f, pivot = Offset(size.width - 6f * unit, 1f * unit)) { drawRect(DesiCrimeColors.Tape, Offset(size.width + 7f * unit - strip.width, -4f * unit), strip) }
}

/** The torn edge every episode picture shares, as fractions of the picture's width (the art is square). */
internal val TornEdge = GenericShape { size, _ -> addPath(tearPath(size.width)) }

private fun tearPath(width: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(width, 0f)
    var i = DesiCrimeTear.Edge.size - 2
    while (i >= 0) {
        lineTo(DesiCrimeTear.Edge[i] * width, DesiCrimeTear.Edge[i + 1] * width)
        i -= 2
    }
    close()
}

private val LampLight = Color(0x47FFB260)
private val WallGlow = Color(0x668C160A)
private val EdgeShadow = Color(0x990E0602)
private val Studio = Color(0xFF0E0602)
private val TopShade = Color(0xC70E0703)
private val PinShadow = Color(0x990A0400)
private val PinRing = Color(0xB3462806)
private val PinDot = Color(0xA65A370A)
private val LampEase = CubicBezierEasing(0f, 0f, 0.58f, 1f)
