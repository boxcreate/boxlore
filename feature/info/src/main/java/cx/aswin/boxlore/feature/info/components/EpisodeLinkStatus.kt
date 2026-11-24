package cx.aswin.boxlore.feature.info.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * episode. Laid out by [InfoStatusMessage] at a readable width, so it reads the same on phones,
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
    val body = stringResource(
        when {
            readingFeed -> R.string.episode_info_reading_feed_body
            podcastId.isNotBlank() -> R.string.episode_info_not_found_body
            // Without a show the link may name an ID only the sharer's phone holds.
            else -> R.string.episode_info_not_found_body_no_show
        },
    )
    val viewShow = InfoStatusAction(stringResource(R.string.episode_info_view_show)) { onViewShow(podcastId) }
    val back = InfoStatusAction(stringResource(R.string.episode_info_back), onBack)
    InfoStatusMessage(
        title = stringResource(if (readingFeed) R.string.episode_info_reading_feed_title else R.string.episode_info_not_found_title),
        body = body,
        modifier = modifier,
        leading = if (readingFeed) ({ BoxLoreLoader.Expressive(size = 64.dp) }) else null,
        // Finding the episode offers nothing to tap; not found offers its show (when known) and Back.
        primary = viewShow.takeIf { !readingFeed && podcastId.isNotBlank() },
        secondary = back.takeIf { !readingFeed },
        tag = if (readingFeed) EpisodeLinkStatusTags.READING_FEED else EpisodeLinkStatusTags.NOT_FOUND,
    )
}
