package cx.aswin.boxlore.feature.info.branded.vergecast

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel
import kotlinx.coroutines.delay

/**
 * The sticker slap: The Vergecast's wordmark sticker flies in and slaps onto Subscribe, impact
 * lines burst out, the button wipes to Subscribed and alerts and auto-download slide in. Timings
 * follow the approved design board, in milliseconds from the tap.
 */
internal object VergecastStickerSlap : BrandedSubscribeMoment {
    override val durationMillis = 1_220
    override val revealDelayMillis = 880

    override fun nudge(elapsedMillis: Float): BrandedButtonNudge {
        // A 170ms thud from 300ms: down 2dp and in to 98.5% at 40%, then back.
        val p = (elapsedMillis - 300f) / 170f
        if (p <= 0f || p >= 1f) return BrandedButtonNudge(0.dp, 1f)
        val amount = if (p < 0.4f) EaseOut.transform(p / 0.4f) else 1f - EaseOut.transform((p - 0.4f) / 0.6f)
        return BrandedButtonNudge((2f * amount).dp, 1f - 0.015f * amount)
    }

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(Unit) {
            delay(IMPACT_MILLIS)
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        }
        Box(modifier, contentAlignment = Alignment.Center) {
            // The Subscribe colour, then the Subscribed colour wiping in from the left.
            Box(
                Modifier.fillMaxSize().clip(shape).drawBehind {
                    val t = elapsedMillis()
                    if (t < 1_040f) drawRect(style.subscribeContainer)
                    val wipe = WipeEasing.transform(progress(t, 740f, 300f))
                    if (wipe > 0f) {
                        // The wipe's leading edge is rounded like the button's joined end.
                        val width = size.width * wipe
                        val radius = minOf(8.dp.toPx(), width / 2f)
                        drawRoundRect(style.subscribedContainer, size = Size(width, size.height), cornerRadius = CornerRadius(radius))
                    }
                },
            )
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer {
                    val out = EaseIn.transform(progress(elapsedMillis(), 0f, 160f))
                    alpha = 1f - out
                    translationY = (10f * out).dp.toPx()
                },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val t = elapsedMillis()
                    val into = EaseOut.transform(progress(t, 970f, 240f))
                    alpha = if (t < 970f) 0f else into
                    translationY = (10f * (1f - into)).dp.toPx()
                },
            )
            // Impact lines either side of the sticker, in the Subscribe label's colour.
            Box(Modifier.fillMaxSize().drawBehind { drawBurst(elapsedMillis(), style.subscribeContent) })
            Image(
                painter = painterResource(R.drawable.vergecast_subscribe_sticker),
                contentDescription = null,
                modifier = Modifier
                    .requiredSize(width = 110.dp, height = 64.dp)
                    // The baked shadow leaves the sticker's centre 2dp above the image's.
                    .graphicsLayer {
                        val sticker = stickerAt(elapsedMillis())
                        transformOrigin = TransformOrigin(0.5f, 28f / 64f)
                        alpha = sticker.alpha
                        translationX = sticker.x.dp.toPx()
                        translationY = (sticker.y + 2f).dp.toPx()
                        rotationZ = sticker.rotation
                        scaleX = sticker.scale
                        scaleY = sticker.scale
                    },
            )
        }
    }

    private const val IMPACT_MILLIS = 330L
}

private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val WipeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val ThrowEasing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.4f)
private val LandEasing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
private val LiftEasing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.6f)

private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

internal data class StickerFrame(val x: Float, val y: Float, val rotation: Float, val scale: Float, val alpha: Float)

private data class Key(val at: Float, val x: Float, val y: Float, val rotation: Float, val scale: Float, val easing: Easing)

// The board's keyframes over 1,000ms; each easing runs to the next keyframe.
private val StickerKeys = listOf(
    Key(0f, 80f, -96f, 28f, 1.9f, ThrowEasing),
    Key(0.33f, 0f, 0f, -9f, 0.88f, LandEasing),
    Key(0.41f, 0f, 0f, -6f, 1.07f, LinearEasing),
    Key(0.5f, 0f, 0f, -6f, 1f, LinearEasing),
    Key(0.76f, 0f, 0f, -6f, 1f, LiftEasing),
    Key(1f, 0f, -4f, -6f, 0.55f, LinearEasing),
)

/** The sticker's position (dp from the button's centre), lean, size and opacity [t] ms after the tap. */
internal fun stickerAt(t: Float): StickerFrame {
    val f = (t / 1_000f).coerceIn(0f, 1f)
    val next = StickerKeys.indexOfFirst { it.at >= f }.coerceAtLeast(1)
    val from = StickerKeys[next - 1]
    val to = StickerKeys[next]
    val e = from.easing.transform(((f - from.at) / (to.at - from.at)).coerceIn(0f, 1f))
    val alpha = when {
        f < 0.08f -> ThrowEasing.transform(f / 0.08f)
        f <= 0.76f -> 1f
        else -> 1f - LiftEasing.transform((f - 0.76f) / 0.24f)
    }
    return StickerFrame(
        x = lerp(from.x, to.x, e),
        y = lerp(from.y, to.y, e),
        rotation = lerp(from.rotation, to.rotation, e),
        scale = lerp(from.scale, to.scale, e),
        alpha = alpha,
    )
}

/** Angle, start and end distance (dp, along the rotated line) of each impact line. */
private val BurstLines = listOf(
    Triple(-76f, -56f, -78f),
    Triple(-90f, -60f, -86f),
    Triple(-104f, -56f, -78f),
    Triple(76f, -56f, -78f),
    Triple(90f, -60f, -86f),
    Triple(104f, -56f, -78f),
)

/** Six 3.5 by 16dp lines from 330ms for 340ms: they grow outwards and fade over the last 45%. */
private fun DrawScope.drawBurst(t: Float, color: Color) {
    if (t < 330f) return
    val p = progress(t, 330f, 340f)
    val e = EaseOut.transform(p)
    val alpha = if (p <= 0.55f) 1f else 1f - EaseOut.transform((p - 0.55f) / 0.45f)
    if (alpha <= 0f) return
    val width = 3.5.dp.toPx()
    val length = 16.dp.toPx() * lerp(0.35f, 1f, e)
    for ((angle, start, end) in BurstLines) {
        val distance = lerp(start, end, e).dp.toPx()
        rotate(angle, pivot = center) {
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - width / 2f, center.y + distance - length / 2f),
                size = Size(width, length),
                cornerRadius = CornerRadius(2.dp.toPx()),
                alpha = alpha,
            )
        }
    }
}
