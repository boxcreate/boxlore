package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

internal object BlobAvatarAccessories {

    fun drawAccessory(
        drawScope: DrawScope,
        accessoryType: BlobAvatarGenreMood.AccessoryType,
        center: Offset,
        bodyWidth: Float,
        bodyHeight: Float,
        w: Float,
        mood: BlobAvatarGenreMood,
    ) {
        when (accessoryType) {
            BlobAvatarGenreMood.AccessoryType.DetectiveFedora ->
                drawDetectiveFedora(drawScope, center, bodyWidth, bodyHeight, w)
            BlobAvatarGenreMood.AccessoryType.CyberVisor ->
                drawCyberVisor(drawScope, center, bodyWidth, bodyHeight, w, mood.keyLightColor)
            BlobAvatarGenreMood.AccessoryType.AthleticSweatband ->
                drawAthleticSweatband(drawScope, center, bodyWidth, bodyHeight, w)
            BlobAvatarGenreMood.AccessoryType.BroadcastBoomMic ->
                drawBroadcastBoomMic(drawScope, center, bodyWidth, bodyHeight, w)
            BlobAvatarGenreMood.AccessoryType.CozyBeanie ->
                drawCozyBeanie(drawScope, center, bodyWidth, bodyHeight, w, mood.keyLightColor)
            BlobAvatarGenreMood.AccessoryType.StarSunglasses ->
                drawStarSunglasses(drawScope, center, bodyWidth, bodyHeight, w)
            BlobAvatarGenreMood.AccessoryType.WizardHat ->
                drawWizardHat(drawScope, center, bodyWidth, bodyHeight, w)
        }
    }

    private fun drawDetectiveFedora(
        drawScope: DrawScope,
        center: Offset,
        bodyWidth: Float,
        bodyHeight: Float,
        w: Float,
    ) {
        val hatCenterY = center.y - (bodyHeight * 0.46f)
        val hatWidth = bodyWidth * 0.88f
        val hatHeight = bodyHeight * 0.30f

        drawScope.rotate(degrees = -6f, pivot = Offset(center.x, hatCenterY)) {
            // Fedora Brim
            val brimRect = RoundRect(
                left = center.x - (hatWidth / 2f),
                top = hatCenterY + (hatHeight * 0.20f),
                right = center.x + (hatWidth / 2f),
                bottom = hatCenterY + (hatHeight * 0.48f),
                cornerRadius = CornerRadius(hatHeight * 0.20f, hatHeight * 0.20f),
            )
            drawRoundRect(
                color = Color(0xFF262E3B),
                topLeft = Offset(brimRect.left, brimRect.top),
                size = Size(brimRect.width, brimRect.height),
                cornerRadius = CornerRadius(hatHeight * 0.20f, hatHeight * 0.20f),
            )

            // Fedora Crown
            val crownPath = Path().apply {
                moveTo(center.x - (hatWidth * 0.30f), hatCenterY + (hatHeight * 0.25f))
                cubicTo(
                    center.x - (hatWidth * 0.28f),
                    hatCenterY - (hatHeight * 0.60f),
                    center.x - (hatWidth * 0.05f),
                    hatCenterY - (hatHeight * 0.40f),
                    center.x,
                    hatCenterY - (hatHeight * 0.50f),
                )
                cubicTo(
                    center.x + (hatWidth * 0.05f),
                    hatCenterY - (hatHeight * 0.40f),
                    center.x + (hatWidth * 0.28f),
                    hatCenterY - (hatHeight * 0.60f),
                    center.x + (hatWidth * 0.30f),
                    hatCenterY + (hatHeight * 0.25f),
                )
                close()
            }
            drawPath(path = crownPath, color = Color(0xFF333E4F))

            // Dark Ribbon Hatband
            drawRoundRect(
                color = Color(0xFF161A22),
                topLeft = Offset(center.x - (hatWidth * 0.29f), hatCenterY + (hatHeight * 0.12f)),
                size = Size(hatWidth * 0.58f, hatHeight * 0.16f),
                cornerRadius = CornerRadius(2f, 2f),
            )

            // Ribbon Gold Buckle
            drawRect(
                color = Color(0xFFFFC107),
                topLeft = Offset(center.x + (hatWidth * 0.10f), hatCenterY + (hatHeight * 0.13f)),
                size = Size(hatWidth * 0.06f, hatHeight * 0.14f),
                style = Stroke(width = (w * 0.012f).coerceAtLeast(1f)),
            )

            // Magnifying Glass
            val glassX = center.x + (bodyWidth * 0.38f)
            val glassY = center.y + (bodyHeight * 0.15f)
            val glassRadius = w * 0.09f
            drawCircle(color = Color(0xFF80DEEA).copy(alpha = 0.35f), radius = glassRadius, center = Offset(glassX, glassY))
            drawCircle(
                color = Color(0xFFE2E8F0),
                radius = glassRadius,
                center = Offset(glassX, glassY),
                style = Stroke(width = (w * 0.016f).coerceAtLeast(1.5f)),
            )
            drawLine(
                color = Color(0xFF8D6E63),
                start = Offset(glassX + (glassRadius * 0.70f), glassY + (glassRadius * 0.70f)),
                end = Offset(glassX + (glassRadius * 1.50f), glassY + (glassRadius * 1.50f)),
                strokeWidth = (w * 0.024f).coerceAtLeast(2f),
                cap = StrokeCap.Round,
            )
        }
    }

