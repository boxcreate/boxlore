package cx.aswin.boxlore.feature.settings.pages

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.feature.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
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

    val authState = rememberAccountAuthState(
        authRepository = authRepository,
        context = context,
        scope = scope,
    )

    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user == null) {
            authState.resetToNewEmail()
        } else if (user.isEmailVerified) {
            authState.isAwaitingVerification = false
            AccountVerificationStorage.setPref(context, false)
        }
    }

    LaunchedEffect(authState.isAwaitingVerification, authState.magicLinkSent) {
        if (authState.isAwaitingVerification || authState.magicLinkSent) {
            scrollState.animateScrollTo(0)
        }
    }

    SettingsScaffold(
        title = "Account",
        onBack = onBack,
        scrollState = scrollState,
    ) {
        val user = currentUser
        val isAwaiting = authState.isAwaitingVerification ||
            (user != null && !user.isEmailVerified && AccountVerificationStorage.getPref(context))

        if (user != null && !isAwaiting) {
            SignedInContent(
                user = user,
                onResetPassword = {
                    val email = user.email
                    if (!email.isNullOrBlank()) {
                        scope.launch {
                            val res = authRepository?.sendPasswordReset(email)
                            if (res?.isSuccess == true) {
                                Toast.makeText(context, "Password reset email sent to $email", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    res?.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                },
                onSignOut = {
                    authRepository?.signOut()
                    authState.resetToNewEmail()
                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                },
                onDeleteAccountClick = { showDeleteConfirmation = true },
                syncStatus = syncStatus,
                onSyncNow = onSyncNow,
            )
        } else {
            SignedOutContent(
                authRepository = authRepository,
                state = authState,
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
            authState.resetToNewEmail()
            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
private fun ColumnScope.SignedInContent(
    user: BoxLoreUser,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    syncStatus: CloudSyncUiStatus,
    onSyncNow: () -> Unit,
) {
    UserProfileCard(user = user, syncStatus = syncStatus)
    CloudSyncInfoGroup(
        syncStatus = syncStatus,
        onSyncNow = onSyncNow,
    )
    AccountManagementGroup(
        user = user,
        onResetPassword = onResetPassword,
        onSignOut = onSignOut,
        onDeleteAccountClick = onDeleteAccountClick,
    )
}

internal fun resolveProviderLabel(providerId: String?): String = when (providerId) {
    "google.com" -> "Google"
    "emailLink" -> "Email Link"
    else -> "Password"
}

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

@Composable
private fun ProviderIcon(providerId: String?) {
    when (providerId) {
        "google.com" -> Image(
            painter = painterResource(cx.aswin.boxlore.core.designsystem.R.drawable.ic_google_logo),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
        )
        "emailLink" -> Icon(
            imageVector = Icons.Rounded.Email,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        else -> Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun AccountStatusPill(
    providerId: String?,
    syncStatus: CloudSyncUiStatus,
    modifier: Modifier = Modifier,
) {
    val isError = syncStatus is CloudSyncUiStatus.Error
    val providerLabel = resolveProviderLabel(providerId)
    val syncText = resolveSyncPillText(syncStatus)
    val syncIcon = resolveSyncPillIcon(syncStatus)
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val syncColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            ProviderIcon(providerId = providerId)
            Spacer(Modifier.width(5.dp))
            Text(
                text = providerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = GoogleSansWeight.medium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "•",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = syncIcon,
                contentDescription = null,
                tint = syncColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = syncText,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = GoogleSansWeight.medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun UserProfileCard(
    user: BoxLoreUser,
    syncStatus: CloudSyncUiStatus,
) {
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
            AnimatedBlobAvatar(
                size = 76.dp,
            )

            Spacer(Modifier.height(14.dp))

            val displayName = user.displayName ?: user.email ?: "boxlore listener"
            Text(
                text = displayName,
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

            Spacer(Modifier.height(16.dp))

            AccountStatusPill(
                providerId = user.providerId,
                syncStatus = syncStatus,
            )
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
        title = "Cloud Sync & Storage",
        footer = "Your subscriptions, queue, and playback progress stay backed up and synchronized across your devices.",
    ) {
        SettingsContent {
            Column(modifier = Modifier.fillMaxWidth()) {
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

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Subscriptions", "Queue", "Progress").forEach { scopeTag ->
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Text(
                                text = scopeTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
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
    user: BoxLoreUser,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
    val isPasswordAccount = user.providerId != "google.com" && !user.email.isNullOrBlank()
    SettingsGroup(title = "Account Management") {
        if (isPasswordAccount) {
            SettingsActionRow(
                title = "Reset Password",
                supportingText = "Send password reset instructions to ${user.email}",
                icon = Icons.Rounded.Key,
                onClick = onResetPassword,
            )
            SettingsDivider()
        }
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
