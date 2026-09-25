package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class BlobAvatarAnimationState(
    val floatProgress: State<Float>,
    val breatheScaleX: State<Float>,
    val breatheScaleY: State<Float>,
    val eyeOpenScale: State<Float>,
    val headBobAngle: State<Float>,
    val pupilLookRatioX: State<Float>,
    val pupilLookRatioY: State<Float>,
)

@Composable
internal fun rememberBlobAvatarAnimationState(): BlobAvatarAnimationState {
    val infiniteTransition = rememberInfiniteTransition(label = "BlobAvatarTransition")

    // Gentle, calming float
    val floatProgress = infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobFloatProgress",
    )

    // Soft squishy breathing
    val breatheScaleX = infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobBreatheX",
    )
    val breatheScaleY = infiniteTransition.animateFloat(
        initialValue = 1.015f,
        targetValue = 0.985f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobBreatheY",
    )

    // Sweet subtle head groove to the music
    val headBobAngle = infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobHeadBob",
    )

    // Natural, cute blink with double-blink
    val eyeOpenScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4600
                1f at 0
                1f at 3600
                0.08f at 3720
                1f at 3840
                0.08f at 3980
                1f at 4100
                1f at 4600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "BlobEyeOpen",
    )

    val (pupilLookRatioX, pupilLookRatioY) = rememberPupilLookAnimations(infiniteTransition)

    return BlobAvatarAnimationState(
        floatProgress = floatProgress,
        breatheScaleX = breatheScaleX,
        breatheScaleY = breatheScaleY,
        eyeOpenScale = eyeOpenScale,
        headBobAngle = headBobAngle,
        pupilLookRatioX = pupilLookRatioX,
        pupilLookRatioY = pupilLookRatioY,
    )
}

@Composable
private fun rememberPupilLookAnimations(
    transition: androidx.compose.animation.core.InfiniteTransition,
): Pair<State<Float>, State<Float>> {
    val pupilLookRatioX = transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                0f at 2000
                0.7f at 2600
                0.7f at 3600
                0f at 4200
                -0.7f at 4800
                -0.7f at 5600
                0f at 6000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "BlobPupilLookX",
    )
    val pupilLookRatioY = transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                0f at 2000
                -0.4f at 2600
                -0.4f at 3600
                0f at 4200
                0.4f at 4800
                0.4f at 5600
                0f at 6000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "BlobPupilLookY",
    )
    return Pair(pupilLookRatioX, pupilLookRatioY)
}

/**
 * An ultra-cute, 3D clay-shaded mochi blob wearing cozy studio headphones.
 * Features warm rosy cheeks, glossy kawaii button eyes with bright catchlights,
 * a sweet delicate smile, and a gentle music groove.
 */
