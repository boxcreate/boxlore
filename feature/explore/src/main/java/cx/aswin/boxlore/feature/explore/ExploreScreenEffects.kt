package cx.aswin.boxlore.feature.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession

/**
 * The Explore screen's lifecycle hooks: the analytics session, resumed and ended with the screen,
 * and its visibility. For you refreshes only while the screen is visible.
 */
@Composable
internal fun ExploreScreenEffects(viewModel: ExploreViewModel) {
    TrackScreenSession(
        onSessionResume = viewModel::onScreenResume,
        onSessionExit = viewModel::trackScreenExit,
    )
    ExploreVisibilityEffect(viewModel::onScreenVisibilityChanged)
}

/**
 * Reports true while the screen's lifecycle is started (split-screen too) and false when it stops
 * or leaves the composition. A new [onVisibilityChanged] instance never restarts the effect.
 */
@Composable
internal fun ExploreVisibilityEffect(onVisibilityChanged: (Boolean) -> Unit) {
    val report by rememberUpdatedState(onVisibilityChanged)
    LifecycleStartEffect(Unit) {
        report(true)
        onStopOrDispose { report(false) }
    }
}
