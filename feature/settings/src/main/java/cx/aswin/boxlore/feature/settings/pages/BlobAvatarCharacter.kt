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

internal data class CharacterDimensions(
    val center: Offset,
    val bodyWidth: Float,
    val bodyHeight: Float,
    val w: Float,
)

internal object BlobAvatarCharacter {

    fun drawCharacter(
        drawScope: DrawScope,
        dimensions: CharacterDimensions,
        renderContext: BlobAvatarRenderContext,
    ) {
        val palette = renderContext.palette
        drawHeadband(drawScope, dimensions, palette.headbandColor)
        drawClayMochiBody(drawScope, dimensions, palette)
        drawRosyBlush(drawScope, dimensions, palette.blushColor)
        drawKawaiiEyes(drawScope, dimensions, renderContext.animState, palette.facialColor, renderContext.expression)
        drawSweetSmile(drawScope, dimensions, palette.facialColor, renderContext.expression.isLaughing)
        drawCozyHeadphones(drawScope, dimensions, palette, renderContext.animState.bassPulseScale.value)
        BlobAvatarAccessories.drawAccessory(
            drawScope = drawScope,
            accessoryType = renderContext.mood.accessoryType,
            center = dimensions.center,
            bodyWidth = dimensions.bodyWidth,
            bodyHeight = dimensions.bodyHeight,
            w = dimensions.w,
            mood = renderContext.mood,
        )
    }

