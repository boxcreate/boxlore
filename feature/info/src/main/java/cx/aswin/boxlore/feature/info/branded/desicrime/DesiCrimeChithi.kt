package cx.aswin.boxlore.feature.info.branded.desicrime

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.Keyframe
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.branded.introProgress
import cx.aswin.boxlore.feature.info.branded.keyframes
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel
import kotlin.random.Random

/**
 * Chai and a chithi, after the show's own segment of listeners' letters: the button becomes a
 * listener's blue inland letter, written edge to edge with a stamp and postmark; a cutting-chai glass
 * is set down on it with one haptic, chai pours in from above and steams, and the glass lifts away,
 * leaving its ring on the letter. Then the button clears into Subscribed. No words of its own.
 * Timings follow the approved design board, in milliseconds from the tap.
 */
internal object DesiCrimeChithi : BrandedSubscribeMoment {
    override val durationMillis = 1_400
    override val revealDelayMillis = 1_240

    /** The glass is set down on the letter. */
    const val SET_DOWN_AT = 430f

    override fun nudge(elapsedMillis: Float): BrandedButtonNudge = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, SET_DOWN_AT)
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Spacer(
                Modifier.fillMaxSize().drawWithCache {
                    val letter = Letter(size.width, 1.dp.toPx())
                    onDrawBehind {
                        val t = elapsedMillis()
                        drawRect(style.subscribedContainer)
                        drawRect(style.subscribeContainer, alpha = 1f - introProgress(t, 60f, 120f))
                        val shown = ChithiKeys.shown(t)
                        if (shown <= 0f) return@onDrawBehind
                        translate(left = -1.04f * size.width * (1f - ChithiKeys.slide(t)), top = ChithiKeys.thunk(t) * 1.dp.toPx()) {
                            with(letter) { draw(t, shown) }
                        }
                    }
                },
            )
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer { alpha = 1f - introProgress(elapsedMillis(), 0f, 80f) },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val land = Land.transform(introProgress(elapsedMillis(), LABEL_AT, 180f))
                    alpha = land
                    scaleX = 1.04f - 0.04f * land
                    scaleY = scaleX
                },
            )
        }
    }

    private const val LABEL_AT = 1_220f
    private val Land = CubicBezierEasing(0f, 0f, 0.58f, 1f)
}

/**
 * The inland letter over a button [width] wide, drawn in the design's 235 by 48 units ([unit] pixels
 * each across its height): what is written on it spreads with the button; the stamp, the postmark
 * and the glass keep their size.
 */
private class Letter(width: Float, private val unit: Float) {
    private val across = width / (DESIGN_WIDTH * unit)
    private fun x(design: Float) = design * across * unit
    private fun y(design: Float) = design * unit

    private val scribbles = (0 until 4).map { line -> scribble(56f, if (line < 3) 168f else 134f, 15f + line * 7.5f, 40 + line) }
    private val centre = x(GLASS_X + GLASS_WIDTH / 2f)
    private val glass = Path().apply {
        val half = GLASS_WIDTH / 2f * unit
        val base = GLASS_WIDTH * BASE / 2f * unit
        moveTo(centre - half, y(GLASS_TOP))
        lineTo(centre + half, y(GLASS_TOP))
        lineTo(centre + base, y(GLASS_TOP + GLASS_HEIGHT))
        lineTo(centre - base, y(GLASS_TOP + GLASS_HEIGHT))
        close()
    }
    private val stream = Path().apply {
        moveTo(centre + unit, -12f * unit)
        cubicTo(centre - 0.5f * unit, 0f, centre + 1.5f * unit, 8f * unit, centre, y(GLASS_TOP + GLASS_HEIGHT - 4f))
    }
    private val streamMeasure = PathMeasure().apply { setPath(stream, false) }
    private val streamPart = Path()

