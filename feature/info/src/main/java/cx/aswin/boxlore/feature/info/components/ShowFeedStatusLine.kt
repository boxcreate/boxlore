package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.ShowFeedStatus

internal const val SHOW_FEED_STATUS_TAG = "podcast_info_feed_status"

/** The text of [status]'s line, or null when the page shows no line. */
internal fun showFeedStatusText(status: ShowFeedStatus): Int? = when (status) {
    ShowFeedStatus.NONE, ShowFeedStatus.CACHED -> null
    ShowFeedStatus.READING_NEWEST -> R.string.podcast_info_feed_reading
    ShowFeedStatus.COMPLETING_ARCHIVE -> R.string.podcast_info_feed_completing
    ShowFeedStatus.FALLBACK -> R.string.podcast_info_feed_fallback
    ShowFeedStatus.OFFLINE -> R.string.podcast_info_feed_offline
    ShowFeedStatus.OFFLINE_NO_COPY -> R.string.podcast_info_feed_offline_empty
}

/**
 * The feed-first show page's status line above its episodes: a feed read in progress, the
 * server's list in place of an unreadable feed, or no connection. A solid tonal surface, announced
 * politely to screen readers when it changes. Nothing for a current list or a recent stored copy.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 */
@Composable
internal fun ShowFeedStatusLine(status: ShowFeedStatus, modifier: Modifier = Modifier) {
    val text = showFeedStatusText(status) ?: return
    val reading = status == ShowFeedStatus.READING_NEWEST || status == ShowFeedStatus.COMPLETING_ARCHIVE
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier =
        modifier
            .fillMaxWidth()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .padding(horizontal = 16.dp)
            .testTag(SHOW_FEED_STATUS_TAG)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (reading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (status == ShowFeedStatus.OFFLINE || status == ShowFeedStatus.OFFLINE_NO_COPY) Icons.Rounded.CloudOff else Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(text = stringResource(text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
