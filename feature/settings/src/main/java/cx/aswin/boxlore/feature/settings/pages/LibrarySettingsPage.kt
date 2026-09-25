package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.components.ContentRegionLanguagePicker
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold

/**
 * Discovery region and language preferences for [LibrarySettingsPage].
 */
data class LibraryDiscoveryPreferences(
    val currentRegion: String,
    val contentLanguages: List<String>,
    val onSetRegion: (String) -> Unit,
    val onSetContentLanguages: (List<String>) -> Unit,
)

/**
 * Export/import callbacks for [LibrarySettingsPage], grouped to keep its parameter count small.
 * Also used by [cx.aswin.boxlore.feature.settings.SettingsScreen].
 */
data class LibraryBackupActions(
    val onExportJson: () -> Unit,
    val onExportOpml: () -> Unit,
    val onImportJson: () -> Unit,
    val onImportOpml: () -> Unit,
)

@Composable
internal fun LibrarySettingsPage(
    discoveryPreferences: LibraryDiscoveryPreferences,
    onAddRssClick: () -> Unit,
    backupActions: LibraryBackupActions,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
    accountStatus: String? = null,
    syncStatus: CloudSyncUiStatus = CloudSyncUiStatus.Idle,
) {
    var isCountryFaqExpanded by rememberSaveable { mutableStateOf(false) }
    val collapseCountryFaq = { isCountryFaqExpanded = false }

    SettingsScaffold(
        title = "Library",
        onBack = onBack,
        onUnconsumedTap = if (isCountryFaqExpanded) collapseCountryFaq else null,
    ) {
        LibraryAccountSyncGroup(
            accountStatus = accountStatus,
            syncStatus = syncStatus,
            onAccountClick = {
                collapseCountryFaq()
                onAccountClick()
            },
        )
        SettingsGroup(title = "Discovery") {
            SettingsContent {
                ContentRegionLanguagePicker(
                    activeRegion = discoveryPreferences.currentRegion,
                    selectedLanguages = discoveryPreferences.contentLanguages,
                    onSwitchRegion = {
                        collapseCountryFaq()
                        discoveryPreferences.onSetRegion(it)
                    },
                    onLanguagesChange = {
                        collapseCountryFaq()
                        discoveryPreferences.onSetContentLanguages(it)
                    },
                )
            }
            SettingsDivider()
            CountryNotListedFaq(
                expanded = isCountryFaqExpanded,
                onExpand = { isCountryFaqExpanded = true },
                onCollapse = collapseCountryFaq,
            )
        }

        SettingsGroup(title = "RSS") {
            SettingsNavigationRow(
                title = "Add podcast by RSS feed",
                supportingText = "For podcasts that are not in the catalog",
                icon = Icons.Rounded.RssFeed,
                onClick = {
                    collapseCountryFaq()
                    onAddRssClick()
                },
            )
        }

        LibraryBackupGroups(
            backupActions = backupActions,
            onAction = collapseCountryFaq,
        )
    }
}

@Composable
private fun LibraryAccountSyncGroup(
    accountStatus: String?,
    syncStatus: CloudSyncUiStatus,
    onAccountClick: () -> Unit,
) {
    SettingsGroup(title = "Account & Cloud Sync") {
        LibraryAccountSyncButton(
            accountStatus = accountStatus,
            syncStatus = syncStatus,
            onClick = onAccountClick,
        )
    }
}

private data class LibrarySyncVisualState(
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
)

