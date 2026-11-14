package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** What the dialog asks, for a download on this phone or one on a linked watch. */
internal object RemoveDownloadCopy {
    fun title(fromWatch: Boolean): String = if (fromWatch) "Remove from watch?" else "Remove download?"

    fun body(episodeTitle: String?, fromWatch: Boolean): String {
        val named = !episodeTitle.isNullOrBlank()
        return when {
            fromWatch && named -> "Remove \"$episodeTitle\" from your watch? You can send it again anytime."
            fromWatch -> "Remove this episode from your watch? You can send it again anytime."
            named -> "Remove downloaded episode \"$episodeTitle\"? You'll need an internet connection to listen to it again."
            else -> "Remove this downloaded episode? You'll need an internet connection to listen to it again."
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
    AlertDialog(
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
                text = RemoveDownloadCopy.title(fromWatch),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.bold,
            )
        },
        text = {
            Text(
                text = RemoveDownloadCopy.body(episodeTitle, fromWatch),
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
                    text = "Remove",
                    fontWeight = GoogleSansWeight.bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    )
}
