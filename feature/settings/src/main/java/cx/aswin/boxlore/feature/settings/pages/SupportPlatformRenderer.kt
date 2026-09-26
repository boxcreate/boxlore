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

    if (fraction < 0.01f) {
        drawTierPlatform(leftPage, 1f, 1f, centerX, centerY, rotation, pulse, tiers[leftPage].auraColor)
    } else if (fraction > 0.99f) {
        drawTierPlatform(rightPage, 1f, 1f, centerX, centerY, rotation, pulse, tiers[rightPage].auraColor)
    } else {
        val leftAlpha = (1f - fraction).coerceIn(0f, 1f)
        val rightAlpha = fraction.coerceIn(0f, 1f)
        val leftScale = 1f - (0.14f * fraction)
        val rightScale = 0.86f + (0.14f * fraction)
        drawTierPlatform(leftPage, leftAlpha, leftScale, centerX, centerY, rotation, pulse, tiers[leftPage].auraColor)
        drawTierPlatform(rightPage, rightAlpha, rightScale, centerX, centerY, rotation, pulse, tiers[rightPage].auraColor)
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
internal fun DrawScope.drawTierPlatform(
    tierIndex: Int,
    alpha: Float,
    scale: Float,
    centerX: Float,
    centerY: Float,
    rotation: Float,
    pulse: Float,
    color: Color,
) {
    if (alpha <= 0.01f) return
    val baseColor = color.copy(alpha = color.alpha * alpha)
    val effectivePulse = pulse * scale

    when (tierIndex) {
        0 -> {
            // Tier 0: Micro Energy Cell - Concentric Orbital Ellipses with 2 atomic satellites
            val baseRadius = 65.dp.toPx() * effectivePulse
            val outerWidth = baseRadius * 2.30f
            val outerHeight = outerWidth * 0.28f
            val innerWidth = baseRadius * 1.45f
            val innerHeight = innerWidth * 0.28f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.35f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = baseRadius * 1.30f,
                ),
                topLeft = Offset(centerX - baseRadius * 1.15f, centerY - outerHeight / 2f),
                size = Size(baseRadius * 2.30f, outerHeight),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.60f * alpha),
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
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.5.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 5.5.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.dp.toPx()))
            }

            drawOval(
                color = baseColor.copy(alpha = 0.88f * alpha),
                topLeft = Offset(centerX - innerWidth / 2f, centerY - innerHeight / 2f),
                size = Size(innerWidth, innerHeight),
                style = Stroke(width = 1.3.dp.toPx()),
            )
        }
        1 -> {
            // Tier 1: Field Battery Pack - Tactical Hexagonal Grid (6 vertices) with 3 capacitor nodes
            val hexRadius = 82.dp.toPx() * effectivePulse
            val innerHexRadius = hexRadius * 0.62f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.36f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = hexRadius * 1.20f,
                ),
                topLeft = Offset(centerX - hexRadius, centerY - hexRadius * 0.28f),
                size = Size(hexRadius * 2f, hexRadius * 0.56f),
            )

            drawIsometricPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = hexRadius,
                sides = 6,
                rotation = rotation * 1.1f,
                color = baseColor.copy(alpha = 0.65f * alpha),
                strokeWidth = 1.4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f),
            )

            drawIsometricPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = innerHexRadius,
                sides = 6,
                rotation = -rotation * 1.4f,
                color = baseColor.copy(alpha = 0.90f * alpha),
                strokeWidth = 1.3.dp.toPx(),
            )

            // 3 vertex energy capacitors
            for (k in 0..2) {
                val angle = (rotation * 1.1 + k * 120.0) * (PI / 180.0)
                val sx = (centerX + hexRadius * cos(angle)).toFloat()
                val sy = (centerY + hexRadius * sin(angle) * 0.28f).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.6.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 5.2.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.2.dp.toPx()))
            }
        }
        2 -> {
            // Tier 2: Power Station - Heavy Octagonal Reactor Grid (8 vertices) with crosshair alignment axis
            val octRadius = 94.dp.toPx() * effectivePulse
            val innerSquareRadius = octRadius * 0.58f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.38f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = octRadius * 1.25f,
                ),
                topLeft = Offset(centerX - octRadius, centerY - octRadius * 0.28f),
                size = Size(octRadius * 2f, octRadius * 0.56f),
            )

            drawIsometricPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = octRadius,
                sides = 8,
                rotation = rotation * 0.9f,
                color = baseColor.copy(alpha = 0.65f * alpha),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f), 0f),
            )

            drawIsometricPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = innerSquareRadius,
                sides = 4,
                rotation = -rotation * 1.8f + 45f,
                color = baseColor.copy(alpha = 0.92f * alpha),
                strokeWidth = 1.4.dp.toPx(),
            )

            // 4 heavy reactor corner containment nodes
            for (k in 0..3) {
                val angle = (rotation * 0.9 + k * 90.0) * (PI / 180.0)
                val sx = (centerX + octRadius * cos(angle)).toFloat()
                val sy = (centerY + octRadius * sin(angle) * 0.28f).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.8.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 6.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.3.dp.toPx()))
            }
        }
        3 -> {
            // Tier 3: Server Tower - Cybernetic Dodecagon (12 vertices) with dual counter-rotating circuit rings
            val dodecRadius = 106.dp.toPx() * effectivePulse
            val midHexRadius = dodecRadius * 0.72f
            val coreRingRadius = dodecRadius * 0.42f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.40f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = dodecRadius * 1.30f,
                ),
                topLeft = Offset(centerX - dodecRadius, centerY - dodecRadius * 0.28f),
                size = Size(dodecRadius * 2f, dodecRadius * 0.56f),
            )

            drawIsometricPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = dodecRadius,
                sides = 12,
                rotation = rotation * 1.2f,
                color = baseColor.copy(alpha = 0.60f * alpha),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
            )

            drawIsometricPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = midHexRadius,
                sides = 6,
                rotation = -rotation * 1.6f,
                color = baseColor.copy(alpha = 0.85f * alpha),
                strokeWidth = 1.3.dp.toPx(),
            )

            drawOval(
                color = baseColor.copy(alpha = 0.95f * alpha),
                topLeft = Offset(centerX - coreRingRadius, centerY - coreRingRadius * 0.28f),
                size = Size(coreRingRadius * 2f, coreRingRadius * 0.56f),
                style = Stroke(width = 1.4.dp.toPx()),
            )

            // 6 peripheral data bus nodes
            for (k in 0..5) {
                val angle = (rotation * 1.2 + k * 60.0) * (PI / 180.0)
                val sx = (centerX + dodecRadius * cos(angle)).toFloat()
                val sy = (centerY + dodecRadius * sin(angle) * 0.28f).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.6.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 5.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.2.dp.toPx()))
            }
        }
        else -> {
            // Tier 4: Quantum Beacon - Majestic 8-Pointed Celestial Star / Arcane Quantum Stargate
            val outerStarRadius = 118.dp.toPx() * effectivePulse
            val innerStarRadius = outerStarRadius * 0.65f
            val coreRingRadius = outerStarRadius * 0.38f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.32f * alpha),
                        baseColor.copy(alpha = 0.45f * alpha),
                        Color.Transparent,
                    ),
                    center = Offset(centerX, centerY),
                    radius = outerStarRadius * 1.35f,
                ),
                topLeft = Offset(centerX - outerStarRadius, centerY - outerStarRadius * 0.28f),
                size = Size(outerStarRadius * 2f, outerStarRadius * 0.56f),
            )

            drawIsometricStar(
                centerX = centerX,
                centerY = centerY,
                outerRadius = outerStarRadius,
                innerRadius = innerStarRadius,
                points = 8,
                rotation = rotation * 1.3f,
                color = baseColor.copy(alpha = 0.85f * alpha),
                strokeWidth = 1.6.dp.toPx(),
            )

            drawIsometricPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = innerStarRadius * 0.85f,
                sides = 8,
                rotation = -rotation * 2.0f,
                color = Color.White.copy(alpha = 0.80f * alpha),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            )

            drawOval(
                color = Color.White.copy(alpha = 0.95f * alpha),
                topLeft = Offset(centerX - coreRingRadius, centerY - coreRingRadius * 0.28f),
                size = Size(coreRingRadius * 2f, coreRingRadius * 0.56f),
                style = Stroke(width = 1.8.dp.toPx()),
            )

            // 8 celestial tachyon sparks
            for (k in 0..7) {
                val angle = (rotation * 1.3 + k * 45.0) * (PI / 180.0)
                val sx = (centerX + outerStarRadius * cos(angle)).toFloat()
                val sy = (centerY + outerStarRadius * sin(angle) * 0.28f).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 3.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 6.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.3.dp.toPx()))
            }
        }
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawIsometricPolygon(
    centerX: Float,
    centerY: Float,
    radius: Float,
    sides: Int,
    rotation: Float,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
) {
    val path = androidx.compose.ui.graphics.Path()
    val ySquash = 0.28f
    for (i in 0 until sides) {
        val angle = (rotation + (i * 360f / sides)) * (PI.toFloat() / 180f)
        val x = centerX + radius * cos(angle)
        val y = centerY + radius * sin(angle) * ySquash
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}

@Suppress("LongParameterList")
private fun DrawScope.drawIsometricStar(
    centerX: Float,
    centerY: Float,
    outerRadius: Float,
    innerRadius: Float,
    points: Int,
    rotation: Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = androidx.compose.ui.graphics.Path()
    val ySquash = 0.28f
    val totalVertices = points * 2
    for (i in 0 until totalVertices) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = (rotation + (i * 360f / totalVertices)) * (PI.toFloat() / 180f)
        val x = centerX + r * cos(angle)
        val y = centerY + r * sin(angle) * ySquash
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth),
    )
}
