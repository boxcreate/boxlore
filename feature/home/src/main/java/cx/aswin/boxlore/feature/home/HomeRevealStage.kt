package cx.aswin.boxlore.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/** The order in which a cold start hands its skeleton over to real sections, one per frame. */
internal object HomeRevealStage {
    const val SKELETON = 0
    const val HERO = 1
    const val YOUR_SHOWS = 2
    const val DISCOVERY = 3
    const val COMPLETE = DISCOVERY
}

/**
 * Content that arrives together would otherwise compose the hero, Your shows and the discovery grid
 * in a single frame, which stalls a cold start for most of a second. Once the viewport is ready the
 * stage advances one step per frame. Content already there when Home first composes shows at once.
 */
@Composable
internal fun rememberHomeRevealStage(viewportReady: Boolean): Int {
    val stage = remember { mutableIntStateOf(if (viewportReady) HomeRevealStage.COMPLETE else HomeRevealStage.SKELETON) }
    LaunchedEffect(viewportReady) {
        if (!viewportReady) {
            stage.intValue = HomeRevealStage.SKELETON
            return@LaunchedEffect
        }
        while (stage.intValue < HomeRevealStage.COMPLETE) {
            withFrameNanos { }
            stage.intValue += 1
        }
    }
    return stage.intValue
}