@Composable
internal fun AnimatedBlobAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 82.dp,
) {
    val animState = rememberBlobAvatarAnimationState()

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val bodyBaseColor = if (isDark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val bodyShadowColor = if (isDark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    }
    val bodyBounceColor = if (isDark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    }

    val facialColor = Color(0xFF1E222A)

    val blushColor = if (isDark) {
        Color(0xFFFF7597).copy(alpha = 0.45f)
    } else {
        Color(0xFFFF7B95).copy(alpha = 0.38f)
    }

    val headbandColor = if (isDark) Color(0xFF282C34) else Color(0xFF3C4048)
    val cushionColor = if (isDark) Color(0xFF1C1F26) else Color(0xFF2E323A)
    val headphoneCupColor = MaterialTheme.colorScheme.tertiary
    val headphoneAccentColor = if (isDark) Color.White.copy(alpha = 0.90f) else MaterialTheme.colorScheme.primary
    val shadowBaseAlpha = if (isDark) 0.10f else 0.18f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val floatOffsetPx = BlobAvatarGeometry.computeFloatOffset(h, animState.floatProgress.value)
            val center = Offset(w / 2f, (h / 2f) + floatOffsetPx)

            // 1. Soft 3D Ground Drop Shadow
            drawGroundShadow(
                center = center,
                w = w,
                h = h,
                floatProgress = animState.floatProgress.value,
                baseAlpha = shadowBaseAlpha,
            )

            // 2. Groovy Head-Bobbing Container
            rotate(degrees = animState.headBobAngle.value, pivot = center) {
                val bodyWidth = BlobAvatarGeometry.computeBodyWidth(w, animState.breatheScaleX.value)
                val bodyHeight = BlobAvatarGeometry.computeBodyHeight(h, animState.breatheScaleY.value)

                // 2A. Headband Arch Over the Head
                drawHeadband(
                    center = center,
                    bodyWidth = bodyWidth,
                    bodyHeight = bodyHeight,
                    bandColor = headbandColor,
                    w = w,
                )

                // 2B. 3D Volumetric Clay Mochi Body
                drawClayMochiBody(
                    center = center,
                    bodyWidth = bodyWidth,
                    bodyHeight = bodyHeight,
                    baseColor = bodyBaseColor,
                    shadowColor = bodyShadowColor,
                    bounceColor = bodyBounceColor,
                )

                // 2C. Soft Rosy Blush Cheeks
                drawRosyBlush(
                    center = center,
                    bodyWidth = bodyWidth,
                    bodyHeight = bodyHeight,
                    blushColor = blushColor,
                    w = w,
                )

                // 2D. Kawaii Glossy Button Eyes (No creepy sclera!)
                drawKawaiiEyes(
                    center = center,
                    bodyWidth = bodyWidth,
                    bodyHeight = bodyHeight,
                    animState = animState,
                    eyeColor = facialColor,
                    w = w,
                )

                // 2E. Sweet Delicate Smile
                drawSweetSmile(
                    center = center,
                    bodyHeight = bodyHeight,
                    smileColor = facialColor,
                    w = w,
                )

                // 2F. Cozy Padded Over-Ear Headphones
                drawCozyHeadphones(
                    center = center,
                    bodyWidth = bodyWidth,
                    bodyHeight = bodyHeight,
                    cupColor = headphoneCupColor,
                    cushionColor = cushionColor,
                    accentColor = headphoneAccentColor,
                    w = w,
                )
            }
        }
    }
}

private fun DrawScope.drawGroundShadow(
    center: Offset,
    w: Float,
    h: Float,
    floatProgress: Float,
    baseAlpha: Float,
) {
    val shadowWidth = w * (0.55f + (floatProgress * 0.03f))
    val shadowHeight = h * 0.10f
    val shadowAlpha = (baseAlpha - (floatProgress * 0.04f)).coerceIn(0.04f, 0.24f)
    val shadowCenter = Offset(center.x, h * 0.90f)

    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = shadowAlpha),
                Color.Black.copy(alpha = shadowAlpha * 0.35f),
                Color.Transparent,
            ),
            center = shadowCenter,
            radius = shadowWidth / 2f,
        ),
        topLeft = Offset(shadowCenter.x - (shadowWidth / 2f), shadowCenter.y - (shadowHeight / 2f)),
        size = Size(shadowWidth, shadowHeight),
    )
}

private fun DrawScope.drawClayMochiBody(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    baseColor: Color,
    shadowColor: Color,
    bounceColor: Color,
) {
    val cornerRadius = CornerRadius(bodyWidth * 0.46f, bodyHeight * 0.46f)
    val bodyRect = RoundRect(
        left = center.x - (bodyWidth / 2f),
        top = center.y - (bodyHeight / 2f),
        right = center.x + (bodyWidth / 2f),
        bottom = center.y + (bodyHeight / 2f),
        cornerRadius = cornerRadius,
    )
    val bodyPath = Path().apply { addRoundRect(bodyRect) }

    // Soft 3D volumetric radial gradient (light from top-left)
    val lightSource = Offset(center.x - (bodyWidth * 0.20f), center.y - (bodyHeight * 0.22f))
    val bodyBrush = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.50f),
            baseColor,
            shadowColor,
        ),
        center = lightSource,
        radius = bodyWidth * 0.85f,
    )
    drawPath(path = bodyPath, brush = bodyBrush)

    // Soft subsurface bounce light on bottom curve
    val bounceBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, bounceColor),
        start = Offset(center.x, center.y),
        end = Offset(center.x, center.y + (bodyHeight / 2f)),
    )
    drawPath(path = bodyPath, brush = bounceBrush)

    // Gentle glossy shine on top-left
    val specWidth = bodyWidth * 0.34f
    val specHeight = bodyHeight * 0.14f
    val specCenter = Offset(center.x - (bodyWidth * 0.18f), center.y - (bodyHeight * 0.26f))
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.15f),
                Color.Transparent,
            ),
            center = specCenter,
            radius = specWidth / 2f,
        ),
        topLeft = Offset(specCenter.x - (specWidth / 2f), specCenter.y - (specHeight / 2f)),
        size = Size(specWidth, specHeight),
    )
}

