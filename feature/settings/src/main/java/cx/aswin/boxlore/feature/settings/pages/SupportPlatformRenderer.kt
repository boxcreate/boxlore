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
    storyProgress: Float = 0f,
) {
    val leftPage = pagePosition.toInt().coerceIn(0, 4)
    val rightPage = (leftPage + 1).coerceAtMost(4)
    val fraction = pagePosition - leftPage

    val center = Offset(size.width / 2f, size.height * 0.50f)

    if (fraction < 0.01f) {
        drawTierHudReticle(
            HudReticleConfig(
                tierIndex = leftPage,
                alpha = 1f,
                scale = 1f,
                center = center,
                rotation = rotation,
                pulse = pulse,
                color = tiers[leftPage].auraColor,
                storyProgress = storyProgress,
            ),
        )
    } else if (fraction > 0.99f) {
        drawTierHudReticle(
            HudReticleConfig(
                tierIndex = rightPage,
                alpha = 1f,
                scale = 1f,
                center = center,
                rotation = rotation,
                pulse = pulse,
                color = tiers[rightPage].auraColor,
                storyProgress = storyProgress,
            ),
        )
    } else {
        val leftAlpha = (1f - fraction).coerceIn(0f, 1f)
        val rightAlpha = fraction.coerceIn(0f, 1f)
        val leftScale = 1f - (0.08f * fraction)
        val rightScale = 0.92f + (0.08f * fraction)
        drawTierHudReticle(
            HudReticleConfig(
                tierIndex = leftPage,
                alpha = leftAlpha,
                scale = leftScale,
                center = center,
                rotation = rotation,
                pulse = pulse,
                color = tiers[leftPage].auraColor,
                storyProgress = storyProgress,
            ),
        )
        drawTierHudReticle(
            HudReticleConfig(
                tierIndex = rightPage,
                alpha = rightAlpha,
                scale = rightScale,
                center = center,
                rotation = rotation,
                pulse = pulse,
                color = tiers[rightPage].auraColor,
                storyProgress = storyProgress,
            ),
        )
    }
}

internal data class HudReticleConfig(
    val tierIndex: Int,
    val alpha: Float,
    val scale: Float,
    val center: Offset,
    val rotation: Float,
    val pulse: Float,
    val color: Color,
    val storyProgress: Float = 0f,
)

internal fun DrawScope.drawTierHudReticle(config: HudReticleConfig) {
    if (config.alpha <= 0.01f) return
    val bracketColor = config.color.copy(alpha = config.color.alpha * config.alpha)
    val effectivePulse = config.pulse * config.scale

    val defaultHw = 104.dp.toPx() * config.scale
    val defaultHh = 96.dp.toPx() * config.scale
    val storyHw = 160.dp.toPx() * config.scale
    val storyHh = 76.dp.toPx() * config.scale

    val hw = defaultHw + (storyHw - defaultHw) * config.storyProgress
    val hh = defaultHh + (storyHh - defaultHh) * config.storyProgress
    val arm = (20.dp.toPx() - (4.dp.toPx() * config.storyProgress)) * config.scale

    // Common tactical corner brackets framing the item or lore card
    drawCornerBrackets(
        center = config.center,
        halfWidth = hw,
        halfHeight = hh,
        armLength = arm,
        color = bracketColor.copy(alpha = (0.50f + 0.35f * config.storyProgress) * config.alpha),
        strokeWidth = 1.5.dp.toPx(),
    )

    // Minimal state subtle telemetry rails framing the lore story
    if (config.storyProgress > 0.02f) {
        val frameAlpha = config.storyProgress * config.alpha * 0.40f
        val lineYTop = config.center.y - hh
        val lineYBottom = config.center.y + hh
        val startX = config.center.x - hw + arm + 6.dp.toPx()
        val endX = config.center.x + hw - arm - 6.dp.toPx()
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        drawLine(
            color = bracketColor.copy(alpha = frameAlpha),
            start = Offset(startX, lineYTop),
            end = Offset(endX, lineYTop),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect,
        )
        drawLine(
            color = bracketColor.copy(alpha = frameAlpha),
            start = Offset(startX, lineYBottom),
            end = Offset(endX, lineYBottom),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect,
        )
    }

    val alpha = config.alpha * (1f - config.storyProgress)
    if (alpha <= 0.01f) return
    val baseColor = config.color.copy(alpha = config.color.alpha * alpha)

    when (config.tierIndex) {
        0 -> drawWitchCellReticle(config, effectivePulse, baseColor, alpha)
        1 -> drawGravePackReticle(config, effectivePulse, baseColor, alpha)
        2 -> drawSunRelicReticle(config, effectivePulse, baseColor, alpha, hw, hh)
        3 -> drawVoidSpireReticle(config, effectivePulse, baseColor, alpha, hw)
        else -> drawBloodOrbReticle(config, effectivePulse, baseColor, alpha, hw, hh)
    }
}

