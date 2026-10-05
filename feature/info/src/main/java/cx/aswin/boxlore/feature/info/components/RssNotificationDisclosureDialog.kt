package cx.aswin.boxlore.feature.info.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.info.R

@Composable
internal fun RssNotificationDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rss_notification_disclosure_title)) },
        text = {
            Text(stringResource(R.string.rss_notification_disclosure_body))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.rss_notification_disclosure_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rss_notification_disclosure_dismiss)) } },
    )
}
