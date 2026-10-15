package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardDensity
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.drawOutline
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
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

/**
 * Geometry for the seed card. The round cover runs [CoverOverflow] past the end edge and is
 * vertically centred, so the card's shape clips it at the top, end and bottom. The swap button sits
 * on the cover's lower start edge, and the text keeps clear of both.
 */
internal object BecauseYouLikeSeedLayout {
    val MinHeight = 112.dp
    val CoverSize = 148.dp
    val CoverOverflow = 32.dp
    val CoverVisibleWidth = CoverSize - CoverOverflow
    val SwapSize = 40.dp
    val SwapRing = 3.dp

    /** End padding of the 48dp swap target; its centre lands on the cover's edge, 20° below centre. */
    val SwapEndPadding = 88.dp
    val SwapDrop = 25.dp
    val TextStartPadding = 20.dp
    val TextEndPadding = 140.dp
    val TextVerticalPadding = 16.dp

    /** Faint tertiary cookies on the text side; the lower, larger one is the one tests sample. */
    const val ShapeAlpha = 0.12f
    val LowerCookieSize = 128.dp
    val LowerCookieStart = (-52).dp
    val LowerCookieFromBottom = 72.dp
}

@Composable
private fun BecauseYouLikeSeed(
    podcast: Podcast,
    onPodcastClick: (Podcast) -> Unit,
    onChangePodcastClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
        Modifier
            .fillMaxWidth()
            .boxloreCardWidth(BoxloreCardFamily.Hero)
            .heightIn(min = BecauseYouLikeSeedLayout.MinHeight)
            .expressiveClickable(shape = shape, onClick = { onPodcastClick(podcast) }),
    ) {
        Box {
            BecauseYouLikeSeedShapes(modifier = Modifier.matchParentSize())
            BecauseYouLikeSeedCover(podcast = podcast, modifier = Modifier.matchParentSize())
            Column(
                modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(
                        start = BecauseYouLikeSeedLayout.TextStartPadding,
                        end = BecauseYouLikeSeedLayout.TextEndPadding,
                        top = BecauseYouLikeSeedLayout.TextVerticalPadding,
                        bottom = BecauseYouLikeSeedLayout.TextVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_because_you_like),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BecauseYouLikeSwapButton(
                onClick = onChangePodcastClick,
                modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = BecauseYouLikeSeedLayout.SwapEndPadding)
                    .offset(y = BecauseYouLikeSeedLayout.SwapDrop),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BecauseYouLikeSeedShapes(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.tertiary
    // Material's expressive shapes, the same family as the Your shows cookie selectors.
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()
    Box(
        modifier.drawWithCache {
            val lowerSize = BecauseYouLikeSeedLayout.LowerCookieSize.toPx()
            val cookieSize = 72.dp.toPx()
            val lower = cookieShape.createOutline(Size(lowerSize, lowerSize), layoutDirection, this)
            val cookie = cookieShape.createOutline(Size(cookieSize, cookieSize), layoutDirection, this)
            val alpha = BecauseYouLikeSeedLayout.ShapeAlpha
            onDrawBehind {
                // Fixed placements on the text side, mirrored in RTL so they never move on recomposition.
                scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
                    val lowerTop = size.height - BecauseYouLikeSeedLayout.LowerCookieFromBottom.toPx()
                    drawSeedShape(lower, lowerSize, color, alpha, Offset(BecauseYouLikeSeedLayout.LowerCookieStart.toPx(), lowerTop), 14f)
                    drawSeedShape(cookie, cookieSize, color, alpha, Offset(size.width * 0.40f, -30.dp.toPx()), -12f)
                }
            }
        },
    )
}

private fun DrawScope.drawSeedShape(outline: Outline, side: Float, color: Color, alpha: Float, at: Offset, degrees: Float) {
    translate(left = at.x, top = at.y) {
        rotate(degrees, pivot = Offset(side / 2f, side / 2f)) {
            drawOutline(outline, color, alpha = alpha)
        }
    }
}

@Composable
private fun BecauseYouLikeSeedCover(podcast: Podcast, modifier: Modifier) {
    Box(modifier) {
        // requiredSize lets the cover outgrow the card; it stays centred and the card clips it.
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 400,
            contentDescription = null,
            modifier =
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = BecauseYouLikeSeedLayout.CoverOverflow)
                .requiredSize(BecauseYouLikeSeedLayout.CoverSize)
                .clip(CircleShape),
        )
    }
}

@Composable
private fun BecauseYouLikeSwapButton(onClick: () -> Unit, modifier: Modifier) {
    val ring = MaterialTheme.colorScheme.tertiaryContainer
    FilledIconButton(
        onClick = onClick,
        colors =
        IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
        // A ring in the card colour cuts the button out of the cover edge it sits on.
        modifier =
        modifier.drawBehind {
            drawCircle(ring, radius = (BecauseYouLikeSeedLayout.SwapSize / 2 + BecauseYouLikeSeedLayout.SwapRing).toPx())
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.SwapHoriz,
            contentDescription = stringResource(R.string.home_change_recommendation_show),
            modifier = Modifier.size(20.dp),
        )
    }
}
