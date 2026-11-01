package cx.aswin.boxlore.core.designsystem.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.ScreenViews

/**
 * The one page-view helper: put it in a screen, tab, sheet or dialog. It sends the view once per
 * visit and registers [screen] with [ScreenViews] while it shows, so the next screen knows the one before.
 *
 * - A visit lasts while the screen stays on the back stack: rotating, folding, resizing or coming
 *   back from a screen opened on top doesn't send it again (the last visit sent is saved). A new
 *   [screen] or new [keys] (another show, the next step) is a new visit, and replaces this screen in
 *   the trail, so the step it replaced is the one before.
 * - [send] gets the screen before. It defaults to `screen_viewed`; a screen with a view event of its
 *   own passes that instead, so its event keeps its name, and `{}` only keeps the trail.
 */
@Composable
fun TrackScreenView(
    screen: String,
    vararg keys: Any?,
    send: (previousScreen: String?) -> Unit = { previous -> AnalyticsHelper.trackScreenViewed(screen, previous) },
) {
    val registration = remember { ScreenViews.Registration() }
    val visit = (listOf(screen) + keys.map(Any?::toString)).joinToString(VISIT_SEPARATOR)
    var sentVisit by rememberSaveable { mutableStateOf<String?>(null) }
    val currentSend by rememberUpdatedState(send)
    DisposableEffect(registration) {
        onDispose { ScreenViews.leave(registration) }
    }
    DisposableEffect(visit) {
        val previous = ScreenViews.show(registration, screen)
        if (sentVisit != visit) {
            sentVisit = visit
            currentSend(previous)
        }
        onDispose {
            // The next show() replaces this screen in place; leaving is the registration's job.
        }
    }
}

private const val VISIT_SEPARATOR = "\u001F"
