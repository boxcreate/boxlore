package cx.aswin.boxlore.core.designsystem.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.PLAYLIST_NAME_MAX_LENGTH
import cx.aswin.boxlore.core.model.normalizePlaylistName
import cx.aswin.boxlore.core.model.text.UiText

/** Wording shared by every playlist dialog, so the show page and Library match: string resources and [UiText]. */
object PlaylistCopy {
    @StringRes val NEW_PLAYLIST = R.string.playlist_new

    @StringRes val RENAME_PLAYLIST = R.string.playlist_rename

    @StringRes val NAME_LABEL = R.string.playlist_name_label

    @StringRes val CREATE = R.string.playlist_create

    @StringRes val SAVE = R.string.playlist_save

    @StringRes val CANCEL = R.string.playlist_cancel

    @StringRes val DELETE = R.string.playlist_delete

    @StringRes val DELETE_TITLE = R.string.playlist_delete_title

    fun showCount(count: Int): UiText = if (count == 0) {
        UiText.res(R.string.playlist_no_shows)
    } else {
        UiText.plural(R.plurals.playlist_show_count, count, count)
    }

    fun deleteBody(name: String): UiText = UiText.res(R.string.playlist_delete_body, name)
}

/**
 * A playlist name dialog: New playlist or Rename playlist. Stateless: the
 * caller keeps [name] in saved state, so a draft survives resizing, folding and recreation.
 * [onConfirm] gets the normalised name and is offered only when it isn't blank.
 */
@Composable
@Suppress("LongParameterList")
fun PlaylistNameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val normalized = normalizePlaylistName(name)
    val confirm = { if (normalized.isNotEmpty()) onConfirm(normalized) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = GoogleSansWeight.bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { onNameChange(it.take(PLAYLIST_NAME_MAX_LENGTH)) },
                label = { Text(stringResource(PlaylistCopy.NAME_LABEL)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth().testTag(PLAYLIST_NAME_FIELD_TAG),
            )
        },
        confirmButton = {
            Button(onClick = confirm, enabled = normalized.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(PlaylistCopy.CANCEL)) }
        },
    )
}

/** Asks before deleting the playlist named [name]. */
@Composable
fun DeletePlaylistDialog(name: String, onConfirm: () -> Unit, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(PlaylistCopy.DELETE_TITLE), style = MaterialTheme.typography.headlineSmall, fontWeight = GoogleSansWeight.bold) },
        text = { Text(PlaylistCopy.deleteBody(name).asString(), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(stringResource(PlaylistCopy.DELETE)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(PlaylistCopy.CANCEL)) }
        },
    )
}

/** The name field in [PlaylistNameDialog], for tests. */
const val PLAYLIST_NAME_FIELD_TAG = "playlist_name_field"