    private fun drawCyberVisor(
        drawScope: DrawScope,
        center: Offset,
        bodyWidth: Float,
        bodyHeight: Float,
        w: Float,
        neonColor: Color,
    ) {
        val visorY = center.y - (bodyHeight * 0.02f)
        val visorWidth = bodyWidth * 0.72f
        val visorHeight = bodyHeight * 0.18f

        // Visor glowing base
        val visorRect = RoundRect(
            left = center.x - (visorWidth / 2f),
            top = visorY - (visorHeight / 2f),
            right = center.x + (visorWidth / 2f),
            bottom = visorY + (visorHeight / 2f),
            cornerRadius = CornerRadius(visorHeight * 0.44f, visorHeight * 0.44f),
        )

        drawScope.drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    neonColor.copy(alpha = 0.85f),
                    Color(0xFF00E5FF).copy(alpha = 0.90f),
                    neonColor.copy(alpha = 0.85f),
                ),
            ),
            topLeft = Offset(visorRect.left, visorRect.top),
            size = Size(visorRect.width, visorRect.height),
            cornerRadius = CornerRadius(visorHeight * 0.44f, visorHeight * 0.44f),
        )

        // Visor White Center Scanline
        drawScope.drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(center.x - (visorWidth * 0.40f), visorY),
            end = Offset(center.x + (visorWidth * 0.40f), visorY),
            strokeWidth = (w * 0.012f).coerceAtLeast(1f),
            cap = StrokeCap.Round,
        )

        // Headphone Antenna with Beacon
        val antennaX = center.x - (bodyWidth * 0.46f)
        val antennaTopY = center.y - (bodyHeight * 0.50f)
        drawScope.drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(antennaX, center.y - (bodyHeight * 0.20f)),
            end = Offset(antennaX, antennaTopY),
            strokeWidth = (w * 0.015f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
        )
        drawScope.drawCircle(color = neonColor, radius = w * 0.035f, center = Offset(antennaX, antennaTopY))
        drawScope.drawCircle(color = Color.White, radius = w * 0.016f, center = Offset(antennaX, antennaTopY))
    }

    private fun drawAthleticSweatband(
        drawScope: DrawScope,
        center: Offset,
        bodyWidth: Float,
        bodyHeight: Float,
        w: Float,
    ) {
        val bandY = center.y - (bodyHeight * 0.22f)
        val bandWidth = bodyWidth * 0.86f
        val bandHeight = bodyHeight * 0.14f

        val sweatbandRect = RoundRect(
            left = center.x - (bandWidth / 2f),
            top = bandY - (bandHeight / 2f),
            right = center.x + (bandWidth / 2f),
            bottom = bandY + (bandHeight / 2f),
            cornerRadius = CornerRadius(bandHeight * 0.35f, bandHeight * 0.35f),
        )

        // Terrycloth Base
        drawScope.drawRoundRect(
            color = Color(0xFFF8FAFC),
            topLeft = Offset(sweatbandRect.left, sweatbandRect.top),
            size = Size(sweatbandRect.width, sweatbandRect.height),
            cornerRadius = CornerRadius(bandHeight * 0.35f, bandHeight * 0.35f),
        )

        // Athletic Stripes (Navy + Crimson)
        drawScope.drawLine(
            color = Color(0xFF1E3A8A),
            start = Offset(center.x - (bandWidth * 0.42f), bandY - (bandHeight * 0.20f)),
            end = Offset(center.x + (bandWidth * 0.42f), bandY - (bandHeight * 0.20f)),
            strokeWidth = (w * 0.014f).coerceAtLeast(1f),
        )
        drawScope.drawLine(
            color = Color(0xFFEF4444),
            start = Offset(center.x - (bandWidth * 0.42f), bandY + (bandHeight * 0.20f)),
            end = Offset(center.x + (bandWidth * 0.42f), bandY + (bandHeight * 0.20f)),
            strokeWidth = (w * 0.014f).coerceAtLeast(1f),
        )

        // Cute Sweat Drop on Forehead
        val dropX = center.x + (bodyWidth * 0.30f)
        val dropY = center.y - (bodyHeight * 0.12f)
        drawScope.drawCircle(color = Color(0xFF38BDF8), radius = w * 0.024f, center = Offset(dropX, dropY))
        drawScope.drawCircle(color = Color.White, radius = w * 0.010f, center = Offset(dropX - (w * 0.006f), dropY - (w * 0.006f)))
    }

    private fun drawBroadcastBoomMic(
        drawScope: DrawScope,
        center: Offset,
        bodyWidth: Float,
        bodyHeight: Float,
        w: Float,
    ) {
        val earCupLeft = center.x - (bodyWidth * 0.46f)
        val earCupY = center.y - (bodyHeight * 0.02f)
        val mouthX = center.x - (bodyWidth * 0.08f)
        val mouthY = center.y + (bodyHeight * 0.12f)

        // Gooseneck Boom Arm
        val boomPath = Path().apply {
            moveTo(earCupLeft, earCupY)
            cubicTo(
                earCupLeft + (bodyWidth * 0.10f),
                earCupY + (bodyHeight * 0.22f),
                mouthX - (bodyWidth * 0.15f),
                mouthY + (bodyHeight * 0.08f),
                mouthX,
                mouthY,
            )
        }
        drawScope.drawPath(
            path = boomPath,
            color = Color(0xFF475569),
            style = Stroke(width = (w * 0.020f).coerceAtLeast(1.5f), cap = StrokeCap.Round),
        )

        // Foam Windscreen Capsule
        val capsuleWidth = w * 0.08f
        val capsuleHeight = w * 0.055f
        drawScope.drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(mouthX - (capsuleWidth / 2f), mouthY - (capsuleHeight / 2f)),
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = CornerRadius(capsuleHeight * 0.40f, capsuleHeight * 0.40f),
        )
        // Red Audio Ring on Mic Tip
        drawScope.drawLine(
            color = Color(0xFFEF4444),
            start = Offset(mouthX + (capsuleWidth * 0.20f), mouthY - (capsuleHeight * 0.40f)),
            end = Offset(mouthX + (capsuleWidth * 0.20f), mouthY + (capsuleHeight * 0.40f)),
            strokeWidth = (w * 0.010f).coerceAtLeast(1f),
        )
    }

    private fun drawCozyBeanie(
        drawScope: DrawScope,
        center: Offset,
        bodyWidth: Float,
        bodyHeight: Float,
        w: Float,
        beanieColor: Color,
    ) {
        val beanieCenterY = center.y - (bodyHeight * 0.35f)
        val beanieWidth = bodyWidth * 0.88f
        val beanieHeight = bodyHeight * 0.34f

        // Slouchy Dome
        val domePath = Path().apply {
            moveTo(center.x - (beanieWidth * 0.44f), beanieCenterY + (beanieHeight * 0.20f))
            cubicTo(
                center.x - (beanieWidth * 0.40f),
                beanieCenterY - (beanieHeight * 0.85f),
                center.x + (beanieWidth * 0.40f),
                beanieCenterY - (beanieHeight * 0.85f),
                center.x + (beanieWidth * 0.44f),
                beanieCenterY + (beanieHeight * 0.20f),
            )
            close()
        }
        drawScope.drawPath(
            path = domePath,
            brush = Brush.verticalGradient(
                colors = listOf(beanieColor, beanieColor.copy(alpha = 0.85f)),
            ),
        )

        // Folded Ribbed Brim
        val brimRect = RoundRect(
            left = center.x - (beanieWidth * 0.46f),
            top = beanieCenterY + (beanieHeight * 0.10f),
            right = center.x + (beanieWidth * 0.46f),
            bottom = beanieCenterY + (beanieHeight * 0.36f),
            cornerRadius = CornerRadius(beanieHeight * 0.12f, beanieHeight * 0.12f),
        )
        drawScope.drawRoundRect(
            color = beanieColor.copy(alpha = 0.95f),
            topLeft = Offset(brimRect.left, brimRect.top),
            size = Size(brimRect.width, brimRect.height),
            cornerRadius = CornerRadius(beanieHeight * 0.12f, beanieHeight * 0.12f),
        )

        // Fluffy Pom-Pom Ball on Top
        val pomPomCenter = Offset(center.x, beanieCenterY - (beanieHeight * 0.65f))
        drawScope.drawCircle(color = Color.White.copy(alpha = 0.90f), radius = w * 0.065f, center = pomPomCenter)
    }

    private fun drawStarSunglasses(
        drawScope: DrawScope,
        center: Offset,
        bodyWidth: Float,
        bodyHeight: Float,
        w: Float,
    ) {
        val glassY = center.y - (bodyHeight * 0.02f)
        val eyeSpacing = bodyWidth * 0.18f

        for (sign in listOf(-1f, 1f)) {
            val starCenter = Offset(center.x + (sign * eyeSpacing), glassY)
            val starRadius = w * 0.075f

            // 5-Point Star Lens
            val starPath = Path()
            val points = 5
            val angleStep = Math.PI / points
            for (i in 0 until (2 * points)) {
                val r = if (i % 2 == 0) starRadius else starRadius * 0.52f
                val angle = (i * angleStep) - (Math.PI / 2.0)
                val x = starCenter.x + (r * kotlin.math.cos(angle)).toFloat()
                val y = starCenter.y + (r * kotlin.math.sin(angle)).toFloat()
                if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
            }
            starPath.close()

            drawScope.drawPath(path = starPath, color = Color(0xFF1E293B).copy(alpha = 0.90f))
            drawScope.drawPath(
                path = starPath,
                color = Color(0xFFFFD700),
                style = Stroke(width = (w * 0.016f).coerceAtLeast(1.5f), join = StrokeJoin.Round),
            )
            // Lens Glint
            drawScope.drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = starRadius * 0.28f,
                center = Offset(starCenter.x + (starRadius * 0.20f), starCenter.y - (starRadius * 0.20f)),
            )
        }

        // Bridge connecting lenses
        drawScope.drawLine(
            color = Color(0xFFFFD700),
            start = Offset(center.x - (w * 0.05f), glassY),
            end = Offset(center.x + (w * 0.05f), glassY),
            strokeWidth = (w * 0.016f).coerceAtLeast(1.5f),
        )
    }

    private fun drawWizardHat(
        drawScope: DrawScope,
        center: Offset,
        bodyWidth: Float,
        bodyHeight: Float,
        w: Float,
    ) {
        val hatCenterY = center.y - (bodyHeight * 0.40f)
        val hatWidth = bodyWidth * 0.90f
        val hatHeight = bodyHeight * 0.55f

        drawScope.rotate(degrees = 8f, pivot = Offset(center.x, hatCenterY)) {
            // Hat Wide Brim
            val brimRect = RoundRect(
                left = center.x - (hatWidth / 2f),
                top = hatCenterY + (hatHeight * 0.15f),
                right = center.x + (hatWidth / 2f),
                bottom = hatCenterY + (hatHeight * 0.38f),
                cornerRadius = CornerRadius(hatHeight * 0.12f, hatHeight * 0.12f),
            )
            drawRoundRect(
                color = Color(0xFF4C1D95),
                topLeft = Offset(brimRect.left, brimRect.top),
                size = Size(brimRect.width, brimRect.height),
                cornerRadius = CornerRadius(hatHeight * 0.12f, hatHeight * 0.12f),
            )

            // Hat Pointed Floppy Cone
            val conePath = Path().apply {
                moveTo(center.x - (hatWidth * 0.32f), hatCenterY + (hatHeight * 0.20f))
                cubicTo(
                    center.x - (hatWidth * 0.10f),
                    hatCenterY - (hatHeight * 0.30f),
                    center.x + (hatWidth * 0.10f),
                    hatCenterY - (hatHeight * 0.70f),
                    center.x + (hatWidth * 0.35f),
                    hatCenterY - (hatHeight * 0.85f),
                )
                cubicTo(
                    center.x + (hatWidth * 0.20f),
                    hatCenterY - (hatHeight * 0.50f),
                    center.x + (hatWidth * 0.25f),
                    hatCenterY - (hatHeight * 0.20f),
                    center.x + (hatWidth * 0.32f),
                    hatCenterY + (hatHeight * 0.20f),
                )
                close()
            }
            drawPath(path = conePath, color = Color(0xFF5B21B6))

            // Starry Gold Hatband
            drawRoundRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(center.x - (hatWidth * 0.30f), hatCenterY + (hatHeight * 0.10f)),
                size = Size(hatWidth * 0.60f, hatHeight * 0.12f),
                cornerRadius = CornerRadius(2f, 2f),
            )
            // Golden Star Sparkle on Tip
            val tipX = center.x + (hatWidth * 0.35f)
            val tipY = hatCenterY - (hatHeight * 0.85f)
            drawCircle(color = Color(0xFFFDE047), radius = w * 0.038f, center = Offset(tipX, tipY))
        }
    }
}
