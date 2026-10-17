package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.auth.EmailLinkSignInStatus
import cx.aswin.boxlore.feature.settings.R

/** An emailed sign-in link's progress on the Account page, with what to do when it fails. */
internal data class AccountEmailLinkUi(
    val status: EmailLinkSignInStatus = EmailLinkSignInStatus.Idle,
    val onRetry: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** While the link is checked. Once signed in, the page switches to the sync panel for the first sync. */
@Composable
internal fun ColumnScope.AccountSigningInContent() {
    Text(
        stringResource(R.string.account_link_signing_in),
        Modifier.padding(top = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
    )
    Text(
        stringResource(R.string.account_link_signing_in_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("account_signing_in"))
    AccountEcho(syncing = true, modifier = Modifier.align(Alignment.CenterHorizontally).size(AccountLinkEchoSize))
}

/**
 * The link didn't sign the listener in. A used or expired link offers a new one to the same address;
 * anything else offers to try the same link again. Other ways to sign in goes back to the sign-in cards.
 */
@Composable
internal fun ColumnScope.AccountLinkFailedContent(
    failure: EmailLinkSignInStatus.Failed,
    state: AccountAuthState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A new link went out: the page moves on to Check your email.
    LaunchedEffect(state.magicLinkSent) {
        if (state.magicLinkSent) onDismiss()
    }

    Text(
        stringResource(if (failure.expired) R.string.account_link_expired else R.string.account_link_failed),
        Modifier.padding(top = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
    )
    Text(
        stringResource(if (failure.expired) R.string.account_link_expired_body else R.string.account_link_failed_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(Modifier.fillMaxWidth().testTag("account_link_failed"), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (failure.expired) {
                AccountPrimaryButton(
                    label = stringResource(R.string.account_link_send_new),
                    onClick = {
                        state.chooseEmailMethod(password = false)
                        state.email = failure.email
                        state.submitEmailLink()
                    },
                    modifier = Modifier.testTag("account_link_send_new"),
                    loading = state.isEmailLoading,
                    enabled = !state.isAnyLoading,
                )
            } else {
                AccountPrimaryButton(
                    label = stringResource(R.string.account_link_try_again),
                    onClick = onRetry,
                    modifier = Modifier.testTag("account_link_try_again"),
                )
            }
            TextButton(
                onClick = {
                    onDismiss()
                    state.showMethods()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("account_link_other_ways"),
            ) {
                Text(stringResource(R.string.account_other_ways))
            }
        }
    }
    state.errorMessage?.let { AuthErrorBanner(message = it) }
    AccountEcho(syncing = false, modifier = Modifier.align(Alignment.CenterHorizontally).size(AccountLinkEchoSize))
}

private val AccountLinkEchoSize = 152.dp
