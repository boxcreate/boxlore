package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.runtime.saveable.Saver

/**
 * Keeps the import picker open when the activity is recreated (a fold, an unfold, a display-size
 * change). Only the picker is kept: it holds no live objects. Running work and its results are owned
 * by the import itself, so every other state comes back as [OpmlImportState.Idle].
 */
internal val OpmlImportStateSaver: Saver<OpmlImportState, Boolean> = Saver(
    save = { state -> state is OpmlImportState.ShowSelector },
    restore = { showSelector -> if (showSelector) OpmlImportState.ShowSelector else OpmlImportState.Idle },
)
