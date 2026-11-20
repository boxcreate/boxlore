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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.feature.settings.R

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
        title = { Text(stringResource(R.string.settings_clear_saved_shows_title)) },
        text = {
            Column(Modifier.tabletSheetScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.settings_clear_saved_shows_body, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_clear_saved_shows_kept),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag(SettingsClearSavedShowsTestTags.CONFIRM)) {
                Text(stringResource(R.string.settings_clear_saved_shows_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(SettingsClearSavedShowsTestTags.CANCEL)) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