private fun DrawScope.drawHeadband(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    bandColor: Color,
    w: Float,
) {
    val bandTopY = BlobAvatarGeometry.computeHeadbandTopY(center.y, bodyHeight)
    val bandLeftX = center.x - (bodyWidth * 0.44f)
    val bandRightX = center.x + (bodyWidth * 0.44f)
    val bandStartY = center.y - (bodyHeight * 0.10f)

    val bandPath = Path().apply {
        moveTo(bandLeftX, bandStartY)
        cubicTo(
            bandLeftX,
            bandTopY,
            bandRightX,
            bandTopY,
            bandRightX,
            bandStartY,
        )
    }

    val bandStroke = BlobAvatarGeometry.computeHeadbandStroke(w)

    drawPath(
        path = bandPath,
        color = bandColor,
        style = Stroke(
            width = bandStroke,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    drawPath(
        path = bandPath,
        color = Color.White.copy(alpha = 0.30f),
        style = Stroke(
            width = bandStroke * 0.32f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun DrawScope.drawCozyHeadphones(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    cupColor: Color,
    cushionColor: Color,
    accentColor: Color,
    w: Float,
) {
    val cupCenterY = center.y - (bodyHeight * 0.02f)
    val cupSpacingX = BlobAvatarGeometry.computeCupSpacing(bodyWidth)

    val cupWidth = BlobAvatarGeometry.computeCupWidth(w)
    val cupHeight = w * BlobAvatarGeometry.CUP_HEIGHT_RATIO

    val ears = listOf(
        Pair(center.x - cupSpacingX, -4f),
        Pair(center.x + cupSpacingX, 4f),
    )

    for ((cupX, tilt) in ears) {
        rotate(degrees = tilt, pivot = Offset(cupX, cupCenterY)) {
            // Soft inner cushion pill
            val cushionRect = RoundRect(
                left = cupX - (cupWidth * 0.48f),
                top = cupCenterY - (cupHeight * 0.48f),
                right = cupX + (cupWidth * 0.48f),
                bottom = cupCenterY + (cupHeight * 0.48f),
                cornerRadius = CornerRadius(cupWidth * 0.44f, cupWidth * 0.44f),
            )
            drawRoundRect(
                color = cushionColor,
                topLeft = Offset(cushionRect.left, cushionRect.top),
                size = Size(cushionRect.width, cushionRect.height),
                cornerRadius = CornerRadius(cupWidth * 0.44f, cupWidth * 0.44f),
            )

            // Outer pastel dome shell
            val shellWidth = cupWidth * 0.76f
            val shellHeight = cupHeight * 0.86f
            val shellLeft = if (cupX < center.x) cupX - (cupWidth * 0.50f) else cupX - (shellWidth * 0.46f)
            val shellTop = cupCenterY - (shellHeight * 0.50f)
            val shellCenter = Offset(shellLeft + (shellWidth / 2f), shellTop + (shellHeight / 2f))

            val shellBrush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f),
                    cupColor,
                    cupColor.copy(alpha = 0.80f),
                ),
                center = Offset(shellCenter.x - (shellWidth * 0.18f), shellCenter.y - (shellHeight * 0.18f)),
                radius = shellWidth * 0.72f,
            )

            drawRoundRect(
                brush = shellBrush,
                topLeft = Offset(shellLeft, shellTop),
                size = Size(shellWidth, shellHeight),
                cornerRadius = CornerRadius(shellWidth * 0.44f, shellWidth * 0.44f),
            )

            // Metallic rim line
            drawRoundRect(
                color = Color.White.copy(alpha = 0.38f),
                topLeft = Offset(shellLeft, shellTop),
                size = Size(shellWidth, shellHeight),
                cornerRadius = CornerRadius(shellWidth * 0.44f, shellWidth * 0.44f),
                style = Stroke(width = (w * 0.014f).coerceAtLeast(1f)),
            )

            // Center audio dot
            val dotRadius = shellWidth * 0.20f
            drawCircle(
                color = accentColor,
                radius = dotRadius,
                center = shellCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = dotRadius * 0.45f,
                center = shellCenter,
            )
        }
    }
}

private fun DrawScope.drawRosyBlush(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    blushColor: Color,
    w: Float,
) {
    val blushY = center.y + (bodyHeight * 0.10f)
    val blushSpacing = BlobAvatarGeometry.computeBlushSpacing(bodyWidth)
    val blushRadius = BlobAvatarGeometry.computeBlushRadius(w)

    for (sign in listOf(-1f, 1f)) {
        val blushCenter = Offset(center.x + (sign * blushSpacing), blushY)
        drawCircle(
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

private fun DrawScope.drawKawaiiEyes(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    animState: BlobAvatarAnimationState,
    eyeColor: Color,
    w: Float,
) {
    val eyeSpacing = bodyWidth * BlobAvatarGeometry.EYE_SPACING_RATIO
    val eyeCenterY = center.y - (bodyHeight * 0.02f)
    val (eyeWidth, eyeHeight) = BlobAvatarGeometry.computeEyeDimensions(w)
    val strokeWidth = BlobAvatarGeometry.computeStrokeWidth(w)
    val eyeOpenScale = animState.eyeOpenScale.value

    val (maxShiftX, maxShiftY) = BlobAvatarGeometry.computeEyeMaxShifts(eyeWidth, eyeHeight)
    val shiftX = maxShiftX * animState.pupilLookRatioX.value
    val shiftY = maxShiftY * animState.pupilLookRatioY.value

    val eyes = listOf(
        Offset(center.x - eyeSpacing, eyeCenterY),
        Offset(center.x + eyeSpacing, eyeCenterY),
    )

    for (eyeCenter in eyes) {
        if (eyeOpenScale > 0.25f) {
            val currentEyeHeight = eyeHeight * eyeOpenScale
            val currentEyeTop = eyeCenter.y - (currentEyeHeight / 2f) + (shiftY * eyeOpenScale)
            val currentEyeLeft = eyeCenter.x - (eyeWidth / 2f) + shiftX

            // Glossy Dark Button Eye Oval (Super friendly, like Kirby/Sanrio)
            drawOval(
                color = eyeColor,
                topLeft = Offset(currentEyeLeft, currentEyeTop),
                size = Size(eyeWidth, currentEyeHeight),
            )

            // Main Sweet White Catchlight Sparkle (top-right)
            val mainGlintRadius = eyeWidth * 0.36f * eyeOpenScale
            drawCircle(
                color = Color.White,
                radius = mainGlintRadius,
                center = Offset(
                    currentEyeLeft + (eyeWidth * 0.65f),
                    currentEyeTop + (currentEyeHeight * 0.32f),
                ),
            )

            // Secondary Tiny Sparkle (bottom-left)
            val secGlintRadius = eyeWidth * 0.18f * eyeOpenScale
            drawCircle(
                color = Color.White.copy(alpha = 0.90f),
                radius = secGlintRadius,
                center = Offset(
                    currentEyeLeft + (eyeWidth * 0.32f),
                    currentEyeTop + (currentEyeHeight * 0.72f),
                ),
            )
        } else {
            // Sweet happy closed eyes (^_^) during blink
            drawArc(
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

private fun DrawScope.drawSweetSmile(
    center: Offset,
    bodyHeight: Float,
    smileColor: Color,
    w: Float,
) {
    val smileWidth = w * 0.12f
    val smileHeight = w * 0.065f
    val strokeWidth = (w * 0.026f).coerceAtLeast(1.5f)

    // Sweet, gentle, friendly smile arc (◡)
    drawArc(
        color = smileColor,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - (smileWidth / 2f), center.y + (bodyHeight * 0.10f)),
        size = Size(smileWidth, smileHeight),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}
