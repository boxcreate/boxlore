package cx.aswin.boxlore.feature.info.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.EpisodeInfoUiState

@Composable
internal fun EpisodeInfoMoreLikeThisCard(
    state: EpisodeInfoUiState.Success,
    onEpisodeClick: (Episode) -> Unit,
) {
    androidx.compose.material3.OutlinedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        colors =
            androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "More Like This",
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.1).sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val similarListState = rememberLazyListState()
            LazyRow(
                state = similarListState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                if (state.similarEpisodesLoading) {
                    items(4) {
                        val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

                        Column(
                            modifier = Modifier.width(120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(120.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(baseColor)
                                        .m3Shimmer(baseColor, highlightColor),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(baseColor)
                                        .m3Shimmer(baseColor, highlightColor),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(14.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(baseColor)
                                        .m3Shimmer(baseColor, highlightColor),
                            )
                        }
                    }
                } else {
                    items(state.similarEpisodes) { episode ->
                        androidx.compose.material3.OutlinedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                androidx.compose.material3.CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier =
                                Modifier
                                    .width(140.dp)
                                    .expressiveClickable {
                                        onEpisodeClick(episode)
                                    },
                        ) {
                            Column {
                                OptimizedImage(
                                    url = episode.imageUrl?.ifEmpty { episode.podcastImageUrl },
                                    proxyWidth = 300,
                                    contentDescription = episode.title,
                                    modifier =
                                        Modifier
                                            .size(140.dp)
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = episode.title,
                                        style =
                                            MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 14.sp,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        minLines = 2,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val podTitle = episode.podcastTitle
                                    if (!podTitle.isNullOrEmpty()) {
                                        Text(
                                            text = podTitle,
                                            style =
                                                MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EpisodeInfoMoreFromPodcastCard(
    state: EpisodeInfoUiState.Success,
    onPodcastClick: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onPodcastLinkClicked: () -> Unit,
    onRelatedEpisodesScrolled: () -> Unit,
    onRelatedEpisodeClicked: () -> Unit,
) {
    androidx.compose.material3.OutlinedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        colors =
            androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .expressiveClickable {
                            onPodcastLinkClicked()
                            onPodcastClick(state.podcastId)
                        }.padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subscriptions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "More from ${state.podcastTitle}",
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.1).sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Go to podcast",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Horizontal episodes row
            val relatedListState = rememberLazyListState()
            LaunchedEffect(relatedListState.isScrollInProgress) {
                if (relatedListState.isScrollInProgress) {
                    onRelatedEpisodesScrolled()
                }
            }
            LazyRow(
                state = relatedListState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                if (state.relatedEpisodesLoading) {
                    // Skeleton loaders
                    items(4) {
                        val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

                        Column(
                            modifier = Modifier.width(120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Skeleton artwork with shimmer
                            Box(
                                modifier =
                                    Modifier
                                        .size(120.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(baseColor)
                                        .m3Shimmer(baseColor, highlightColor),
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Skeleton text with shimmer
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(baseColor)
                                        .m3Shimmer(baseColor, highlightColor),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(14.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(baseColor)
                                        .m3Shimmer(baseColor, highlightColor),
                            )
                        }
                    }
                } else if (state.relatedEpisodes.isNotEmpty()) {
                    items(state.relatedEpisodes) { episode ->
                        androidx.compose.material3.OutlinedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                androidx.compose.material3.CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier =
                                Modifier
                                    .width(140.dp)
                                    .expressiveClickable {
                                        onRelatedEpisodeClicked()
                                        onEpisodeClick(episode)
                                    },
                        ) {
                            Column {
                                // Episode Artwork
                                OptimizedImage(
                                    url = episode.imageUrl?.ifEmpty { state.episode.podcastImageUrl },
                                    proxyWidth = 300, // 140dp thumbnails
                                    contentDescription = episode.title,
                                    modifier =
                                        Modifier
                                            .size(140.dp)
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                    contentScale = ContentScale.Crop,
                                )

                                // Title in card footer - minLines for even sizing
                                Text(
                                    text = episode.title,
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 14.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    minLines = 3,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                } else {
                    // No episodes message
                    item {
                        Text(
                            text = "No other episodes available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
