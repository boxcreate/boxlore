package cx.aswin.boxlore.feature.info.branded.waveform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel
import kotlinx.coroutines.delay

/**
 * The logo intro, like the start of an MKBHD video. The header's logo cuts to a flat line, its bars
 * burst out from the centre, the red block slices in, the whole logo glitches red and cyan, and it
 * beats once. On the button, a small waveform does the same and Subscribed glitches in.
 */
internal object WaveformLogoIntro : BrandedSubscribeMoment {
    override val durationMillis = WaveformMotion.INTRO_MILLIS
    override val revealDelayMillis = WaveformMotion.REVEAL_MILLIS

    override fun nudge(elapsedMillis: Float) = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(Unit) {
            delay(WaveformMotion.HAPTIC_MILLIS.toLong())
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        }
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(if (elapsedMillis() < WaveformButtonMotion.BUTTON_SWAP_MILLIS) style.subscribeContainer else style.subscribedContainer)
                },
            )
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer {
                    val out = WaveformButtonMotion.subscribeLabelOut(elapsedMillis())
                    alpha = 1f - out
                    translationY = (10f * out).dp.toPx()
                },
            )
            ButtonWave(elapsedMillis, style.subscribeContent)
            GlitchedLabel(elapsedMillis, labels, style.subscribedContent)
        }
    }
}

/** Nine bars in the button, bursting out from the centre like the logo. */
@Composable
private fun ButtonWave(elapsedMillis: () -> Float, color: Color) {
    Spacer(
        Modifier
            .size(width = 56.dp, height = 36.dp)
            .graphicsLayer {
                val t = elapsedMillis()
                val (x, y) = WaveformButtonMotion.buttonWaveShift(t)
                alpha = WaveformButtonMotion.buttonWaveAlpha(t)
                translationX = x.dp.toPx()
                translationY = y.dp.toPx()
            }
            .drawBehind {
                val t = elapsedMillis()
                val unit = 1.dp.toPx()
                ButtonWaveBars.forEachIndexed { i, (height, delay) ->
                    val h = height * WaveformButtonMotion.buttonWaveBarScale(t, delay) * unit
                    drawRect(color, Offset((3f + 6f * i) * unit, 18f * unit - h / 2f), Size(3f * unit, h))
                }
            },
    )
}

/** Each bar's height in dp and when it bursts out. */
private val ButtonWaveBars = listOf(6f to 240, 12f to 210, 18f to 180, 26f to 150, 32f to 120, 26f to 150, 18f to 180, 12f to 210, 6f to 240)

/** Subscribed, landing with red and cyan copies that split, cross and close up. */
@Composable
private fun GlitchedLabel(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, color: Color) {
    Box(
        Modifier.graphicsLayer {
            val frame = WaveformButtonMotion.subscribedLabel(elapsedMillis())
            alpha = frame.alpha
            translationX = frame.shift.dp.toPx()
        },
        contentAlignment = Alignment.Center,
    ) {
        SplitCopy(elapsedMillis, labels, WaveformColors.GlitchRed, sign = 1f)
        SplitCopy(elapsedMillis, labels, WaveformColors.GlitchCyan, sign = -1f)
        SubscriptionLabel(text = labels.subscribed, compact = labels.compact, color = color)
    }
}

@Composable
private fun SplitCopy(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, color: Color, sign: Float) {
    SubscriptionLabel(
        text = labels.subscribed,
        compact = labels.compact,
        color = color,
        modifier = Modifier.graphicsLayer {
            val frame = WaveformButtonMotion.subscribedLabel(elapsedMillis())
            alpha = frame.splitAlpha
            translationX = (sign * frame.split).dp.toPx()
        },
    )
}
