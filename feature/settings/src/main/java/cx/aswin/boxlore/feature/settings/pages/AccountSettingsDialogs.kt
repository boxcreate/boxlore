package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import cx.aswin.boxlore.feature.settings.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal sealed interface AccountDeletionOutcome {
    data object Success : AccountDeletionOutcome
    data object ReauthRequired : AccountDeletionOutcome
    data class Failure(val message: UiText) : AccountDeletionOutcome
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
                Toast.makeText(context, R.string.account_deleted_toast, Toast.LENGTH_SHORT).show()
            }
            AccountDeletionOutcome.ReauthRequired -> {
                onReauthRequired()
            }
            is AccountDeletionOutcome.Failure -> {
                Toast.makeText(context, outcome.message.resolve(context), Toast.LENGTH_LONG).show()
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
    onOpenAccountDeletionInfo: () -> Unit = {},
) {
    if (showDeleteConfirmation) {
        DeleteAccountConfirmationDialog(
            onDismiss = onDismissDeleteConfirmation,
            onConfirmDelete = onConfirmDelete,
            onOpenAccountDeletionInfo = onOpenAccountDeletionInfo,
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
    onOpenAccountDeletionInfo: () -> Unit = {},
) {
    TrackScreenView("delete_account_confirmation")
    BoxloreAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_delete_dialog_title), fontWeight = GoogleSansWeight.bold) },
        text = {
            Column(Modifier.tabletSheetScroll()) {
                Text(
                    stringResource(R.string.account_delete_dialog_body),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.account_delete_dialog_learn_more),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = GoogleSansWeight.medium,
                    modifier = Modifier.clickable(onClick = onOpenAccountDeletionInfo),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.account_delete_dialog_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun ReauthRequiredDialog(
    onDismiss: () -> Unit,
    onSignOutToReauth: () -> Unit,
) {
    TrackScreenView("account_reauthentication_required")
    BoxloreAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_reauth_dialog_title), fontWeight = GoogleSansWeight.bold) },
        text = {
            Text(
                stringResource(R.string.account_reauth_dialog_body),
            )
        },
        confirmButton = {
            Button(onClick = onSignOutToReauth) {
                Text(stringResource(R.string.account_reauth_dialog_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
