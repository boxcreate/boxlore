package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
        val fallbackOpened = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ACCOUNT_DELETION_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!fallbackOpened) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Account Deletion URL", ACCOUNT_DELETION_URL))
            android.widget.Toast.makeText(context, "Link copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
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

    val user = currentUser
    val isAwaiting = isAwaitingVerificationState(authState, user, context)
    val mode = accountPageMode(user != null && !isAwaiting, isOnboarding, isAwaiting, authState.magicLinkSent && !authState.usePasswordAuth)
    AccountPageScaffold(
        onBack = onBack,
        isOnboarding = isOnboarding,
        scroll = scrollState,
        showNotNow = isOnboarding && !mode.signedIn,
        bottomBar = if (mode == AccountPageMode.SignedInOnboarding) {
            { AccountContinueButton(onBack) }
        } else {
            null
        },
    ) {
        when (mode) {
            AccountPageMode.SignedInOnboarding -> user?.let { AccountSignedInOnboarding(it, syncStatus, onSyncNow) }
            AccountPageMode.SignedInSettings -> user?.let {
                AccountSignedInSettings(
                    user = it,
                    syncStatus = syncStatus,
                    actions = SignedInActions(
                        onResetPassword = { sendResetPasswordEmail(scope, authRepository, it.email, context) },
                        onSignOut = { handleSignOut(authRepository, authState, context) },
                        onDeleteAccountClick = { showDeleteConfirmation = true },
                        onOpenAccountDeletionInfo = onOpenAccountDeletionInfo,
                        onSyncNow = onSyncNow,
                    ),
                )
            }
            AccountPageMode.ConfirmEmail -> AccountCheckEmailContent(AccountEmailCheck.Confirm, authState)
            AccountPageMode.CheckLinkEmail -> AccountCheckEmailContent(AccountEmailCheck.Link, authState)
            AccountPageMode.SignedOut -> SignedOutContent(authState)
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

/** What the Account page shows. Signed in, onboarding confirms with Continue; Settings manages the account. */
internal enum class AccountPageMode(val signedIn: Boolean) {
    SignedInOnboarding(true),
    SignedInSettings(true),
    ConfirmEmail(false),
    CheckLinkEmail(false),
    SignedOut(false),
}

/** A new password account waiting for its confirmation counts as signed out until confirmed; a sent sign-in link waits for the inbox. */
internal fun accountPageMode(signedIn: Boolean, onboarding: Boolean, awaitingVerification: Boolean, linkSent: Boolean): AccountPageMode = when {
    signedIn && onboarding -> AccountPageMode.SignedInOnboarding
    signedIn -> AccountPageMode.SignedInSettings
    awaitingVerification -> AccountPageMode.ConfirmEmail
    linkSent -> AccountPageMode.CheckLinkEmail
    else -> AccountPageMode.SignedOut
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

internal data class SignedInActions(
    val onResetPassword: () -> Unit,
    val onSignOut: () -> Unit,
    val onDeleteAccountClick: () -> Unit,
    val onOpenAccountDeletionInfo: () -> Unit,
    val onSyncNow: () -> Unit,
)
