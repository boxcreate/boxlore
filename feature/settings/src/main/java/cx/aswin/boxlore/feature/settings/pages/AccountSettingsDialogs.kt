package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

internal fun handleDeleteAccount(
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
internal fun AccountSettingsDialogs(
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
