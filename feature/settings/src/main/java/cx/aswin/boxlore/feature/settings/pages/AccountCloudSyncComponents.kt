package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup

/**
 * Where sync stands, in one solid panel: seafoam when it is on or up to date, primary while a sync runs
 * (with an indeterminate bar), error colours when it failed. Up to date shows when it last synced. Sync
 * now (Try again after a failure) is always one tap away, except while syncing. With [showEcho], Echo
 * stands beside the status on a tone-on-tone stage.
 */
@Composable
internal fun AccountSyncPanel(syncStatus: CloudSyncUiStatus, onSyncNow: () -> Unit, showEcho: Boolean) {
    val colors = MaterialTheme.colorScheme
    val syncing = syncStatus is CloudSyncUiStatus.Syncing
    val failed = syncStatus is CloudSyncUiStatus.Error
    val (container, content) = when {
        failed -> colors.errorContainer to colors.onErrorContainer
        syncing -> colors.primaryContainer to colors.onPrimaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    val (title, body) = accountSyncCopy(syncStatus)
    Surface(Modifier.fillMaxWidth().testTag("account_sync_status"), shape = RoundedCornerShape(28.dp), color = container, contentColor = content) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(accountSyncIcon(syncStatus), contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                if (showEcho) AccountEcho(syncing, Modifier.size(AccountPanelEchoSize), stageColor = content.copy(alpha = 0.12f))
            }
            if (syncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = content, trackColor = content.copy(alpha = 0.2f))
            } else {
                OutlinedButton(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account_sync_now"),
                    border = BorderStroke(1.dp, content),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
                ) {
                    Icon(Icons.Rounded.Sync, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (failed) R.string.account_sync_retry else R.string.account_sync_now),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = GoogleSansWeight.medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The panel's heading and the line under it for each sync state. */
@Composable
private fun accountSyncCopy(status: CloudSyncUiStatus): Pair<String, String> = when (status) {
    is CloudSyncUiStatus.Error -> stringResource(R.string.account_sync_error) to stringResource(R.string.account_sync_error_body)
    CloudSyncUiStatus.Syncing -> stringResource(R.string.account_sync_busy) to stringResource(R.string.account_sync_busy_body)
    is CloudSyncUiStatus.Success -> stringResource(R.string.account_sync_done) to formatRelativeSyncTime(status.syncedAt)
    CloudSyncUiStatus.Idle -> stringResource(R.string.account_sync_on) to stringResource(R.string.account_sync_on_body)
}

private fun accountSyncIcon(status: CloudSyncUiStatus) = when (status) {
    is CloudSyncUiStatus.Error -> Icons.Rounded.CloudOff
    CloudSyncUiStatus.Syncing -> Icons.Rounded.CloudSync
    else -> Icons.Rounded.CloudDone
}

/** Who is signed in, then Reset password (email accounts only), Sign out and Delete account. */
@Composable
internal fun AccountManagementGroup(
    user: BoxLoreUser,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
    val isPasswordAccount = user.providerId != PROVIDER_GOOGLE && !user.email.isNullOrBlank()
    SettingsGroup(title = stringResource(R.string.account_section)) {
        AccountIdentityRow(user)
        SettingsDivider()
        if (isPasswordAccount) {
            SettingsActionRow(title = stringResource(R.string.account_reset_password), icon = Icons.Rounded.Key, onClick = onResetPassword)
            SettingsDivider()
        }
        SettingsActionRow(title = stringResource(R.string.account_sign_out), icon = Icons.AutoMirrored.Rounded.Logout, onClick = onSignOut)
        SettingsDivider()
        SettingsActionRow(title = stringResource(R.string.account_delete), icon = Icons.Rounded.DeleteForever, destructive = true, onClick = onDeleteAccountClick)
    }
}

@Composable
private fun AccountIdentityRow(user: BoxLoreUser) {
    val name = user.displayName?.takeIf { it.isNotBlank() }
    val title = name ?: user.email ?: stringResource(R.string.account_listener)
    Row(Modifier.fillMaxWidth().padding(16.dp).testTag("account_identity"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (name != null && !user.email.isNullOrBlank()) {
                Text(user.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(resolveProviderLine(user.providerId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The privacy policy and how account deletion works, as quiet links under the page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountFooterLinks(onOpenAccountDeletionInfo: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
        TextButton({ runCatching { uriHandler.openUri(BOXLORE_PRIVACY_POLICY_URL) } }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.account_privacy_policy)) }
        TextButton(onOpenAccountDeletionInfo, Modifier.heightIn(min = 48.dp).testTag("account_deletion_info")) { Text(stringResource(R.string.account_how_deletion_works)) }
    }
}

/** How the account signs in, as a line under the listener's name. */
internal fun resolveProviderLine(providerId: String?): String = when (providerId) {
    PROVIDER_GOOGLE -> "Signed in with Google"
    PROVIDER_EMAIL_LINK -> "Signed in with an email link"
    else -> "Signed in with a password"
}

/** When the library last synced, for the status panel. */
internal fun formatRelativeSyncTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return "Not synced yet"
    val minutes = (now - timestamp).coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Synced just now"
        minutes < 60 -> "Synced $minutes min ago"
        hours < 24 -> "Synced $hours hr ago"
        days == 1L -> "Synced yesterday"
        else -> "Synced $days days ago"
    }
}

internal const val PROVIDER_GOOGLE = "google.com"
internal const val PROVIDER_EMAIL_LINK = "emailLink"
private val AccountPanelEchoSize = 104.dp