private fun DrawScope.drawWitchCellReticle(
    config: HudReticleConfig,
    effectivePulse: Float,
    baseColor: Color,
    alpha: Float,
) {
    val radius = 88.dp.toPx() * effectivePulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(baseColor.copy(alpha = 0.22f * alpha), Color.Transparent),
            center = config.center,
            radius = radius * 1.30f,
        ),
        center = config.center,
        radius = radius * 1.30f,
    )

    // Segmented circular calibration ring
    drawCircle(
        color = baseColor.copy(alpha = 0.65f * alpha),
        radius = radius,
        center = config.center,
        style = Stroke(
            width = 1.4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f), config.rotation * 0.9f),
        ),
    )

    // Inner subtle calibration circle
    val innerRingRadius = radius * 0.62f
    drawCircle(
        color = baseColor.copy(alpha = 0.40f * alpha),
        radius = innerRingRadius,
        center = config.center,
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), -config.rotation * 0.7f),
        ),
    )

    // 4 Cardinal Rangefinder Crosshairs
    val tickIn = radius - 9.dp.toPx()
    val tickOut = radius + 11.dp.toPx()
    drawLine(baseColor.copy(alpha = 0.85f * alpha), Offset(config.center.x, config.center.y - tickOut), Offset(config.center.x, config.center.y - tickIn), 1.5.dp.toPx())
    drawLine(baseColor.copy(alpha = 0.85f * alpha), Offset(config.center.x, config.center.y + tickIn), Offset(config.center.x, config.center.y + tickOut), 1.5.dp.toPx())
    drawLine(baseColor.copy(alpha = 0.85f * alpha), Offset(config.center.x - tickOut, config.center.y), Offset(config.center.x - tickIn, config.center.y), 1.5.dp.toPx())
    drawLine(baseColor.copy(alpha = 0.85f * alpha), Offset(config.center.x + tickIn, config.center.y), Offset(config.center.x + tickOut, config.center.y), 1.5.dp.toPx())

    // 4 Corner Alignment Ticks at 45 deg
    for (k in 0..3) {
        val angle = (45.0 + k * 90.0) * (PI / 180.0)
        val inX = (config.center.x + (radius - 5.dp.toPx()) * cos(angle)).toFloat()
        val inY = (config.center.y + (radius - 5.dp.toPx()) * sin(angle)).toFloat()
        val outX = (config.center.x + (radius + 5.dp.toPx()) * cos(angle)).toFloat()
        val outY = (config.center.y + (radius + 5.dp.toPx()) * sin(angle)).toFloat()
        drawLine(baseColor.copy(alpha = 0.55f * alpha), Offset(inX, inY), Offset(outX, outY), 1.dp.toPx())
    }

    // 2 Orbiting Calibration Nodes
    for (k in 0..1) {
        val angle = (config.rotation * 1.4 + k * 180.0) * (PI / 180.0)
        val sx = (config.center.x + radius * cos(angle)).toFloat()
        val sy = (config.center.y + radius * sin(angle)).toFloat()
        drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.5.dp.toPx(), center = Offset(sx, sy))
        drawCircle(color = baseColor, radius = 5.dp.toPx(), center = Offset(sx, sy), style = Stroke(1.2.dp.toPx()))
    }
}

