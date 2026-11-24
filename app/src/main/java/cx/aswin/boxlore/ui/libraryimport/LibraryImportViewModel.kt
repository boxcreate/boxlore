package cx.aswin.boxlore.ui.libraryimport

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The library import overlay's state and its running work, kept in the activity's view model store
 * so that a recreation (text or display size, a density change on a foldable) keeps the page, its
 * progress and a restore in progress.
 *
 * [runJson] restores a boxlore backup and [runOpmlStep] runs the OPML step the current state asks
 * for ([runOpmlImportStep]); both report each new state through their callback. They are built by
 * the app root from application-scoped repositories and the application context, never the activity.
 */
internal class LibraryImportViewModel(
    private val runJson: suspend (Uri, (OpmlImportState) -> Unit) -> Unit,
    private val runOpmlStep: suspend (OpmlImportState, (OpmlImportState) -> Unit) -> Unit,
) : ViewModel() {
    /** What the import overlay shows; [OpmlImportState.Idle] when it is closed. */
    var state by mutableStateOf<OpmlImportState>(OpmlImportState.Idle)

    /** Where the import was opened from, for analytics (`welcome_screen`, `home_import_banner`, ...). */
    var source by mutableStateOf("welcome_screen")

    /** When the last OPML step was started; 0 before the first. */
    var triggerKey by mutableLongStateOf(0L)
        private set

    private var step: Job? = null
    private var markedPhase: String? = null

    /**
     * True the first time each import phase ([libraryImportPhase]) is reached, so the root marks the
     * import once per phase even when the activity is recreated; a closed import resets it.
     */
    fun claimPhaseMark(phase: String?): Boolean {
        val claimed = phase != null && phase != markedPhase
        markedPhase = phase
        return claimed
    }

    /** Restores a boxlore backup from [uri]. */
    fun importJson(uri: Uri) {
        viewModelScope.launch { runJson(uri) { state = it } }
    }

    /** Runs the OPML step for the current [state], replacing a step still running. */
    fun runStep(key: Long = System.currentTimeMillis()) {
        triggerKey = key
        step?.cancel()
        val current = state
        step = viewModelScope.launch { runOpmlStep(current) { state = it } }
    }
}
