package cx.aswin.boxlore.feature.info.branded.jre

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel

/**
 * Third eye: the Subscribe button goes dark, an eye like the one on the show's badge opens in it,
 * its dotted rays spread across the button, it pulses once with a buzz, and it closes into
 * Subscribed. The badge's own eye lights up on the same clock. About 1.4 seconds.
 */
internal object JreThirdEye : BrandedSubscribeMoment {
    override val durationMillis = JreEye.DURATION_MILLIS
    override val revealDelayMillis = JreEye.REVEAL_MILLIS

    override fun nudge(elapsedMillis: Float) = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, JreEye.PULSE_MILLIS)
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val millis = elapsedMillis()
                drawRect(if (JreEye.baseDark(millis)) Dark else style.subscribeContainer)
                drawDarkness(JreEye.darkness(millis))
                drawButtonEye(center, millis)
                drawRect(style.subscribedContainer, alpha = JreEye.settle(millis))
            }
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer {
                    val (labelAlpha, labelScale) = JreEye.subscribeLabel(elapsedMillis())
                    alpha = labelAlpha
                    scaleX = labelScale
                    scaleY = labelScale
                },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val (landAlpha, landScale) = JreEye.subscribedLabel(elapsedMillis())
                    alpha = landAlpha
                    scaleX = landScale
                    scaleY = landScale
                },
            )
        }
    }
}

private val Dark = Color(0xFF0C0605)
private val DarkHeart = Color(0xFF2A1206)
private val Ray = Color(0xFFF87000)
private val BadgeRay = Color(0xFFFFB45E)

/** A dark room with a faint warm heart, as an oval 60% of the button wide and 120% tall. */
private fun DrawScope.drawDarkness(alpha: Float) {
    if (alpha <= 0f) return
    val rx = size.width * 0.6f
    scale(scaleX = 1f, scaleY = size.height * 1.2f / rx, pivot = center) {
        drawRect(
            Brush.radialGradient(0f to DarkHeart, 0.7f to Dark, 1f to Dark, center = center, radius = rx),
            topLeft = Offset(0f, center.y - rx),
            size = androidx.compose.ui.geometry.Size(size.width, rx * 2),
            alpha = alpha,
        )
    }
}

/** The button's eye at [eye]: its glow, its eight dotted rays and the eye itself, beating together. */
private fun DrawScope.drawButtonEye(eye: Offset, millis: Float) {
    val unit = 1.dp.toPx()
    scale(JreEye.pulse(millis), pivot = eye) {
        val glow = JreEye.glow(millis)
        if (glow > 0f) {
            scale(scaleX = 1f, scaleY = 30f / 70f, pivot = eye) {
                drawCircle(
                    Brush.radialGradient(0f to Color(0xBFFF9A3C), 1f to Color(0x00FF7A1A), center = eye, radius = 70 * unit),
                    radius = 70 * unit,
                    center = eye,
                    alpha = glow,
                )
            }
        }
        drawDots(JreEye.ButtonDots, eye, millis, JreEye.BUTTON_FIRST_DOT, Ray, unit)
        JreEye.lid(millis)?.let { open -> drawEye(eye, open, unit) }
    }
}

/** The badge's own eye lighting up: a soft warm glow and its rays spreading past the badge's dots. */
internal fun DrawScope.drawBadgeEye(eye: Offset, millis: Float) {
    val unit = 1.dp.toPx()
    scale(JreEye.pulse(millis), pivot = eye) {
        val glow = JreEye.glow(millis)
        if (glow > 0f) {
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xF2FFD9A8),
                    0.35f to Color(0x8CFF9A3C),
                    1f to Color(0x00FF7A1A),
                    center = eye,
                    radius = 24 * unit,
                ),
                radius = 24 * unit,
                center = eye,
                alpha = glow,
            )
        }
        drawDots(JreEye.BadgeDots, eye, millis, JreEye.BADGE_FIRST_DOT, BadgeRay, unit)
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawDots(dots: List<JreEye.Dot>, eye: Offset, millis: Float, firstMillis: Float, color: Color, unit: Float) {
    dots.forEach { dot ->
        val (out, scale, alpha) = JreEye.dot(millis, dot.index, firstMillis)
        if (alpha <= 0f) return@forEach
        drawCircle(color, radius = dot.radius * scale * unit, center = eye + dot.target * (out * unit), alpha = alpha)
    }
}

/** The badge's eye: an almond with an orange rim, a blue-grey iris, the pupil and a glint; [open] is its height. */
private fun DrawScope.drawEye(eye: Offset, open: Float, unit: Float) {
    scale(scaleX = 1f, scaleY = open, pivot = eye) {
        val almond = Path().apply {
            moveTo(eye.x - 15 * unit, eye.y)
            quadraticTo(eye.x, eye.y - 11.5f * unit, eye.x + 15 * unit, eye.y)
            quadraticTo(eye.x, eye.y + 11.5f * unit, eye.x - 15 * unit, eye.y)
            close()
        }
        drawPath(almond, Color(0xFFFFF4E6))
        drawPath(almond, Ray, style = Stroke(width = 2 * unit))
        drawCircle(Color(0xFF5D84A3), radius = 5.4f * unit, center = eye)
        drawCircle(Color(0xFF1A0B02), radius = 5.4f * unit, center = eye, style = Stroke(width = 1.2f * unit))
        drawCircle(Color(0xFF0B0605), radius = 2.6f * unit, center = eye)
        drawCircle(Color.White, radius = 1f * unit, center = eye + Offset(-1.6f * unit, -1.7f * unit))
    }
}
