package cx.aswin.boxlore.feature.settings.pages

import android.widget.Toast
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
    val bodyOutline: Color,
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
    val bodyBase = if (isDark) {
        Color(0xFFF1F5F9)
    } else {
        Color(0xFFF8FAFC)
    }

    val bodyShadow = if (isDark) {
        Color(0xFFCBD5E1)
    } else {
        Color(0xFFCBD5E1)
    }

    val bodyBounce = if (isDark) {
        genreMood.rimLightColor.copy(alpha = 0.38f)
    } else {
        genreMood.rimLightColor.copy(alpha = 0.34f)
    }

    val bodyOutline = if (isDark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color(0xFF94A3B8).copy(alpha = 0.45f)
    }

    val facialColor = Color(0xFF0F172A)
    val blushColor = Color(0xFFFF5277).copy(alpha = if (isDark) 0.50f else 0.40f)

    val headbandColor = if (isDark) Color(0xFF1E293B) else Color(0xFF334155)
    val cushionColor = if (isDark) Color(0xFF0F172A) else Color(0xFF1E293B)
    val headphoneCup = genreMood.keyLightColor
    val headphoneAccent = genreMood.rimLightColor
    val shadowAlpha = if (isDark) 0.22f else 0.26f

    return BlobAvatarPalette(
        bodyBase = bodyBase,
        bodyShadow = bodyShadow,
        bodyBounce = bodyBounce,
        bodyOutline = bodyOutline,
        facialColor = facialColor,
        blushColor = blushColor,
        headbandColor = headbandColor,
        cushionColor = cushionColor,
        headphoneCup = headphoneCup,
        headphoneAccent = headphoneAccent,
        shadowAlpha = shadowAlpha,
    )
}

internal data class BlobFacialExpression(
    val isWinking: Boolean,
    val isClosed: Boolean,
    val isLaughing: Boolean,
)

internal data class BlobAvatarRenderContext(
    val mood: BlobAvatarGenreMood,
    val palette: BlobAvatarPalette,
    val animState: BlobAvatarAnimationState,
    val expression: BlobFacialExpression,
    val squishY: Float,
    val isDark: Boolean,
)

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val squishScaleY = remember { Animatable(1f) }
    var isWinking by remember { mutableStateOf(false) }

    val onTapAvatar = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        activeMood = BlobAvatarGenreMood.next(activeMood)
        Toast.makeText(
            context,
            "${activeMood.emoji} ${activeMood.displayName}",
            Toast.LENGTH_SHORT,
        ).show()
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

    val expression = BlobFacialExpression(
        isWinking = isWinking,
        isClosed = activeMood == BlobAvatarGenreMood.ChillMusic,
        isLaughing = activeMood == BlobAvatarGenreMood.ComedyTalk,
    )

    val renderContext = BlobAvatarRenderContext(
        mood = activeMood,
        palette = palette,
        animState = animState,
        expression = expression,
        squishY = squishScaleY.value,
        isDark = isDark,
    )

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTapAvatar() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAvatarLayers(renderContext, this.size.width, this.size.height)
        }
    }
}

private fun DrawScope.drawAvatarLayers(
    renderContext: BlobAvatarRenderContext,
    w: Float,
    h: Float,
) {
    val mood = renderContext.mood
    val palette = renderContext.palette
    val animState = renderContext.animState

    val floatOffsetPx = BlobAvatarGeometry.computeFloatOffset(h, animState.floatProgress.value)
    val center = Offset(w / 2f, (h / 2f) + floatOffsetPx)

    drawDualSpotlights(mood = mood, w = w, h = h, isDark = renderContext.isDark)

    BlobAvatarEnvironments.drawEnvironment(
        drawScope = this,
        environmentType = mood.environmentType,
        center = center,
        w = w,
        h = h,
        mood = mood,
        progress = animState.particleProgress.value,
    )

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
        waveColor = mood.rimLightColor,
    )

    drawGenreParticles(
        center = center,
        w = w,
        mood = mood,
        progress = animState.particleProgress.value,
    )

    val squishX = BlobAvatarGeometry.computeSquishScaleX(renderContext.squishY)

    scale(scaleX = squishX, scaleY = renderContext.squishY, pivot = center) {
        rotate(degrees = animState.headBobAngle.value, pivot = center) {
            val bodyWidth = BlobAvatarGeometry.computeBodyWidth(w, animState.breatheScaleX.value)
            val bodyHeight = BlobAvatarGeometry.computeBodyHeight(h, animState.breatheScaleY.value)
            val dimensions = CharacterDimensions(
                center = center,
                bodyWidth = bodyWidth,
                bodyHeight = bodyHeight,
                w = w,
            )
            BlobAvatarCharacter.drawCharacter(this, dimensions, renderContext)
        }
    }
}

private fun DrawScope.drawDualSpotlights(
    mood: BlobAvatarGenreMood,
    w: Float,
    h: Float,
    isDark: Boolean,
) {
    val auraAlpha = if (isDark) 0.22f else 0.38f
    val keyAlpha = if (isDark) 0.35f else 0.42f
    val rimAlpha = if (isDark) 0.30f else 0.38f

    // Ambient backlight halo behind the mascot to create sharp contrast and luminous definition against light surfaces
    val auraOrigin = Offset(w / 2f, h / 2f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                mood.keyLightColor.copy(alpha = auraAlpha),
                mood.rimLightColor.copy(alpha = auraAlpha * 0.65f),
                Color.Transparent,
            ),
            center = auraOrigin,
            radius = w * 0.52f,
        ),
        radius = w * 0.52f,
        center = auraOrigin,
    )

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
