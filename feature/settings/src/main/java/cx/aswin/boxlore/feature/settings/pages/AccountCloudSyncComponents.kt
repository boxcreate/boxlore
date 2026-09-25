package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsGroup

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
            title = "Synchronizing...",
            subtitle = "Uploading local changes and fetching updates...",
            icon = Icons.Rounded.CloudSync,
            containerColor = primaryContainer,
            tintColor = onPrimaryContainer,
        )
        is CloudSyncUiStatus.Success -> SyncDisplayState(
            title = "Library Synchronized",
            subtitle = formatRelativeSyncTime(syncStatus.syncedAt),
            icon = Icons.Rounded.CloudDone,
            containerColor = primaryContainer,
            tintColor = onPrimaryContainer,
        )
        is CloudSyncUiStatus.Error -> SyncDisplayState(
            title = "Sync Issue",
            subtitle = syncStatus.message,
            icon = Icons.Rounded.CloudOff,
            containerColor = errorContainer,
            tintColor = onErrorContainer,
        )
        CloudSyncUiStatus.Idle -> SyncDisplayState(
            title = "Library Sync Ready",
            subtitle = "Connected and ready to synchronize changes.",
            icon = Icons.Rounded.CloudDone,
            containerColor = primaryContainer,
            tintColor = onPrimaryContainer,
        )
    }
}

@Composable
internal fun SyncNowButton(
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
) {
    val rotation = if (isSyncing) {
        val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
        val animatedRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "SyncSpin",
        )
        animatedRotation
    } else {
        0f
    }

    val hapticFeedback = LocalHapticFeedback.current

    IconButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onSyncNow()
        },
        enabled = !isSyncing,
    ) {
        Icon(
            imageVector = Icons.Rounded.Sync,
            contentDescription = "Sync now",
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation),
            tint = if (isSyncing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
internal fun CloudSyncInfoGroup(
    syncStatus: CloudSyncUiStatus,
    onSyncNow: () -> Unit,
) {
    val isSyncing = syncStatus is CloudSyncUiStatus.Syncing
    val displayState = resolveSyncDisplayState(syncStatus)

    SettingsGroup(
        title = "Cloud Sync & Storage",
        footer = "Your subscriptions, queue, and playback progress stay backed up and synchronized across your devices.",
    ) {
        SettingsContent {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = displayState.containerColor,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = displayState.icon,
                            contentDescription = null,
                            tint = displayState.tintColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayState.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = GoogleSansWeight.bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = displayState.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncStatus is CloudSyncUiStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                SyncNowButton(
                    isSyncing = isSyncing,
                    onSyncNow = onSyncNow,
                )
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