    private fun drawClayMochiBody(
        drawScope: DrawScope,
        dimensions: CharacterDimensions,
        palette: BlobAvatarPalette,
    ) {
        val center = dimensions.center
        val bodyWidth = dimensions.bodyWidth
        val bodyHeight = dimensions.bodyHeight
        val cornerRadius = CornerRadius(bodyWidth * 0.46f, bodyHeight * 0.46f)
        val bodyRect = RoundRect(
            left = center.x - (bodyWidth / 2f),
            top = center.y - (bodyHeight / 2f),
            right = center.x + (bodyWidth / 2f),
            bottom = center.y + (bodyHeight / 2f),
            cornerRadius = cornerRadius,
        )
        val bodyPath = Path().apply { addRoundRect(bodyRect) }

        val lightSource = Offset(center.x - (bodyWidth * 0.20f), center.y - (bodyHeight * 0.22f))
        val bodyBrush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                palette.bodyBase,
                palette.bodyShadow,
            ),
            center = lightSource,
            radius = bodyWidth * 0.85f,
        )
        drawScope.drawPath(path = bodyPath, brush = bodyBrush)

        val bounceBrush = Brush.linearGradient(
            colors = listOf(Color.Transparent, palette.bodyBounce),
            start = Offset(center.x, center.y),
            end = Offset(center.x, center.y + (bodyHeight / 2f)),
        )
        drawScope.drawPath(path = bodyPath, brush = bounceBrush)

        val specWidth = bodyWidth * 0.34f
        val specHeight = bodyHeight * 0.14f
        val specCenter = Offset(center.x - (bodyWidth * 0.18f), center.y - (bodyHeight * 0.26f))
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.70f),
                    Color.White.copy(alpha = 0.25f),
                    Color.Transparent,
                ),
                center = specCenter,
                radius = specWidth / 2f,
            ),
            topLeft = Offset(specCenter.x - (specWidth / 2f), specCenter.y - (specHeight / 2f)),
            size = Size(specWidth, specHeight),
        )
    }

    private fun drawHeadband(
        drawScope: DrawScope,
        dimensions: CharacterDimensions,
        bandColor: Color,
    ) {
        val center = dimensions.center
        val bodyWidth = dimensions.bodyWidth
        val bodyHeight = dimensions.bodyHeight
        val w = dimensions.w
        val bandTopY = BlobAvatarGeometry.computeHeadbandTopY(center.y, bodyHeight)
        val bandLeftX = center.x - (bodyWidth * 0.44f)
        val bandRightX = center.x + (bodyWidth * 0.44f)
        val bandStartY = center.y - (bodyHeight * 0.10f)

        val bandPath = Path().apply {
            moveTo(bandLeftX, bandStartY)
            cubicTo(bandLeftX, bandTopY, bandRightX, bandTopY, bandRightX, bandStartY)
        }

        val bandStroke = BlobAvatarGeometry.computeHeadbandStroke(w)

        drawScope.drawPath(
            path = bandPath,
            color = bandColor,
            style = Stroke(width = bandStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        drawScope.drawPath(
            path = bandPath,
            color = Color.White.copy(alpha = 0.45f),
            style = Stroke(width = bandStroke * 0.30f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    private fun drawCozyHeadphones(
        drawScope: DrawScope,
        dimensions: CharacterDimensions,
        palette: BlobAvatarPalette,
        bassScale: Float,
    ) {
        val center = dimensions.center
        val bodyWidth = dimensions.bodyWidth
        val bodyHeight = dimensions.bodyHeight
        val w = dimensions.w
        val cupCenterY = center.y - (bodyHeight * 0.02f)
        val (cupSpacingX, baseCupWidth) = BlobAvatarGeometry.computeCupParams(w, bodyWidth, bassScale)
        val cupHeight = w * BlobAvatarGeometry.CUP_HEIGHT_RATIO * bassScale

        val ears = listOf(
            Pair(center.x - cupSpacingX, -4f),
            Pair(center.x + cupSpacingX, 4f),
        )

        for ((cupX, tilt) in ears) {
            drawScope.rotate(degrees = tilt, pivot = Offset(cupX, cupCenterY)) {
                val cushionRect = RoundRect(
                    left = cupX - (baseCupWidth * 0.48f),
                    top = cupCenterY - (cupHeight * 0.48f),
                    right = cupX + (baseCupWidth * 0.48f),
                    bottom = cupCenterY + (cupHeight * 0.48f),
                    cornerRadius = CornerRadius(baseCupWidth * 0.44f, baseCupWidth * 0.44f),
                )
                drawRoundRect(
                    color = palette.cushionColor,
                    topLeft = Offset(cushionRect.left, cushionRect.top),
                    size = Size(cushionRect.width, cushionRect.height),
                    cornerRadius = CornerRadius(baseCupWidth * 0.44f, baseCupWidth * 0.44f),
                )

                val shellWidth = baseCupWidth * 0.78f
                val shellHeight = cupHeight * 0.88f
                val shellLeft = if (cupX < center.x) cupX - (baseCupWidth * 0.50f) else cupX - (shellWidth * 0.46f)
                val shellTop = cupCenterY - (shellHeight * 0.50f)
                val shellCenter = Offset(shellLeft + (shellWidth / 2f), shellTop + (shellHeight / 2f))

                val shellBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.60f),
                        palette.headphoneCup,
                        palette.headphoneCup.copy(alpha = 0.85f),
                    ),
                    center = Offset(shellCenter.x - (shellWidth * 0.18f), shellCenter.y - (shellHeight * 0.18f)),
                    radius = shellWidth * 0.75f,
                )

                drawRoundRect(
                    brush = shellBrush,
                    topLeft = Offset(shellLeft, shellTop),
                    size = Size(shellWidth, shellHeight),
                    cornerRadius = CornerRadius(shellWidth * 0.44f, shellWidth * 0.44f),
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.50f),
                    topLeft = Offset(shellLeft, shellTop),
                    size = Size(shellWidth, shellHeight),
                    cornerRadius = CornerRadius(shellWidth * 0.44f, shellWidth * 0.44f),
                    style = Stroke(width = (w * 0.015f).coerceAtLeast(1f)),
                )

                val dotRadius = shellWidth * 0.22f
                drawCircle(color = palette.headphoneAccent, radius = dotRadius, center = shellCenter)
                drawCircle(color = Color.White.copy(alpha = 0.75f), radius = dotRadius * 0.45f, center = shellCenter)
            }
        }
    }

    private fun drawRosyBlush(
        drawScope: DrawScope,
        dimensions: CharacterDimensions,
        blushColor: Color,
    ) {
        val center = dimensions.center
        val bodyWidth = dimensions.bodyWidth
        val bodyHeight = dimensions.bodyHeight
        val w = dimensions.w
        val blushY = center.y + (bodyHeight * 0.10f)
        val (blushSpacing, blushRadius) = BlobAvatarGeometry.computeBlushParams(w, bodyWidth)

        for (sign in listOf(-1f, 1f)) {
            val blushCenter = Offset(center.x + (sign * blushSpacing), blushY)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blushColor, blushColor.copy(alpha = 0.22f), Color.Transparent),
                    center = blushCenter,
                    radius = blushRadius,
                ),
                radius = blushRadius,
                center = blushCenter,
            )
        }
    }

    private fun drawKawaiiEyes(
        drawScope: DrawScope,
        dimensions: CharacterDimensions,
        animState: BlobAvatarAnimationState,
        eyeColor: Color,
        expression: BlobFacialExpression,
    ) {
        val center = dimensions.center
        val bodyWidth = dimensions.bodyWidth
        val bodyHeight = dimensions.bodyHeight
        val w = dimensions.w
        val eyeSpacing = bodyWidth * BlobAvatarGeometry.EYE_SPACING_RATIO
        val eyeCenterY = center.y - (bodyHeight * 0.02f)
        val (eyeWidth, eyeHeight) = BlobAvatarGeometry.computeEyeDimensions(w)
        val strokeWidth = BlobAvatarGeometry.computeStrokeWidth(w)
        val eyeOpenScale = if (expression.isWinking) 0.08f else animState.eyeOpenScale.value

        val (maxShiftX, maxShiftY) = BlobAvatarGeometry.computeEyeMaxShifts(eyeWidth, eyeHeight)
        val shiftX = maxShiftX * animState.pupilLookRatioX.value
        val shiftY = maxShiftY * animState.pupilLookRatioY.value

        val eyes = listOf(
            Pair(Offset(center.x - eyeSpacing, eyeCenterY), expression.isClosed),
            Pair(Offset(center.x + eyeSpacing, eyeCenterY), expression.isClosed || expression.isWinking),
        )

        for ((eyeCenter, isThisClosedOrWinking) in eyes) {
            val currentScale = if (isThisClosedOrWinking) 0.08f else eyeOpenScale
            if (currentScale > 0.25f) {
                val currentEyeHeight = eyeHeight * currentScale
                val currentEyeTop = eyeCenter.y - (currentEyeHeight / 2f) + (shiftY * currentScale)
                val currentEyeLeft = eyeCenter.x - (eyeWidth / 2f) + shiftX

                drawScope.drawOval(
                    color = eyeColor,
                    topLeft = Offset(currentEyeLeft, currentEyeTop),
                    size = Size(eyeWidth, currentEyeHeight),
                )

                val mainGlintRadius = eyeWidth * 0.36f * currentScale
                drawScope.drawCircle(
                    color = Color.White,
                    radius = mainGlintRadius,
                    center = Offset(
                        currentEyeLeft + (eyeWidth * 0.65f),
                        currentEyeTop + (currentEyeHeight * 0.32f),
                    ),
                )

                val secGlintRadius = eyeWidth * 0.18f * currentScale
                drawScope.drawCircle(
                    color = Color.White.copy(alpha = 0.90f),
                    radius = secGlintRadius,
                    center = Offset(
                        currentEyeLeft + (eyeWidth * 0.32f),
                        currentEyeTop + (currentEyeHeight * 0.72f),
                    ),
                )
            } else {
                drawScope.drawArc(
                    color = eyeColor,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(eyeCenter.x - (eyeWidth * 0.90f), eyeCenter.y - (eyeHeight * 0.30f)),
                    size = Size(eyeWidth * 1.8f, eyeHeight * 0.75f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
    }

    private fun drawSweetSmile(
        drawScope: DrawScope,
        dimensions: CharacterDimensions,
        smileColor: Color,
        isLaughing: Boolean,
    ) {
        val center = dimensions.center
        val bodyHeight = dimensions.bodyHeight
        val w = dimensions.w
        val smileWidth = w * (if (isLaughing) 0.16f else 0.12f)
        val smileHeight = w * (if (isLaughing) 0.10f else 0.065f)
        val strokeWidth = (w * 0.026f).coerceAtLeast(1.5f)

        if (isLaughing) {
            val mouthTopY = center.y + (bodyHeight * 0.09f)
            val mouthPath = Path().apply {
                moveTo(center.x - (smileWidth / 2f), mouthTopY)
                lineTo(center.x + (smileWidth / 2f), mouthTopY)
                cubicTo(
                    center.x + (smileWidth / 2f),
                    mouthTopY + smileHeight,
                    center.x - (smileWidth / 2f),
                    mouthTopY + smileHeight,
                    center.x - (smileWidth / 2f),
                    mouthTopY,
                )
                close()
            }
            drawScope.drawPath(path = mouthPath, color = smileColor)
            val tongueRadius = smileWidth * 0.26f
            drawScope.drawCircle(
                color = Color(0xFFFF5277),
                radius = tongueRadius,
                center = Offset(center.x, mouthTopY + (smileHeight * 0.65f)),
            )
        } else {
            drawScope.drawArc(
                color = smileColor,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - (smileWidth / 2f), center.y + (bodyHeight * 0.10f)),
                size = Size(smileWidth, smileHeight),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}
