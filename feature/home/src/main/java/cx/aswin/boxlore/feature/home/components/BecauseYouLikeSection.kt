package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardDensity
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.logic.HomeBecauseYouLikeLogic

@Composable
fun BecauseYouLikeSection(
    podcast: Podcast,
    recommendations: StableEpisodeList,
    suggestedPodcasts: StablePodcastList,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    onPlayEpisode: (Episode, Podcast) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
    onChangePodcastClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Editorial seed stamp: the tilted cover and heart badge give this rail a distinct identity.
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .expressiveClickable(onClick = { onPodcastClick(podcast) }),
        ) {
            Box {
                Box(
                    modifier =
                    Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.extraLarge),
                ) {
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 20.dp, y = (-34).dp)
                            .size(104.dp)
                            .rotate(10f)
                            .clip(ExpressiveShapes.SoftBurst)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)),
                    )
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 78.dp, y = 38.dp)
                            .size(84.dp)
                            .rotate(-12f)
                            .clip(ExpressiveShapes.Cookie6)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)),
                    )
                }

                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(modifier = Modifier.size(64.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            shadowElevation = 3.dp,
                            modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                                .rotate(-3f),
                        ) {
                            OptimizedImage(
                                url = podcast.imageUrl,
                                proxyWidth = 120,
                                contentDescription = null,
                                modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.large),
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 2.dp,
                            modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = cx.aswin.boxlore.core.designsystem.R.drawable.mood_heart_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BECAUSE YOU LIKE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = GoogleSansWeight.bold,
                            letterSpacing = 0.7.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = podcast.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onChangePodcastClick,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Change seed podcast",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        // --- Subsection 1: Suggested Shows (OutlinedCard Grid Matching CuratedEpisodeCard) ---
        val distinctSuggestedPodcasts = remember(suggestedPodcasts.list) {
            HomeBecauseYouLikeLogic.filterRailPodcasts(suggestedPodcasts.list)
        }
        if (distinctSuggestedPodcasts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BecauseYouLikeSectionHeader(
                    title = "Similar Shows",
                    icon = Icons.Rounded.Subscriptions,
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(HomeFeedSpacing.RailItemGap),
                ) {
                    items(
                        distinctSuggestedPodcasts,
                        key = { LazyListKeyPolicy.safeKey(it.id, prefix = "byl_show") }
                    ) { suggestedPodcast ->
                        PodcastCard(
                            podcast = suggestedPodcast,
                            onClick = { onPodcastClick(suggestedPodcast) },
                            showSubtitle = false,
                            density = FeedMediaCardDensity.Rail,
                            modifier = Modifier.fillParentMaxWidth(HomeFeedSpacing.RAIL_CARD_WIDTH_FRACTION),
                        )
                    }
                }
            }
        }

        // --- Subsection 2: Recommended Episodes ---
        val distinctRecommendations = remember(recommendations.list) {
            HomeBecauseYouLikeLogic.filterRailEpisodes(recommendations.list)
        }
        if (distinctRecommendations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BecauseYouLikeSectionHeader(
                    title = "Recommended Episodes",
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(HomeFeedSpacing.RailItemGap),
                ) {
                    items(
                        distinctRecommendations,
                        key = { LazyListKeyPolicy.safeKey(it.id, prefix = "byl_ep") }
                    ) { episode ->
                        val parentPodcast =
                            Podcast(
                                id = episode.podcastId ?: "",
                                title = episode.podcastTitle ?: "Podcast",
                                artist = "",
                                imageUrl =
                                episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: episode.imageUrl?.takeIf { it.isNotBlank() }
                                    ?: "",
                                description = "",
                                genre = episode.podcastGenre ?: "Podcast",
                            )
                        CuratedEpisodeCard(
                            podcast = parentPodcast,
                            episode = episode,
                            onClick = { onEpisodeClick(episode, parentPodcast) },
                            showSubtitle = false,
                            modifier = Modifier.fillParentMaxWidth(HomeFeedSpacing.RAIL_CARD_WIDTH_FRACTION),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BecauseYouLikeSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
