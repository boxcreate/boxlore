package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Keeps the import picker open when the activity is recreated (a fold, an unfold, a display-size
 * change). Only the picker is kept: it holds no live objects. Running work and its results are owned
 * by the import itself, so every other state comes back as [OpmlImportState.Idle].
 */
internal val OpmlImportStateSaver: Saver<OpmlImportState, Boolean> = Saver(
    save = { state -> state is OpmlImportState.ShowSelector },
    restore = { showSelector -> if (showSelector) OpmlImportState.ShowSelector else OpmlImportState.Idle },
)

/** The import dialog's [state] and the screen it was opened from ([source], an analytics value). */
internal class OpmlImportHolder(val state: MutableState<OpmlImportState>, val source: MutableState<String>)

/** The import dialog's state and where it was opened from, both kept across recreation. */
@Composable
internal fun rememberOpmlImportState(): OpmlImportHolder {
    val state = rememberSaveable(stateSaver = OpmlImportStateSaver) { mutableStateOf<OpmlImportState>(OpmlImportState.Idle) }
    val source = rememberSaveable { mutableStateOf("welcome_screen") }
    return remember(state, source) { OpmlImportHolder(state, source) }
}
