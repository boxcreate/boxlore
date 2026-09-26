package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

internal object BlobAvatarEnvironments {

    fun drawEnvironment(
        drawScope: DrawScope,
        environmentType: BlobAvatarGenreMood.EnvironmentType,
        center: Offset,
        w: Float,
        h: Float,
        mood: BlobAvatarGenreMood,
        progress: Float,
    ) {
        when (environmentType) {
            BlobAvatarGenreMood.EnvironmentType.VenetianBlinds ->
                drawVenetianBlinds(drawScope, w, h, mood)
            BlobAvatarGenreMood.EnvironmentType.CyberMatrix ->
                drawCyberMatrix(drawScope, w, h, mood, progress)
            BlobAvatarGenreMood.EnvironmentType.StadiumLights ->
                drawStadiumLights(drawScope, w, h, mood)
            BlobAvatarGenreMood.EnvironmentType.OnAirSign ->
                drawOnAirSign(drawScope, w, h, mood)
            BlobAvatarGenreMood.EnvironmentType.LofiBokeh ->
                drawLofiBokeh(drawScope, w, h, mood, progress)
            BlobAvatarGenreMood.EnvironmentType.ComedyStage ->
                drawComedyStage(drawScope, center, w, h, mood)
            BlobAvatarGenreMood.EnvironmentType.MysticRunes ->
                drawMysticRunes(drawScope, center, w, h, mood, progress)
        }
    }

    private fun drawVenetianBlinds(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        mood: BlobAvatarGenreMood,
    ) {
        // Flashlight beam from upper right
        val lightOrigin = Offset(w * 0.92f, h * 0.08f)
        drawScope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    mood.keyLightColor.copy(alpha = 0.26f),
                    mood.keyLightColor.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = lightOrigin,
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = lightOrigin,
        )