    fun DrawScope.draw(t: Float, alpha: Float) {
        drawRect(Brush.linearGradient(0f to PaperLight, 0.55f to PaperMid, 1f to PaperDeep, end = Offset(size.width, size.height)), alpha = alpha)
        drawRoundRect(
            EdgeBlue,
            Offset(3f * unit, 3f * unit),
            Size(size.width - 6f * unit, size.height - 6f * unit),
            CornerRadius(20f * unit),
            alpha = alpha * 0.8f,
            style = Stroke(0.7f * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.2f * unit, 1.6f * unit))),
        )
        drawLine(Color.White, Offset(x(140f), y(3f)), Offset(x(140f), size.height - y(3f)), unit, alpha = alpha * 0.55f)
        drawLine(Crease, Offset(x(140f) + 1.2f * unit, y(3f)), Offset(x(140f) + 1.2f * unit, size.height - y(3f)), 0.6f * unit, alpha = alpha * 0.4f)
        scribbles.forEach { drawPath(it, Handwriting, alpha = alpha * 0.72f, style = Stroke(0.9f * unit, cap = StrokeCap.Round)) }
        drawStamp(alpha)
        val ring = ChithiKeys.ring(t) * alpha
        if (ring > 0f) drawRing(ring)
        drawStream(t, alpha)
        translate(top = ChithiKeys.glassY(t) * unit) { drawGlass(t, alpha) }
        for ((dx, delay) in Wisps) drawWisp(dx, delay, t, alpha)
    }

    private fun DrawScope.drawStamp(alpha: Float) {
        translate(x(22f), y(8f)) {
            drawRect(Stamp, size = Size(13f * unit, 15f * unit), alpha = alpha)
            drawRect(StampEdge, size = Size(13f * unit, 15f * unit), alpha = alpha, style = Stroke(1.6f * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * unit, 0.9f * unit))))
            val figure = Path().apply {
                moveTo(4f * unit, 4f * unit)
                lineTo(9f * unit, 4f * unit)
                lineTo(8.3f * unit, 11f * unit)
                lineTo(4.7f * unit, 11f * unit)
                close()
            }
            drawPath(figure, StampEdge, alpha = alpha * 0.9f)
        }
        val postmark = Stroke(0.7f * unit)
        drawCircle(Postmark, 6.5f * unit, Offset(x(38f), y(21f)), alpha = alpha * 0.6f, style = postmark)
        for (row in 0 until 3) {
            val wave = Path().apply {
                var at = x(38f) + 6f * unit
                val level = y(18.5f + row * 2.5f)
                moveTo(at, level)
                repeat(3) {
                    quadraticTo(at + 1.5f * unit, level - 1.6f * unit, at + 3f * unit, level)
                    quadraticTo(at + 4.5f * unit, level + 1.6f * unit, at + 6f * unit, level)
                    at += 6f * unit
                }
            }
            drawPath(wave, Postmark, alpha = alpha * 0.6f, style = postmark)
        }
    }

    private fun DrawScope.drawRing(alpha: Float) {
        val bottom = y(GLASS_TOP + GLASS_HEIGHT - 1f)
        val rx = GLASS_WIDTH * BASE / 2f * unit
        drawOval(RingDark, Offset(centre - rx - 1.2f * unit, bottom - 3f * unit), Size(2 * (rx + 1.2f * unit), 6f * unit), alpha = alpha * 0.55f, style = Stroke(1.6f * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * unit, 3f * unit, 6f * unit, 2f * unit))))
        drawOval(Tea, Offset(centre + 0.6f * unit - rx + 0.6f * unit, bottom + 0.4f * unit - 2.2f * unit), Size(2 * (rx - 0.6f * unit), 4.4f * unit), alpha = alpha * 0.35f, style = Stroke(0.7f * unit))
    }

    private fun DrawScope.drawStream(t: Float, alpha: Float) {
        val length = streamMeasure.length
        if (length <= 0f) return
        val dash = DASH * unit
        val offset = ChithiKeys.pourOffset(t) * unit
        val from = (-offset).coerceIn(0f, length)
        val to = (dash - offset).coerceIn(0f, length)
        if (to <= from) return
        streamPart.reset()
        streamMeasure.getSegment(from, to, streamPart, true)
        drawPath(streamPart, Brush.verticalGradient(0f to TeaLight, 1f to Tea), alpha = alpha, style = Stroke(2.6f * unit, cap = StrokeCap.Round))
    }

    private fun DrawScope.drawGlass(t: Float, alpha: Float) {
        val top = y(GLASS_TOP)
        val bottom = y(GLASS_TOP + GLASS_HEIGHT)
        val half = GLASS_WIDTH / 2f * unit
        val base = GLASS_WIDTH * BASE / 2f * unit
        drawOval(GlassShadow, Offset(centre - base - 1.5f * unit, bottom + 0.6f * unit - 1.6f * unit), Size(2 * (base + 1.5f * unit), 3.2f * unit), alpha = alpha)
        clipPath(glass) {
            val fill = ChithiKeys.fill(t)
            if (fill > 0f) {
                scale(scaleX = 1f, scaleY = fill, pivot = Offset(centre, bottom)) {
                    drawRect(Brush.verticalGradient(0f to TeaLight, 1f to TeaDeep, startY = top + 6f * unit, endY = bottom), Offset(centre - half, top + 6f * unit), Size(2 * half, bottom - top - 6f * unit), alpha = alpha)
                }
            }
            val froth = ChithiKeys.froth(t)
            if (froth > 0f) drawRect(Froth, Offset(centre - half, top + 5f * unit), Size(2 * half, 2.6f * unit), alpha = alpha * froth)
        }
        drawPath(glass, GlassTint, alpha = alpha)
        drawPath(glass, GlassEdge, alpha = alpha, style = Stroke(0.9f * unit))
        for (f in Ribs) {
            val at = centre - half + 2 * half * f
            drawLine(Color.White, Offset(at, top + 2f * unit), Offset(centre + (at - centre) * BASE, bottom - unit), 0.55f * unit, alpha = alpha * 0.32f)
        }
        drawLine(Color.White, Offset(centre - half + 2.4f * unit, top + 2f * unit), Offset(centre - half + 4.6f * unit, bottom - 2f * unit), 1.3f * unit, alpha = alpha * 0.65f)
        drawOval(GlassEdge, Offset(centre - half, top - 1.4f * unit), Size(2 * half, 2.8f * unit), alpha = alpha, style = Stroke(0.8f * unit))
    }

    private fun DrawScope.drawWisp(dx: Float, delay: Float, t: Float, alpha: Float) {
        val wisp = ChithiKeys.wisp(t, delay)
        if (wisp.alpha <= 0f) return
        val foot = Offset(centre + dx * unit, y(GLASS_TOP - 1f))
        translate(top = wisp.rise * unit) {
            scale(scaleX = 1f, scaleY = wisp.stretch, pivot = foot) {
                val path = Path().apply {
                    moveTo(foot.x, foot.y)
                    relativeCubicTo(-2.4f * unit, -3f * unit, 2.4f * unit, -5f * unit, 0f, -9f * unit)
                }
                drawPath(path, Color.White, alpha = alpha * wisp.alpha, style = Stroke(1.3f * unit, cap = StrokeCap.Round))
            }
        }
    }

    /** One line of cursive-looking handwriting: small loops and a word gap or two, no letters and no words. */
    private fun scribble(from: Float, to: Float, level: Float, seed: Int): Path {
        val random = Random(seed)
        val gaps = listOf(random.nextInt((from + 18).toInt(), (to - 30).toInt()), random.nextInt((from + 50).toInt(), (to - 10).toInt()))
        return Path().apply {
            var at = from
            var height = level
            moveTo(x(at), y(height))
            while (at < to) {
                val step = random.nextDouble(3.4, 5.2).toFloat()
                val loop = random.nextDouble(0.9, 2.1).toFloat()
                if (gaps.any { kotlin.math.abs(at - it) < 3f }) {
                    at += step + 3f
                    moveTo(x(at), y(height))
                    continue
                }
                val drift = random.nextDouble(-0.4, 0.4).toFloat()
                quadraticTo(x(at + step / 2f), y(height - loop), x(at + step), y(height + drift))
                at += step
                height += drift
            }
        }
    }

    companion object {
        const val DESIGN_WIDTH = 235f
        const val GLASS_X = 184f
        const val GLASS_TOP = 9f
        const val GLASS_WIDTH = 26f
        const val GLASS_HEIGHT = 34f

        /** The glass narrows to its base. */
        const val BASE = 0.78f
        const val DASH = 60f
        val Ribs = listOf(0.18f, 0.36f, 0.54f, 0.72f, 0.9f)
        val Wisps = listOf(-4f to 780f, 2f to 850f, 6f to 920f)
    }
}

