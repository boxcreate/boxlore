package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.settings.ProfileSettingsDestination
import cx.aswin.boxlore.feature.settings.components.SettingsCategoryCard
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.dialogs.SupportDevelopmentSheet

@Composable
internal fun SettingsHub(
    onBack: () -> Unit,
    onNavigate: (ProfileSettingsDestination) -> Unit,
) {
    var showSupportSheet by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = "Settings",
        onBack = onBack,
        actions = {
            FilledTonalButton(
                onClick = { showSupportSheet = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = "Support development",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Support us",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        if (showSupportSheet) {
            SupportDevelopmentSheet(
                onDismissRequest = { showSupportSheet = false },
            )
        }

        SettingsCategoryCard(
            title = "Cloud Sync & Backups",
            description = "Account sync, OPML feeds, and library backups",
            icon = Icons.Rounded.CloudSync,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.SyncAndBackups) },
        )
        SettingsCategoryCard(
            title = "Library",
            description = "Discovery region, content languages, and RSS feeds",
            icon = Icons.AutoMirrored.Rounded.LibraryBooks,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.Library) },
        )
        SettingsCategoryCard(
            title = "Appearance",
            description = "Theme and navigation settings",
            icon = Icons.Rounded.Palette,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.Appearance) },
        )
        SettingsCategoryCard(
            title = "Playback",
            description = "Intro skip, seek controls, and completed episodes",
            icon = Icons.Rounded.Tune,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.Playback) },
        )
        SettingsCategoryCard(
            title = "Downloads",
            description = "Smart and automatic episode downloads",
            icon = Icons.Rounded.DownloadForOffline,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { onNavigate(ProfileSettingsDestination.Downloads) },
        )
        SettingsCategoryCard(
            title = "Privacy",
            description = "Analytics data and deletion requests",
            icon = Icons.Rounded.PrivacyTip,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.Privacy) },
        )
        SettingsCategoryCard(
            title = "About",
            description = "App version and Podcast Index",
            icon = Icons.Rounded.Info,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.About) },
        )
    }
}
