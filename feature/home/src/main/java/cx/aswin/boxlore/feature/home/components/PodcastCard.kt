package cx.aswin.boxlore.feature.home.components

import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast

/**
 * Layout density for discovery poster cards.
 * Title-only posters reserve a fixed foot ([Rail]: 2 lines, [Grid]: 3) and vertically
 * center shorter titles so rows stay equal height.
 */
enum class FeedMediaCardDensity {
    Rail,
    Grid,
}

@Composable
fun PodcastCard(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showGenreChip: Boolean = false,
    showSubtitle: Boolean = true,
    density: FeedMediaCardDensity = FeedMediaCardDensity.Grid,
) {
    FeedMediaCard(
        imageUrl = podcast.imageUrl,
        title = podcast.title.replace("+", " "),
        subtitle = if (showSubtitle) podcast.artist.replace("+", " ") else null,
        onClick = onClick,
        modifier = modifier,
        titleMaxLines =
            when {
                showSubtitle -> 2
                density == FeedMediaCardDensity.Rail -> 2
                else -> 3
            },
        imageBadge = {
            if (showGenreChip && podcast.genre.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart),
                ) {
                    Text(
                        text = podcast.genre.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = GoogleSansWeight.bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        },
        imageOverlay = {
            if (podcast.medium == "video" || podcast.latestEpisode?.enclosureType?.startsWith("video/") == true) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd),
                ) {
                    Box(
                        modifier = Modifier.padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
@Suppress("LongParameterList")
fun FeedMediaCard(
    imageUrl: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 2,
    imageBadge: @Composable (BoxScope.() -> Unit)? = null,
    imageOverlay: @Composable (BoxScope.() -> Unit)? = null,
) {
    val lines = titleMaxLines.coerceAtLeast(1)
    val showSubtitle = !subtitle.isNullOrBlank()

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.expressiveClickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
            ) {
                OptimizedImage(
                    url = imageUrl,
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                )

                if (imageBadge != null) {
                    imageBadge()
                }

                if (imageOverlay != null) {
                    imageOverlay()
                }
            }

            if (!showSubtitle) {
                // Equal-height posters: reserve [lines] title lines; vertically center shorter titles.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(HomeFeedSpacing.railTextFootHeight(lines) + HomeFeedSpacing.CardTextPadding * 2)
                            .padding(HomeFeedSpacing.CardTextPadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        maxLines = lines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(HomeFeedSpacing.CardTextPadding),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        maxLines = lines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
