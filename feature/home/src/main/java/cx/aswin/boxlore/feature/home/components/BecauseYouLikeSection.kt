package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardDensity
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.drawOutline
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R
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
    Column(modifier = modifier.fillMaxWidth()) {
        BecauseYouLikeSeed(
            podcast = podcast,
            onPodcastClick = onPodcastClick,
            onChangePodcastClick = onChangePodcastClick,
        )
        DiscoveryExpressiveTheme {
            Column {
                // Suggested shows retain their existing filtering and stable keys.
                val distinctSuggestedPodcasts = remember(suggestedPodcasts.list) {
                    HomeBecauseYouLikeLogic.filterRailPodcasts(suggestedPodcasts.list)
                }
                if (distinctSuggestedPodcasts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(HomeFeedSpacing.RelatedRailGap))
                    Column(verticalArrangement = Arrangement.spacedBy(HomeFeedSpacing.HeaderContentGap)) {
                        HomeChildSectionHeader(
                            title = stringResource(R.string.home_similar_shows),
                            icon = RecommendationHeaderIcons.SimilarShows,
                        )

                        HomeDiscoveryRail(compact = true) { cardWidth ->
                            items(
                                distinctSuggestedPodcasts,
                                key = { LazyListKeyPolicy.safeKey(it.id, prefix = "byl_show") }
                            ) { suggestedPodcast ->
                                PodcastCard(
                                    podcast = suggestedPodcast,
                                    onClick = { onPodcastClick(suggestedPodcast) },
                                    showSubtitle = false,
                                    density = FeedMediaCardDensity.Rail,
                                    modifier = Modifier.width(cardWidth),
                                    presentation = FeedMediaCardPresentation.ExpressivePoster,
                                )
                            }
                        }
                    }
                }

                // Recommended episodes use the same poster presentation.
                val distinctRecommendations = remember(recommendations.list) {
                    HomeBecauseYouLikeLogic.filterRailEpisodes(recommendations.list)
                }
                if (distinctRecommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(HomeFeedSpacing.RelatedRailGap))
                    Column(verticalArrangement = Arrangement.spacedBy(HomeFeedSpacing.HeaderContentGap)) {
                        HomeChildSectionHeader(
                            title = stringResource(R.string.home_recommended_episodes),
                            icon = RecommendationHeaderIcons.EpisodesToTry,
                        )

                        HomeDiscoveryRail(compact = true) { cardWidth ->
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
                                    modifier = Modifier.width(cardWidth),
                                    presentation = FeedMediaCardPresentation.ExpressivePoster,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BecauseYouLikeSeed(
    podcast: Podcast,
    onPodcastClick: (Podcast) -> Unit,
    onChangePodcastClick: () -> Unit,
) {
    // Editorial seed stamp: the tilted cover and heart badge give this rail a distinct identity.
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .expressiveClickable(onClick = { onPodcastClick(podcast) }),
    ) {
        Box {
            BecauseYouLikeSeedDecorations()

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
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                        text = stringResource(R.string.home_because_you_like),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                        contentDescription = stringResource(R.string.home_change_recommendation_show),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.BecauseYouLikeSeedDecorations() {
    val upperColor = MaterialTheme.colorScheme.tertiary
    val lowerColor = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.extraLarge).drawWithCache {
            val upperSize = 120.dp.toPx()
            val lowerSize = 104.dp.toPx()
            val upperOutline = ExpressiveShapes.SoftBurst.createOutline(Size(upperSize, upperSize), layoutDirection, this)
            val lowerOutline = ExpressiveShapes.Cookie9.createOutline(Size(lowerSize, lowerSize), layoutDirection, this)
            // Stable edge peeks keep the artwork and its original mood badge unobstructed.
            val lowerLeft = maxOf(104.dp.toPx(), size.width * 0.52f - lowerSize / 2f)
            onDrawBehind {
                scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
                    translate(left = size.width - 88.dp.toPx(), top = -72.dp.toPx()) {
                        rotate(11f, pivot = Offset(upperSize / 2f, upperSize / 2f)) {
                            drawOutline(upperOutline, upperColor, alpha = 0.10f)
                        }
                    }
                    translate(left = lowerLeft, top = size.height - 34.dp.toPx()) {
                        rotate(-16f, pivot = Offset(lowerSize / 2f, lowerSize / 2f)) {
                            drawOutline(lowerOutline, lowerColor, alpha = 0.12f)
                        }
                    }
                }
            }
        },
    )
}
