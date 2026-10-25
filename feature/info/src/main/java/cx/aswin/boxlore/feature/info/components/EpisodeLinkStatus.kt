package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.feature.info.R

/** Test tags of [EpisodeLinkStatus]. */
internal object EpisodeLinkStatusTags {
    const val READING_FEED = "episode_info_reading_feed"
    const val NOT_FOUND = "episode_info_not_found"
}

/**
 * The episode page while it reads a show's feed for a shared episode, and when nothing lists the
 * episode. Centred in the window's safe area at a readable width, so it reads the same on phones,
 * tablets, split windows and either half of a folded screen; the page's state lives in its
 * ViewModel, so window changes never restart the read.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
 */
@Composable
internal fun EpisodeLinkStatus(
    readingFeed: Boolean,
    podcastId: String,
    onViewShow: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MAX_CONTENT_WIDTH.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag(if (readingFeed) EpisodeLinkStatusTags.READING_FEED else EpisodeLinkStatusTags.NOT_FOUND)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (readingFeed) BoxLoreLoader.Expressive(size = 64.dp)
            Text(
                text = stringResource(if (readingFeed) R.string.episode_info_reading_feed_title else R.string.episode_info_not_found_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    when {
                        readingFeed -> R.string.episode_info_reading_feed_body
                        podcastId.isNotBlank() -> R.string.episode_info_not_found_body
                        // Without a show the link may name an ID only the sharer's phone holds.
                        else -> R.string.episode_info_not_found_body_no_show
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!readingFeed) {
                if (podcastId.isNotBlank()) {
                    Button(onClick = { onViewShow(podcastId) }) { Text(stringResource(R.string.episode_info_view_show)) }
                }
                TextButton(onClick = onBack) { Text(stringResource(R.string.episode_info_back)) }
            }
        }
    }
}

private const val MAX_CONTENT_WIDTH = 480
