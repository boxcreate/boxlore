package cx.aswin.boxlore.feature.widgets.logic

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

/**
 * Asks the player for its position at a steady pace while an episode plays, and not at all while it is
 * paused or no playback widget is placed. The app only tracks the position while it is on screen, which
 * is exactly when a home-screen widget is not being looked at.
 */
object WidgetProgressPoller {
    suspend fun run(
        playing: Flow<Boolean>,
        intervalMs: Long,
        hasWidgets: () -> Boolean,
        refresh: suspend () -> Unit,
    ) {
        playing.distinctUntilChanged().collectLatest { isPlaying ->
            while (isPlaying && currentCoroutineContext().isActive) {
                delay(intervalMs)
                if (hasWidgets()) refresh()
            }
        }
    }
}
