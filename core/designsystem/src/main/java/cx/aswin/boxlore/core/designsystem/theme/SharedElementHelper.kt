package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.BoundsTransform
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.SharedTransitionScope.OverlayClip

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Conveniently applies shared element transition if scopes are available.
 * @param key Unique key for the shared element.
 * @param clipShape Shape to clip the element to during the transition (fixes sharp corners).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.simpleSharedElement(
    key: String,
    clipShape: Shape? = null
): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current

    if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            this@composed.sharedElement(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedScope,
                clipInOverlayDuringTransition = OverlayClip(clipShape ?: androidx.compose.ui.graphics.RectangleShape)
            )
        }
    } else {
        this
    }
}

/**
 * Applies sharedBounds for container animations (entire card morphs).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.simpleSharedBounds(
    key: String,
    clipShape: Shape? = null
): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current

    if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            this@composed.sharedBounds(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedScope,
                clipInOverlayDuringTransition = OverlayClip(clipShape ?: androidx.compose.ui.graphics.RectangleShape)
            )
        }
    } else {
        this
    }
}

/**
 * Keeps content from disappearing during shared transitions.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.keepDuringTransition(): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current

    if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            with(animatedScope) {
                this@composed
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                    .animateEnterExit(
                        enter = fadeIn(),
                        exit = fadeOut()
                    )
            }
        }
    } else {
        this
    }
}
