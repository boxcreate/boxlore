package cx.aswin.boxlore.feature.history

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogConfirmButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogDismissButton

/** The Clear all confirmation: wiping the whole history is a filled red button; Cancel is quiet text. */
@Composable
internal fun HistoryScreenDialogs(showClearDialog: Boolean, viewModel: HistoryViewModel, onDismissClearDialog: () -> Unit,) {
    if (showClearDialog) {
        TrackScreenView("clear_listening_history_confirmation")
        BoxloreAlertDialog(
            onDismissRequest = onDismissClearDialog,
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_body)) },
            confirmButton = {
                BoxloreDialogConfirmButton(
                    text = stringResource(R.string.history_clear_confirm_action),
                    onClick = {
                        viewModel.clearAllHistory()
                        onDismissClearDialog()
                    },
                    destructive = true,
                )
            },
            dismissButton = { BoxloreDialogDismissButton(stringResource(R.string.history_cancel), onDismissClearDialog) },
        )
    }
}
