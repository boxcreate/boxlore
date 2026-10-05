package cx.aswin.boxlore.feature.info.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun RssNotificationDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable notifications for a public feed?") },
        text = {
            Text("Adding this RSS feed does not publish its URL. Enabling notifications sends the feed URL to boxlore’s shared checker. The URL is stored in public GitHub backups, and episode details are also published. Keep notifications off for private, premium, or token-protected feeds.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("This is a public feed · Enable") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep notifications off") } },
    )
}
