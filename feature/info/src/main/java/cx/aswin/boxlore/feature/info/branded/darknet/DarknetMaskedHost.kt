package cx.aswin.boxlore.feature.info.branded.darknet

import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.branded.brandedArtNow
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel

/**
 * The masked host: the button goes dark and Jack's masked avatar comes up in the middle, standing
 * still; his avatar's own red ink burst lights up behind him with a hot core, his glasses flash red
 * with one haptic, film grain runs over it all, and the button clears into Subscribed. No words of
 * its own. Timings follow the approved design board, in milliseconds from the tap.
 */
internal object DarknetMaskedHost : BrandedSubscribeMoment {
    override val durationMillis = 1_240
    override val revealDelayMillis = 1_080

    /** His glasses flash red. */
    const val FLASH_AT = 440f

    override fun nudge(elapsedMillis: Float): BrandedButtonNudge = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, FLASH_AT)
        val jack = brandedArtNow(R.drawable.darknet_jack)
        val burst = brandedArtNow(R.drawable.darknet_burst)
        val grain = brandedArtNow(R.drawable.darknet_grain)
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Spacer(
                Modifier.fillMaxSize().drawWithCache {
                    val unit = 1.dp.toPx()
                    val lensGlow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = LensGlow.toArgb()
                        maskFilter = BlurMaskFilter(4f * unit, BlurMaskFilter.Blur.NORMAL)
                    }
                    onDrawBehind {
                        val t = elapsedMillis()
                        // Once the dark covers the button it is already Subscribed underneath, so it clears straight to it.
                        drawRect(if (t < DARK_COVERS_AT) style.subscribeContainer else style.subscribedContainer)
                        val dark = Out.transform(progress(t, 20f, 90f)) * (1f - In.transform(progress(t, 960f, 150f)))
                        if (dark <= 0f) return@onDrawBehind
                        drawRect(DarknetColors.Page, alpha = dark)
                        val lights = 1f - In.transform(progress(t, 840f, 180f))
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val high = Offset(size.width / 2f, 20f * unit)
                        lightsOn(t)?.let { (alpha, s) ->
                            scale(s, pivot = centre) {
                                ellipse(BackLight, centre, 130f * unit, 75f * unit, alpha * lights)
                            }
                        }
                        drawBurst(burst, high, unit, t, lights)
                        lightsOn(t)?.let { (alpha, s) ->
                            scale(s, pivot = high) {
                                ellipse(HotCore, high, 85f * unit, 75f * unit, alpha * lights, BlendMode.Screen)
                            }
                        }
                        drawJack(jack, unit, t, lensGlow)
                        drawGrain(grain, t)
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
                    val land = Out.transform(progress(elapsedMillis(), 1_060f, 180f))
                    alpha = land
                    scaleX = 1.04f - 0.04f * land
                    scaleY = scaleX
                },
            )
        }
    }

    /** The red light coming on: up past full, a dip, full again, then settling just under it; with its scale. */
    private fun lightsOn(t: Float): Pair<Float, Float>? {
        val p = Snap.transform(progress(t, 180f, 520f))
        if (p <= 0f) return null
        return when {
            p < 0.35f -> (p / 0.35f) to (0.6f + 0.45f * p / 0.35f)
            p < 0.55f -> (1f - 0.4f * (p - 0.35f) / 0.2f) to (1.05f - 0.05f * (p - 0.35f) / 0.65f)
            p < 0.7f -> (0.6f + 0.4f * (p - 0.55f) / 0.15f) to (1.05f - 0.05f * (p - 0.35f) / 0.65f)
            else -> (1f - 0.1f * (p - 0.7f) / 0.3f) to (1.05f - 0.05f * (p - 0.35f) / 0.65f)
        }
    }

    /** His avatar's ink burst, behind him, turning a little as it grows. */
    private fun DrawScope.drawBurst(burst: ImageBitmap, centre: Offset, unit: Float, t: Float, lights: Float) {
        val p = Snap.transform(progress(t, 200f, 560f))
        if (p <= 0f) return
        val alpha = if (p < 0.4f) p / 0.4f else 1f - 0.2f * (p - 0.4f) / 0.6f
        val side = 300f * unit * (0.5f + 0.5f * p)
        rotate(10f * p, pivot = centre) {
            drawImage(
                burst,
                dstOffset = IntOffset((centre.x - side / 2f).toInt(), (centre.y - side / 2f).toInt()),
                dstSize = IntSize(side.toInt(), side.toInt()),
                alpha = alpha * lights,
            )
        }
    }

    /** Jack, 51 by 46, standing at the button's foot in the middle, lit up; his lenses flash red. */
    private fun DrawScope.drawJack(jack: ImageBitmap, unit: Float, t: Float, lensGlow: android.graphics.Paint) {
        val p = progress(t, 60f, 380f)
        if (p <= 0f) return
        val gone = 1f - In.transform(progress(t, 880f, 160f))
        val alpha = (p / 0.3f).coerceAtMost(1f) * gone
        val bright = 0.35f + 0.65f * Out.transform(p)
        val w = JACK_WIDTH * unit
        val h = JACK_HEIGHT * unit
        val left = size.width / 2f - w / 2f
        val top = size.height - h
        drawImage(
            jack,
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
            alpha = alpha,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(bright, bright, bright, 1f) }),
        )
        val flash = lensFlash(t) * (1f - In.transform(progress(t, 860f, 120f)))
        if (flash <= 0f) return
        listOf(0.282f, 0.489f).forEach { x ->
            val c = Offset(left + w * x, top + h * 0.437f)
            lensGlow.alpha = (flash * 0.85f * 255).toInt()
            drawContext.canvas.nativeCanvas.drawOval(c.x - 5.5f * unit, c.y - 4.5f * unit, c.x + 5.5f * unit, c.y + 4.5f * unit, lensGlow)
            drawOval(Lens, topLeft = c - Offset(3.5f * unit, 2.5f * unit), size = Size(7f * unit, 5f * unit), alpha = flash)
        }
    }

    private fun lensFlash(t: Float): Float {
        val p = progress(t, FLASH_AT, 300f)
        return when {
            p <= 0f -> 0f
            p < 0.25f -> p / 0.25f
            p < 0.55f -> 1f - 0.65f * (p - 0.25f) / 0.3f
            p < 0.75f -> 0.35f + 0.65f * (p - 0.55f) / 0.2f
            else -> 1f - 0.15f * (p - 0.75f) / 0.25f
        }
    }

    /** Film grain over everything, jumping every 60ms. */
    private fun DrawScope.drawGrain(grain: ImageBitmap, t: Float) {
        val p = progress(t, 40f, 940f)
        val alpha = when {
            p <= 0f || p >= 1f -> 0f
            p < 0.2f -> 0.75f * p / 0.2f
            p < 0.85f -> 0.75f
            else -> 0.75f * (1f - p) / 0.15f
        }
        if (alpha <= 0f) return
        val unit = size.height / 48f
        val jitter = GrainJitter[((t / 60f).toInt()) % GrainJitter.size]
        var y = -20f * unit + jitter.y * unit
        while (y < size.height) {
            var x = -20f * unit + jitter.x * unit
            while (x < size.width) {
                drawImage(grain, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(grain.width, grain.height), alpha = alpha, blendMode = BlendMode.Overlay)
                x += grain.width
            }
            y += grain.height
        }
    }

    private fun DrawScope.ellipse(colors: Array<Pair<Float, Color>>, centre: Offset, rx: Float, ry: Float, alpha: Float, blend: BlendMode = BlendMode.SrcOver) {
        if (alpha <= 0f) return
        scale(scaleX = 1f, scaleY = ry / rx, pivot = centre) {
            drawCircle(Brush.radialGradient(*colors, center = centre, radius = rx), radius = rx, center = centre, alpha = alpha, blendMode = blend)
        }
    }

    private const val DARK_COVERS_AT = 110f
    private const val JACK_WIDTH = 51f
    private const val JACK_HEIGHT = 46f

    private val BackLight = arrayOf(0f to Color(0xF2E50706), 0.42f to Color(0x66E50706), 0.72f to Color(0x00E50706), 1f to Color(0x00E50706))
    private val HotCore = arrayOf(0f to Color(0xB3FF965A), 0.4f to Color(0x73FF4628), 0.62f to Color(0x33FF2814), 0.8f to Color(0x00FF2814), 1f to Color(0x00FF2814))
    private val Lens = Color(0xFFFF2A20)
    private val LensGlow = Color(0xD9FF2A20)
    private val GrainJitter = listOf(Offset(0f, 0f), Offset(-7f, 4f), Offset(5f, -6f), Offset(-4f, -3f))
}

private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

private val Snap = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
private val Out = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val In = CubicBezierEasing(0.42f, 0f, 1f, 1f)
