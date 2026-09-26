package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the tactical HUD calibration reticle centered behind the floating battery artifact,
 * transforming seamlessly across the 5 power tiers with gyro rotations and optical targeting marks.
 */
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
    val centerY = size.height * 0.50f

    if (fraction < 0.01f) {
        drawTierHudReticle(leftPage, 1f, 1f, centerX, centerY, rotation, pulse, tiers[leftPage].auraColor)
    } else if (fraction > 0.99f) {
        drawTierHudReticle(rightPage, 1f, 1f, centerX, centerY, rotation, pulse, tiers[rightPage].auraColor)
    } else {
        val leftAlpha = (1f - fraction).coerceIn(0f, 1f)
        val rightAlpha = fraction.coerceIn(0f, 1f)
        val leftScale = 1f - (0.08f * fraction)
        val rightScale = 0.92f + (0.08f * fraction)
        drawTierHudReticle(leftPage, leftAlpha, leftScale, centerX, centerY, rotation, pulse, tiers[leftPage].auraColor)
        drawTierHudReticle(rightPage, rightAlpha, rightScale, centerX, centerY, rotation, pulse, tiers[rightPage].auraColor)
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
internal fun DrawScope.drawTierHudReticle(
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
    val hw = 104.dp.toPx() * scale
    val hh = 96.dp.toPx() * scale
    val arm = 20.dp.toPx() * scale

    // Common tactical corner brackets framing the item
    drawCornerBrackets(
        centerX = centerX,
        centerY = centerY,
        halfWidth = hw,
        halfHeight = hh,
        armLength = arm,
        color = baseColor.copy(alpha = 0.50f * alpha),
        strokeWidth = 1.5.dp.toPx(),
    )

    when (tierIndex) {
        0 -> {
            // Tier 0: Unit Blue - Precision Rangefinder Reticle
            val radius = 88.dp.toPx() * effectivePulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.22f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = radius * 1.30f,
                ),
                center = Offset(centerX, centerY),
                radius = radius * 1.30f,
            )

            // Segmented circular calibration ring
            drawCircle(
                color = baseColor.copy(alpha = 0.65f * alpha),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f), rotation * 0.9f),
                ),
            )

            // Inner subtle calibration circle
            val innerRingRadius = radius * 0.62f
            drawCircle(
                color = baseColor.copy(alpha = 0.40f * alpha),
                radius = innerRingRadius,
                center = Offset(centerX, centerY),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), -rotation * 0.7f),
                ),
            )

            // 4 Cardinal Rangefinder Crosshairs
            val tickIn = radius - 9.dp.toPx()
            val tickOut = radius + 11.dp.toPx()
            drawLine(baseColor.copy(alpha = 0.85f * alpha), Offset(centerX, centerY - tickOut), Offset(centerX, centerY - tickIn), 1.5.dp.toPx())
            drawLine(baseColor.copy(alpha = 0.85f * alpha), Offset(centerX, centerY + tickIn), Offset(centerX, centerY + tickOut), 1.5.dp.toPx())
            drawLine(baseColor.copy(alpha = 0.85f * alpha), Offset(centerX - tickOut, centerY), Offset(centerX - tickIn, centerY), 1.5.dp.toPx())
            drawLine(baseColor.copy(alpha = 0.85f * alpha), Offset(centerX + tickIn, centerY), Offset(centerX + tickOut, centerY), 1.5.dp.toPx())

            // 4 Corner Alignment Ticks at 45 deg
            for (k in 0..3) {
                val angle = (45.0 + k * 90.0) * (PI / 180.0)
                val inX = (centerX + (radius - 5.dp.toPx()) * cos(angle)).toFloat()
                val inY = (centerY + (radius - 5.dp.toPx()) * sin(angle)).toFloat()
                val outX = (centerX + (radius + 5.dp.toPx()) * cos(angle)).toFloat()
                val outY = (centerY + (radius + 5.dp.toPx()) * sin(angle)).toFloat()
                drawLine(baseColor.copy(alpha = 0.55f * alpha), Offset(inX, inY), Offset(outX, outY), 1.dp.toPx())
            }

            // 2 Orbiting Calibration Nodes
            for (k in 0..1) {
                val angle = (rotation * 1.4 + k * 180.0) * (PI / 180.0)
                val sx = (centerX + radius * cos(angle)).toFloat()
                val sy = (centerY + radius * sin(angle)).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.5.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = baseColor, radius = 5.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.2.dp.toPx()))
            }
        }
        1 -> {
            // Tier 1: Grid Hawk - Tactical Hexagonal Flight Reticle
            val hexRadius = 88.dp.toPx() * effectivePulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.24f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = hexRadius * 1.30f,
                ),
                center = Offset(centerX, centerY),
                radius = hexRadius * 1.30f,
            )

            // Outer Hexagonal calibration perimeter
            drawPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = hexRadius,
                sides = 6,
                rotation = rotation * 1.1f,
                color = baseColor.copy(alpha = 0.70f * alpha),
                strokeWidth = 1.4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
            )

            // Inner Hexagonal Core
            drawPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = hexRadius * 0.58f,
                sides = 6,
                rotation = -rotation * 0.8f,
                color = baseColor.copy(alpha = 0.45f * alpha),
                strokeWidth = 1.dp.toPx(),
            )

            // Lateral Vector Rangefinder Wings
            val wingStart = 65.dp.toPx()
            val wingEnd = 120.dp.toPx()
            drawLine(baseColor.copy(alpha = 0.70f * alpha), Offset(centerX - wingEnd, centerY), Offset(centerX - wingStart, centerY), 1.4.dp.toPx())
            drawLine(baseColor.copy(alpha = 0.70f * alpha), Offset(centerX + wingStart, centerY), Offset(centerX + wingEnd, centerY), 1.4.dp.toPx())

            // 4 Lateral Hash Notches on each wing
            for (i in 0..3) {
                val off = 75.dp.toPx() + i * 12.dp.toPx()
                drawLine(baseColor.copy(alpha = 0.75f * alpha), Offset(centerX - off, centerY - 4.dp.toPx()), Offset(centerX - off, centerY + 4.dp.toPx()), 1.1.dp.toPx())
                drawLine(baseColor.copy(alpha = 0.75f * alpha), Offset(centerX + off, centerY - 4.dp.toPx()), Offset(centerX + off, centerY + 4.dp.toPx()), 1.1.dp.toPx())
            }

            // Top & Bottom Chevron Targeting Triangles
            drawChevron(centerX, centerY - hexRadius - 7.dp.toPx(), 6.5.dp.toPx(), true, baseColor.copy(alpha = 0.90f * alpha))
            drawChevron(centerX, centerY + hexRadius + 7.dp.toPx(), 6.5.dp.toPx(), false, baseColor.copy(alpha = 0.90f * alpha))
        }
        2 -> {
            // Tier 2: Vault Prime - Reinforced Octagonal Aegis
            val octRadius = 92.dp.toPx() * effectivePulse
            val innerRadius = octRadius * 0.65f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.26f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = octRadius * 1.30f,
                ),
                center = Offset(centerX, centerY),
                radius = octRadius * 1.30f,
            )

            // Octagonal Reinforced Shield Ring
            drawPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = octRadius,
                sides = 8,
                rotation = rotation * 0.85f,
                color = baseColor.copy(alpha = 0.75f * alpha),
                strokeWidth = 1.6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
            )

            // Inner Solid Concentric Core
            drawCircle(
                color = baseColor.copy(alpha = 0.85f * alpha),
                radius = innerRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.3.dp.toPx()),
            )

            // 4 Reinforced Corner Tabs (Double Corner Notches)
            drawCornerBrackets(
                centerX = centerX,
                centerY = centerY,
                halfWidth = hw - 10.dp.toPx(),
                halfHeight = hh - 10.dp.toPx(),
                armLength = 9.dp.toPx(),
                color = baseColor.copy(alpha = 0.40f * alpha),
                strokeWidth = 1.1.dp.toPx(),
            )

            // 8 Perimeter Alignment Nodes
            for (k in 0..7) {
                val angle = (rotation * 0.85 + k * 45.0) * (PI / 180.0)
                val sx = (centerX + octRadius * cos(angle)).toFloat()
                val sy = (centerY + octRadius * sin(angle)).toFloat()
                drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.5.dp.toPx(), center = Offset(sx, sy))
            }
        }
        3 -> {
            // Tier 3: Stack Zero - High-Density Quantum Matrix
            val boxRadius = 88.dp.toPx() * effectivePulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor.copy(alpha = 0.28f * alpha), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = boxRadius * 1.35f,
                ),
                center = Offset(centerX, centerY),
                radius = boxRadius * 1.35f,
            )

            // 45° Diamond Perimeter Reticle
            drawPolygon(
                centerX = centerX,
                centerY = centerY,
                radius = boxRadius * 1.15f,
                sides = 4,
                rotation = 45f,
                color = baseColor.copy(alpha = 0.60f * alpha),
                strokeWidth = 1.4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
            )

            // Counter-Rotating Gyroscopic Ring
            drawCircle(
                color = baseColor.copy(alpha = 0.85f * alpha),
                radius = boxRadius * 0.78f,
                center = Offset(centerX, centerY),
                style = Stroke(
                    width = 1.3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -rotation * 1.5f),
                ),
            )

            // Lateral Pitch Ladder Telemetry (6 micro ticks on each side)
            for (i in -3..3) {
                if (i == 0) continue
                val ty = centerY + i * 14.dp.toPx()
                drawLine(baseColor.copy(alpha = 0.70f * alpha), Offset(centerX - hw + 6.dp.toPx(), ty), Offset(centerX - hw + 16.dp.toPx(), ty), 1.2.dp.toPx())
                drawLine(baseColor.copy(alpha = 0.70f * alpha), Offset(centerX + hw - 16.dp.toPx(), ty), Offset(centerX + hw - 6.dp.toPx(), ty), 1.2.dp.toPx())
            }
        }
        else -> {
            // Tier 4: Orbit X - Supreme Celestial Matrix
            val outerRadius = 96.dp.toPx() * effectivePulse
            val midRadius = outerRadius * 0.74f
            val coreRadius = outerRadius * 0.46f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f * alpha),
                        baseColor.copy(alpha = 0.35f * alpha),
                        Color.Transparent,
                    ),
                    center = Offset(centerX, centerY),
                    radius = outerRadius * 1.35f,
                ),
                center = Offset(centerX, centerY),
                radius = outerRadius * 1.35f,
            )

            // Outer Gyroscope Aperture (Dashed)
            drawCircle(
                color = baseColor.copy(alpha = 0.70f * alpha),
                radius = outerRadius,
                center = Offset(centerX, centerY),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), rotation * 1.1f),
                ),
            )

            // Middle Gyroscope Ring with 8 Radial Solar Spikes
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * alpha),
                radius = midRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.4.dp.toPx()),
            )

            for (k in 0..7) {
                val angle = (rotation * 0.9 + k * 45.0) * (PI / 180.0)
                val inX = (centerX + midRadius * cos(angle)).toFloat()
                val inY = (centerY + midRadius * sin(angle)).toFloat()
                val outX = (centerX + (midRadius + 9.dp.toPx()) * cos(angle)).toFloat()
                val outY = (centerY + (midRadius + 9.dp.toPx()) * sin(angle)).toFloat()
                drawLine(Color.White.copy(alpha = 0.85f * alpha), Offset(inX, inY), Offset(outX, outY), 1.4.dp.toPx())
            }

            // Inner Core Aperture Ring (Counter-Rotating)
            drawCircle(
                color = baseColor.copy(alpha = 0.95f * alpha),
                radius = coreRadius,
                center = Offset(centerX, centerY),
                style = Stroke(
                    width = 1.3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), -rotation * 1.6f),
                ),
            )

            // 4 Vertex Diamonds at Corner Bracket Junctions
            drawVertexDiamond(centerX - hw, centerY - hh, 4.5.dp.toPx(), Color.White.copy(alpha = alpha), baseColor)
            drawVertexDiamond(centerX + hw, centerY - hh, 4.5.dp.toPx(), Color.White.copy(alpha = alpha), baseColor)
            drawVertexDiamond(centerX - hw, centerY + hh, 4.5.dp.toPx(), Color.White.copy(alpha = alpha), baseColor)
            drawVertexDiamond(centerX + hw, centerY + hh, 4.5.dp.toPx(), Color.White.copy(alpha = alpha), baseColor)
        }
    }
}

