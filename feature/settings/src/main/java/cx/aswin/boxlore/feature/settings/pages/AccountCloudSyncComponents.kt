package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R

internal fun resolveSyncPillText(syncStatus: CloudSyncUiStatus): String = when (syncStatus) {
    is CloudSyncUiStatus.Syncing -> "Syncing..."
    is CloudSyncUiStatus.Error -> "Sync issue"
    else -> "Cloud sync active"
}

internal fun resolveSyncPillIcon(syncStatus: CloudSyncUiStatus): ImageVector = when (syncStatus) {
    is CloudSyncUiStatus.Error -> Icons.Rounded.CloudOff
    is CloudSyncUiStatus.Syncing -> Icons.Rounded.CloudSync
    else -> Icons.Rounded.CheckCircle
}

internal data class SyncDisplayState(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val containerColor: Color,
    val tintColor: Color,
)

@Composable
internal fun resolveSyncDisplayState(syncStatus: CloudSyncUiStatus): SyncDisplayState {
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    return when (syncStatus) {
        is CloudSyncUiStatus.Syncing -> SyncDisplayState(
            title = stringResource(R.string.account_sync_busy),
            subtitle = stringResource(R.string.account_sync_busy_body),
            icon = Icons.Rounded.CloudSync,
            containerColor = primaryContainer,
            tintColor = onPrimaryContainer,
        )
        is CloudSyncUiStatus.Success -> SyncDisplayState(
            title = stringResource(R.string.account_sync_success),
            subtitle = formatRelativeSyncTime(syncStatus.syncedAt),
            icon = Icons.Rounded.CloudDone,
            containerColor = primaryContainer,
            tintColor = onPrimaryContainer,
        )
        is CloudSyncUiStatus.Error -> SyncDisplayState(
            title = stringResource(R.string.account_sync_error),
            subtitle = stringResource(R.string.account_sync_error_body),
            icon = Icons.Rounded.CloudOff,
            containerColor = errorContainer,
            tintColor = onErrorContainer,
        )
        CloudSyncUiStatus.Idle -> SyncDisplayState(
            title = stringResource(R.string.account_sync_ready),
            subtitle = stringResource(R.string.account_sync_ready_body),
            icon = Icons.Rounded.CloudDone,
            containerColor = primaryContainer,
            tintColor = onPrimaryContainer,
        )
    }
}

@Composable
internal fun CloudSyncInfoGroup(syncStatus: CloudSyncUiStatus, onSyncNow: () -> Unit) {
    if (syncStatus is CloudSyncUiStatus.Idle || syncStatus is CloudSyncUiStatus.Success) {
        ReadyCloudSyncCard(syncStatus, onSyncNow)
        return
    }
    val isSyncing = syncStatus is CloudSyncUiStatus.Syncing
    val displayState = resolveSyncDisplayState(syncStatus)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.account_sync_title), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
        Surface(
            color = if (syncStatus is CloudSyncUiStatus.Error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().testTag("account_sync_status"),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(displayState.icon, null, Modifier.size(28.dp))
                    Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(displayState.title, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                        Text(displayState.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (isSyncing) {
                    val progressDescription = stringResource(R.string.account_sync_progress)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription }, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                } else {
                    FilledTonalButton(onSyncNow, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Icon(Icons.Rounded.Sync, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (syncStatus is CloudSyncUiStatus.Error) R.string.account_sync_retry else R.string.account_sync_now))
                    }
                }
            }
        }
        Text(stringResource(R.string.account_sync_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadyCloudSyncCard(syncStatus: CloudSyncUiStatus, onSyncNow: () -> Unit) {
    val displayState = resolveSyncDisplayState(syncStatus)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().testTag("account_sync_status"),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (syncStatus is CloudSyncUiStatus.Success) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.account_sync_title), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                    if (syncStatus is CloudSyncUiStatus.Success) {
                        Text(displayState.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(stringResource(R.string.account_sync_ready_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account_sync_now"),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            ) {
                Icon(Icons.Rounded.Sync, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_sync_now), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

internal fun formatRelativeSyncTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return "Never synced"
    val diff = (now - timestamp).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 30 -> "Synced just now"
        minutes < 1 -> "Synced less than a minute ago"
        minutes == 1L -> "Synced 1m ago"
        minutes < 60 -> "Synced ${minutes}m ago"
        hours == 1L -> "Synced 1h ago"
        hours < 24 -> "Synced ${hours}h ago"
        days == 1L -> "Synced 1d ago"
        else -> "Synced ${days}d ago"
    }
}
