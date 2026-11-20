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
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.settings.ProfileSettingsDestination
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsCategoryCard
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportEntryImpression
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportEntrySurface

@Composable
internal fun SettingsHub(
    onBack: () -> Unit,
    onNavigate: (ProfileSettingsDestination) -> Unit,
    onCheckForUpdates: (() -> Unit)? = null,
    showWearOs: Boolean = false,
) {
    SupportEntryImpression(SupportEntrySurface.SettingsTopBar)
    SettingsScaffold(
        title = stringResource(R.string.settings_page_hub),
        onBack = onBack,
        actions = {
            FilledTonalButton(
                onClick = { onNavigate(ProfileSettingsDestination.Support) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = stringResource(R.string.settings_page_support),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_page_support),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        SettingsCategoryCard(
            title = stringResource(R.string.settings_page_sync),
            description = stringResource(R.string.settings_hub_sync_description),
            icon = Icons.Rounded.CloudSync,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.SyncAndBackups) },
        )
        SettingsCategoryCard(
            title = stringResource(R.string.settings_page_library),
            description = stringResource(R.string.settings_hub_library_description),
            icon = Icons.AutoMirrored.Rounded.LibraryBooks,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.Library) },
        )
        SettingsCategoryCard(
            title = stringResource(R.string.settings_page_appearance),
            description = stringResource(R.string.settings_hub_appearance_description),
            icon = Icons.Rounded.Palette,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.Appearance) },
        )
        SettingsCategoryCard(
            title = stringResource(R.string.settings_page_playback),
            description = stringResource(R.string.settings_hub_playback_description),
            icon = Icons.Rounded.Tune,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.Playback) },
        )
        SettingsCategoryCard(
            title = stringResource(R.string.settings_page_downloads),
            description = stringResource(R.string.settings_hub_downloads_description),
            icon = Icons.Rounded.DownloadForOffline,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { onNavigate(ProfileSettingsDestination.Downloads) },
        )
        if (showWearOs) {
            SettingsCategoryCard(
                title = stringResource(R.string.settings_page_wear_os),
                description = stringResource(R.string.settings_hub_wear_os_description),
                icon = Icons.Rounded.Watch,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { onNavigate(ProfileSettingsDestination.WearOs) },
            )
        }
        SettingsCategoryCard(
            title = stringResource(R.string.settings_privacy_title),
            description = stringResource(R.string.settings_hub_privacy_description),
            icon = Icons.Rounded.PrivacyTip,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.Privacy) },
        )
        SettingsCategoryCard(
            title = stringResource(R.string.settings_category_about),
            description = stringResource(R.string.settings_hub_about_description),
            icon = Icons.Rounded.Info,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onNavigate(ProfileSettingsDestination.About) },
        )
        onCheckForUpdates?.let { check ->
            SettingsNavigationRow(
                title = stringResource(R.string.settings_check_for_updates),
                icon = Icons.Rounded.SystemUpdate,
                onClick = check,
            )
        }
    }
}
