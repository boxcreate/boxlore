package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel

/** The sun's warm core and soft glow, from its centre out to the glow's edge. */
private val Sun = arrayOf(
    0f to Color(0xFFFFFDF2),
    0.26f to Color(0xFFFFF1A8),
    0.42f to Color(0xFFFFD862),
    0.44f to Color(0x80FFE896),
    1f to Color(0x00FFE896),
)

/**
 * Daybreak: "Subscribe" sinks away as the show's dawn rises up through the button from its bottom
 * edge, a small sun clears the horizon with one light tap, then daylight washes the sky into the
 * Subscribed colour and Subscribed comes up. About 1.2 seconds.
 */
internal object DailyDaybreak : BrandedSubscribeMoment {
    override val durationMillis = DailyDawn.DURATION_MILLIS
    override val revealDelayMillis = DailyDawn.REVEAL_MILLIS

    override fun nudge(elapsedMillis: Float) = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, DailyDawn.SUN_CLEAR_MILLIS)
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawDaybreak(elapsedMillis(), style) }
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer {
                    val (labelAlpha, drop) = DailyDawn.subscribeLabel(elapsedMillis())
                    alpha = labelAlpha
                    translationY = 5.dp.toPx() * drop
                },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer { alpha = DailyDawn.subscribedLabel(elapsedMillis()) },
            )
        }
    }
}

/** The night button, the dawn rising over it, the sun, then the daylight; once it is full day, only the daylight. */
private fun DrawScope.drawDaybreak(millis: Float, style: BrandedToolbarStyle) {
    val daylight = DailyDawn.daylight(millis)
    if (daylight < 1f) {
        drawRect(style.subscribeContainer)
        val skyTop = size.height * (1f - DailyDawn.sky(millis))
        if (skyTop < size.height) {
            // The cover's sky, deep blue at the top to yellow at the horizon, over the button's full height.
            drawRect(Brush.verticalGradient(*DailyColors.Dawn.toTypedArray()), topLeft = Offset(0f, skyTop), size = Size(size.width, size.height - skyTop))
        }
        val unit = 1.dp.toPx()
        val radius = DailyDawn.SUN_DIAMETER / 2f * unit
        val centre = Offset(size.width * DailyDawn.SUN_ACROSS + radius, (DailyDawn.sunTop(millis) * unit) + radius)
        val sunAlpha = DailyDawn.sunAlpha(millis)
        if (sunAlpha > 0f && centre.y - DailyDawn.SUN_GLOW_RADIUS * unit < size.height) {
            drawCircle(Brush.radialGradient(*Sun, center = centre, radius = DailyDawn.SUN_GLOW_RADIUS * unit), radius = DailyDawn.SUN_GLOW_RADIUS * unit, center = centre, alpha = sunAlpha)
        }
    }
    if (daylight > 0f) drawRect(style.subscribedContainer, alpha = daylight)
}
