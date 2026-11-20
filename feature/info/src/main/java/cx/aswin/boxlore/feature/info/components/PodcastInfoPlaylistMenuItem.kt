package cx.aswin.boxlore.feature.info.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.info.R

/** Add to playlist in the show page menu (followed shows only). */
@Composable
internal fun AddToPlaylistMenuItem(onDismiss: () -> Unit, onAddToPlaylist: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.podcast_info_add_to_playlist)) },
        onClick = {
            onDismiss()
            onAddToPlaylist()
        },
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
    )
}
