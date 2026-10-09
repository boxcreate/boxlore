package cx.aswin.boxlore.feature.info.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.info.R

@Composable
internal fun MissingEpisodesConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.RssFeed, contentDescription = null) },
        title = { Text(stringResource(R.string.podcast_info_missing_title)) },
        text = {
            Text(
                stringResource(R.string.podcast_info_missing_message),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(stringResource(R.string.podcast_info_missing_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.podcast_info_missing_cancel))
            }
        },
    )
}
