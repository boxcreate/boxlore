package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class BlobAvatarAnimationState(
    val floatProgress: State<Float>,
    val breatheScaleX: State<Float>,
    val breatheScaleY: State<Float>,
    val eyeOpenScale: State<Float>,
    val headBobAngle: State<Float>,
    val pupilLookRatioX: State<Float>,
    val pupilLookRatioY: State<Float>,
    val soundwaveProgress: State<Float>,
    val particleProgress: State<Float>,
    val bassPulseScale: State<Float>,
)

@Composable
internal fun rememberBlobAvatarAnimationState(): BlobAvatarAnimationState {
    val infiniteTransition = rememberInfiniteTransition(label = "BlobAvatarTransition")

    val floatProgress = infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobFloatProgress",
    )

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

    val headBobAngle = infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobHeadBob",
    )

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

    val soundwaveProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "BlobSoundwave",
    )

    val particleProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "BlobParticles",
    )

    val bassPulseScale = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobBassPulse",
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
        soundwaveProgress = soundwaveProgress,
        particleProgress = particleProgress,
        bassPulseScale = bassPulseScale,
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

internal data class BlobAvatarPalette(
    val bodyBase: Color,
    val bodyShadow: Color,
    val bodyBounce: Color,
    val facialColor: Color,
    val blushColor: Color,
    val headbandColor: Color,
    val cushionColor: Color,
    val headphoneCup: Color,
    val headphoneAccent: Color,
    val shadowAlpha: Float,
)

@Composable
internal fun resolveAvatarPalette(
    genreMood: BlobAvatarGenreMood,
    isDark: Boolean,
): BlobAvatarPalette {
    val primaryColor = MaterialTheme.colorScheme.primary

    val bodyBase = if (isDark) {
        Color(0xFFF1F5F9)
    } else {
        Color(0xFFFFFDF8)
    }

    val bodyShadow = if (isDark) {
        Color(0xFFCBD5E1)
    } else {
        Color(0xFFE2E8F0)
    }

    val bodyBounce = if (isDark) {
        genreMood.rimLightColor.copy(alpha = 0.38f)
    } else {
        primaryColor.copy(alpha = 0.20f)
    }

    val facialColor = Color(0xFF0F172A)
    val blushColor = Color(0xFFFF5277).copy(alpha = if (isDark) 0.50f else 0.40f)

    val headbandColor = if (isDark) Color(0xFF1E293B) else Color(0xFF334155)
    val cushionColor = if (isDark) Color(0xFF0F172A) else Color(0xFF1E293B)
    val headphoneCup = genreMood.keyLightColor
    val headphoneAccent = genreMood.rimLightColor
    val shadowAlpha = if (isDark) 0.22f else 0.18f

    return BlobAvatarPalette(
        bodyBase = bodyBase,
        bodyShadow = bodyShadow,
        bodyBounce = bodyBounce,
        facialColor = facialColor,
        blushColor = blushColor,
        headbandColor = headbandColor,
        cushionColor = cushionColor,
        headphoneCup = headphoneCup,
        headphoneAccent = headphoneAccent,
        shadowAlpha = shadowAlpha,
    )
}

/**
 * An ultra-expressive, tactile 3D mochi mascot avatar with dual studio spotlights,
 * concentric headphone soundwaves, spring-physics squish on tap, and podcast genre moods.
 */