@Composable
private fun resolveLibrarySyncVisual(
    isSignedIn: Boolean,
    syncStatus: CloudSyncUiStatus,
): LibrarySyncVisualState {
    val isSyncError = syncStatus is CloudSyncUiStatus.Error
    val isSyncing = syncStatus is CloudSyncUiStatus.Syncing

    return when {
        !isSignedIn -> LibrarySyncVisualState(
            icon = Icons.Rounded.CloudSync,
            iconTint = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        )
        isSyncError -> LibrarySyncVisualState(
            icon = Icons.Rounded.CloudOff,
            iconTint = MaterialTheme.colorScheme.error,
            iconBackground = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        )
        isSyncing -> LibrarySyncVisualState(
            icon = Icons.Rounded.CloudSync,
            iconTint = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        )
        else -> LibrarySyncVisualState(
            icon = Icons.Rounded.CloudDone,
            iconTint = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun LibrarySyncStatusBadge(
    syncStatus: CloudSyncUiStatus,
    modifier: Modifier = Modifier,
) {
    val isSyncError = syncStatus is CloudSyncUiStatus.Error
    val isSyncing = syncStatus is CloudSyncUiStatus.Syncing

    val statusDotColor = if (isSyncError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val statusText = when {
        isSyncError -> "Sync issue"
        isSyncing -> "Syncing..."
        else -> "Cloud sync active"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(statusDotColor, CircleShape),
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusDotColor,
            fontWeight = GoogleSansWeight.medium,
        )
    }
}

@Composable
private fun LibraryAccountSyncButton(
    accountStatus: String?,
    syncStatus: CloudSyncUiStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSignedIn = accountStatus != null
    val visual = resolveLibrarySyncVisual(isSignedIn, syncStatus)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable(
                shape = MaterialTheme.shapes.large,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.medium,
            color = visual.iconBackground,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = visual.iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Cloud sync with account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = if (isSignedIn) {
                    "Signed in as $accountStatus"
                } else {
                    "Sign in to backup and sync across devices"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isSignedIn) {
                Spacer(Modifier.height(1.dp))
                LibrarySyncStatusBadge(syncStatus = syncStatus)
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Open account settings",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LibraryBackupGroups(
    backupActions: LibraryBackupActions,
    onAction: () -> Unit,
) {
    SettingsGroup(title = "Export") {
        SettingsNavigationRow(
            title = "Full library backup",
            supportingText = "Subscriptions, history, likes, settings, and catalog shows with Missing episodes? (JSON)",
            icon = Icons.Rounded.FileUpload,
            onClick = {
                onAction()
                backupActions.onExportJson()
            },
        )
        SettingsDivider()
        SettingsNavigationRow(
            title = "Subscriptions only",
            supportingText = "OPML file",
            icon = Icons.Rounded.FileUpload,
            onClick = {
                onAction()
                backupActions.onExportOpml()
            },
        )
    }

    SettingsGroup(title = "Import") {
        SettingsNavigationRow(
            title = "Full library backup",
            supportingText = "Restores Missing episodes? opt-ins and refreshes those shows",
            icon = Icons.Rounded.FileDownload,
            onClick = {
                onAction()
                backupActions.onImportJson()
            },
        )
        SettingsDivider()
        SettingsNavigationRow(
            title = "Subscriptions only",
            supportingText = "Import from an OPML file",
            icon = Icons.Rounded.FileDownload,
            onClick = {
                onAction()
                backupActions.onImportOpml()
            },
        )
    }
}

@Composable
private fun CountryNotListedFaq(
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .expressiveClickable(
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        if (expanded) onCollapse() else onExpand()
                    },
                ).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Why is my country not listed?",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = GoogleSansWeight.medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector =
                if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .expressiveClickable(
                        shape = MaterialTheme.shapes.medium,
                        onClick = onCollapse,
                    ).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Why isn't my country listed?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "We are actively working to expand our global coverage! Currently, we prioritize adding new countries based on active user demand.",
                )
                Text(
                    text = "How this affects your experience:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "Search is still global: You can still search for, find, and access all content, even if your country isn't officially listed yet.",
                )
                FaqParagraph(
                    "Smart recommendations still work: The recommendation engine will still suggest the closest matches based on your listening habits, irrespective of your country selection.",
                )
                FaqParagraph(
                    "What country changes: Charts and regional ranking. Changing country also resets your language picks to a recommended set for that market.",
                )
                FaqParagraph(
                    "Languages are separate: Use the language chips in this section to fine-tune For You and vibes (English stays on; pick up to three more). Charts stay country-based and are not hard-filtered by language.",
                )
                Text(
                    text = "Want another country or language?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "Tell us via Feedback or GitHub — we expand coverage based on demand.",
                )
            }
        }
    }
}

@Composable
private fun FaqParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
