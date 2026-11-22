package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.text.UiText

/** What the dialog asks, for a download on this phone or one on a linked watch. */
internal object RemoveDownloadCopy {
    fun title(fromWatch: Boolean): UiText = UiText.res(if (fromWatch) R.string.remove_download_title_watch else R.string.remove_download_title)

    fun body(episodeTitle: String?, fromWatch: Boolean): UiText {
        val named = !episodeTitle.isNullOrBlank()
        return when {
            fromWatch && named -> UiText.res(R.string.remove_download_body_watch_named, episodeTitle.orEmpty())
            fromWatch -> UiText.res(R.string.remove_download_body_watch)
            named -> UiText.res(R.string.remove_download_body_named, episodeTitle.orEmpty())
            else -> UiText.res(R.string.remove_download_body)
        }
    }
}

/**
 * Material 3 confirmation dialog displayed before removing a downloaded episode: from this phone,
 * or with [fromWatch] from a linked watch.
 */
@Composable
fun RemoveDownloadConfirmationDialog(
    episodeTitle: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fromWatch: Boolean = false,
) {
    BoxloreAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = RemoveDownloadCopy.title(fromWatch).asString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.bold,
            )
        },
        text = {
            Text(
                text = RemoveDownloadCopy.body(episodeTitle, fromWatch).asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(
                    text = stringResource(R.string.remove_download_confirm),
                    fontWeight = GoogleSansWeight.bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.remove_download_cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    )
}
