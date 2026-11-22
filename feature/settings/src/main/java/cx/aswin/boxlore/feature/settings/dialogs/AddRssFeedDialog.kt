package cx.aswin.boxlore.feature.settings.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.feature.settings.R

/** Stable Compose [testTag] ids for Settings RSS dialog instrumentation / Maestro. */
object SettingsRssTestTags {
    const val URL_FIELD = "settings_add_rss_url"
    const val CONFIRM = "settings_add_rss_confirm"
    const val CANCEL = "settings_add_rss_cancel"
}

@Composable
internal fun AddRssFeedDialog(
    url: String,
    error: String?,
    isAdding: Boolean,
    onUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView("add_rss_feed")
    BoxloreAlertDialog(
        onDismissRequest = {
            if (!isAdding) {
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.RssFeed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(stringResource(R.string.settings_rss_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().tabletSheetScroll(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_rss_feed_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(SettingsRssTestTags.URL_FIELD),
                    enabled = !isAdding,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.settings_rss_feed_url)) },
                    placeholder = { Text("https://example.com/feed.xml") },
                    isError = error != null,
                    trailingIcon =
                    if (url.isNotEmpty() && !isAdding) {
                        {
                            IconButton(onClick = { onUrlChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = stringResource(R.string.settings_rss_clear),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    supportingText =
                    error?.let { message ->
                        {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                AnimatedVisibility(visible = isAdding) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.settings_rss_subscribing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isAdding && url.isNotBlank(),
                modifier = Modifier.testTag(SettingsRssTestTags.CONFIRM),
            ) {
                Text(stringResource(R.string.settings_rss_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isAdding,
                modifier = Modifier.testTag(SettingsRssTestTags.CANCEL),
            ) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
internal fun RssMatchConfirmationDialog(
    rssTitle: String,
    podcastIndexTitle: String,
    isLinking: Boolean,
    onUseRssSource: () -> Unit,
    onKeepSeparate: () -> Unit,
) {
    TrackScreenView("rss_match_confirmation")
    BoxloreAlertDialog(
        onDismissRequest = {
            if (!isLinking) onKeepSeparate()
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.RssFeed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.settings_rss_match_title)) },
        text = {
            Column(Modifier.tabletSheetScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text =
                    stringResource(R.string.settings_rss_match_body, rssTitle, podcastIndexTitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                    stringResource(R.string.settings_rss_match_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(visible = isLinking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUseRssSource,
                enabled = !isLinking,
            ) {
                Text(stringResource(R.string.settings_rss_match_use_rss))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onKeepSeparate,
                enabled = !isLinking,
            ) {
                Text(stringResource(R.string.settings_rss_match_keep_separate))
            }
        },
    )
}
