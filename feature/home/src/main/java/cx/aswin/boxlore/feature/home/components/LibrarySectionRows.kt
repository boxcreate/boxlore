package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isLatestEpisodeNew

@Composable
internal fun SelectorCover(
    podcast: Podcast,
    lastSeenId: String?,
    isSelected: Boolean,
    isAnyPodcastSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(60.dp),
) {
    val latestEpisodeId = podcast.latestEpisode?.id
    val latestEpisodePubDate = podcast.latestEpisode?.publishedDate ?: 0L

    val hasRecentNew =
        remember(podcast.subscribedAt, latestEpisodeId, latestEpisodePubDate, lastSeenId) {
            podcast.isLatestEpisodeNew(lastSeenId)
        }

    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 0.95f, label = "scale")
    val alpha by animateFloatAsState(
        targetValue =
            if (isSelected) {
                1f
            } else if (isAnyPodcastSelected) {
                0.6f
            } else {
                1f
            },
        label = "alpha",
    )
    val cornerRadius by animateDpAsState(targetValue = if (isSelected) 16.dp else 12.dp, label = "cornerRadius")
    val borderStrokeWidth by animateDpAsState(targetValue = if (isSelected) 3.dp else 0.dp, label = "borderStrokeWidth")

    Box(
        modifier =
            modifier
                .scale(scale),
    ) {
        // Inner Box to apply clipping and clickable to the cover image container
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .expressiveClickable(
                        shape = RoundedCornerShape(cornerRadius),
                        onClick = onClick,
                    ).clip(RoundedCornerShape(cornerRadius)),
        ) {
            OptimizedImage(
                url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
                proxyWidth = 120,
                contentDescription = podcast.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alpha),
            )

            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .border(borderStrokeWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius)),
                )
            }
        }

        // New episode "NEW" text chip indicator (overlapping the top right corner) with a slow shimmer effect
        if (hasRecentNew && !isSelected) {
            NewEpisodeBadge(
                modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
            )
        }
    }
}
