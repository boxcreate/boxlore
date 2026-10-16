package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cx.aswin.boxlore.feature.settings.R

/** What the email in the listener's inbox does: sign them in, or confirm a new password account. */
internal enum class AccountEmailCheck { Link, Confirm }

/**
 * After an email goes out: one card with the address (and a way to change it), Open email app, and
 * resending. A sign-in link signs in as soon as it is opened on this phone. Confirming a new password
 * account is checked again whenever the listener comes back to boxlore, and on I've confirmed. A
 * single line says to look in spam; Echo waits on its stage below.
 */
@Composable
internal fun ColumnScope.AccountCheckEmailContent(kind: AccountEmailCheck, state: AccountAuthState) {
    val haptics = LocalHapticFeedback.current
    val email = state.email.ifBlank { state.authRepository?.currentUser?.value?.email.orEmpty() }
    if (kind == AccountEmailCheck.Confirm) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            state.checkVerificationStatus(silentOnFailure = true, onSuccess = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) })
        }
    }

    Text(
        stringResource(if (kind == AccountEmailCheck.Link) R.string.account_check_title else R.string.account_confirm_title),
        Modifier.padding(top = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
    )
    Text(
        stringResource(if (kind == AccountEmailCheck.Link) R.string.account_check_body else R.string.account_confirm_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(Modifier.fillMaxWidth().testTag("account_check_email"), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MarkEmailUnread, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.account_sent_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(email, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(
                    onClick = if (kind == AccountEmailCheck.Link) state::resetToNewEmail else state::signOutAndResetToSignUp,
                    modifier = Modifier.testTag("account_change_email"),
                ) {
                    Icon(Icons.Rounded.Edit, stringResource(R.string.account_change_email))
                }
            }
            val gmail = email.trim().endsWith("@gmail.com", ignoreCase = true) || email.trim().endsWith("@googlemail.com", ignoreCase = true)
            AccountPrimaryButton(
                label = stringResource(if (gmail) R.string.account_open_gmail else R.string.account_open_email),
                onClick = { openGmailOrEmailApp(state.context, email) },
                modifier = Modifier.testTag("account_open_email"),
            )
            if (kind == AccountEmailCheck.Link) {
                AccountTonalButton(stringResource(R.string.account_resend_link), state::submitEmailLink, Modifier.fillMaxWidth().testTag("account_resend"), enabled = !state.isAnyLoading)
            } else {
                AccountTonalButton(
                    label = stringResource(if (state.isCheckingVerification) R.string.account_checking else R.string.account_confirmed),
                    onClick = { state.checkVerificationStatus(silentOnFailure = false, onSuccess = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }) },
                    modifier = Modifier.fillMaxWidth().testTag("account_confirmed"),
                    enabled = !state.isCheckingVerification,
                )
                val cooling = state.resendCooldownSeconds > 0
                TextButton(
                    onClick = state::resendVerificationEmail,
                    enabled = !cooling && !state.isEmailLoading,
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("account_resend"),
                ) {
                    Text(if (cooling) stringResource(R.string.account_resend_in, state.resendCooldownSeconds) else stringResource(R.string.account_resend_email))
                }
            }
        }
    }
    state.errorMessage?.let { AuthErrorBanner(message = it) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.account_spam_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AccountEcho(syncing = false, modifier = Modifier.align(Alignment.CenterHorizontally).size(AccountCheckEchoSize))
}

private val AccountCheckEchoSize = 152.dp
