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
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

@Composable
internal fun AutoDownloadSettingsHelp(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
        title = { Text("How automatic downloads work") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AutoDownloadHelpSection(
                    "Choose your shows",
                    "Turn on auto-download from a podcast's page. It picks up new episodes, rather than downloading the show's archive.",
                )
                AutoDownloadHelpSection(
                    "While boxlore is closed",
                    "Show notifications can trigger downloads when new episodes arrive. Optional background checks provide another way to find episodes, about every 6 hours. Android may delay those checks.",
                )
                AutoDownloadHelpSection(
                    "Battery and network limits",
                    "Background checks use extra battery and data and always pause on low battery. Their network and charging limits also apply to the downloads they start. The Downloads Wi-Fi setting applies to audio files from every discovery method.",
                )
                AutoDownloadHelpSection(
                    "Keeping episodes",
                    "The per-show limit replaces only older, completed automatic downloads; manually saved files are protected from that limit. Remove after listening deletes downloaded files when you finish an episode.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun AutoDownloadHelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.semiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
