package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * Podcast card matching HomeScreen's PodcastCard exactly
 */
@Composable
fun ExplorePodcastCard(
    podcast: Podcast,
    cardHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showGenreChip: Boolean = false
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.expressiveClickable(onClick = onClick)
    ) {
        Column {
            // Image Container with optional Genre Chip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                )
                
                // Genre Chip (only shown when showGenreChip is true)
                if (showGenreChip && podcast.genre.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = podcast.genre.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Video Badge overlay on image
                if (podcast.medium == "video" || podcast.latestEpisode?.enclosureType?.startsWith("video/") == true) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Box(
                            modifier = Modifier.padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Text content below image
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExploreVibeCard(
    vibe: Pair<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine dynamic colors based on string hash for variety
    val hash = vibe.first.hashCode()
    val containerColor = when (hash % 3) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (hash % 3) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = vibe.second,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CuratedVibeHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Curated: $title",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = rememberSectionHeaderFontFamily()
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ExploreVibeChip(
    vibe: Pair<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hash = vibe.first.hashCode()
    val containerColor = when (hash % 3) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (hash % 3) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = modifier
            .height(44.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = vibe.second,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExploreEpisodeHeroCard(
    episode: Episode,
    isFallback: Boolean = true,
    labelText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .expressiveClickable(shape = RoundedCornerShape(20.dp), onClick = onClick)
    ) {
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() },
            proxyWidth = 600,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.3f to Color.Black.copy(alpha = 0.15f),
                            0.6f to Color.Black.copy(alpha = 0.65f),
                            1.0f to Color.Black
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .padding(14.dp)
                .align(Alignment.TopStart)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = labelText ?: (if (isFallback) "POPULAR IN YOUR REGION" else "FEATURED RECOMMENDATION"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = episode.podcastTitle ?: "",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (episode.duration > 0) {
                    val minutes = episode.duration / 60
                    Text(
                        text = "${minutes} min read/listen",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                
                val genre = episode.podcastGenre
                if (!genre.isNullOrBlank()) {
                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ExploreEpisodeBentoCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.expressiveClickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
            ) {
                OptimizedImage(
                    url = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() },
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                )

                if (episode.duration > 0) {
                    Surface(
                          shape = MaterialTheme.shapes.small,
                          color = Color.Black.copy(alpha = 0.6f),
                          modifier = Modifier
                              .align(Alignment.BottomEnd)
                              .padding(6.dp)
                    ) {
                        Text(
                            text = "${episode.duration / 60}m",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.podcastTitle ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
