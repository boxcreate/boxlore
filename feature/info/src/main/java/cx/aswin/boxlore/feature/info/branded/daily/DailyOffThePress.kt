package cx.aswin.boxlore.feature.info.branded.daily

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel

/** Brushed steel, lit across its middle. */
private val Steel = arrayOf(0f to Color(0xFF8D8D8D), 0.38f to Color(0xFFF2F2F2), 0.62f to Color(0xFFC9C9C9), 1f to Color(0xFF7A7A7A))
private val RollerShadow = Color(0x59000000)

/**
 * Off the press: a steel roller runs across the Subscribe button from left to right, with one light
 * tap as it bites, printing a fresh sheet in the Subscribed colours behind it with "Subscribed" on
 * it and a glint of the show's dawn at its edge, then lifts off. About 1.1 seconds.
 */
internal object DailyOffThePress : BrandedSubscribeMoment {
    override val durationMillis = DailyPress.DURATION_MILLIS
    override val revealDelayMillis = DailyPress.REVEAL_MILLIS

    override fun nudge(elapsedMillis: Float) = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, DailyPress.TAP_MILLIS)
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val edge = size.width * DailyPress.printed(elapsedMillis())
                drawRect(style.subscribedContainer, size = Size(edge, size.height))
                drawRect(style.subscribeContainer, topLeft = Offset(edge, 0f), size = Size(size.width - edge, size.height))
            }
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer {
                    val (labelAlpha, lift) = DailyPress.subscribeLabel(elapsedMillis())
                    alpha = labelAlpha
                    translationY = -6.dp.toPx() * lift
                },
            )
            // "Subscribed" is printed only where the sheet has come through.
            Box(
                Modifier.matchParentSize().drawWithContent {
                    clipRect(right = size.width * DailyPress.printed(elapsedMillis())) { this@drawWithContent.drawContent() }
                },
                contentAlignment = Alignment.Center,
            ) {
                SubscriptionLabel(
                    text = labels.subscribed,
                    compact = labels.compact,
                    color = style.subscribedContent,
                    modifier = Modifier.graphicsLayer { alpha = DailyPress.printedInk(elapsedMillis()) },
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val millis = elapsedMillis()
                DailyPress.roller(millis)?.let { (alpha, height) -> drawRoller(size.width * DailyPress.printed(millis), alpha, height) }
            }
        }
    }
}

/** The roller, 12dp of steel centred on the printing edge, with the dawn's glint on the fresh sheet beside it. */
private fun DrawScope.drawRoller(edge: Float, alpha: Float, height: Float) {
    val unit = 1.dp.toPx()
    val left = edge - 6 * unit
    scale(scaleX = 1f, scaleY = height, pivot = center) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.Black.copy(alpha = alpha).toArgb()
            // CSS's 0 0 6px blur is a sigma of 3dp.
            setShadowLayer(((3 * unit - 0.5f) / 0.57735f).coerceAtLeast(0.1f), 0f, 0f, RollerShadow.copy(alpha = RollerShadow.alpha * alpha).toArgb())
        }
        drawIntoCanvas { it.nativeCanvas.drawRect(left, 0f, left + 12 * unit, size.height, shadow) }
        drawRect(
            Brush.horizontalGradient(*Steel, startX = left, endX = left + 12 * unit),
            topLeft = Offset(left, 0f),
            size = Size(12 * unit, size.height),
            alpha = alpha,
        )
        drawRect(
            Brush.verticalGradient(*DailyColors.DawnAcross.toTypedArray()),
            topLeft = Offset(left - 3 * unit, 0f),
            size = Size(3 * unit, size.height),
            alpha = alpha,
        )
    }
}
