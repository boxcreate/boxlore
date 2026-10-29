package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.ShowFeedStatus

internal const val SHOW_FEED_STATUS_TAG = "podcast_info_feed_status"
internal const val SHOW_LIST_STATUS_PROGRESS_TAG = "podcast_info_list_status_progress"

/** The text of [status]'s line, or null when the page shows no line. */
internal fun showFeedStatusText(status: ShowFeedStatus): Int? = when (status) {
    ShowFeedStatus.NONE, ShowFeedStatus.CACHED -> null
    ShowFeedStatus.READING_NEWEST -> R.string.podcast_info_feed_reading
    ShowFeedStatus.COMPLETING_ARCHIVE -> R.string.podcast_info_feed_completing
    ShowFeedStatus.FALLBACK -> R.string.podcast_info_feed_fallback
    ShowFeedStatus.OFFLINE -> R.string.podcast_info_feed_offline
    ShowFeedStatus.OFFLINE_NO_COPY -> R.string.podcast_info_feed_offline_empty
}

/** True while [status] is a feed read in progress, so its line carries the wavy progress. */
internal fun showFeedStatusBusy(status: ShowFeedStatus): Boolean = status == ShowFeedStatus.READING_NEWEST || status == ShowFeedStatus.COMPLETING_ARCHIVE

/**
 * The feed-first show page's status above its episodes: a feed read in progress, the server's
 * list in place of an unreadable feed, or no connection. Nothing for a current list or a recent
 * stored copy.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 */
@Composable
internal fun ShowFeedStatusLine(status: ShowFeedStatus, modifier: Modifier = Modifier) {
    val text = showFeedStatusText(status) ?: return
    val offline = status == ShowFeedStatus.OFFLINE || status == ShowFeedStatus.OFFLINE_NO_COPY
    ShowListStatusLine(
        text = stringResource(text),
        busy = showFeedStatusBusy(status),
        icon = if (offline) Icons.Rounded.CloudOff else Icons.Rounded.Info,
        modifier = modifier,
    )
}

/**
 * One status line at the head of the episode list: the text in the page's quieter colour and, while
 * something loads, Material's wavy progress under it. No card or pill: it reads as part of the list.
 * Announced politely to screen readers when it changes.
 */
@Composable
internal fun ShowListStatusLine(text: String, busy: Boolean, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .testTag(SHOW_FEED_STATUS_TAG)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!busy) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (busy) {
            BoxLoreLoader.LinearWavy(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag(SHOW_LIST_STATUS_PROGRESS_TAG))
        }
    }
}
