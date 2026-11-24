package cx.aswin.boxlore.feature.info.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogConfirmButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogDismissButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIcon
import cx.aswin.boxlore.feature.info.R

@Composable
internal fun MissingEpisodesConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    TrackScreenView("podcast_missing_episodes_confirmation")
    BoxloreAlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { BoxloreDialogIcon(Icons.Rounded.RssFeed) },
        title = { Text(stringResource(R.string.podcast_info_missing_title)) },
        text = {
            Text(
                stringResource(R.string.podcast_info_missing_message),
            )
        },
        confirmButton = {
            BoxloreDialogConfirmButton(
                stringResource(R.string.podcast_info_missing_allow),
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            )
        },
        dismissButton = { BoxloreDialogDismissButton(stringResource(R.string.podcast_info_missing_cancel), onDismissRequest) },
    )
}