/** The moment's keyframes, from the design board, in milliseconds from the tap. */
internal object ChithiKeys {
    private val Glass = CubicBezierEasing(0.3f, 0.8f, 0.3f, 1f)
    private val Out = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** The letter over the button: there until just before Subscribed lands. */
    fun shown(t: Float): Float = if (t < 1_150f) 1f else (1f - (t - 1_150f) / 70f).coerceAtLeast(0f)

    /** The letter slides in from the left. */
    fun slide(t: Float): Float = CubicBezierEasing(0.25f, 0.8f, 0.3f, 1f).transform(introProgress(t, 10f, 230f))

    /** The letter's small jump as the glass is set down, in dp. */
    fun thunk(t: Float): Float = keyframes(introProgress(t, 430f, 160f), Keyframe(0f, 0f, Out), Keyframe(0.35f, 0.8f, Out), Keyframe(0.7f, -0.3f, Out), Keyframe(1f, 0f))

    /** The glass's height above where it stands, in dp: lowered in, held while it fills, lifted away. */
    fun glassY(t: Float): Float = keyframes(
        introProgress(t, 190f, 1_000f),
        Keyframe(0f, -58f, Glass),
        Keyframe(0.24f, 1f, Glass),
        Keyframe(0.3f, 0f, Glass),
        Keyframe(0.76f, 0f, Glass),
        Keyframe(1f, -64f),
    )

