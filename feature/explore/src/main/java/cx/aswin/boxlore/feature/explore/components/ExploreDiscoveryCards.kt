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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.genreLabelRes
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCard
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardSkeleton
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodcastGenres
import cx.aswin.boxlore.feature.explore.R

/**
 * Browse, curated topics and show-search results share Home's card presentation. [showGenre] allows
 * the genre tag, which appears only for a real genre, in the listener's language. [rank] is the
 * show's place in Top charts, shown on the cover.
 */
@Composable
internal fun ExploreBrowsePodcastCard(
    podcast: Podcast,
    featured: Boolean,
    showGenre: Boolean,
    rank: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (featured && rank != null) {
        // The featured card draws no cover chrome, so its number sits over the artwork's top corner,
        // never over the title; the card itself says the number, so the badge is silent.
        val description = exploreRankDescription(rank)
        Box(Modifier.fillMaxWidth()) {
            ExploreBrowsePodcastCard(
                podcast,
                featured = true,
                showGenre = showGenre,
                rank = null,
                onClick = onClick,
                modifier = Modifier.semantics { contentDescription = description },
            )
            ExploreRankBadge(rank = rank, announce = false, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        return
    }
    val genre = if (showGenre) exploreGenreTag(podcast.genre) else null
    FeedMediaCard(
        imageUrl = podcast.imageUrl,
        fallbackImageUrls = listOf(podcast.fallbackImageUrl, podcast.latestEpisode?.imageUrl),
        title = podcast.title,
        subtitle = null,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        presentation = if (featured) FeedMediaCardPresentation.ExpressiveFeatured else FeedMediaCardPresentation.ExpressivePoster,
        imageChrome = {
            if (rank != null) {
                ExploreRankBadge(rank = rank, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            if (genre != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    // With a rank on top, the tag moves to the bottom corner.
                    modifier = Modifier.align(if (rank != null) Alignment.BottomStart else Alignment.TopStart).padding(8.dp),
                ) {
                    Text(
                        genre,
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

/**
 * The tag for a show's genre: its translated name, the feed's own text for a genre boxlore has no
 * name for, or null for none or the "Podcast" placeholder a show with no category gets.
 */
@Composable
internal fun exploreGenreTag(raw: String?): String? {
    if (raw.isNullOrBlank() || PodcastGenres.isPlaceholder(raw)) return null
    return genreLabelRes(raw)?.let { stringResource(it) } ?: raw.trim()
}
