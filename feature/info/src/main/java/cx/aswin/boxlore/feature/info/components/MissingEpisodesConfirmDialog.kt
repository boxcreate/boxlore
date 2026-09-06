package cx.aswin.boxlore.feature.info.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

@Composable
internal fun MissingEpisodesConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Get every episode") },
        text = {
            Text(
                buildAnnotatedString {
                    append(
                        "If you prefer to stay unsubscribed but still want the latest " +
                            "episodes, let boxlore use this show’s publisher feed to fill " +
                            "any gaps. You only need to allow this once—future visits " +
                            "refresh automatically.\n\n",
                    )
                    append("Full feeds can be large, so boxlore asks first.\n\n")
                    withStyle(SpanStyle(fontWeight = GoogleSansWeight.bold)) {
                        append(
                            "Subscribe and boxlore will automatically keep the latest " +
                                "episodes up to date.",
                        )
                    }
                },
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text("Auto-fetch from publisher")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text("Not now")
            }
        },
    )
}
