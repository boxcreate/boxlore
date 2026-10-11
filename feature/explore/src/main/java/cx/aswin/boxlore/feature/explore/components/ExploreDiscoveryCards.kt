package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCard
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardSkeleton
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.explore.R

/** Browse, curated topics and show-search results share Home's card presentation. */
@Composable
internal fun ExploreBrowsePodcastCard(podcast: Podcast, featured: Boolean, showGenre: Boolean, onClick: () -> Unit) {
    FeedMediaCard(
        imageUrl = podcast.imageUrl,
        title = podcast.title,
        subtitle = null,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        presentation = if (featured) FeedMediaCardPresentation.ExpressiveFeatured else FeedMediaCardPresentation.ExpressivePoster,
        imageChrome = {
            if (showGenre && podcast.genre.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Text(
                        podcast.genre,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (podcast.medium == "video" || podcast.latestEpisode?.enclosureType?.startsWith("video/") == true) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.Videocam,
                        contentDescription = stringResource(R.string.explore_video),
                        modifier = Modifier.padding(6.dp).size(16.dp),
                    )
                }
            }
        },
    )
}

@Composable
internal fun ExploreBrowseCardSkeleton(featured: Boolean) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    FeedMediaCardSkeleton(
        modifier = Modifier.fillMaxWidth(),
        presentation = if (featured) FeedMediaCardPresentation.ExpressiveFeatured else FeedMediaCardPresentation.ExpressivePoster,
    ) { placeholder -> Box(placeholder.m3Shimmer(base, highlight)) }
}
