package cx.aswin.boxlore.feature.settings.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow
import kotlinx.coroutines.launch

@Composable
fun AutoDownloadSettingsScreen(
    userPrefs: UserPreferencesRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val wifiOnly by userPrefs.autoDownloadWifiOnlyStream.collectAsStateWithLifecycle(initialValue = true)
    val maxEpisodes by userPrefs.autoDownloadMaxEpisodesStream.collectAsStateWithLifecycle(initialValue = 2)
    val deleteCompleted by userPrefs.autoDownloadDeleteCompletedStream.collectAsStateWithLifecycle(initialValue = true)
    val background by userPrefs.autoDownloadBackgroundSettingsStream.collectAsStateWithLifecycle(initialValue = AutoDownloadBackgroundSettings())
    var showHelp by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = "Automatic downloads",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showHelp = true }) {
                Icon(Icons.Rounded.Info, contentDescription = "How automatic downloads work")
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("For the shows you choose", style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.semiBold)
                    Text("Turn on auto-download from each show's page.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SettingsGroup(title = "Downloads") {
            SettingsSwitchRow(
                title = "Wi-Fi only",
                supportingText = "Audio files wait for an unmetered connection.",
                icon = Icons.Rounded.Wifi,
                checked = wifiOnly,
                onCheckedChange = { scope.launch { userPrefs.setAutoDownloadWifiOnly(it) } },
            )
        }

        SettingsGroup(title = "Storage") {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Keep episodes per show", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Replace the oldest automatic download when this limit is reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConnectedOptionSelector(
                        options = listOf(1 to "1", 2 to "2", 3 to "3", 5 to "5"),
                        selected = maxEpisodes,
                        onSelect = { scope.launch { userPrefs.setAutoDownloadMaxEpisodes(it) } },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
            SettingsDivider()
            SettingsSwitchRow(
                title = "Remove after listening",
                supportingText = "Delete downloaded files after an episode is finished.",
                icon = Icons.Rounded.DeleteOutline,
                checked = deleteCompleted,
                onCheckedChange = { scope.launch { userPrefs.setAutoDownloadDeleteCompleted(it) } },
            )
        }

        AutoDownloadBackgroundSettingsCard(
            settings = background,
            onEnabledChange = { scope.launch { userPrefs.setAutoDownloadBackgroundChecksEnabled(it) } },
            onWifiOnlyChange = { scope.launch { userPrefs.setAutoDownloadBackgroundWifiOnly(it) } },
            onChargingOnlyChange = { scope.launch { userPrefs.setAutoDownloadBackgroundChargingOnly(it) } },
        )
    }

    if (showHelp) {
        AutoDownloadSettingsHelp(onDismiss = { showHelp = false })
    }
}
