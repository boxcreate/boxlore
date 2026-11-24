package cx.aswin.boxlore.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cx.aswin.boxlore.core.model.Episode

/**
 * App-level questions that must stay open when the activity is recreated, held in the activity's
 * view model store. Not saved state: an episode with a long description could make the saved-state
 * bundle too large.
 */
internal class ShellPendingDialogs : ViewModel() {
    /** The Lore episode waiting on "Replace your queue with Lore?", or null when the question is closed. */
    var loreQueueConflictEpisode by mutableStateOf<Episode?>(null)
}
