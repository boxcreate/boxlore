package cx.aswin.boxlore.wear.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.cloud.SignInProblem
import cx.aswin.boxlore.wear.cloud.WatchAccountState

/** The account as Settings shows it, with what its buttons do. */
internal class AccountSettings(
    val state: WatchAccountState,
    val problem: SignInProblem?,
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit,
)

/** The account in one line: the signed-in [name] when the phone gave one, else the words of [label]. */
internal data class AccountSummary(val name: String?, val label: Int)

/** What the account rows say. */
internal object AccountCopy {
    /** What Settings' first screen says under Account. */
    fun summary(state: WatchAccountState): AccountSummary =
        when (state) {
            is WatchAccountState.SignedIn -> AccountSummary(state.account?.takeIf { it.isNotBlank() }, R.string.wear_account_signed_in)
            WatchAccountState.SigningIn -> AccountSummary(null, R.string.wear_account_signing_in)
            WatchAccountState.SignedOut -> AccountSummary(null, R.string.wear_account_signed_out)
        }

    /** The message under Sign in after a try that did not work. */
    fun problem(problem: SignInProblem): Int =
        when (problem) {
            SignInProblem.PhoneAway -> R.string.wear_account_phone_away
            SignInProblem.PhoneSignedOut -> R.string.wear_account_phone_signed_out
            SignInProblem.Failed -> R.string.wear_account_failed
        }
}

/** The account page's rows: Sign in with the phone, or who is signed in and Sign out. */
internal fun TransformingLazyColumnScope.accountItems(account: AccountSettings, spec: TransformationSpec) {
    when (val state = account.state) {
        is WatchAccountState.SignedIn -> {
            item(key = "account-name") {
                FilledTonalButton(
                    // Nothing to open: the row says who is signed in.
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(state.account ?: stringResource(R.string.wear_account_signed_in), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(stringResource(R.string.wear_account_syncing)) },
                )
            }
            item(key = "account-sign-out") {
                FilledTonalButton(
                    onClick = account.onSignOut,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.wear_account_sign_out)) },
                    secondaryLabel = { Text(stringResource(R.string.wear_account_sign_out_detail)) },
                )
            }
        }
        else -> {
            val signingIn = state == WatchAccountState.SigningIn
            item(key = "account-sign-in") {
                Button(
                    onClick = account.onSignIn,
                    enabled = !signingIn,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(if (signingIn) R.string.wear_account_signing_in else R.string.wear_account_sign_in)) },
                    secondaryLabel = { Text(stringResource(R.string.wear_account_sign_in_detail)) },
                )
            }
            account.problem?.let { problem ->
                item(key = "account-problem") {
                    Text(
                        text = stringResource(AccountCopy.problem(problem)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).transformedHeight(this, spec),
                    )
                }
            }
        }
    }
}
