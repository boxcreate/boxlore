package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCard
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.explore.R

/** Search keeps the first episode featured and the remaining results in their original grid order. */
@Composable
internal fun ExploreSearchEpisodeCard(episode: Episode, featured: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FeedMediaCard(
        imageUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl.orEmpty(),
        title = episode.title,
        subtitle = null,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        presentation = if (featured) FeedMediaCardPresentation.ExpressiveFeatured else FeedMediaCardPresentation.ExpressivePoster,
        imageChrome = {
            if (episode.duration > 0) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.explore_duration_minutes, episode.duration / 60),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        },
    )
}

/** Standalone icons and solid-theme text follow the same hierarchy as Explore's feed. */
@Composable
internal fun ExploreSearchSectionHeader(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = GoogleSansWeight.semiBold,
            fontFamily = rememberSectionHeaderFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 10.dp).semantics { heading() },
        )
    }
}
