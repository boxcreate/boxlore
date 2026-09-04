package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * Square artwork + title-only foot used by Home “Based on Your Taste” and Explore For You.
 */
@Composable
fun CuratedEpisodeCard(
    podcast: Podcast,
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSubtitle: Boolean = true,
) {
    val isNew =
        episode.publishedDate > 0L &&
            (System.currentTimeMillis() / 1000L - episode.publishedDate) < 2 * 24 * 60 * 60L

    FeedMediaCard(
        imageUrl = (episode.imageUrl ?: "").ifEmpty { podcast.imageUrl },
        title = episode.title,
        subtitle = if (showSubtitle) podcast.title else null,
        onClick = onClick,
        modifier = modifier,
        titleMaxLines = 3,
        imageChrome = {
            if (isNew) {
                Box(
                    modifier =
                    Modifier
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopStart),
                ) {
                    Text(
                        text = "NEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp),
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        },
    )
}
