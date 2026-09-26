package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawTransformingPlatform(
    pagePosition: Float,
    rotation: Float,
    pulse: Float,
    tiers: List<SupportTierCardData>,
) {
    val leftPage = pagePosition.toInt().coerceIn(0, 4)
    val rightPage = (leftPage + 1).coerceAtMost(4)
    val fraction = pagePosition - leftPage

    val centerX = size.width / 2f
    val centerY = size.height * 0.77f

    if (fraction < 0.02f) {
        drawTierPlatform(leftPage, 1f, centerX, centerY, rotation, pulse, tiers[leftPage].auraColor)
    } else if (fraction > 0.98f) {
        drawTierPlatform(rightPage, 1f, centerX, centerY, rotation, pulse, tiers[rightPage].auraColor)
    } else {
        drawTierPlatform(leftPage, 1f - fraction, centerX, centerY, rotation, pulse, tiers[leftPage].auraColor)
        drawTierPlatform(rightPage, fraction, centerX, centerY, rotation, pulse, tiers[rightPage].auraColor)
    }
}

@Suppress("LongMethod")
internal fun DrawScope.drawTierPlatform(
    tierIndex: Int,
    alpha: Float,
    centerX: Float,
    centerY: Float,
    rotation: Float,
    pulse: Float,
    color: Color,
) {
    if (alpha <= 0.01f) return
    val baseColor = color.copy(alpha = color.alpha * alpha)

    when (tierIndex) {
        0 -> {
            val baseWidth = 130.dp.toPx() * pulse
            val outerWidth = baseWidth * 1.30f
            val outerHeight = 40.dp.toPx()
            val innerWidth = baseWidth * 0.75f
            val innerHeight = 20.dp.toPx()

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.28f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = baseWidth * 0.65f,
                ),
                topLeft = Offset(centerX - baseWidth * 0.65f, centerY - 18.dp.toPx()),
                size = Size(baseWidth * 1.30f, 36.dp.toPx()),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.55f * alpha),
                topLeft = Offset(centerX - outerWidth / 2f, centerY - outerHeight / 2f),
                size = Size(outerWidth, outerHeight),
                style = Stroke(
                    width = 1.3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), rotation * 1.2f),
                ),
            )

            for (k in 0..1) {
                val angle = (rotation * 1.2 + k * 180.0) * (PI / 180.0)
                val sx = (centerX + (outerWidth / 2f) * cos(angle)).toFloat()
                val sy = (centerY + (outerHeight / 2f) * sin(angle)).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.4.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 5.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.dp.toPx()))
            }

            drawOval(
                color = baseColor.copy(alpha = 0.85f * alpha),
                topLeft = Offset(centerX - innerWidth / 2f, centerY - innerHeight / 2f),
                size = Size(innerWidth, innerHeight),
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
        1 -> {
            val baseWidth = 150.dp.toPx() * pulse
            val outerWidth = baseWidth * 1.40f
            val outerHeight = 46.dp.toPx()
            val midWidth = baseWidth * 1.08f
            val midHeight = 32.dp.toPx()
            val innerWidth = baseWidth * 0.70f
            val innerHeight = 20.dp.toPx()

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.32f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = baseWidth * 0.70f,
                ),
                topLeft = Offset(centerX - baseWidth * 0.70f, centerY - 20.dp.toPx()),
                size = Size(baseWidth * 1.40f, 40.dp.toPx()),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.50f * alpha),
                topLeft = Offset(centerX - outerWidth / 2f, centerY - outerHeight / 2f),
                size = Size(outerWidth, outerHeight),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f), rotation * 1.4f),
                ),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.65f * alpha),
                topLeft = Offset(centerX - midWidth / 2f, centerY - midHeight / 2f),
                size = Size(midWidth, midHeight),
                style = Stroke(
                    width = 1.1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), -rotation * 1.8f),
                ),
            )

            for (k in 0..2) {
                val angle = (rotation * 1.4 + k * 120.0) * (PI / 180.0)
                val sx = (centerX + (outerWidth / 2f) * cos(angle)).toFloat()
                val sy = (centerY + (outerHeight / 2f) * sin(angle)).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.6.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 5.2.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.2.dp.toPx()))
            }

            drawOval(
                color = baseColor.copy(alpha = 0.88f * alpha),
                topLeft = Offset(centerX - innerWidth / 2f, centerY - innerHeight / 2f),
                size = Size(innerWidth, innerHeight),
                style = Stroke(width = 1.3.dp.toPx()),
            )
        }
        2 -> {
            val baseWidth = 170.dp.toPx() * pulse
            val outerWidth = baseWidth * 1.48f
            val outerHeight = 52.dp.toPx()
            val midWidth = baseWidth * 1.18f
            val midHeight = 38.dp.toPx()
            val innerWidth = baseWidth * 0.72f
            val innerHeight = 22.dp.toPx()

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.35f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = baseWidth * 0.75f,
                ),
                topLeft = Offset(centerX - baseWidth * 0.75f, centerY - 22.dp.toPx()),
                size = Size(baseWidth * 1.50f, 44.dp.toPx()),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.50f * alpha),
                topLeft = Offset(centerX - outerWidth / 2f, centerY - outerHeight / 2f),
                size = Size(outerWidth, outerHeight),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), rotation * 1.1f),
                ),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.65f * alpha),
                topLeft = Offset(centerX - midWidth / 2f, centerY - midHeight / 2f),
                size = Size(midWidth, midHeight),
                style = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), -rotation * 2.2f),
                ),
            )

            for (k in 0..3) {
                val angle = (rotation * 1.1 + k * 90.0) * (PI / 180.0)
                val sx = (centerX + (outerWidth / 2f) * cos(angle)).toFloat()
                val sy = (centerY + (outerHeight / 2f) * sin(angle)).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.8.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 5.5.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.2.dp.toPx()))
            }

            drawOval(
                color = baseColor.copy(alpha = 0.90f * alpha),
                topLeft = Offset(centerX - innerWidth / 2f, centerY - innerHeight / 2f),
                size = Size(innerWidth, innerHeight),
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
        3 -> {
            val baseWidth = 190.dp.toPx() * pulse
            val outerWidth = baseWidth * 1.52f
            val outerHeight = 56.dp.toPx()
            val midWidth = baseWidth * 1.20f
            val midHeight = 40.dp.toPx()
            val coreWidth = baseWidth * 0.82f
            val coreHeight = 26.dp.toPx()

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.36f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = baseWidth * 0.78f,
                ),
                topLeft = Offset(centerX - baseWidth * 0.78f, centerY - 24.dp.toPx()),
                size = Size(baseWidth * 1.56f, 48.dp.toPx()),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.52f * alpha),
                topLeft = Offset(centerX - outerWidth / 2f, centerY - outerHeight / 2f),
                size = Size(outerWidth, outerHeight),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 8f), rotation * 1.5f),
                ),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.70f * alpha),
                topLeft = Offset(centerX - midWidth / 2f, centerY - midHeight / 2f),
                size = Size(midWidth, midHeight),
                style = Stroke(
                    width = 1.3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), -rotation * 2.4f),
                ),
            )

            for (k in 0..3) {
                val angle = (rotation * 1.5 + k * 90.0) * (PI / 180.0)
                val sx = (centerX + (outerWidth / 2f) * cos(angle)).toFloat()
                val sy = (centerY + (outerHeight / 2f) * sin(angle)).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 3.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 6.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.3.dp.toPx()))

                val trailAngle = (rotation * 1.5 + k * 90.0 - 15.0) * (PI / 180.0)
                val tx = (centerX + (outerWidth / 2f) * cos(trailAngle)).toFloat()
                val ty = (centerY + (outerHeight / 2f) * sin(trailAngle)).toFloat()
                drawCircle(color = baseColor.copy(alpha = 0.6f * alpha), radius = 1.8.dp.toPx(), center = Offset(tx, ty))
            }

            drawOval(
                color = baseColor.copy(alpha = 0.92f * alpha),
                topLeft = Offset(centerX - coreWidth / 2f, centerY - coreHeight / 2f),
                size = Size(coreWidth, coreHeight),
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
        else -> {
            val baseWidth = 210.dp.toPx() * pulse
            val outerWidth = baseWidth * 1.58f
            val outerHeight = 62.dp.toPx()
            val midWidth = baseWidth * 1.25f
            val midHeight = 44.dp.toPx()
            val coreWidth = baseWidth * 0.85f
            val coreHeight = 28.dp.toPx()

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f * alpha),
                        baseColor.copy(alpha = 0.40f * alpha),
                        Color.Transparent,
                    ),
                    center = Offset(centerX, centerY),
                    radius = baseWidth * 0.82f,
                ),
                topLeft = Offset(centerX - baseWidth * 0.82f, centerY - 26.dp.toPx()),
                size = Size(baseWidth * 1.64f, 52.dp.toPx()),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.55f * alpha),
                topLeft = Offset(centerX - outerWidth / 2f, centerY - outerHeight / 2f),
                size = Size(outerWidth, outerHeight),
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), rotation * 1.6f),
                ),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.75f * alpha),
                topLeft = Offset(centerX - midWidth / 2f, centerY - midHeight / 2f),
                size = Size(midWidth, midHeight),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), -rotation * 2.8f),
                ),
            )

            for (k in 0..5) {
                val angle = (rotation * 1.6 + k * 60.0) * (PI / 180.0)
                val sx = (centerX + (outerWidth / 2f) * cos(angle)).toFloat()
                val sy = (centerY + (outerHeight / 2f) * sin(angle)).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 3.2.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 6.5.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.4.dp.toPx()))
            }

            drawOval(
                color = Color.White.copy(alpha = 0.90f * alpha),
                topLeft = Offset(centerX - (coreWidth * 0.60f) / 2f, centerY - 8.dp.toPx()),
                size = Size(coreWidth * 0.60f, 16.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawOval(
                color = baseColor.copy(alpha = 0.95f * alpha),
                topLeft = Offset(centerX - coreWidth / 2f, centerY - coreHeight / 2f),
                size = Size(coreWidth, coreHeight),
                style = Stroke(width = 1.6.dp.toPx()),
            )
        }
    }
}
