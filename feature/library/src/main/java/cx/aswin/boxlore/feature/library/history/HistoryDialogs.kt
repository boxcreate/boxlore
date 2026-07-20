package cx.aswin.boxlore.feature.library.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.library.HistoryViewModel
import cx.aswin.boxlore.feature.library.R
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreenDialogs(
    showTrackingNotice: Boolean,
    showClearDialog: Boolean,
    showDatePicker: Boolean,
    viewModel: HistoryViewModel,
    onDismissClearDialog: () -> Unit,
    onDismissDatePicker: () -> Unit,
) {
    if (showTrackingNotice) {
        AlertDialog(
            onDismissRequest = viewModel::dismissTrackingNotice,
            title = { Text(stringResource(R.string.history_tracking_notice_title)) },
            text = { Text(stringResource(R.string.history_tracking_notice_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissTrackingNotice) {
                    Text(stringResource(R.string.history_tracking_notice_confirm))
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearDialog,
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissClearDialog()
                        viewModel.clearAllHistory()
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

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date =
                                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            viewModel.setFilterDate(date)
                        }
                        onDismissDatePicker()
                    },
                ) {
                    Text(stringResource(R.string.history_pick_date))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDatePicker) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
