package cx.aswin.boxlore.feature.settings.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R

@Composable
internal fun AutoDownloadSettingsHelp(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_auto_download_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AutoDownloadHelpSection(
                    stringResource(R.string.settings_auto_download_help_shows_title),
                    stringResource(R.string.settings_auto_download_help_shows),
                )
                AutoDownloadHelpSection(
                    stringResource(R.string.settings_auto_download_background_title),
                    stringResource(R.string.settings_auto_download_help_closed),
                )
                AutoDownloadHelpSection(
                    stringResource(R.string.settings_auto_download_help_limits_title),
                    stringResource(R.string.settings_auto_download_help_limits),
                )
                AutoDownloadHelpSection(
                    stringResource(R.string.settings_auto_download_help_keeping_title),
                    stringResource(R.string.settings_auto_download_help_keeping),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_got_it)) } },
    )
}

@Composable
private fun AutoDownloadHelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.semiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
