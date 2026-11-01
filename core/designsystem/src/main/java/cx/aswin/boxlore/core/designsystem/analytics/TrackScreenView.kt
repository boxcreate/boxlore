package cx.aswin.boxlore.core.designsystem.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.ScreenViews

/**
 * The one page-view helper: put it in a screen, tab, sheet or dialog. It sends the view once per
 * visit and keeps [ScreenViews] on [screen] while it shows, so the next screen knows the one before.
 *
 * - A visit lasts while the screen stays on the back stack: rotating, folding, resizing or coming
 *   back from a screen opened on top doesn't send it again (the sent flag is saved). A new value of
 *   [keys] (another show on the same page) is a new visit.
 * - [send] gets the screen before. It defaults to `screen_viewed`; a screen with a view event of its
 *   own passes that instead, so its event keeps its name.
 */
@Composable
fun TrackScreenView(
    screen: String,
    vararg keys: Any?,
    send: (previousScreen: String?) -> Unit = { previous -> AnalyticsHelper.trackScreenViewed(screen, previous) },
) {
    var sent by rememberSaveable(screen, *keys) { mutableStateOf(false) }
    val currentSend by rememberUpdatedState(send)
    DisposableEffect(screen, *keys) {
        val previous = ScreenViews.enter(screen)
        if (!sent) {
            sent = true
            currentSend(previous)
        }
        onDispose { ScreenViews.leave(screen) }
    }
}
