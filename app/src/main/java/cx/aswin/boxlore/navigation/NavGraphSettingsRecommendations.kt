package cx.aswin.boxlore.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cx.aswin.boxlore.core.prefs.DiscoverySection
import cx.aswin.boxlore.feature.settings.pages.HiddenShowsControl
import kotlinx.coroutines.launch

/* LeaveMeAlone's wiring: its section switches and the shows hidden everywhere. */

/** Saves a LeaveMeAlone switch. */
internal fun discoverySectionSetter(w: NavGraphWiring): (DiscoverySection, Boolean) -> Unit = { section, on ->
    w.scope.launch {
        // A failed write keeps the stored value, which the switch keeps showing.
        try {
            w.userPrefs.setDiscoverySection(section, on)
        } catch (e: java.io.IOException) {
            android.util.Log.w("Settings", "LeaveMeAlone switch ${section.name} was not saved", e)
        }
    }
}

/** LeaveMeAlone's hidden shows, read live, with Unhide. */
@Composable
internal fun rememberHiddenShowsControl(w: NavGraphWiring): HiddenShowsControl {
    // Null until the stored list is read, so the empty state never flashes before the list.
    val shows by w.userPrefs.hiddenShows.showsStream.collectAsState(initial = null)
    return HiddenShowsControl(shows = shows, onUnhide = { show -> w.scope.launch { w.userPrefs.hiddenShows.unhide(show.matchIds) } })
}