        // Film-noir venetian blind shadow slats
        val slatCount = 5
        val slatHeight = h * 0.06f
        val slatSpacing = h * 0.18f
        for (i in 0 until slatCount) {
            val y = (i * slatSpacing) + (h * 0.04f)
            drawScope.drawRoundRect(
                color = Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(0f, y),
                size = Size(w, slatHeight),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
    }

    private fun drawCyberMatrix(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        mood: BlobAvatarGenreMood,
        progress: Float,
    ) {
        // Digital grid dots
        val cols = 5
        val rows = 5
        val stepX = w / (cols + 1)
        val stepY = h / (rows + 1)
        for (r in 1..rows) {
            for (c in 1..cols) {
                drawScope.drawCircle(
                    color = mood.keyLightColor.copy(alpha = 0.16f),
                    radius = (w * 0.009f).coerceAtLeast(1.2f),
                    center = Offset(c * stepX, r * stepY),
                )
            }
        }

        // Horizontal audio laser scanline
        val scanY = h * progress
        drawScope.drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    mood.rimLightColor.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.80f),
                    mood.rimLightColor.copy(alpha = 0.55f),
                    Color.Transparent,
                ),
            ),
            start = Offset(0f, scanY),
            end = Offset(w, scanY),
            strokeWidth = (w * 0.012f).coerceAtLeast(1f),
        )

        // Tech HUD corner brackets
        val bracketSize = w * 0.10f
        val bracketStroke = (w * 0.012f).coerceAtLeast(1f)
        val bracketColor = mood.keyLightColor.copy(alpha = 0.40f)
        drawScope.drawLine(bracketColor, Offset(w * 0.06f, w * 0.06f), Offset(w * 0.06f + bracketSize, w * 0.06f), bracketStroke)
        drawScope.drawLine(bracketColor, Offset(w * 0.06f, w * 0.06f), Offset(w * 0.06f, w * 0.06f + bracketSize), bracketStroke)
        drawScope.drawLine(bracketColor, Offset(w * 0.94f, h * 0.94f), Offset(w * 0.94f - bracketSize, h * 0.94f), bracketStroke)
        drawScope.drawLine(bracketColor, Offset(w * 0.94f, h * 0.94f), Offset(w * 0.94f, h * 0.94f - bracketSize), bracketStroke)
    }

    private fun drawStadiumLights(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        mood: BlobAvatarGenreMood,
    ) {
        val lightRadius = w * 0.024f
        val lightColor = mood.keyLightColor

        // Top-left and Top-right stadium floodlight clusters
        val towers = listOf(Offset(w * 0.12f, h * 0.10f), Offset(w * 0.88f, h * 0.10f))
        for (tower in towers) {
            // Glow halo
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lightColor.copy(alpha = 0.35f), Color.Transparent),
                    center = tower,
                    radius = w * 0.35f,
                ),
                radius = w * 0.35f,
                center = tower,
            )
            // Cluster of 3 bulbs
            for (dx in listOf(-lightRadius * 1.5f, 0f, lightRadius * 1.5f)) {
                drawScope.drawCircle(color = Color.White, radius = lightRadius, center = Offset(tower.x + dx, tower.y))
                drawScope.drawCircle(
                    color = lightColor,
                    radius = lightRadius * 1.25f,
                    center = Offset(tower.x + dx, tower.y),
                    style = Stroke(width = (w * 0.008f).coerceAtLeast(1f)),
                )
            }
        }

        // Floating victory confetti
        val confettiColors = listOf(Color(0xFFFFC107), Color(0xFF2979FF), Color(0xFFEF4444), Color(0xFF10B981))
        val confettiPositions = listOf(
            Offset(w * 0.20f, h * 0.35f),
            Offset(w * 0.80f, h * 0.30f),
            Offset(w * 0.25f, h * 0.70f),
            Offset(w * 0.75f, h * 0.65f),
        )
        for ((idx, pos) in confettiPositions.withIndex()) {
            drawScope.rotate(degrees = (idx * 35f) + 15f, pivot = pos) {
                drawRoundRect(
                    color = confettiColors[idx % confettiColors.size].copy(alpha = 0.75f),
                    topLeft = Offset(pos.x - (w * 0.02f), pos.y - (w * 0.01f)),
                    size = Size(w * 0.04f, w * 0.02f),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
        }
    }

    private fun drawOnAirSign(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        mood: BlobAvatarGenreMood,
    ) {
        // Acoustic damping tile mesh pattern in corners
        val meshColor = mood.rimLightColor.copy(alpha = 0.18f)
        val meshStroke = (w * 0.010f).coerceAtLeast(1f)
        for (i in 1..3) {
            val offset = i * (w * 0.06f)
            drawScope.drawLine(meshColor, Offset(0f, offset), Offset(offset, 0f), meshStroke)
            drawScope.drawLine(meshColor, Offset(w - offset, 0f), Offset(w, offset), meshStroke)
        }

        // Glowing "ON AIR" studio pill badge at top center
        val badgeWidth = w * 0.46f
        val badgeHeight = h * 0.12f
        val badgeLeft = (w - badgeWidth) / 2f
        val badgeTop = h * 0.04f
        val badgeCenter = Offset(w / 2f, badgeTop + (badgeHeight / 2f))

        // Neon Red Halo
        drawScope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFEF4444).copy(alpha = 0.35f), Color.Transparent),
                center = badgeCenter,
                radius = badgeWidth * 0.80f,
            ),
            radius = badgeWidth * 0.80f,
            center = badgeCenter,
        )

        // Badge pill body
        drawScope.drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(badgeLeft, badgeTop),
            size = Size(badgeWidth, badgeHeight),
            cornerRadius = CornerRadius(badgeHeight * 0.35f, badgeHeight * 0.35f),
        )
        // Red glowing border
        drawScope.drawRoundRect(
            color = Color(0xFFEF4444),
            topLeft = Offset(badgeLeft, badgeTop),
            size = Size(badgeWidth, badgeHeight),
            cornerRadius = CornerRadius(badgeHeight * 0.35f, badgeHeight * 0.35f),
            style = Stroke(width = (w * 0.015f).coerceAtLeast(1.5f)),
        )

        // Studio Live Dot + "ON AIR" Indicator bar
        val liveDotRadius = badgeHeight * 0.22f
        val dotCenter = Offset(badgeLeft + (badgeWidth * 0.22f), badgeCenter.y)
        drawScope.drawCircle(color = Color(0xFFEF4444), radius = liveDotRadius, center = dotCenter)
        drawScope.drawCircle(color = Color.White, radius = liveDotRadius * 0.45f, center = dotCenter)

        // Broadcast horizontal status wave
        drawScope.drawLine(
            color = Color.White.copy(alpha = 0.90f),
            start = Offset(badgeLeft + (badgeWidth * 0.40f), badgeCenter.y),
            end = Offset(badgeLeft + (badgeWidth * 0.82f), badgeCenter.y),
            strokeWidth = (w * 0.016f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
        )
    }

    private fun drawLofiBokeh(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        mood: BlobAvatarGenreMood,
        progress: Float,
    ) {
        // Floating fairy light bokeh orbs
        val orbs = listOf(
            Triple(0.20f, 0.25f, 0.12f),
            Triple(0.80f, 0.20f, 0.15f),
            Triple(0.15f, 0.75f, 0.14f),
            Triple(0.85f, 0.70f, 0.11f),
            Triple(0.50f, 0.88f, 0.13f),
        )
        for ((idx, orb) in orbs.withIndex()) {
            val (baseX, baseY, radiusRatio) = orb
            val floatY = (baseY * h) + (sin((progress * 2f * Math.PI) + idx).toFloat() * (h * 0.03f))
            val center = Offset(baseX * w, floatY)
            val orbRadius = w * radiusRatio
            val orbColor = if (idx % 2 == 0) mood.keyLightColor else mood.rimLightColor

            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orbColor.copy(alpha = 0.28f), orbColor.copy(alpha = 0.08f), Color.Transparent),
                    center = center,
                    radius = orbRadius,
                ),
                radius = orbRadius,
                center = center,
            )
        }

        // Cozy angled lofi rain streaks
        val rainColor = Color.White.copy(alpha = 0.18f)
        val rainStroke = (w * 0.010f).coerceAtLeast(1f)
        val rainX = listOf(w * 0.25f, w * 0.45f, w * 0.70f)
        for (rx in rainX) {
            drawScope.drawLine(
                color = rainColor,
                start = Offset(rx, h * 0.15f),
                end = Offset(rx - (w * 0.06f), h * 0.35f),
                strokeWidth = rainStroke,
                cap = StrokeCap.Round,
            )
        }
    }

    private fun drawComedyStage(
        drawScope: DrawScope,
        center: Offset,
        w: Float,
        h: Float,
        mood: BlobAvatarGenreMood,
    ) {
        // Stage warm spotlight circle on the mascot
        drawScope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    mood.keyLightColor.copy(alpha = 0.35f),
                    mood.keyLightColor.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
                center = center,
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = center,
        )

        // Top comedy curtain swag curves (Left & Right in warm golden amber)
        val curtainColor = Color(0xFFF59E0B).copy(alpha = 0.35f)
        val leftDrape = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.30f, 0f)
            cubicTo(w * 0.20f, h * 0.14f, w * 0.05f, h * 0.12f, 0f, h * 0.09f)
            close()
        }
        val rightDrape = Path().apply {
            moveTo(w, 0f)
            lineTo(w * 0.70f, 0f)
            cubicTo(w * 0.80f, h * 0.14f, w * 0.95f, h * 0.12f, w, h * 0.09f)
            close()
        }
        drawScope.drawPath(path = leftDrape, color = curtainColor)
        drawScope.drawPath(path = rightDrape, color = curtainColor)

        // Laugh sparkles
        val sparkleX = listOf(w * 0.20f, w * 0.82f)
        val sparkleY = listOf(h * 0.30f, h * 0.28f)
        for (i in sparkleX.indices) {
            drawScope.drawCircle(color = Color(0xFFFFD700), radius = w * 0.022f, center = Offset(sparkleX[i], sparkleY[i]))
            drawScope.drawCircle(color = Color.White, radius = w * 0.010f, center = Offset(sparkleX[i], sparkleY[i]))
        }
    }

    private fun drawMysticRunes(
        drawScope: DrawScope,
        center: Offset,
        w: Float,
        h: Float,
        mood: BlobAvatarGenreMood,
        progress: Float,
    ) {
        // Rotating arcane rune ring around the mascot
        val runeRadius = w * 0.44f
        val rotationAngle = progress * 360f

        drawScope.rotate(degrees = rotationAngle, pivot = center) {
            drawCircle(
                color = mood.rimLightColor.copy(alpha = 0.30f),
                radius = runeRadius,
                center = center,
                style = Stroke(width = (w * 0.010f).coerceAtLeast(1f)),
            )

            // Arcane rune tick marks
            val tickCount = 8
            val tickAngle = 360f / tickCount
            for (i in 0 until tickCount) {
                val rad = Math.toRadians((i * tickAngle).toDouble())
                val innerR = runeRadius - (w * 0.035f)
                val outerR = runeRadius + (w * 0.035f)
                val start = Offset(center.x + (innerR * cos(rad)).toFloat(), center.y + (innerR * sin(rad)).toFloat())
                val end = Offset(center.x + (outerR * cos(rad)).toFloat(), center.y + (outerR * sin(rad)).toFloat())
                drawLine(
                    color = mood.rimLightColor.copy(alpha = 0.50f),
                    start = start,
                    end = end,
                    strokeWidth = (w * 0.012f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Floating campfire embers
        val embers = listOf(
            Triple(0.25f, 0.80f, 0.40f),
            Triple(0.72f, 0.75f, 0.65f),
            Triple(0.35f, 0.60f, 0.85f),
            Triple(0.65f, 0.55f, 0.20f),
        )
        for ((idx, ember) in embers.withIndex()) {
            val (baseX, startY, phaseOffset) = ember
            val phase = (progress + phaseOffset) % 1f
            val emberY = (startY * h) - (phase * h * 0.40f)
            val emberX = (baseX * w) + (sin((phase * 2f * Math.PI) + idx).toFloat() * (w * 0.04f))
            val emberAlpha = (sin(phase * Math.PI).toFloat() * 0.85f).coerceIn(0f, 1f)

            drawScope.drawCircle(
                color = mood.keyLightColor.copy(alpha = emberAlpha),
                radius = (w * 0.020f).coerceAtLeast(1.5f),
                center = Offset(emberX, emberY),
            )
            drawScope.drawCircle(
                color = Color.White.copy(alpha = emberAlpha),
                radius = (w * 0.008f).coerceAtLeast(1f),
                center = Offset(emberX, emberY),
            )
        }
    }
}
