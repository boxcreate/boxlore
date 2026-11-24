package cx.aswin.boxlore.feature.info.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogConfirmButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogDismissButton
import cx.aswin.boxlore.feature.info.R

@Composable
internal fun RssNotificationDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TrackScreenView("rss_notification_disclosure")
    BoxloreAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rss_notification_disclosure_title)) },
        text = {
            Text(stringResource(R.string.rss_notification_disclosure_body))
        },
        confirmButton = { BoxloreDialogConfirmButton(stringResource(R.string.rss_notification_disclosure_confirm), onConfirm) },
        dismissButton = { BoxloreDialogDismissButton(stringResource(R.string.rss_notification_disclosure_dismiss), onDismiss) },
    )
}
