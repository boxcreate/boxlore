package cx.aswin.boxlore.ui

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.launch

/**
 * The show name and author a Lore episode is queued with when it carries no show of its own. These
 * are the playback layer's stored sentinels ("Unknown Podcast" is recognised by QueueShowPolicy and
 * Home), so they stay English and are never translated here.
 */
internal data class LoreShowFallback(val title: String, val author: String)

@Composable
internal fun rememberLoreShowFallback(): LoreShowFallback = LORE_SHOW_FALLBACK

private val LORE_SHOW_FALLBACK = LoreShowFallback("Unknown Podcast", "Unknown") // i18n-ignore: stored sentinel

/** The show a Lore card's episode joins the queue under, built from what the episode carries. */
internal fun loreQueuePodcast(episode: Episode, fallback: LoreShowFallback): Podcast = Podcast(
    id = episode.podcastId ?: "unknown",
    title = episode.podcastTitle ?: fallback.title,
    artist = episode.podcastTitle ?: fallback.author,
    imageUrl = episode.podcastImageUrl ?: episode.imageUrl ?: "",
)

/** Conflict dialog when starting a Lore queue over an existing normal queue. */
@Composable
fun LoreQueueConflictDialog(pendingLoreEpisode: Episode, onDismiss: () -> Unit, onConfirmStart: suspend (Episode) -> Unit,) {
    TrackScreenView("lore_queue_conflict")
    val scope = rememberCoroutineScope()
    BoxloreAlertDialog(
        onDismissRequest = {
            AnalyticsHelper.trackLoreQueueConflictResult(pendingLoreEpisode.id, "cancelled")
            onDismiss()
        },
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.lore_queue_conflict_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.lore_queue_conflict_message),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    AnalyticsHelper.trackLoreQueueConflictResult(
                        pendingLoreEpisode.id,
                        "start_lore_queue",
                    )
                    scope.launch {
                        try {
                            onConfirmStart(pendingLoreEpisode)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to start Lore queue", e)
                        }
                    }
                },
                shape = CircleShape,
            ) {
                Text(stringResource(R.string.lore_queue_conflict_replace))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    AnalyticsHelper.trackLoreQueueConflictResult(pendingLoreEpisode.id, "cancelled")
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(stringResource(R.string.lore_queue_conflict_keep))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
