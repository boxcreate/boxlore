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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A wrapper that provides smooth predictive back gesture animations.
 * 
 * Applies progressive transformations as the user swipes back:
 * - Scale: 1.0 -> 0.9
 * - Translation X: follows swipe direction
 * - Corner radius: 0dp -> 24dp
 * - Slight shadow elevation
 */
@Composable
fun PredictiveBackWrapper(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // Animation state
    var gestureProgress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableFloatStateOf(0f) } // -1 = left, 1 = right
    val animatedProgress = remember { Animatable(0f) }
    
    // Derived visual values
    val scale = 1f - (animatedProgress.value * 0.1f) // 1.0 -> 0.9
    val translationX = animatedProgress.value * swipeEdge * 100f // Follows swipe direction
    val cornerRadius = animatedProgress.value * 24f // 0 -> 24dp
    val elevation = animatedProgress.value * 8f // 0 -> 8dp
    
    PredictiveBackHandler(enabled = enabled) { backEvents: Flow<BackEventCompat> ->
        try {
            backEvents.collect { event ->
                gestureProgress = event.progress
                swipeEdge = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                    
                // Snap to gesture progress directly in the current suspend context
                animatedProgress.snapTo(event.progress)
            }
                
            // Gesture completed - animate to end and trigger back
            scope.launch {
                animatedProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            onBack()
        } catch (e: CancellationException) {
            // Gesture cancelled - animate back to rest
            scope.launch {
                animatedProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.translationX = translationX
                
                // Apply corner radius via clip
                shape = RoundedCornerShape(cornerRadius.dp)
                clip = animatedProgress.value > 0.01f
                
                // Elevation for floating effect
                shadowElevation = elevation
            }
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(cornerRadius.dp)
            )
    ) {
        content()
    }
}