private fun DrawScope.drawGravePackReticle(
    config: HudReticleConfig,
    effectivePulse: Float,
    baseColor: Color,
    alpha: Float,
) {
    val hexRadius = 88.dp.toPx() * effectivePulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(baseColor.copy(alpha = 0.24f * alpha), Color.Transparent),
            center = config.center,
            radius = hexRadius * 1.30f,
        ),
        center = config.center,
        radius = hexRadius * 1.30f,
    )

    // Outer Hexagonal calibration perimeter
    drawPolygon(
        center = config.center,
        radius = hexRadius,
        sides = 6,
        rotation = config.rotation * 1.1f,
        color = baseColor.copy(alpha = 0.70f * alpha),
        strokeWidth = 1.4.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
    )

    // Inner Hexagonal Core
    drawPolygon(
        center = config.center,
        radius = hexRadius * 0.58f,
        sides = 6,
        rotation = -config.rotation * 0.8f,
        color = baseColor.copy(alpha = 0.45f * alpha),
        strokeWidth = 1.dp.toPx(),
    )

    // Lateral Vector Rangefinder Wings
    val wingStart = 65.dp.toPx()
    val wingEnd = 120.dp.toPx()
    drawLine(baseColor.copy(alpha = 0.70f * alpha), Offset(config.center.x - wingEnd, config.center.y), Offset(config.center.x - wingStart, config.center.y), 1.4.dp.toPx())
    drawLine(baseColor.copy(alpha = 0.70f * alpha), Offset(config.center.x + wingStart, config.center.y), Offset(config.center.x + wingEnd, config.center.y), 1.4.dp.toPx())

    // 4 Lateral Hash Notches on each wing
    for (i in 0..3) {
        val off = 75.dp.toPx() + i * 12.dp.toPx()
        drawLine(baseColor.copy(alpha = 0.75f * alpha), Offset(config.center.x - off, config.center.y - 4.dp.toPx()), Offset(config.center.x - off, config.center.y + 4.dp.toPx()), 1.1.dp.toPx())
        drawLine(baseColor.copy(alpha = 0.75f * alpha), Offset(config.center.x + off, config.center.y - 4.dp.toPx()), Offset(config.center.x + off, config.center.y + 4.dp.toPx()), 1.1.dp.toPx())
    }

    // Top & Bottom Chevron Targeting Triangles
    drawChevron(config.center.x, config.center.y - hexRadius - 7.dp.toPx(), 6.5.dp.toPx(), true, baseColor.copy(alpha = 0.90f * alpha))
    drawChevron(config.center.x, config.center.y + hexRadius + 7.dp.toPx(), 6.5.dp.toPx(), false, baseColor.copy(alpha = 0.90f * alpha))
}

private fun DrawScope.drawSunRelicReticle(
    config: HudReticleConfig,
    effectivePulse: Float,
    baseColor: Color,
    alpha: Float,
    hw: Float,
    hh: Float,
) {
    val octRadius = 92.dp.toPx() * effectivePulse
    val innerRadius = octRadius * 0.65f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(baseColor.copy(alpha = 0.26f * alpha), Color.Transparent),
            center = config.center,
            radius = octRadius * 1.30f,
        ),
        center = config.center,
        radius = octRadius * 1.30f,
    )

    // Octagonal Reinforced Shield Ring
    drawPolygon(
        center = config.center,
        radius = octRadius,
        sides = 8,
        rotation = config.rotation * 0.85f,
        color = baseColor.copy(alpha = 0.75f * alpha),
        strokeWidth = 1.6.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
    )

    // Inner Solid Concentric Core
    drawCircle(
        color = baseColor.copy(alpha = 0.85f * alpha),
        radius = innerRadius,
        center = config.center,
        style = Stroke(width = 1.3.dp.toPx()),
    )

    // 4 Reinforced Corner Tabs (Double Corner Notches)
    drawCornerBrackets(
        center = config.center,
        halfWidth = hw - 10.dp.toPx(),
        halfHeight = hh - 10.dp.toPx(),
        armLength = 9.dp.toPx(),
        color = baseColor.copy(alpha = 0.40f * alpha),
        strokeWidth = 1.1.dp.toPx(),
    )

    // 8 Perimeter Alignment Nodes
    for (k in 0..7) {
        val angle = (config.rotation * 0.85 + k * 45.0) * (PI / 180.0)
        val sx = (config.center.x + octRadius * cos(angle)).toFloat()
        val sy = (config.center.y + octRadius * sin(angle)).toFloat()
        drawCircle(color = Color.White.copy(alpha = alpha), radius = 2.5.dp.toPx(), center = Offset(sx, sy))
    }
}

