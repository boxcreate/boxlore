package cx.aswin.boxlore.feature.info.branded.history

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.LocalBrandedHeaderMotion
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel

/**
 * Through the ages: the Subscribe button becomes a strip of the velvet wall, and the show's latest
 * episodes, oldest first, rush in from the right in small bronze frames, blurred with speed. The
 * newest lands in the middle, a brass picture lamp clicks on over it with one haptic, its light
 * flickers and settles, the frames either side dim, and the wall clears into Subscribed. No words of
 * its own. Timings follow the approved design board, in milliseconds from the tap.
 */
internal object HistoryThroughTheAges : BrandedSubscribeMoment {
    override val durationMillis = 1_240
    override val revealDelayMillis = 1_080

    /** The lamp's light clicks on over the newest frame. */
    const val LIGHT_AT = 720f

    const val FRAME = 40f
    const val GAP = 8f
    const val RUN_AT = 60f
    const val RUN_MILLIS = 660f

    override fun nudge(elapsedMillis: Float): BrandedButtonNudge = BrandedButtonNudge(0.dp, 1f)

    /**
     * Where the strip's left edge is, in dp from the button's left, [elapsedMillis] in, for [frames]
     * frames on a button [width] dp wide: just off the right edge at first, the newest frame centred
     * at the end.
     */
    fun stripLeft(elapsedMillis: Float, frames: Int, width: Float): Float {
        val stripWidth = frames * FRAME + (frames - 1).coerceAtLeast(0) * GAP
        val start = width + 15f
        val land = width / 2f - (stripWidth - FRAME / 2f)
        return start + (land - start) * RunEasing.transform(progress(elapsedMillis, RUN_AT, RUN_MILLIS))
    }

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, LIGHT_AT)
        val velvet = ImageBitmap.imageResource(R.drawable.trih_velvet)
        // The header's frames, newest first; the strip runs oldest to newest.
        val pictures = (LocalBrandedHeaderMotion.current?.pictures ?: emptyList()).ifEmpty { listOf(null) }.reversed()
        BoxWithConstraints(modifier.clip(shape), contentAlignment = Alignment.Center) {
            val width = maxWidth.value
            Spacer(Modifier.fillMaxSize().drawBehind { drawWall(velvet, elapsedMillis(), style) })
            FrameStrip(pictures, width, elapsedMillis, Modifier.align(Alignment.CenterStart))
            Spacer(Modifier.fillMaxSize().drawBehind { drawLamp(elapsedMillis()) })
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer { alpha = 1f - progress(elapsedMillis(), 0f, 60f) },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val land = Out.transform(progress(elapsedMillis(), LAND_AT, LAND_MILLIS))
                    alpha = land
                    scaleX = 1.04f - 0.04f * land
                    scaleY = scaleX
                },
            )
        }
    }

    /** Under the wall the button is already Subscribed once the wall covers it; the velvet, its top 80dp above the button, dimmed. */
    private fun DrawScope.drawWall(velvet: ImageBitmap, t: Float, style: BrandedToolbarStyle) {
        drawRect(if (t < WALL_COVERS_AT) style.subscribeContainer else style.subscribedContainer)
        val wall = Out.transform(progress(t, 20f, 120f)) * (1f - In.transform(progress(t, CLEAR_AT + 20f, CLEAR_MILLIS)))
        if (wall <= 0f) return
        val unit = 1.dp.toPx()
        val side = maxOf(400f * unit, size.width)
        drawImage(
            velvet,
            dstOffset = IntOffset(0, (-80f * unit).toInt()),
            dstSize = IntSize(side.toInt(), side.toInt()),
            alpha = wall,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.62f, 0.62f, 0.62f, 1f) }),
        )
    }

    /** The frames, oldest first, rushing in from the right, blurred with speed; the newest pops as the light hits it. */
    @Composable
    private fun FrameStrip(pictures: List<String?>, width: Float, elapsedMillis: () -> Float, modifier: Modifier) {
        Row(
            modifier
                // The strip is wider than the button: it is laid out whole and clipped by the button.
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .offset { IntOffset((stripLeft(elapsedMillis(), pictures.size, width) * density).toInt(), 0) }
                .graphicsLayer {
                    val t = elapsedMillis()
                    alpha = 1f - In.transform(progress(t, CLEAR_AT, CLEAR_MILLIS))
                    val blur = motionBlur(progress(t, RUN_AT, RUN_MILLIS))
                    renderEffect = if (blur > 0.05f) BlurEffect(blur * 2f * density, 0.01f, TileMode.Decal) else null
                },
        ) {
            pictures.forEachIndexed { i, picture ->
                val newest = i == pictures.lastIndex
                BronzeFrame(
                    picture = picture,
                    fallback = null,
                    style = StripFrame,
                    modifier = Modifier
                        .size(FRAME.dp)
                        .graphicsLayer {
                            if (newest) {
                                val (s, _) = landPop(elapsedMillis())
                                scaleX = s
                                scaleY = s
                            }
                        },
                    dim = { if (newest) -(landPop(elapsedMillis()).second - 1f) * 0.6f else 0f },
                )
                if (!newest) Spacer(Modifier.width(GAP.dp))
            }
        }
    }

    /** Motion blur in dp at [p] through the run: strongest early, gone as it lands. */
    private fun motionBlur(p: Float): Float = when {
        p <= 0f || p >= 1f -> 0f
        p < 0.22f -> 1.6f * p / 0.22f
        p < 0.78f -> 1.6f + (0.4f - 1.6f) * ((p - 0.22f) / 0.56f)
        else -> 0.4f * (1f - (p - 0.78f) / 0.22f)
    }

    /** The frames either side dim, the brass lamp clicks on, and its light cone flickers over the newest frame. */
    private fun DrawScope.drawLamp(t: Float) {
        val gone = 1f - In.transform(progress(t, CLEAR_AT, CLEAR_MILLIS))
        val dim = Out.transform(progress(t, 620f, 200f)) * gone
        if (dim > 0f) {
            val w = size.width * 0.42f
            drawRect(Brush.horizontalGradient(0f to SideDim, 1f to SideDim.copy(alpha = 0f), endX = w), size = Size(w, size.height), alpha = dim)
            drawRect(
                Brush.horizontalGradient(0f to SideDim.copy(alpha = 0f), 1f to SideDim, startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height),
                alpha = dim,
            )
        }
        val unit = 1.dp.toPx()
        val light = lightFlicker(t) * gone
        if (light > 0f) drawLightCone(unit, light)
        val lamp = Out.transform(progress(t, 640f, 120f)) * gone
        if (lamp > 0f) {
            val lw = 26f * unit
            drawRoundRect(
                Brush.verticalGradient(0f to Color(0xFFB8955C), 1f to Color(0xFF4A3417), endY = 5f * unit),
                topLeft = Offset((size.width - lw) / 2f, -6f * unit),
                size = Size(lw, 11f * unit),
                cornerRadius = CornerRadius(6f * unit),
                alpha = lamp,
            )
            drawLine(Color(0x66FFECD2), Offset((size.width - lw) / 2f + 3f * unit, 5.5f * unit), Offset((size.width + lw) / 2f - 3f * unit, 5.5f * unit), unit, alpha = lamp)
        }
    }

    /** The light cone, widening down from the lamp, screened over the frame. */
    private fun DrawScope.drawLightCone(unit: Float, light: Float) {
        val cw = 110f * unit
        val left = (size.width - cw) / 2f
        val cone = Path().apply {
            moveTo(left + cw * 0.38f, 0f)
            lineTo(left + cw * 0.62f, 0f)
            lineTo(left + cw, size.height)
            lineTo(left, size.height)
            close()
        }
        val top = Offset(size.width / 2f, 0f)
        clipPath(cone) {
            scale(scaleX = 0.46f / 0.85f * cw / size.height, scaleY = 1f, pivot = top) {
                drawCircle(
                    Brush.radialGradient(0f to Color(0xD9FFECD2), 0.45f to Color(0x59FFE2C4), 0.8f to Color(0x00FFE2C4), center = top, radius = size.height * 0.85f),
                    radius = size.height * 2f,
                    center = top,
                    alpha = light,
                    blendMode = BlendMode.Screen,
                )
            }
        }
    }

    /** The newest frame's pop as the light hits it: its scale and brightness. */
    private fun landPop(t: Float): Pair<Float, Float> {
        val p = progress(t, LIGHT_AT, 230f)
        if (p <= 0f) return 1f to 1f
        val e = Pop.transform(p)
        return if (e < 0.4f) {
            val k = e / 0.4f
            (1f + 0.16f * k) to (1f + 0.25f * k)
        } else {
            val k = (e - 0.4f) / 0.6f
            (1.16f - 0.06f * k) to (1.25f - 0.13f * k)
        }
    }

    /** The lamp's light: on, a flicker down, on again, then steady. */
    private fun lightFlicker(t: Float): Float {
        val p = progress(t, LIGHT_AT, 260f)
        return when {
            p <= 0f -> 0f
            p < 0.3f -> p / 0.3f
            p < 0.45f -> 1f - 0.65f * ((p - 0.3f) / 0.15f)
            p < 0.65f -> 0.35f + 0.65f * ((p - 0.45f) / 0.2f)
            else -> 1f - 0.1f * ((p - 0.65f) / 0.35f)
        }
    }

    private const val WALL_COVERS_AT = 140f
    private const val CLEAR_AT = 920f
    private const val CLEAR_MILLIS = 140f
    private const val LAND_AT = 1_060f
    private const val LAND_MILLIS = 180f

    private val StripFrame = FrameStyle(oval = false, moulding = 3.dp, mount = 2.dp, shadows = StripShadows, bevel = 1.6.dp)
    private val SideDim = Color(0xE6140409)
}

private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

private val RunEasing = CubicBezierEasing(0.1f, 0.72f, 0.22f, 1f)
private val Pop = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
private val Out = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val In = CubicBezierEasing(0.42f, 0f, 1f, 1f)
