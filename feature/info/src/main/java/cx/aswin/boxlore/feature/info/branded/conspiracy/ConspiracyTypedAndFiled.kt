package cx.aswin.boxlore.feature.info.branded.conspiracy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel
import java.util.Locale

/**
 * Typed and filed: a typewriter strikes FILED. into the Subscribe button letter by letter, the
 * carriage returns, and the button wipes in from the right to Subscribed while alerts and
 * auto-download slide in. Timings follow the approved design board, in milliseconds from the tap.
 */
internal object ConspiracyTypedAndFiled : BrandedSubscribeMoment {
    override val durationMillis = 1_170
    override val revealDelayMillis = 860

    override fun nudge(elapsedMillis: Float): BrandedButtonNudge {
        // Each strike jolts the button 1.5dp down and back over 70ms.
        for (i in 0 until STRIKES) {
            val p = (elapsedMillis - strikeAt(i)) / 70f
            if (p in 0f..1f) {
                val amount = if (p < 0.5f) Ease.transform(p / 0.5f) else 1f - Ease.transform((p - 0.5f) / 0.5f)
                return BrandedButtonNudge((1.5f * amount).dp, 1f)
            }
        }
        return BrandedButtonNudge(0.dp, 1f)
    }

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, strikeAt(0))
        val fonts = rememberConspiracyFonts()
        val typed = stringResource(R.string.conspiracy_filed).uppercase(Locale.getDefault())
        // The whole moment is clipped to the button, like paper behind the typewriter's window.
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val t = elapsedMillis()
                    if (t < 990f) drawRect(style.subscribeContainer)
                    val wipe = WipeEasing.transform(progress(t, 680f, 300f))
                    if (wipe > 0f) {
                        // Wipes in from the right; its leading edge is rounded like the joined end.
                        val width = size.width * wipe
                        val radius = minOf(8.dp.toPx(), width / 2f)
                        drawRoundRect(
                            style.subscribedContainer,
                            topLeft = Offset(size.width - width, 0f),
                            size = Size(width, size.height),
                            cornerRadius = CornerRadius(radius),
                        )
                    }
                },
            )
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer {
                    val out = EaseIn.transform(progress(elapsedMillis(), 0f, 140f))
                    alpha = 1f - out
                    translationY = (10f * out).dp.toPx()
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    // The carriage returns: the typed line slides away left and fades.
                    val back = CarriageEasing.transform(progress(elapsedMillis(), 640f, 280f))
                    translationX = (-190f * back).dp.toPx()
                    alpha = 1f - back
                },
            ) {
                typed.forEachIndexed { i, letter ->
                    Text(
                        letter.toString(),
                        style = brandedTextStyle(fonts.typewriter, 21.sp, 21.sp, letterSpacing = 2.sp),
                        color = style.subscribeContent,
                        modifier = Modifier.graphicsLayer {
                            val strike = strikeAt(i.coerceAtMost(STRIKES - 1))
                            val p = progress(elapsedMillis(), strike, 90f)
                            alpha = when {
                                elapsedMillis() < strike -> 0f
                                p < 0.55f -> EaseOut.transform(p / 0.55f)
                                else -> 1f
                            }
                            val (dy, s) = strikeShape(p)
                            translationY = dy.dp.toPx()
                            scaleX = s
                            scaleY = s
                        },
                    )
                }
            }
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val t = elapsedMillis()
                    val into = EaseOut.transform(progress(t, 900f, 260f))
                    alpha = if (t < 900f) 0f else into
                    translationX = (26f * (1f - into)).dp.toPx()
                },
            )
        }
    }

    private const val STRIKES = 6
}

/** When the [i]th letter strikes: from 150ms, every 75ms. */
private fun strikeAt(i: Int): Float = 150f + 75f * i

/** A key's strike: it lands big and high, dips just past the line, then sits on it. */
private fun strikeShape(p: Float): Pair<Float, Float> = if (p < 0.55f) {
    val e = EaseOut.transform(p / 0.55f)
    lerp(-4f, 1f, e) to lerp(1.35f, 0.95f, e)
} else {
    val e = EaseOut.transform((p - 0.55f) / 0.45f)
    lerp(1f, 0f, e) to lerp(0.95f, 1f, e)
}

private val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val WipeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val CarriageEasing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.4f)

private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)