private fun DrawScope.drawCornerBrackets(
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    armLength: Float,
    color: Color,
    strokeWidth: Float,
) {
    val left = centerX - halfWidth
    val right = centerX + halfWidth
    val top = centerY - halfHeight
    val bottom = centerY + halfHeight

    // Top-Left ┌
    drawLine(color, Offset(left, top + armLength), Offset(left, top), strokeWidth)
    drawLine(color, Offset(left, top), Offset(left + armLength, top), strokeWidth)
    drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(left, top))

    // Top-Right ┐
    drawLine(color, Offset(right - armLength, top), Offset(right, top), strokeWidth)
    drawLine(color, Offset(right, top), Offset(right, top + armLength), strokeWidth)
    drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(right, top))

    // Bottom-Left └
    drawLine(color, Offset(left, bottom - armLength), Offset(left, bottom), strokeWidth)
    drawLine(color, Offset(left, bottom), Offset(left + armLength, bottom), strokeWidth)
    drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(left, bottom))

    // Bottom-Right ┘
    drawLine(color, Offset(right - armLength, bottom), Offset(right, bottom), strokeWidth)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - armLength), strokeWidth)
    drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(right, bottom))
}

@Suppress("LongParameterList")
private fun DrawScope.drawPolygon(
    centerX: Float,
    centerY: Float,
    radius: Float,
    sides: Int,
    rotation: Float,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
) {
    val path = Path()
    for (i in 0 until sides) {
        val angle = (rotation + (i * 360f / sides)) * (PI.toFloat() / 180f)
        val x = centerX + radius * cos(angle)
        val y = centerY + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}

private fun DrawScope.drawChevron(
    cx: Float,
    cy: Float,
    size: Float,
    pointDown: Boolean,
    color: Color,
) {
    val dy = if (pointDown) size else -size
    val path = Path().apply {
        moveTo(cx - size, cy - dy)
        lineTo(cx, cy)
        lineTo(cx + size, cy - dy)
    }
    drawPath(path = path, color = color, style = Stroke(width = 1.3.dp.toPx()))
}

private fun DrawScope.drawVertexDiamond(
    cx: Float,
    cy: Float,
    size: Float,
    fillColor: Color,
    strokeColor: Color,
) {
    val path = Path().apply {
        moveTo(cx, cy - size)
        lineTo(cx + size, cy)
        lineTo(cx, cy + size)
        lineTo(cx - size, cy)
        close()
    }
    drawPath(path = path, color = fillColor)
    drawPath(path = path, color = strokeColor, style = Stroke(width = 1.dp.toPx()))
}