private fun DrawScope.drawVoidSpireReticle(
    config: HudReticleConfig,
    effectivePulse: Float,
    baseColor: Color,
    alpha: Float,
    hw: Float,
) {
    val boxRadius = 88.dp.toPx() * effectivePulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(baseColor.copy(alpha = 0.28f * alpha), Color.Transparent),
            center = config.center,
            radius = boxRadius * 1.35f,
        ),
        center = config.center,
        radius = boxRadius * 1.35f,
    )

    // 45° Diamond Perimeter Reticle
    drawPolygon(
        center = config.center,
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
        center = config.center,
        style = Stroke(
            width = 1.3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -config.rotation * 1.5f),
        ),
    )

    // Lateral Pitch Ladder Telemetry (6 micro ticks on each side)
    for (i in -3..3) {
        if (i == 0) continue
        val ty = config.center.y + i * 14.dp.toPx()
        drawLine(baseColor.copy(alpha = 0.70f * alpha), Offset(config.center.x - hw + 6.dp.toPx(), ty), Offset(config.center.x - hw + 16.dp.toPx(), ty), 1.2.dp.toPx())
        drawLine(baseColor.copy(alpha = 0.70f * alpha), Offset(config.center.x + hw - 16.dp.toPx(), ty), Offset(config.center.x + hw - 6.dp.toPx(), ty), 1.2.dp.toPx())
    }
}

private fun DrawScope.drawBloodOrbReticle(
    config: HudReticleConfig,
    effectivePulse: Float,
    baseColor: Color,
    alpha: Float,
    hw: Float,
    hh: Float,
) {
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
            center = config.center,
            radius = outerRadius * 1.35f,
        ),
        center = config.center,
        radius = outerRadius * 1.35f,
    )

    // Outer Gyroscope Aperture (Dashed)
    drawCircle(
        color = baseColor.copy(alpha = 0.70f * alpha),
        radius = outerRadius,
        center = config.center,
        style = Stroke(
            width = 1.4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), config.rotation * 1.1f),
        ),
    )

    // Middle Gyroscope Ring with 8 Radial Solar Spikes
    drawCircle(
        color = Color.White.copy(alpha = 0.85f * alpha),
        radius = midRadius,
        center = config.center,
        style = Stroke(width = 1.4.dp.toPx()),
    )

    for (k in 0..7) {
        val angle = (config.rotation * 0.9 + k * 45.0) * (PI / 180.0)
        val inX = (config.center.x + midRadius * cos(angle)).toFloat()
        val inY = (config.center.y + midRadius * sin(angle)).toFloat()
        val outX = (config.center.x + (midRadius + 9.dp.toPx()) * cos(angle)).toFloat()
        val outY = (config.center.y + (midRadius + 9.dp.toPx()) * sin(angle)).toFloat()
        drawLine(Color.White.copy(alpha = 0.85f * alpha), Offset(inX, inY), Offset(outX, outY), 1.4.dp.toPx())
    }

    // Inner Core Aperture Ring (Counter-Rotating)
    drawCircle(
        color = baseColor.copy(alpha = 0.95f * alpha),
        radius = coreRadius,
        center = config.center,
        style = Stroke(
            width = 1.3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), -config.rotation * 1.6f),
        ),
    )

    // 4 Vertex Diamonds at Corner Bracket Junctions
    drawVertexDiamond(config.center.x - hw, config.center.y - hh, 4.5.dp.toPx(), Color.White.copy(alpha = alpha), baseColor)
    drawVertexDiamond(config.center.x + hw, config.center.y - hh, 4.5.dp.toPx(), Color.White.copy(alpha = alpha), baseColor)
    drawVertexDiamond(config.center.x - hw, config.center.y + hh, 4.5.dp.toPx(), Color.White.copy(alpha = alpha), baseColor)
    drawVertexDiamond(config.center.x + hw, config.center.y + hh, 4.5.dp.toPx(), Color.White.copy(alpha = alpha), baseColor)
}

private fun DrawScope.drawCornerBrackets(
    center: Offset,
    halfWidth: Float,
    halfHeight: Float,
    armLength: Float,
    color: Color,
    strokeWidth: Float,
) {
    val left = center.x - halfWidth
    val right = center.x + halfWidth
    val top = center.y - halfHeight
    val bottom = center.y + halfHeight

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

private fun DrawScope.drawPolygon(
    center: Offset,
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
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
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
