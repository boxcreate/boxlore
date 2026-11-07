package cx.aswin.boxlore.feature.info.branded.conspiracy

import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel

/**
 * It all connects: the Subscribe button turns into a patch of the evidence board, three scraps land
 * on it and are pinned, red yarn closes a triangle between them, and a red marker eye is drawn inside
 * it with one haptic and a small tug of the yarn. Then the board clears into Subscribed. No words of
 * its own. Timings follow the approved design board, in milliseconds from the tap.
 */
internal object ConspiracyItAllConnects : BrandedSubscribeMoment {
    override val durationMillis = 1_240
    override val revealDelayMillis = 1_080

    /** The pupil lands, the yarn tugs and the haptic plays together. */
    const val EYE_AT = 790f

    override fun nudge(elapsedMillis: Float): BrandedButtonNudge = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, EYE_AT)
        val dark = LocalEffectiveDarkTheme.current
        val paper = ImageBitmap.imageResource(if (dark) R.drawable.conspiracy_board_black else R.drawable.conspiracy_board_white)
        val scrap = ImageBitmap.imageResource(R.drawable.conspiracy_scrap)
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Spacer(
                Modifier.fillMaxSize().drawWithCache {
                    val unit = 1.dp.toPx()
                    val left = size.width / 2f - CENTRE_X * unit
                    val triangle = YarnStrand(TRIANGLE, Offset(left / unit, 0f), unit, seed = 3)
                    val yarnPaints = YarnPaints(unit, Color(0x52000000), width = 2.3f, shadowShift = Offset(1.2f, 3f))
                    val eye = Path().apply {
                        val x = left + CENTRE_X * unit
                        val y = EYE_Y * unit
                        val w = 13f * unit
                        val h = 6f * unit
                        moveTo(x - w, y)
                        cubicTo(x - 7f * unit, y - h, x + 7f * unit, y - h, x + w, y)
                        cubicTo(x + 7f * unit, y + h, x - 7f * unit, y + h, x - w, y)
                    }
                    val eyeLength = PathMeasure().apply { setPath(eye, false) }.length
                    val eyeStroke = 1.9f * unit
                    onDrawBehind {
                        val t = elapsedMillis()
                        // Under the board, once it covers the button, the button is already Subscribed, so it clears straight to it.
                        drawRect(if (t < BOARD_COVERS_AT) style.subscribeContainer else style.subscribedContainer)
                        val board = EaseOut.transform(progress(t, 20f, 100f)) * (1f - EaseIn.transform(progress(t, CLEAR_AT, CLEAR_MILLIS)))
                        if (board <= 0f) return@onDrawBehind
                        drawBoardPatch(paper, board)
                        val tug = tug(t)
                        withTransform({ scale(tug, tug, pivot = Offset(size.width / 2f, size.height * 0.6f)) }) {
                            drawClues(left, unit, t, board, scrap)
                            if (board < 1f) {
                                // Fading with the board: the yarn and marks are drawn at its strength.
                                drawContext.canvas.nativeCanvas.saveLayerAlpha(0f, 0f, size.width, size.height, (board * 255).toInt())
                            }
                            triangle.draw(this, YarnEase.transform(progress(t, 360f, 300f)), 0f, yarnPaints)
                            drawEye(eye, eyeLength, eyeStroke, left + CENTRE_X * unit, EYE_Y * unit, unit, t)
                            if (board < 1f) drawContext.canvas.nativeCanvas.restore()
                            drawMomentPins(left, unit, t, board)
                        }
                    }
                },
            )
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer { alpha = 1f - progress(elapsedMillis(), 0f, 70f) },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val land = EaseOut.transform(progress(elapsedMillis(), LAND_AT, LAND_MILLIS))
                    alpha = land
                    scaleX = 1.04f - 0.04f * land
                    scaleY = scaleX
                },
            )
        }
    }

    /** The scraps are placed on the design's 235dp-wide button, centred on any other width. */
    private const val CENTRE_X = 117.5f
    private const val EYE_Y = 26f
    private const val CLEAR_AT = 940f
    private const val CLEAR_MILLIS = 150f
    private const val BOARD_COVERS_AT = 120f
    private const val LAND_AT = 1_060f
    private const val LAND_MILLIS = 180f

    /** The three clues, on the design's button: left, top and right. */
    private val A = Offset(42f, 33f)
    private val B = Offset(117.5f, 11f)
    private val C = Offset(193f, 33f)

    /** Yarn from each pin to the next, bowing a little, closing the triangle. */
    private val TRIANGLE = "M${A.x} ${A.y} C ${A.x + 20} ${A.y - 4}, ${B.x - 34} ${B.y + 9}, ${B.x} ${B.y} " +
        "C ${B.x + 34} ${B.y + 9}, ${C.x - 20} ${C.y - 4}, ${C.x} ${C.y} " +
        "C ${C.x - 40} ${C.y + 5}, ${A.x + 40} ${A.y + 5}, ${A.x} ${A.y}"

    private fun DrawScope.drawBoardPatch(paper: ImageBitmap, alpha: Float) {
        // The board's paper, covering the button like CSS background-size: cover.
        val scale = maxOf(size.width / paper.width, size.height / paper.height)
        val w = paper.width * scale
        val h = paper.height * scale
        drawImage(
            paper,
            dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()),
            dstSize = IntSize(w.toInt() + 1, h.toInt() + 1),
            alpha = alpha,
        )
    }

    /** The three scraps drop onto the board one after another: an index card, the yellow scrap, a clipping. */
    private fun DrawScope.drawClues(left: Float, unit: Float, t: Float, alpha: Float, scrap: ImageBitmap) {
        clue(ClueCard(A, -13f, Size(26f, 15f), -8f, 60f), left, unit, t, alpha) { w, h ->
            drawRect(IndexCardPaper, size = Size(w, h))
            drawRect(ConspiracyColors.IndexCardRule, topLeft = Offset(0f, 4f * unit), size = Size(w, unit))
        }
        clue(ClueCard(B, -12f, Size(24f, 14f), 4f, 140f), left, unit, t, alpha) { w, h ->
            val scale = maxOf(w / scrap.width, h / scrap.height)
            clipRect(0f, 0f, w, h) {
                drawImage(
                    scrap,
                    dstOffset = IntOffset(((w - scrap.width * scale) / 2f).toInt(), ((h - scrap.height * scale) / 2f).toInt()),
                    dstSize = IntSize((scrap.width * scale).toInt() + 1, (scrap.height * scale).toInt() + 1),
                )
            }
        }
        clue(ClueCard(C, -13f, Size(26f, 15f), 7f, 220f), left, unit, t, alpha) { w, h ->
            drawRect(ConspiracyColors.Clipping, size = Size(w, h))
            var y = 0f
            while (y < h) {
                drawRect(PrintLine, topLeft = Offset(0f, y), size = Size(w, 1.5f * unit))
                y += 4f * unit
            }
        }
    }

    /** One scrap in the moment: pinned at [at], its left edge [dx] from the pin, turned [rotation]°, dropping in at [start]. */
    private class ClueCard(val at: Offset, val dx: Float, val size: Size, val rotation: Float, val start: Float)

    private inline fun DrawScope.clue(card: ClueCard, left: Float, unit: Float, t: Float, alpha: Float, paint: DrawScope.(Float, Float) -> Unit) {
        val p = Drop.transform(progress(t, card.start, 160f))
        if (p <= 0f) return
        val w = card.size.width * unit
        val h = card.size.height * unit
        val x = left + (card.at.x + card.dx) * unit
        val y = (card.at.y - 3f) * unit - 8f * unit * (1f - p)
        translate(x, y) {
            rotate(card.rotation, pivot = Offset(w / 2f, h / 2f)) {
                drawContext.canvas.nativeCanvas.saveLayerAlpha(-4f * unit, -4f * unit, w + 6f * unit, h + 8f * unit, (p * alpha * 255).toInt())
                drawRect(ClueShadow, topLeft = Offset(0f, 2f * unit), size = Size(w, h))
                paint(w, h)
                drawContext.canvas.nativeCanvas.restore()
            }
        }
    }

    /** Small pushpins, one in each clue, pressed in as it lands. */
    private fun DrawScope.drawMomentPins(left: Float, unit: Float, t: Float, alpha: Float) {
        listOf(A to 140f, B to 220f, C to 300f).forEach { (at, start) ->
            val p = Drop.transform(progress(t, start, 150f))
            if (p <= 0f) return@forEach
            val (scale, shown) = if (p < 0.7f) (2f - 1.1f * p / 0.7f) to p / 0.7f else (0.9f + 0.1f * (p - 0.7f) / 0.3f) to 1f
            val centre = Offset(left + at.x * unit, at.y * unit)
            scale(scale, pivot = centre) {
                val r = 5.5f * unit
                drawCircle(PinShadow, radius = r, center = centre + Offset(2f * unit, 4f * unit), alpha = shown * alpha)
                val head = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        centre.x - r + 0.34f * 2f * r,
                        centre.y - r + 0.30f * 2f * r,
                        10.58f * unit,
                        intArrayOf(0xF2FFFFFF.toInt(), 0xF2FFFFFF.toInt(), 0xFFFF8C7C.toInt(), 0xFFE2352A.toInt(), 0xFFA31A11.toInt(), 0xFF6E0F09.toInt()),
                        floatArrayOf(0f, 1.4f / 10.58f, 1.9f / 10.58f, 0.38f, 0.72f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    this.alpha = (shown * alpha * 255).toInt()
                }
                drawContext.canvas.nativeCanvas.drawCircle(centre.x, centre.y, r, head)
                drawCircle(PinRing, radius = r, center = centre, style = Stroke(0.6f * unit), alpha = shown * alpha)
            }
        }
    }

    /** The marker eye: its outline drawn in one stroke, then its iris, then the pupil dotted in. */
    private fun DrawScope.drawEye(eye: Path, length: Float, width: Float, x: Float, y: Float, unit: Float, t: Float) {
        val drawn = EaseOut.transform(progress(t, 640f, 170f))
        if (drawn <= 0f) return
        val outline = (drawn / 0.75f).coerceAtMost(1f)
        drawPath(
            eye,
            ConspiracyColors.Marker,
            style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(length * outline, length + 1f))),
        )
        val iris = ((drawn - 0.75f) / 0.25f).coerceIn(0f, 1f)
        if (iris > 0f) {
            drawArc(
                ConspiracyColors.Marker,
                startAngle = 180f,
                sweepAngle = -360f * iris,
                useCenter = false,
                topLeft = Offset(x - 3.2f * unit, y - 3.2f * unit),
                size = Size(6.4f * unit, 6.4f * unit),
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        val pupil = progress(t, EYE_AT, 60f)
        if (pupil > 0f) drawCircle(ConspiracyColors.Marker, radius = 1.6f * unit, center = Offset(x, y), alpha = pupil)
    }

    /** The yarn's tug as the eye is drawn: everything on the board grows 4.5% and settles back. */
    private fun tug(t: Float): Float {
        val p = progress(t, EYE_AT, 200f)
        if (p <= 0f || p >= 1f) return 1f
        val e = Drop.transform(p)
        return if (e < 0.4f) 1f + 0.045f * (e / 0.4f) else 1.045f - 0.045f * ((e - 0.4f) / 0.6f)
    }
}

private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

private val Drop = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
private val YarnEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private val IndexCardPaper = Color(0xFFF4F0E6)
private val PrintLine = Color(0x4D28241E)
private val ClueShadow = Color(0x40000000)
private val PinShadow = Color(0x52000000)
private val PinRing = Color(0x593C0000)
