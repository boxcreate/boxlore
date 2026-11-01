package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** Asks before auto-organize is turned off, from its page; turning it on is done on that page. */
@Composable
internal fun AutoOrganizeDisableDialog(
    onProceed: () -> Unit,
    onCancel: () -> Unit,
) {
    TrackScreenView("disable_auto_organize_confirmation")
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Turn off auto-organize?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Genre folders keep their genre link, so they keep filling after this (by design).
                Text(
                    text = "boxlore stops making new genre folders. The genre folders you have stay, and new shows of their genre still join them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "To get rid of one, delete the folder. Its shows stay in your library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onProceed,
                shape = ExpressiveShapes.Pill,
            ) {
                Text("Turn off", fontWeight = GoogleSansWeight.bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
            ) {
                Text("Cancel", fontWeight = GoogleSansWeight.medium)
            }
        },
    )
}
