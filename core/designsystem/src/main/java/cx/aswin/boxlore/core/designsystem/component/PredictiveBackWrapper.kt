package cx.aswin.boxlore.core.designsystem.component

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A wrapper that provides smooth predictive back gesture animations.
 *
 * Applies progressive transformations as the user swipes back:
 * - Scale: 1.0 -> 0.9
 * - Translation X: follows swipe direction
 * - Corner radius: 0dp -> 24dp
 * - Slight shadow elevation
 *
 * After the gesture ends (commit or cancel), peek progress returns to rest.
 * This wrapper stays around the NavHost, so a completed Back that replaces
 * the start destination must not leave the next screen at 0.9 scale.
 */
@Composable
fun PredictiveBackWrapper(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var swipeEdge by remember { mutableFloatStateOf(0f) }
    val animatedProgress = remember { Animatable(PredictiveBackPeek.REST_PROGRESS) }

    val scale = PredictiveBackPeek.scaleFor(animatedProgress.value)
    val translationX = animatedProgress.value * swipeEdge * 100f
    val cornerRadius = animatedProgress.value * 24f
    val elevation = animatedProgress.value * 8f

    LaunchedEffect(enabled) {
        if (!enabled && animatedProgress.value != PredictiveBackPeek.REST_PROGRESS) {
            animatedProgress.animateTo(
                targetValue = PredictiveBackPeek.REST_PROGRESS,
                animationSpec = predictiveBackRestSpring(),
            )
        }
    }

    PredictiveBackHandler(enabled = enabled) { backEvents: Flow<BackEventCompat> ->
        try {
            backEvents.collect { event ->
                swipeEdge =
                    if (event.swipeEdge == BackEventCompat.EDGE_LEFT) {
                        1f
                    } else {
                        -1f
                    }
                animatedProgress.snapTo(event.progress)
            }
            onBack()
        } catch (_: CancellationException) {
            // Gesture cancelled or handler disabled after commit.
        } finally {
            scope.launch {
                animatedProgress.animateTo(
                    targetValue = PredictiveBackPeek.progressAfterGesture(),
                    animationSpec = predictiveBackRestSpring(),
                )
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.translationX = translationX
                    shape = RoundedCornerShape(cornerRadius.dp)
                    clip = animatedProgress.value > 0.01f
                    shadowElevation = elevation
                }.background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(cornerRadius.dp),
                ),
    ) {
        content()
    }
}

private fun predictiveBackRestSpring() =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
