package cx.aswin.boxlore.feature.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R

object SettingsResetAnalyticsTestTags {
    const val CONFIRM = "settings_reset_analytics_confirm"
    const val CANCEL = "settings_reset_analytics_cancel"
}

@Composable
internal fun ResetAnalyticsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView("reset_analytics_confirmation")
    BoxloreAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(stringResource(R.string.settings_reset_analytics_title))
        },
        text = {
            Column(Modifier.tabletSheetScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_reset_analytics_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_reset_analytics_kept_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = GoogleSansWeight.bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.settings_reset_analytics_kept_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_reset_analytics_device_id),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag(SettingsResetAnalyticsTestTags.CONFIRM),
            ) {
                Text(stringResource(R.string.settings_privacy_new_id_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SettingsResetAnalyticsTestTags.CANCEL),
            ) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
