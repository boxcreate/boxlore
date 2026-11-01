package cx.aswin.boxlore.feature.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll

object SettingsClearSavedShowsTestTags {
    const val CONFIRM = "settings_clear_saved_shows_confirm"
    const val CANCEL = "settings_clear_saved_shows_cancel"
}

/**
 * Confirms clearing the saved episode lists of shows the listener opened but doesn't follow
 * (Library settings › Storage, P4 PR 20). Says what goes, that it comes back on the next open, and
 * what stays.
 */
@Composable
internal fun ClearSavedShowsDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView("clear_saved_shows_confirmation")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = null) },
        title = { Text("Clear saved shows?") },
        text = {
            Column(Modifier.tabletSheetScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${savedShowsLabel(count)} you don’t follow will be removed from this phone. They load again when you open a show.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Shows you follow, your listening history, downloads and queue aren’t affected.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag(SettingsClearSavedShowsTestTags.CONFIRM)) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(SettingsClearSavedShowsTestTags.CANCEL)) {
                Text("Cancel")
            }
        },
    )
}

/** "Episode lists of 1 show" / "Episode lists of 12 shows". */
internal fun savedShowsLabel(count: Int): String = if (count == 1) "Episode lists of 1 show" else "Episode lists of $count shows"
