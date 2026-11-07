package cx.aswin.boxlore.feature.info.branded.serial

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel

/**
 * One more on the stack: the button goes dark and a small stack of the logo's cards waits in its
 * middle; Serial's own card, the S, drops onto it with a bump and one haptic, the stack steps
 * down-left to make room (its bottom card slipping away), and a red glow comes up behind the new top
 * card. Then the button clears into Subscribed. No words of its own. Timings follow the approved
 * design board, in milliseconds from the tap.
 */
internal object SerialOneMore : BrandedSubscribeMoment {
    override val durationMillis = 1_240
    override val revealDelayMillis = 1_080

    /** The S card lands and the stack steps down. */
    const val LAND_AT = 470f

    override fun nudge(elapsedMillis: Float): BrandedButtonNudge = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, LAND_AT)
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    val t = elapsedMillis()
                    // Once the dark covers the button it is already Subscribed underneath, so it clears straight to it.
                    drawRect(if (t < DARK_COVERS_AT) style.subscribeContainer else style.subscribedContainer)
                    val dark = Out.transform(progress(t, 20f, 90f)) * (1f - In.transform(progress(t, 960f, 150f)))
                    if (dark <= 0f) return@drawBehind
                    drawRect(SerialColors.CardBlack, alpha = dark)
                    val shown = Out.transform(progress(t, 40f, 80f)) * (1f - In.transform(progress(t, 940f, 150f)))
                    if (shown > 0f) drawStack(t, shown)
                },
            )
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer { alpha = 1f - In.transform(progress(elapsedMillis(), 0f, 70f)) },
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

    /** The small stack in the middle of the button, 26 by 32 cards, the S card dropping onto it. */
    private fun DrawScope.drawStack(t: Float, shown: Float) {
        val unit = 1.dp.toPx()
        val card = Size(CARD_WIDTH * unit, CARD_HEIGHT * unit)
        val corner = 5f * unit
        val rim = 1.5f * unit
        val origin = Offset(size.width / 2f - 8f * unit, 4f * unit)
        val step = Step.transform(progress(t, LAND_AT, 220f))
        val sink = In.transform(progress(t, LAND_AT, 220f))
        // The bottom card slips away down-left as the others step down to take its place.
        for (k in 3 downTo 0) {
            val from = -3.5f * k
            val (to, alpha) = if (k == 3) -16f to 1f - sink else -3.5f * (k + 1) to 1f
            val move = if (k == 3) from + (to - from) * sink else from + (to - from) * step
            translate(origin.x + move * unit, origin.y - move * unit) { drawPlainCard(card, corner, rim, alpha * shown) }
        }
        val glow = Glow.at(progress(t, 560f, 360f))
        if (glow > 0f) {
            val centre = origin + Offset(card.width / 2f, card.height / 2f)
            scale(scaleX = 1f, scaleY = GLOW_RY / GLOW_RX, pivot = centre) {
                drawCircle(Brush.radialGradient(*GlowStops, center = centre, radius = GLOW_RX * unit), radius = GLOW_RX * unit, center = centre, alpha = glow * shown)
            }
        }
        val p = progress(t, 160f, 380f)
        if (p <= 0f) return
        val alpha = DropFade.at(p) * shown
        if (alpha <= 0f) return
        translate(origin.x + DropX.at(p) * unit, origin.y + DropY.at(p) * unit) {
            rotate(DropTurn.at(p), pivot = Offset(card.width / 2f, card.height / 2f)) {
                if (alpha >= 1f) {
                    drawSerialCard(card, corner, rim)
                } else {
                    // Fading in: draw it into its own layer so the S and its card fade as one.
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, card), Paint().apply { this.alpha = alpha })
                    drawSerialCard(card, corner, rim)
                    drawContext.canvas.restore()
                }
            }
        }
    }

    private const val DARK_COVERS_AT = 110f
    private const val CARD_WIDTH = 26f
    private const val CARD_HEIGHT = 32f
    private const val GLOW_RX = 59f
    private const val GLOW_RY = 36f

    private val Snap = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
    private val Step = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)
    private val DropFade = SerialKeys(Snap, 0f to 0f, 0.25f to 1f, 1f to 1f)
    private val DropX = SerialKeys(Snap, 0f to 10f, 0.82f to 0f, 1f to 0f)
    private val DropY = SerialKeys(Snap, 0f to -60f, 0.82f to 2f, 1f to 0f)
    private val DropTurn = SerialKeys(Snap, 0f to 10f, 0.82f to -1f, 1f to 0f)
    private val Glow = SerialKeys(Out, 0f to 0f, 0.35f to 1f, 1f to 0.55f)
    private val GlowStops = arrayOf(0f to Color(0xD9EB1C24), 0.45f to Color(0x4DEB1C24), 0.72f to Color(0x00EB1C24), 1f to Color(0x00EB1C24))
}

private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

private val Out = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val In = CubicBezierEasing(0.42f, 0f, 1f, 1f)