    /** The pour's dash offset along the stream, in dp: the stream reaches down, runs, then its tail falls in after it. */
    fun pourOffset(t: Float): Float = keyframes(introProgress(t, 450f, 470f), Keyframe(0f, 60f), Keyframe(0.3f, 0f), Keyframe(0.75f, 0f), Keyframe(1f, -60f))

    fun fill(t: Float): Float = Out.transform(introProgress(t, 520f, 360f))

    fun froth(t: Float): Float = introProgress(t, 860f, 120f)

    fun ring(t: Float): Float = introProgress(t, 1_000f, 160f)

    /** A wisp of steam starting [delay] after the tap: it rises, stretches and fades. */
    fun wisp(t: Float, delay: Float): Wisp {
        val p = introProgress(t, delay, 360f)
        if (t < delay) return Wisp(0f, 2f, 0.6f)
        return Wisp(
            alpha = keyframes(p, Keyframe(0f, 0f, Out), Keyframe(0.4f, 0.85f, Out), Keyframe(1f, 0f)),
            rise = 2f - 11f * Out.transform(p),
            stretch = 0.6f + 0.5f * Out.transform(p),
        )
    }

    data class Wisp(val alpha: Float, val rise: Float, val stretch: Float)
}

private val PaperLight = Color(0xFFDCE9F6)
private val PaperMid = Color(0xFFC6DAF0)
private val PaperDeep = Color(0xFFB3CCE8)
private val EdgeBlue = Color(0xFF7E9CC6)
private val Crease = Color(0xFF6F8DB8)
private val Handwriting = Color(0xFF1D3A6E)
private val Stamp = Color(0xFFEE7112)
private val StampEdge = Color(0xFFF4ECDC)
private val Postmark = Color(0xFF3B4F7A)
private val Tea = Color(0xFFA8642C)
private val TeaLight = Color(0xFFE0A160)
private val TeaDeep = Color(0xFF6A3510)
private val Froth = Color(0xFFEBD6B0)
private val RingDark = Color(0xFF8A4A1A)
private val GlassShadow = Color(0x40141E32)
private val GlassTint = Color(0x29E6F0F8)
private val GlassEdge = Color(0xE6FAFCFF)
