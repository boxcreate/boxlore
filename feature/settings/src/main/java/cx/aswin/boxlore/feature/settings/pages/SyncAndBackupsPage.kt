package cx.aswin.boxlore.feature.settings.pages

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold

/**
 * Export/import callbacks for [SyncAndBackupsPage], grouped to keep its parameter count small.
 * Also used by [cx.aswin.boxlore.feature.settings.SettingsScreen].
 */
data class LibraryBackupActions(
    val onExportJson: () -> Unit,
    val onExportOpml: () -> Unit,
    val onImportJson: () -> Unit,
    val onImportOpml: () -> Unit,
)

@Composable
internal fun SyncAndBackupsPage(
    accountStatus: String?,
    syncStatus: CloudSyncUiStatus,
    backupActions: LibraryBackupActions,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
) {
    SettingsScaffold(
        title = "Cloud Sync & Backups",
        onBack = onBack,
    ) {
        SyncAccountSection(
            accountStatus = accountStatus,
            syncStatus = syncStatus,
            onAccountClick = onAccountClick,
        )
        SyncBackupExportGroup(backupActions = backupActions)
        SyncBackupImportGroup(backupActions = backupActions)
    }
}

@Composable
private fun SyncAccountSection(
    accountStatus: String?,
    syncStatus: CloudSyncUiStatus,
    onAccountClick: () -> Unit,
) {
    SettingsGroup(title = "Cloud Sync") {
        CloudSyncCard(
            accountStatus = accountStatus,
            syncStatus = syncStatus,
            onClick = onAccountClick,
        )
    }
}

private data class SyncVisualState(
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
)

@Composable
private fun resolveSyncVisual(
    isSignedIn: Boolean,
    syncStatus: CloudSyncUiStatus,
): SyncVisualState {
    val isSyncError = syncStatus is CloudSyncUiStatus.Error
    val isSyncing = syncStatus is CloudSyncUiStatus.Syncing

    return when {
        !isSignedIn || isSyncing -> SyncVisualState(
            icon = Icons.Rounded.CloudSync,
            iconTint = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        )
        isSyncError -> SyncVisualState(
            icon = Icons.Rounded.CloudOff,
            iconTint = MaterialTheme.colorScheme.error,
            iconBackground = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        )
        else -> SyncVisualState(
            icon = Icons.Rounded.CloudDone,
            iconTint = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        )
    }
}

internal fun resolveSyncBadgeText(syncStatus: CloudSyncUiStatus): String = when {
    syncStatus is CloudSyncUiStatus.Error -> "Sync issue"
    syncStatus is CloudSyncUiStatus.Syncing -> "Syncing..."
    else -> "Cloud sync active"
}

internal fun resolveCloudSyncSubtitle(accountStatus: String?): String =
    if (accountStatus != null) {
        "Signed in as $accountStatus"
    } else {
        "Sign in to backup and sync across devices"
    }

@Composable
private fun SyncStatusBadge(
    syncStatus: CloudSyncUiStatus,
    modifier: Modifier = Modifier,
) {
    val isSyncError = syncStatus is CloudSyncUiStatus.Error

    val statusDotColor = if (isSyncError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val statusText = resolveSyncBadgeText(syncStatus)

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
private fun CloudSyncCard(
    accountStatus: String?,
    syncStatus: CloudSyncUiStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSignedIn = accountStatus != null
    val visual = resolveSyncVisual(isSignedIn, syncStatus)

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
                text = "Cloud sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = resolveCloudSyncSubtitle(accountStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isSignedIn) {
                Spacer(Modifier.height(1.dp))
                SyncStatusBadge(syncStatus = syncStatus)
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
private fun SyncBackupExportGroup(
    backupActions: LibraryBackupActions,
) {
    SettingsGroup(title = "Export") {
        SettingsNavigationRow(
            title = "Full library backup",
            supportingText = "Subscriptions, history, likes, settings, and catalog shows with Missing episodes? (JSON)",
            icon = Icons.Rounded.FileUpload,
            onClick = backupActions.onExportJson,
        )
        SettingsDivider()
        SettingsNavigationRow(
            title = "Subscriptions only",
            supportingText = "OPML file",
            icon = Icons.Rounded.FileUpload,
            onClick = backupActions.onExportOpml,
        )
    }
}

@Composable
private fun SyncBackupImportGroup(
    backupActions: LibraryBackupActions,
) {
    SettingsGroup(title = "Import") {
        SettingsNavigationRow(
            title = "Full library backup",
            supportingText = "Restores Missing episodes? opt-ins and refreshes those shows",
            icon = Icons.Rounded.FileDownload,
            onClick = backupActions.onImportJson,
        )
        SettingsDivider()
        SettingsNavigationRow(
            title = "Subscriptions only",
            supportingText = "Import from an OPML file",
            icon = Icons.Rounded.FileDownload,
            onClick = backupActions.onImportOpml,
        )
    }
}
