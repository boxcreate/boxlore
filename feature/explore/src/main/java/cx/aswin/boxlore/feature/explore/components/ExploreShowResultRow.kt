package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.explore.R

/**
 * One Find a show result as a list row: cover, the show's name (2 lines), who makes it and its
 * genre when known. [shape] places it in a connected list (see [exploreConnectedRowShape]).
 */
@Composable
internal fun ExploreShowResultRow(
    podcast: Podcast,
    showGenre: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    uniformHeight: Boolean = false,
) {
    ExploreShowListRow(podcast, showGenre, shape, onClick = onClick, modifier = modifier, uniformHeight = uniformHeight)
}

/**
 * The row Find a show results and tablet Top charts share; [leading] sits before the cover. With
 * [uniformHeight] every row keeps two title lines and the author and genre lines, so rows in
 * several columns stay the same height and read in order, left to right. Result rows are at least
 * 88dp tall, chart rows (with a rank) 80dp.
 */
@Composable
internal fun ExploreShowListRow(
    podcast: Podcast,
    showGenre: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    uniformHeight: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val genre = if (showGenre) exploreGenreTag(podcast.genre) else null
    val minHeight = if (leading == null) 88.dp else 80.dp
    val video = podcast.medium == "video" || podcast.latestEpisode?.enclosureType?.startsWith("video/") == true
    Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke(this)
            OptimizedImage(
                url = podcast.imageUrl,
                fallbackUrls = listOf(podcast.fallbackImageUrl, podcast.latestEpisode?.imageUrl),
                proxyWidth = 200,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = if (uniformHeight) 2 else 1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (uniformHeight || podcast.artist.isNotBlank()) {
                    Text(
                        podcast.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (genre != null || (uniformHeight && showGenre)) {
                    Text(
                        genre.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (video) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Videocam,
                        contentDescription = stringResource(R.string.explore_video),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** A connected list's shape: large outer corners where a column starts or ends, small ones between. */
internal fun exploreConnectedRowShape(starts: Boolean, ends: Boolean): Shape {
    val top = if (starts) ConnectedOuterCorner else ConnectedInnerCorner
    val bottom = if (ends) ConnectedOuterCorner else ConnectedInnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val ConnectedOuterCorner = 24.dp
private val ConnectedInnerCorner = 6.dp
