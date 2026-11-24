package cx.aswin.boxlore.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * App-level questions that must stay open when the activity is recreated, held in the activity's
 * view model store. Not saved state: an episode with a long description could make the saved-state
 * bundle too large.
 */
internal class ShellPendingDialogs : ViewModel() {
    /** The Lore episode waiting on "Start a Lore queue?", or null when the question is closed. */
    var loreQueueConflictEpisode by mutableStateOf<Episode?>(null)

    /**
     * Start was chosen for [episode]: closes the question and runs [start] on this holder's scope,
     * which outlives the activity, so a recreation can't drop an accepted Start halfway.
     */
    fun startLoreQueue(episode: Episode, start: suspend (Episode) -> Unit) {
        loreQueueConflictEpisode = null
        viewModelScope.launch {
            try {
                start(episode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start Lore queue", e)
            }
        }
    }
}
