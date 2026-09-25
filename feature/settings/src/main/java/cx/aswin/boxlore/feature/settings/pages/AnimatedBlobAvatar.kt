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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class BlobAvatarAnimationState(
    val floatY: State<Float>,
    val breatheScaleX: State<Float>,
    val breatheScaleY: State<Float>,
    val eyeOpenScale: State<Float>,
    val pupilOffsetX: State<Float>,
    val pupilOffsetY: State<Float>,
)

@Composable
internal fun rememberBlobAvatarAnimationState(): BlobAvatarAnimationState {
    val infiniteTransition = rememberInfiniteTransition(label = "BlobAvatarTransition")

    val floatY = infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobFloatY",
    )

    val breatheScaleX = infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobBreatheX",
    )
    val breatheScaleY = infiniteTransition.animateFloat(
        initialValue = 1.03f,
        targetValue = 0.97f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BlobBreatheY",
    )

    val eyeOpenScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3400
                0.08f at 3520
                1f at 3640
                0.08f at 3780
                1f at 3900
                1f at 4200
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "BlobEyeOpen",
    )

    val pupilOffsetX = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                0f at 1800
                3.5f at 2400
                3.5f at 3400
                0f at 4000
                -3.5f at 4600
                -3.5f at 5400
                0f at 6000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "BlobPupilOffsetX",
    )
    val pupilOffsetY = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                0f at 1800
                -1.5f at 2400
                -1.5f at 3400
                0f at 4000
                1.5f at 4600
                1.5f at 5400
                0f at 6000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "BlobPupilOffsetY",
    )

    return BlobAvatarAnimationState(
        floatY = floatY,
        breatheScaleX = breatheScaleX,
        breatheScaleY = breatheScaleY,
        eyeOpenScale = eyeOpenScale,
        pupilOffsetX = pupilOffsetX,
        pupilOffsetY = pupilOffsetY,
    )
}

/**
 * A delightful, lightweight, animated 2D face blob avatar for boxlore profile display.
 * Pure Compose Canvas implementation with soft breathing, organic floating,
 * pupils that look around smoothly, and periodic blinking.
 */
@Composable
internal fun AnimatedBlobAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
) {
    val animState = rememberBlobAvatarAnimationState()

    val bodyColor = MaterialTheme.colorScheme.primaryContainer
    val onBodyColor = MaterialTheme.colorScheme.onPrimaryContainer
    val blushColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, (h / 2f) + animState.floatY.value)

            val bodyWidth = w * 0.88f * animState.breatheScaleX.value
            val bodyHeight = h * 0.82f * animState.breatheScaleY.value

            drawBlobBody(center = center, bodyWidth = bodyWidth, bodyHeight = bodyHeight, bodyColor = bodyColor)

            drawBlush(center = center, bodyWidth = bodyWidth, bodyHeight = bodyHeight, blushRadius = w * 0.08f, blushColor = blushColor)

            val eyeSpacing = bodyWidth * 0.18f
            val eyeCenterY = center.y - (bodyHeight * 0.04f)
            val eyes = listOf(
                Offset(center.x - eyeSpacing, eyeCenterY),
                Offset(center.x + eyeSpacing, eyeCenterY),
            )
            drawEyes(
                eyes = eyes,
                eyeOpenScale = animState.eyeOpenScale.value,
                pupilOffsetX = animState.pupilOffsetX.value,
                pupilOffsetY = animState.pupilOffsetY.value,
                eyeRadius = w * 0.105f,
                onBodyColor = onBodyColor,
                strokeWidth = 2.4.dp.toPx(),
            )

            drawSmile(
                center = center,
                smileWidth = w * 0.16f,
                smileHeight = h * 0.09f,
                bodyHeight = bodyHeight,
                onBodyColor = onBodyColor,
                strokeWidth = 2.4.dp.toPx(),
            )
        }
    }
}

private fun DrawScope.drawBlobBody(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    bodyColor: Color,
) {
    val cornerRadius = CornerRadius(bodyWidth * 0.44f, bodyHeight * 0.44f)
    val bodyRect = RoundRect(
        left = center.x - (bodyWidth / 2f),
        top = center.y - (bodyHeight / 2f),
        right = center.x + (bodyWidth / 2f),
        bottom = center.y + (bodyHeight / 2f),
        cornerRadius = cornerRadius,
    )
    val bodyPath = Path().apply { addRoundRect(bodyRect) }
    drawPath(path = bodyPath, color = bodyColor)

    // Soft top specular highlight for smooth 3D depth
    val highlightWidth = bodyWidth * 0.65f
    val highlightHeight = bodyHeight * 0.22f
    drawOval(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = Offset(center.x - (highlightWidth / 2f), center.y - (bodyHeight / 2f) + 4.dp.toPx()),
        size = Size(highlightWidth, highlightHeight),
    )
}

private fun DrawScope.drawBlush(
    center: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    blushRadius: Float,
    blushColor: Color,
) {
    val blushY = center.y + (bodyHeight * 0.12f)
    val blushSpacing = bodyWidth * 0.32f
    drawCircle(
        color = blushColor,
        radius = blushRadius,
        center = Offset(center.x - blushSpacing, blushY),
    )
    drawCircle(
        color = blushColor,
        radius = blushRadius,
        center = Offset(center.x + blushSpacing, blushY),
    )
}

private fun DrawScope.drawEyes(
    eyes: List<Offset>,
    eyeOpenScale: Float,
    pupilOffsetX: Float,
    pupilOffsetY: Float,
    eyeRadius: Float,
    onBodyColor: Color,
    strokeWidth: Float,
) {
    for (eyeCenter in eyes) {
        if (eyeOpenScale > 0.25f) {
            val eyeHeight = eyeRadius * 2f * eyeOpenScale
            val eyeTop = eyeCenter.y - (eyeHeight / 2f)

            drawOval(
                color = Color.White,
                topLeft = Offset(eyeCenter.x - eyeRadius, eyeTop),
                size = Size(eyeRadius * 2f, eyeHeight),
            )

            val pupilRadius = eyeRadius * 0.58f
            val pupilCenter = Offset(
                x = eyeCenter.x + pupilOffsetX,
                y = eyeCenter.y + (pupilOffsetY * eyeOpenScale),
            )
            drawCircle(
                color = onBodyColor,
                radius = pupilRadius * eyeOpenScale,
                center = pupilCenter,
            )

            drawCircle(
                color = Color.White,
                radius = pupilRadius * 0.32f * eyeOpenScale,
                center = Offset(
                    pupilCenter.x + (pupilRadius * 0.3f),
                    pupilCenter.y - (pupilRadius * 0.28f * eyeOpenScale),
                ),
            )
        } else {
            drawArc(
                color = onBodyColor,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(eyeCenter.x - (eyeRadius * 0.85f), eyeCenter.y - (eyeRadius * 0.35f)),
                size = Size(eyeRadius * 1.7f, eyeRadius * 0.8f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawSmile(
    center: Offset,
    smileWidth: Float,
    smileHeight: Float,
    bodyHeight: Float,
    onBodyColor: Color,
    strokeWidth: Float,
) {
    drawArc(
        color = onBodyColor,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(center.x - (smileWidth / 2f), center.y + (bodyHeight * 0.08f)),
        size = Size(smileWidth, smileHeight),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}
