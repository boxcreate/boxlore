package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.AuthRepository
import cx.aswin.boxlore.feature.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun AccountSettingsPage(
    authRepository: AuthRepository?,
    onBack: () -> Unit,
    syncStatus: CloudSyncUiStatus = CloudSyncUiStatus.Idle,
    onSyncNow: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val currentUser by (
        authRepository?.currentUser?.collectAsState()
            ?: remember { mutableStateOf<BoxLoreUser?>(null) }
    )

    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showReauthRequiredDialog by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = "Account",
        onBack = onBack,
        scrollState = scrollState,
    ) {
        val user = currentUser
        if (user != null) {
            SignedInContent(
                user = user,
                onSignOut = {
                    authRepository?.signOut()
                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                },
                onDeleteAccountClick = { showDeleteConfirmation = true },
                syncStatus = syncStatus,
                onSyncNow = onSyncNow,
            )
        } else {
            SignedOutContent(
                authRepository = authRepository,
            )
        }
    }

    AccountSettingsDialogs(
        showDeleteConfirmation = showDeleteConfirmation,
        showReauthRequiredDialog = showReauthRequiredDialog,
        onDismissDeleteConfirmation = { showDeleteConfirmation = false },
        onConfirmDelete = {
            showDeleteConfirmation = false
            handleDeleteAccount(
                scope = scope,
                context = context,
                authRepository = authRepository,
                onReauthRequired = { showReauthRequiredDialog = true },
            )
        },
        onDismissReauthRequired = { showReauthRequiredDialog = false },
        onSignOutToReauth = {
            showReauthRequiredDialog = false
            authRepository?.signOut()
            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
        },
    )
}

internal sealed interface AccountDeletionOutcome {
    data object Success : AccountDeletionOutcome
    data object ReauthRequired : AccountDeletionOutcome
    data class Failure(val message: String) : AccountDeletionOutcome
}

internal fun resolveAccountDeletionOutcome(result: Result<*>?): AccountDeletionOutcome {
    if (result?.isSuccess == true) {
        return AccountDeletionOutcome.Success
    }
    val error = result?.exceptionOrNull()
    return if (error.isRecentLoginRequired()) {
        AccountDeletionOutcome.ReauthRequired
    } else {
        AccountDeletionOutcome.Failure(cleanAccountError(error?.localizedMessage))
    }
}

private fun handleDeleteAccount(
    scope: CoroutineScope,
    context: Context,
    authRepository: AuthRepository?,
    onReauthRequired: () -> Unit,
) {
    scope.launch {
        when (val outcome = resolveAccountDeletionOutcome(authRepository?.deleteAccount())) {
            AccountDeletionOutcome.Success -> {
                Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
            }
            AccountDeletionOutcome.ReauthRequired -> {
                onReauthRequired()
            }
            is AccountDeletionOutcome.Failure -> {
                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun AccountSettingsDialogs(
    showDeleteConfirmation: Boolean,
    showReauthRequiredDialog: Boolean,
    onDismissDeleteConfirmation: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissReauthRequired: () -> Unit,
    onSignOutToReauth: () -> Unit,
) {
    if (showDeleteConfirmation) {
        DeleteAccountConfirmationDialog(
            onDismiss = onDismissDeleteConfirmation,
            onConfirmDelete = onConfirmDelete,
        )
    }

    if (showReauthRequiredDialog) {
        ReauthRequiredDialog(
            onDismiss = onDismissReauthRequired,
            onSignOutToReauth = onSignOutToReauth,
        )
    }
}

@Composable
private fun DeleteAccountConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Account", fontWeight = GoogleSansWeight.bold) },
        text = {
            Text(
                "Are you sure you want to delete your boxlore account? " +
                    "This permanently removes your cloud profile and cross-device sync data. " +
                    "Your local downloads and podcast catalog on this device will not be erased.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete Permanently")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ReauthRequiredDialog(
    onDismiss: () -> Unit,
    onSignOutToReauth: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent Sign-In Required", fontWeight = GoogleSansWeight.bold) },
        text = {
            Text(
                "For your security, deleting your account requires recent authentication. " +
                    "Please sign out and sign back in, then try deleting your account again.",
            )
        },
        confirmButton = {
            Button(onClick = onSignOutToReauth) {
                Text("Sign Out to Re-authenticate")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ColumnScope.SignedInContent(
    user: BoxLoreUser,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    syncStatus: CloudSyncUiStatus,
    onSyncNow: () -> Unit,
) {
    UserProfileCard(user = user)
    CloudSyncInfoGroup(
        syncStatus = syncStatus,
        onSyncNow = onSyncNow,
    )
    AccountManagementGroup(
        onSignOut = onSignOut,
        onDeleteAccountClick = onDeleteAccountClick,
    )
}

@Composable
private fun UserProfileCard(user: BoxLoreUser) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(68.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = (user.displayName ?: user.email)?.take(1)?.uppercase() ?: "B"
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = user.displayName ?: user.email ?: "boxlore listener",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                textAlign = TextAlign.Center,
            )

            val email = user.email
            if (user.displayName != null && email != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Cloud sync active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = GoogleSansWeight.medium,
                    )
                }
            }
        }
    }
}

private data class SyncDisplayState(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val containerColor: Color,
    val tintColor: Color,
)

@Composable
private fun resolveSyncDisplayState(syncStatus: CloudSyncUiStatus): SyncDisplayState {
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
private fun SyncNowButton(
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
private fun CloudSyncInfoGroup(
    syncStatus: CloudSyncUiStatus,
    onSyncNow: () -> Unit,
) {
    val isSyncing = syncStatus is CloudSyncUiStatus.Syncing
    val displayState = resolveSyncDisplayState(syncStatus)

    SettingsGroup(
        title = "Cloud Synchronization",
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

@Composable
private fun AccountManagementGroup(
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
    SettingsGroup(title = "Account Management") {
        SettingsActionRow(
            title = "Sign Out",
            supportingText = "Disconnect this device from your cloud account",
            icon = Icons.AutoMirrored.Rounded.Logout,
            onClick = onSignOut,
        )
        SettingsDivider()
        SettingsActionRow(
            title = "Delete Account",
            supportingText = "Permanently remove your cloud profile and cross-device data",
            icon = Icons.Rounded.DeleteForever,
            destructive = true,
            onClick = onDeleteAccountClick,
        )
    }
}
