package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.PLAYLIST_NAME_MAX_LENGTH
import cx.aswin.boxlore.core.model.normalizePlaylistName

/** Wording shared by every playlist dialog, so the show page, episode page, player and Library match. */
object PlaylistCopy {
    const val NEW_PLAYLIST = "New playlist"
    const val RENAME_PLAYLIST = "Rename playlist"
    const val SAVE_QUEUE = "Save queue as playlist"
    const val NAME_LABEL = "Name"
    const val CREATE = "Create"
    const val SAVE = "Save"
    const val CANCEL = "Cancel"
    const val DELETE = "Delete"
    const val DELETE_TITLE = "Delete playlist?"

    fun episodeCount(count: Int): String = when (count) {
        0 -> "No episodes yet"
        1 -> "1 episode"
        else -> "$count episodes"
    }

    fun deleteBody(name: String): String = "“$name” will be deleted. This can’t be undone. Your downloads and listening history stay as they are."

    /** The player's Save queue as playlist: what gets saved. */
    fun saveQueueBody(upNextCount: Int): String = when (upNextCount) {
        0 -> "Saves the episode playing now."
        1 -> "Saves the episode playing now and 1 episode up next."
        else -> "Saves the episode playing now and $upNextCount episodes up next."
    }
}

/**
 * A playlist name dialog: New playlist, Rename playlist or Save queue as playlist. Stateless: the
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
    supportingText: String? = null,
) {
    val normalized = normalizePlaylistName(name)
    val confirm = { if (normalized.isNotEmpty()) onConfirm(normalized) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = GoogleSansWeight.bold) },
        text = {
            Column {
                supportingText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { onNameChange(it.take(PLAYLIST_NAME_MAX_LENGTH)) },
                    label = { Text(PlaylistCopy.NAME_LABEL) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth().testTag(PLAYLIST_NAME_FIELD_TAG),
                )
            }
        },
        confirmButton = {
            Button(onClick = confirm, enabled = normalized.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(PlaylistCopy.CANCEL) }
        },
    )
}

/** Asks before deleting the playlist named [name]. */
@Composable
fun DeletePlaylistDialog(name: String, onConfirm: () -> Unit, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(PlaylistCopy.DELETE_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = GoogleSansWeight.bold) },
        text = { Text(PlaylistCopy.deleteBody(name), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(PlaylistCopy.DELETE) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(PlaylistCopy.CANCEL) }
        },
    )
}

/** The name field in [PlaylistNameDialog], for tests. */
const val PLAYLIST_NAME_FIELD_TAG = "playlist_name_field"
