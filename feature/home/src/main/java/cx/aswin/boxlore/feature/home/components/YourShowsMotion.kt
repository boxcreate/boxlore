package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import cx.aswin.boxlore.feature.home.StableEpisodeList

internal object YourShowsMotion {
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 220f)
    fun <T> effects(): TweenSpec<T> = tween(240, easing = FastOutSlowInEasing)
    val EnterFade = effects<Float>()
    val ExitFade = tween<Float>(150, easing = FastOutSlowInEasing)
}

/** The persistent selector anchors a small vertical reveal and one continuous size morph. */
internal fun <S> AnimatedContentTransitionScope<S>.yourShowsContentTransform(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        fadeIn(YourShowsMotion.EnterFade) +
            slideInVertically(YourShowsMotion.spatial()) { direction * (it / 12).coerceAtMost(24) }
        ) togetherWith (
        fadeOut(YourShowsMotion.ExitFade) +
            slideOutVertically(YourShowsMotion.spatial()) { -direction * (it / 16).coerceAtMost(18) }
        ) using SizeTransform(clip = false) { _, _ -> YourShowsMotion.spatial() }
}

internal data class SelectedShowPresentation(
    val episodes: StableEpisodeList,
    val loading: Boolean,
    val refreshing: Boolean,
)

/** An exiting show's content retains its last displayed frame instead of adopting the next show. */
@Composable
internal fun <T> retainExitingShowsFrame(active: Boolean, current: T): T {
    val previous = remember { mutableStateOf(current) }
    SideEffect { if (active) previous.value = current }
    return if (active) current else previous.value
}