@Composable
internal fun AnimatedBlobAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    initialMood: BlobAvatarGenreMood? = null,
) {
    val animState = rememberBlobAvatarAnimationState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var activeMood by remember {
        mutableStateOf(initialMood ?: BlobAvatarGenreMood.random())
    }

    val palette = resolveAvatarPalette(activeMood, isDark)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val squishScaleY = remember { Animatable(1f) }
    var isWinking by remember { mutableStateOf(false) }

    val onTapAvatar = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        activeMood = BlobAvatarGenreMood.next(activeMood)
        isWinking = true
        coroutineScope.launch {
            squishScaleY.snapTo(0.80f)
            squishScaleY.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        coroutineScope.launch {
            delay(650)
            isWinking = false
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTapAvatar() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val floatOffsetPx = BlobAvatarGeometry.computeFloatOffset(h, animState.floatProgress.value)
            val center = Offset(w / 2f, (h / 2f) + floatOffsetPx)

            drawDualSpotlights(mood = activeMood, w = w, h = h, isDark = isDark)

            drawGroundShadow(
                center = center,
                w = w,
                h = h,
                floatProgress = animState.floatProgress.value,
                baseAlpha = palette.shadowAlpha,
            )

            drawSoundwaveRipples(
                center = center,
                w = w,
                h = h,
                animProgress = animState.soundwaveProgress.value,
                waveColor = activeMood.rimLightColor,
            )

            drawGenreParticles(
                center = center,
                w = w,
                mood = activeMood,
                progress = animState.particleProgress.value,
            )

            val squishY = squishScaleY.value
            val squishX = BlobAvatarGeometry.computeSquishScaleX(squishY)

            scale(scaleX = squishX, scaleY = squishY, pivot = center) {
                rotate(degrees = animState.headBobAngle.value, pivot = center) {
                    val bodyWidth = BlobAvatarGeometry.computeBodyWidth(w, animState.breatheScaleX.value)
                    val bodyHeight = BlobAvatarGeometry.computeBodyHeight(h, animState.breatheScaleY.value)

                    drawHeadband(center = center, bodyWidth = bodyWidth, bodyHeight = bodyHeight, bandColor = palette.headbandColor, w = w)
                    drawClayMochiBody(center = center, bodyWidth = bodyWidth, bodyHeight = bodyHeight, palette = palette)
                    drawRosyBlush(center = center, bodyWidth = bodyWidth, bodyHeight = bodyHeight, blushColor = palette.blushColor, w = w)
                    drawKawaiiEyes(
                        center = center,
                        bodyWidth = bodyWidth,
                        bodyHeight = bodyHeight,
                        animState = animState,
                        eyeColor = palette.facialColor,
                        isWinking = isWinking,
                        w = w,
                    )
                    drawSweetSmile(center = center, bodyHeight = bodyHeight, smileColor = palette.facialColor, w = w)
                    drawCozyHeadphones(
                        center = center,
                        bodyWidth = bodyWidth,
                        bodyHeight = bodyHeight,
                        palette = palette,
                        bassScale = animState.bassPulseScale.value,
                        w = w,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawDualSpotlights(
    mood: BlobAvatarGenreMood,
    w: Float,
    h: Float,
    isDark: Boolean,
) {
    val keyAlpha = if (isDark) 0.32f else 0.16f
    val rimAlpha = if (isDark) 0.28f else 0.14f

    val keyLightOrigin = Offset(w * 0.15f, h * 0.10f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(mood.keyLightColor.copy(alpha = keyAlpha), Color.Transparent),
            center = keyLightOrigin,
            radius = w * 0.65f,
        ),
        radius = w * 0.65f,
        center = keyLightOrigin,
    )

    val rimLightOrigin = Offset(w * 0.85f, h * 0.90f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(mood.rimLightColor.copy(alpha = rimAlpha), Color.Transparent),
            center = rimLightOrigin,
            radius = w * 0.60f,
        ),
        radius = w * 0.60f,
        center = rimLightOrigin,
    )
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

private fun DrawScope.drawSoundwaveRipples(
    center: Offset,
    w: Float,
    h: Float,
    animProgress: Float,
    waveColor: Color,
) {
    val earSpacing = w * 0.42f
    val earY = center.y - (h * 0.01f)

    val waves = listOf(animProgress, (animProgress + 0.5f) % 1f)

    for (p in waves) {
        val radius = BlobAvatarGeometry.computeSoundwaveRadius(w, p)
        val alpha = BlobAvatarGeometry.computeSoundwaveAlpha(p)
        val stroke = (w * 0.016f * (1f - (p * 0.4f))).coerceAtLeast(1f)

        // Left ear wave arc
        drawArc(
            color = waveColor.copy(alpha = alpha),
            startAngle = 110f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - earSpacing - radius, earY - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Right ear wave arc
        drawArc(
            color = waveColor.copy(alpha = alpha),
            startAngle = -70f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x + earSpacing - radius, earY - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawGenreParticles(
    center: Offset,
    w: Float,
    mood: BlobAvatarGenreMood,
    progress: Float,
) {
    val color = mood.keyLightColor
    val count = 3
    for (i in 0 until count) {
        val phase = (progress + (i.toFloat() / count)) % 1f
        val particleY = center.y - (w * 0.15f) - (phase * w * 0.35f)
        val sway = sin((phase * 2f * Math.PI) + i).toFloat() * (w * 0.08f)
        val particleX = if (i % 2 == 0) center.x - (w * 0.30f) + sway else center.x + (w * 0.30f) + sway
        val alpha = (sin(phase * Math.PI).toFloat() * 0.85f).coerceIn(0f, 1f)
        val size = w * 0.040f

        when (mood.particleType) {
            BlobAvatarGenreMood.ParticleType.MusicNote -> {
                drawCircle(color = color.copy(alpha = alpha), radius = size * 0.5f, center = Offset(particleX, particleY))
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(particleX + (size * 0.5f), particleY),
                    end = Offset(particleX + (size * 0.5f), particleY - (size * 1.4f)),
                    strokeWidth = (w * 0.012f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
            }
            BlobAvatarGenreMood.ParticleType.EqualizerBar -> {
                val barHeight = size * (1f + sin(phase * Math.PI * 4).toFloat().coerceAtLeast(0f))
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(particleX, particleY - barHeight),
                    end = Offset(particleX, particleY + barHeight),
                    strokeWidth = (w * 0.015f).coerceAtLeast(1.5f),
                    cap = StrokeCap.Round,
                )
            }
            else -> {
                drawCircle(color = color.copy(alpha = alpha), radius = size * 0.55f, center = Offset(particleX, particleY))
            }
        }
    }
}

private fun DrawScope.drawClayMochiBody(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    palette: BlobAvatarPalette,
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
    drawPath(path = bodyPath, brush = bodyBrush)

    val bounceBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, palette.bodyBounce),
        start = Offset(center.x, center.y),
        end = Offset(center.x, center.y + (bodyHeight / 2f)),
    )
    drawPath(path = bodyPath, brush = bounceBrush)

    val specWidth = bodyWidth * 0.34f
    val specHeight = bodyHeight * 0.14f
    val specCenter = Offset(center.x - (bodyWidth * 0.18f), center.y - (bodyHeight * 0.26f))
    drawOval(
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
        cubicTo(bandLeftX, bandTopY, bandRightX, bandTopY, bandRightX, bandStartY)
    }

    val bandStroke = BlobAvatarGeometry.computeHeadbandStroke(w)

    drawPath(
        path = bandPath,
        color = bandColor,
        style = Stroke(width = bandStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    drawPath(
        path = bandPath,
        color = Color.White.copy(alpha = 0.45f),
        style = Stroke(width = bandStroke * 0.30f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawCozyHeadphones(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    palette: BlobAvatarPalette,
    bassScale: Float,
    w: Float,
) {
    val cupCenterY = center.y - (bodyHeight * 0.02f)
    val (cupSpacingX, baseCupWidth) = BlobAvatarGeometry.computeCupParams(w, bodyWidth, bassScale)
    val cupHeight = w * BlobAvatarGeometry.CUP_HEIGHT_RATIO * bassScale

    val ears = listOf(
        Pair(center.x - cupSpacingX, -4f),
        Pair(center.x + cupSpacingX, 4f),
    )

    for ((cupX, tilt) in ears) {
        rotate(degrees = tilt, pivot = Offset(cupX, cupCenterY)) {
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

private fun DrawScope.drawRosyBlush(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    blushColor: Color,
    w: Float,
) {
    val blushY = center.y + (bodyHeight * 0.10f)
    val (blushSpacing, blushRadius) = BlobAvatarGeometry.computeBlushParams(w, bodyWidth)

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
    isWinking: Boolean,
    w: Float,
) {
    val eyeSpacing = bodyWidth * BlobAvatarGeometry.EYE_SPACING_RATIO
    val eyeCenterY = center.y - (bodyHeight * 0.02f)
    val (eyeWidth, eyeHeight) = BlobAvatarGeometry.computeEyeDimensions(w)
    val strokeWidth = BlobAvatarGeometry.computeStrokeWidth(w)
    val eyeOpenScale = if (isWinking) 0.08f else animState.eyeOpenScale.value

    val (maxShiftX, maxShiftY) = BlobAvatarGeometry.computeEyeMaxShifts(eyeWidth, eyeHeight)
    val shiftX = maxShiftX * animState.pupilLookRatioX.value
    val shiftY = maxShiftY * animState.pupilLookRatioY.value

    val eyes = listOf(
        Pair(Offset(center.x - eyeSpacing, eyeCenterY), false),
        Pair(Offset(center.x + eyeSpacing, eyeCenterY), isWinking),
    )

    for ((eyeCenter, isThisWinking) in eyes) {
        val currentScale = if (isThisWinking) 0.08f else eyeOpenScale
        if (currentScale > 0.25f) {
            val currentEyeHeight = eyeHeight * currentScale
            val currentEyeTop = eyeCenter.y - (currentEyeHeight / 2f) + (shiftY * currentScale)
            val currentEyeLeft = eyeCenter.x - (eyeWidth / 2f) + shiftX

            drawOval(
                color = eyeColor,
                topLeft = Offset(currentEyeLeft, currentEyeTop),
                size = Size(eyeWidth, currentEyeHeight),
            )

            val mainGlintRadius = eyeWidth * 0.36f * currentScale
            drawCircle(
                color = Color.White,
                radius = mainGlintRadius,
                center = Offset(
                    currentEyeLeft + (eyeWidth * 0.65f),
                    currentEyeTop + (currentEyeHeight * 0.32f),
                ),
            )

            val secGlintRadius = eyeWidth * 0.18f * currentScale
            drawCircle(
                color = Color.White.copy(alpha = 0.90f),
                radius = secGlintRadius,
                center = Offset(
                    currentEyeLeft + (eyeWidth * 0.32f),
                    currentEyeTop + (currentEyeHeight * 0.72f),
                ),
            )
        } else {
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
