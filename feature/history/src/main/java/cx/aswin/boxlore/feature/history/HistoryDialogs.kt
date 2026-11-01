package cx.aswin.boxlore.feature.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView

/** The Clear all confirmation. */
@Composable
internal fun HistoryScreenDialogs(showClearDialog: Boolean, viewModel: HistoryViewModel, onDismissClearDialog: () -> Unit,) {
    if (showClearDialog) {
        TrackScreenView("clear_listening_history_confirmation")
        AlertDialog(
            onDismissRequest = onDismissClearDialog,
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        onDismissClearDialog()
                    },
                ) {
                    Text(stringResource(R.string.history_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearDialog) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }
}
