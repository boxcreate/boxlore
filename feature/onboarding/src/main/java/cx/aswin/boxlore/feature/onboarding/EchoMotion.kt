package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One choreography for every Echo change: the old content leaves quickly, then the new content
 * rises in on a grounded spring. Choices follow the question in a short stagger. System
 * animation scale applies to all of it; at zero every step is instant.
 */
internal object EchoMotion {
    const val ExitMillis = 110
    const val EnterDelayMillis = 90
    const val EnterFadeMillis = 220
    const val StaggerStartMillis = 140L
    const val StaggerStepMillis = 45L

    private val riseSpring = spring(dampingRatio = 0.86f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)
    private val sizeSpring = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f, visibilityThreshold = IntSize.VisibilityThreshold)
    val itemSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 380f)

    fun <S> handoff(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(tween(EnterFadeMillis, delayMillis = EnterDelayMillis)) + slideInVertically(riseSpring) { it / 8 })
            .togetherWith(fadeOut(tween(ExitMillis)) + slideOutVertically(tween(ExitMillis + 60)) { -it / 12 })
            .using(SizeTransform(clip = false) { _, _ -> sizeSpring })
    }
}

/**
 * One entry progress per answer for [key], started together in the usual stagger. Held by the whole
 * group, so answers that move to another row or column when the window changes never enter again.
 */
@Composable
internal fun rememberEchoStagger(key: Any, count: Int): List<Animatable<Float, AnimationVector1D>> {
    val items = remember(key, count) { List(count) { Animatable(0f) } }
    LaunchedEffect(items) {
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        items.forEachIndexed { index, item ->
            launch {
                if (scale > 0f) delay(((EchoMotion.StaggerStartMillis + index * EchoMotion.StaggerStepMillis) * scale).toLong())
                item.animateTo(1f, EchoMotion.itemSpring)
            }
        }
    }
    return items
}

/** Fades and lifts [content] in with its [progress] from [rememberEchoStagger]. */
@Composable
internal fun EchoStaggeredEntry(progress: Animatable<Float, AnimationVector1D>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 14.dp.toPx()
        },
    ) { content() }
}
