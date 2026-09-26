package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.feature.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val PROVIDER_GOOGLE = "google.com"
private const val PROVIDER_EMAIL_LINK = "emailLink"
private const val ACCOUNT_DELETION_URL = "https://aswin.cx/boxlore/account-deletion/"

@Composable
private fun AccountStateEffects(
    currentUser: BoxLoreUser?,
    authState: AccountAuthState,
    context: Context,
    scrollState: ScrollState,
) {
    var previousUser by remember { mutableStateOf<BoxLoreUser?>(null) }
    LaunchedEffect(currentUser) {
        val user = currentUser
        val prev = previousUser
        previousUser = user
        if (prev != null && user == null) {
            authState.resetToNewEmail()
        } else if (user?.isEmailVerified == true) {
            authState.isAwaitingVerification = false
            AccountVerificationStorage.setPref(context, false)
        }
    }

    LaunchedEffect(authState.isAwaitingVerification, authState.magicLinkSent) {
        if (authState.isAwaitingVerification || authState.magicLinkSent) {
            scrollState.animateScrollTo(0)
        }
    }
}

private fun openAccountDeletionInfo(context: Context, uriHandler: UriHandler) {
    val opened = runCatching {
        uriHandler.openUri(ACCOUNT_DELETION_URL)
        true
    }.getOrDefault(false)
    if (!opened) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ACCOUNT_DELETION_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
internal fun AccountSettingsPage(
    authRepository: AuthRepository?,
    onBack: () -> Unit,
    syncStatus: CloudSyncUiStatus = CloudSyncUiStatus.Idle,
    onSyncNow: () -> Unit = {},
    isOnboarding: Boolean = false,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val onOpenAccountDeletionInfo = { openAccountDeletionInfo(context, uriHandler) }
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

    AccountStateEffects(
        currentUser = currentUser,
        authState = authState,
        context = context,
        scrollState = scrollState,
    )

    SettingsScaffold(
        title = "Account",
        onBack = onBack,
        scrollState = scrollState,
    ) {
        val user = currentUser
        val isAwaiting = isAwaitingVerificationState(authState, user, context)

        if (user != null && !isAwaiting) {
            SignedInContent(
                user = user,
                syncStatus = syncStatus,
                actions = SignedInActions(
                    onResetPassword = {
                        sendResetPasswordEmail(scope, authRepository, user.email, context)
                    },
                    onSignOut = {
                        handleSignOut(authRepository, authState, context)
                    },
                    onDeleteAccountClick = { showDeleteConfirmation = true },
                    onOpenAccountDeletionInfo = onOpenAccountDeletionInfo,
                    onSyncNow = onSyncNow,
                    onContinueToHome = onBack,
                ),
                isOnboarding = isOnboarding,
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
            handleSignOut(authRepository, authState, context)
        },
        onOpenAccountDeletionInfo = onOpenAccountDeletionInfo,
    )
}

private fun isAwaitingVerificationState(
    authState: AccountAuthState,
    user: BoxLoreUser?,
    context: Context,
): Boolean = authState.isAwaitingVerification ||
    (user != null && !user.isEmailVerified && AccountVerificationStorage.getPref(context))

private fun sendResetPasswordEmail(
    scope: CoroutineScope,
    authRepository: AuthRepository?,
    email: String?,
    context: Context,
) {
    if (email.isNullOrBlank()) return
    scope.launch {
        val res = authRepository?.sendPasswordReset(email)
        val message = if (res?.isSuccess == true) {
            "Password reset email sent to $email"
        } else {
            res?.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email"
        }
        val duration = if (res?.isSuccess == true) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        Toast.makeText(context, message, duration).show()
    }
}

private fun handleSignOut(
    authRepository: AuthRepository?,
    authState: AccountAuthState,
    context: Context,
) {
    authRepository?.signOut()
    authState.resetToNewEmail()
    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
}

private data class SignedInActions(
    val onResetPassword: () -> Unit,
    val onSignOut: () -> Unit,
    val onDeleteAccountClick: () -> Unit,
    val onOpenAccountDeletionInfo: () -> Unit,
    val onSyncNow: () -> Unit,
    val onContinueToHome: () -> Unit = {},
)

@Composable
private fun ColumnScope.SignedInContent(
    user: BoxLoreUser,
    syncStatus: CloudSyncUiStatus,
    actions: SignedInActions,
    isOnboarding: Boolean = false,
) {
    UserProfileCard(user = user, syncStatus = syncStatus)
    if (isOnboarding) {
        OnboardingContinueHero(onContinue = actions.onContinueToHome)
    }
    CloudSyncInfoGroup(
        syncStatus = syncStatus,
        onSyncNow = actions.onSyncNow,
    )
    AccountManagementGroup(
        user = user,
        onResetPassword = actions.onResetPassword,
        onSignOut = actions.onSignOut,
        onDeleteAccountClick = actions.onDeleteAccountClick,
        onOpenAccountDeletionInfo = actions.onOpenAccountDeletionInfo,
    )
}

@Composable
private fun OnboardingContinueHero(
    onContinue: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Account connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "You're all set to start listening.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Continue to boxlore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

internal fun resolveProviderLabel(providerId: String?): String = when (providerId) {
    PROVIDER_GOOGLE -> "Google"
    PROVIDER_EMAIL_LINK -> "Email Link"
    else -> "Password"
}

@Composable
private fun ProviderIcon(providerId: String?) {
    when (providerId) {
        PROVIDER_GOOGLE -> Image(
            painter = painterResource(cx.aswin.boxlore.core.designsystem.R.drawable.ic_google_logo),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
        )
        PROVIDER_EMAIL_LINK -> Icon(
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

@Composable
private fun AccountManagementGroup(
    user: BoxLoreUser,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onOpenAccountDeletionInfo: () -> Unit,
) {
    val isPasswordAccount = user.providerId != PROVIDER_GOOGLE && !user.email.isNullOrBlank()
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
        SettingsDivider()
        SettingsActionRow(
            title = "Account & Data Deletion Info",
            supportingText = "Learn how your data is handled after deletion or request partial data removal",
            icon = Icons.Rounded.Info,
            onClick = onOpenAccountDeletionInfo,
        )
    }
}
