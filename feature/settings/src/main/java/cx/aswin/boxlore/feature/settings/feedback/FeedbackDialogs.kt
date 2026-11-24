package cx.aswin.boxlore.feature.settings.feedback

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.boxloreShowsOwnCopyConfirmation
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R

@Composable
fun LogsPreviewDialog(
    logs: String?,
    isLoading: Boolean,
    diagnosticInfo: DiagnosticInfo?,
    onShare: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TrackScreenView("feedback_logs_preview")
    BoxloreAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.feedback_logs_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.semiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.feedback_logs_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LogsTextSurface(logs = logs, isLoading = isLoading)
                LogsActionButtons(
                    logs = logs,
                    diagnosticInfo = diagnosticInfo,
                    isLoading = isLoading,
                    onShare = onShare,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.feedback_close))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun LogsTextSurface(
    logs: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 280.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else {
            val verticalScroll = rememberScrollState()
            val horizontalScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll),
            ) {
                Text(
                    text = logs ?: stringResource(R.string.feedback_logs_none),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LogsActionButtons(
    logs: String?,
    diagnosticInfo: DiagnosticInfo?,
    isLoading: Boolean,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val isActionEnabled = !isLoading

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                val report = diagnosticInfo?.toMarkdownReport(logs) ?: (logs ?: "")
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.feedback_logs_clip_label), report))
                if (boxloreShowsOwnCopyConfirmation()) Toast.makeText(context, R.string.feedback_logs_copied, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f),
            enabled = isActionEnabled,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.feedback_logs_copy),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FilledTonalButton(
            onClick = onShare,
            modifier = Modifier.weight(1f),
            enabled = isActionEnabled,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.feedback_logs_share),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
