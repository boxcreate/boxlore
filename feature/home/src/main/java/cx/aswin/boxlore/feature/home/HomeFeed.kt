package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeInsetsDp
import cx.aswin.boxlore.core.designsystem.layout.boxlorePosterColumns
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.components.HeroCarousel
import cx.aswin.boxlore.feature.home.components.HomeFeedSpacing
import cx.aswin.boxlore.feature.home.components.HomeLoadingHandoffMillis
import cx.aswin.boxlore.feature.home.components.HomeLoadingReveal
import cx.aswin.boxlore.feature.home.components.HomeRecommendationLayout
import cx.aswin.boxlore.feature.home.components.bleed
import cx.aswin.boxlore.feature.home.logic.shouldAnimateHomeShimmer
import cx.aswin.boxlore.feature.home.logic.showHomeDiscoveryContent
import kotlinx.coroutines.delay

@androidx.compose.runtime.Stable
internal data class PodcastFeedContent(
    val heroItems: StableHeroList,
    val latestItems: StablePodcastList,
    val subscribedItems: StablePodcastList,
    val editorialRows: StableEditorialRowList,
    val gridItems: StablePodcastList,
    val recommendations: StableEpisodeList,
    val selectedPodcastEpisodes: StableEpisodeList,
    val selectedShowStatus: SelectedShowStatus = SelectedShowStatus(),
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedUiState(
    val discoveryGreeting: DiscoveryGreeting,
    val selectedCategory: String?,
    val selectedPodcastId: String?,
    val briefing: Briefing?,
    val briefingChapters: List<cx.aswin.boxlore.core.model.Chapter>,
    val seemsToLikePodcast: Podcast?,
    val showImportBanner: Boolean,
    val pinnedPodcastIds: Set<String> = emptySet(),
    /** Off with Curated rows, which the greeting introduces. */
    val showDiscoveryGreeting: Boolean = true,
    /** False once Picked for you is off, which also removes Explore's For you tab. */
    val exploreHasForYou: Boolean = true,
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedRecommendationState(
    val becauseYouLikeRecommendations: StableEpisodeList,
    val becauseYouLikePodcasts: StablePodcastList,
    val isRecommendationsLoading: Boolean = true,
    val isRecommendationsFallback: Boolean = true,
    val onChangePodcastClick: () -> Unit = {},
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedLoadingState(
    val isEditorialRowsLoading: Boolean = false,
    val isFilterLoading: Boolean,
    val isSelectedPodcastLoading: Boolean = false,
    val isSelectedRssRefreshing: Boolean = false,
    val isLoading: Boolean,
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedPlayback(
    val player: HomePlaybackUi,
    val episodePlaybackState: StablePlaybackStateMap,
    val softExpireProgressEpisodeIds: Set<String> = emptySet(),
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedLayout(
    val gridState: LazyStaggeredGridState,
    val modifier: Modifier = Modifier,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PodcastFeed(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
    loadingState: PodcastFeedLoadingState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    layout: PodcastFeedLayout,
) {
    val context = LocalContext.current
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    val posterColumns = boxlorePosterColumns(
        LocalBoxloreWindowLayout.current,
        boxloreContentWidthDp(),
    )
    KeepGridPlaceAcrossColumnChanges(layout.gridState, posterColumns)
    val revealStage = rememberHomeRevealStage(viewportReady = !loadingState.isLoading)
    val derivedState = rememberPodcastFeedDerivedState(content, feedState, recommendationState, loadingState, revealStage, HomeFeedSpacing.exploreItemCount(posterColumns, tablet))
    // Scrolling changes the visible keys often; only a change in the answer recomposes the feed.
    val hasVisibleLoading by remember(
        layout.gridState,
        loadingState.isLoading,
        loadingState.isFilterLoading,
        loadingState.isEditorialRowsLoading,
        recommendationState.isRecommendationsLoading,
        loadingState.isSelectedPodcastLoading,
    ) {
        derivedStateOf {
            shouldAnimateHomeShimmer(
                visibleKeys = layout.gridState.layoutInfo.visibleItemsInfo.mapNotNullTo(HashSet()) { it.key as? String },
                initialLoading = loadingState.isLoading,
                discoveryLoading = loadingState.isFilterLoading,
                editorialLoading = loadingState.isEditorialRowsLoading,
                recommendationsLoading = recommendationState.isRecommendationsLoading,
                selectedShowLoading = loadingState.isSelectedPodcastLoading,
            )
        }
    }
    val shimmerActive by produceState(initialValue = hasVisibleLoading, hasVisibleLoading) {
        // Preserve the same sweep through the short loading-cover handoff.
        if (!hasVisibleLoading) delay(HomeLoadingHandoffMillis.toLong())
        value = hasVisibleLoading
    }
    val revealing = !loadingState.isLoading && revealStage < HomeRevealStage.COMPLETE
    // Marquee's hero runs edge to edge under the floating top bar; other styles get no top inset.
    val marqueeTopInset = if (cx.aswin.boxlore.core.prefs.HeroCardStyle.sanitize(cx.aswin.boxlore.feature.home.components.LocalHeroCardStyle.current) ==
        cx.aswin.boxlore.core.prefs.HeroCardStyle.MARQUEE
    ) {
        cx.aswin.boxlore.feature.home.components.LocalHeroTopInset.current
    } else {
        null
    }
    ShimmerScope(active = hasVisibleLoading || shimmerActive || revealing) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(posterColumns),
            state = layout.gridState,
            modifier = layout.modifier.fillMaxSize().testTag("home_feed_grid"),
            // The side gutter starts past the camera cutout and side system buttons of a sideways phone.
            contentPadding = boxloreHorizontalSafeContentPadding(PaddingValues(start = GridSidePadding, end = GridSidePadding, bottom = 160.dp)),
            horizontalArrangement = Arrangement.spacedBy(HomeFeedSpacing.GridGap),
            verticalItemSpacing = HomeFeedSpacing.GridGap,
        ) {
            smartHeroItem(content, playback, callbacks, derivedState, marqueeTopInset)
            if (tablet && feedState.briefing != null) {
                val showLibrary = shouldShowYourShows(content, feedState, loadingState)
                // Keyed like the section it leads with on phones, so the grid keeps its place across the tablet size.
                item(span = StaggeredGridItemSpan.FullLine, key = if (showLibrary) "your_shows" else "briefing", contentType = "tablet_leading_sections") {
                    TabletHomeLeadingSections(
                        showLibrary = showLibrary,
                        library = { YourShowsFeedSection(content, feedState, loadingState, playback, callbacks, derivedState) },
                        briefing = { DailyBriefingFeedCard(feedState, playback, callbacks, context) },
                    )
                }
            } else {
                yourShowsItem(content, feedState, loadingState, playback, callbacks, derivedState)
                dailyBriefingItem(feedState, playback, callbacks, context)
            }
            curatedForYouItems(content, feedState, recommendationState, playback, callbacks, derivedState, HomeRecommendationLayout(posterColumns, tablet))
            discoveryGreetingItem(feedState, callbacks, loadingState.isEditorialRowsLoading, content.editorialRows.list.size)
            editorialFeedItems(content, feedState, loadingState, callbacks)
            discoverFeedItems(feedState, derivedState, callbacks, posterColumns, tablet)
        }
    }
}

/**
 * A staggered grid starts again from the top when its column count changes, as it does when a phone
 * turns sideways (2 poster columns to 5) or a window is resized. Home puts the listener back on the
 * item and offset they were at, in both directions.
 */
@Composable
private fun KeepGridPlaceAcrossColumnChanges(state: LazyStaggeredGridState, columns: Int) {
    val place = remember(state) { GridPlace(columns) }
    LaunchedEffect(state, columns) {
        if (place.columns != columns) {
            place.columns = columns
            val index = place.index
            val offset = place.offset
            if (state.firstVisibleItemIndex != index || state.firstVisibleItemScrollOffset != offset) state.scrollToItem(index, offset)
        }
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            place.index = index
            place.offset = offset
        }
    }
}

/** The grid's last settled place and the column count it was measured with. Not state: it never redraws anything. */
private class GridPlace(var columns: Int) {
    var index = 0
    var offset = 0
}

internal data class PodcastFeedDerivedState(
    val viewportReady: Boolean,
    val heroLoaded: Boolean,
    val hasBecauseYouLike: Boolean,
    val hasRecommendations: Boolean,
    val discoverItems: List<Podcast>,
    val showDiscoverContent: Boolean,
    val discoverGenreChip: Boolean,
    val showDiscoverSkeleton: Boolean,
    val yourShowsReady: Boolean = viewportReady,
)

@Composable
private fun rememberPodcastFeedDerivedState(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
    loadingState: PodcastFeedLoadingState,
    revealStage: Int,
    exploreCount: Int = HomeFeedSpacing.ExploreGridCap,
): PodcastFeedDerivedState {
    val viewportReady = !loadingState.isLoading
    val discoverItems =
        remember(content.gridItems.list, feedState.selectedCategory, exploreCount) {
            content.gridItems.list.distinctBy { it.id }.take(exploreCount)
        }
    val discoveryRevealed = revealStage >= HomeRevealStage.DISCOVERY
    return PodcastFeedDerivedState(
        viewportReady = viewportReady,
        heroLoaded = viewportReady && content.heroItems.list.isNotEmpty() && revealStage >= HomeRevealStage.HERO,
        hasBecauseYouLike = hasBecauseYouLike(feedState, recommendationState),
        hasRecommendations = recommendationState.isRecommendationsLoading || content.recommendations.list.isNotEmpty(),
        discoverItems = discoverItems,
        showDiscoverContent = showHomeDiscoveryContent(loadingState.isLoading, discoverItems.isNotEmpty()) && discoveryRevealed,
        discoverGenreChip = false,
        showDiscoverSkeleton = loadingState.isLoading ||
            (loadingState.isFilterLoading && discoverItems.isEmpty()) ||
            (discoverItems.isNotEmpty() && !discoveryRevealed),
        yourShowsReady = viewportReady && revealStage >= HomeRevealStage.YOUR_SHOWS,
    )
}

private fun hasBecauseYouLike(
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
): Boolean = feedState.seemsToLikePodcast != null &&
    (
        recommendationState.becauseYouLikeRecommendations.list.isNotEmpty() ||
            recommendationState.becauseYouLikePodcasts.list.isNotEmpty()
        )

private fun LazyStaggeredGridScope.smartHeroItem(
    content: PodcastFeedContent,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    derivedState: PodcastFeedDerivedState,
    marqueeTopInset: androidx.compose.ui.unit.Dp?,
) {
    if (derivedState.viewportReady && content.heroItems.list.isEmpty()) {
        // Without a hero, the rest of Home still starts below Marquee's floating top bar.
        if (marqueeTopInset != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "hero_top_inset", contentType = "spacer") {
                androidx.compose.foundation.layout.Spacer(Modifier.height(marqueeTopInset))
            }
        }
        return
    }
    item(span = StaggeredGridItemSpan.FullLine, key = "hero", contentType = "hero") {
        // Marquee's cover runs past the gutter and the side insets to both screen edges; its words keep clear of them.
        val (safeStart, safeEnd) = boxloreHorizontalSafeInsetsDp()
        PinnedGridItemContent(if (marqueeTopInset != null) Modifier.bleed(GridSidePadding + safeStart, GridSidePadding + safeEnd) else Modifier) {
            // The carousel draws its position dots in the 12dp strip under the cards; the skeleton
            // keeps the same strip, so the gap to Your shows never changes.
            HomeLoadingReveal(
                ready = derivedState.heroLoaded,
                placeholder = {
                    // Marquee's hero meets Your shows' sheet directly, so its skeleton keeps no strip below.
                    cx.aswin.boxlore.feature.home.components.HeroSkeleton(
                        Modifier.padding(bottom = if (marqueeTopInset != null) 0.dp else cx.aswin.boxlore.feature.home.components.HeroIndicatorStrip),
                    )
                },
            ) {
                SmartHeroCarousel(content.heroItems, playback.player, callbacks)
            }
        }
    }
}

@Composable
internal fun PinnedGridItemContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pinnable = androidx.compose.ui.layout.LocalPinnableContainer.current
    androidx.compose.runtime.DisposableEffect(pinnable) {
        val handle = pinnable?.pin()
        onDispose { handle?.release() }
    }
    androidx.compose.foundation.layout.Box(modifier) { content() }
}

/** The feed's side padding, which Marquee's hero and the side rails run past to reach the screen edges. */
private val GridSidePadding = HomeFeedSpacing.GridSide

@Composable
private fun SmartHeroCarousel(
    heroItems: StableHeroList,
    playback: HomePlaybackUi,
    callbacks: HomeFeedCallbacks,
) {
    HeroCarousel(
        heroItems = heroItems,
        currentPlayingPodcastId = playback.currentPlayingPodcastId,
        currentPlayingEpisodeId = playback.currentPlayingEpisodeId,
        isPlaying = playback.isPlaying,
        onPlayClick = { podcast, bundle -> callbacks.onPlayClick?.invoke(podcast, bundle) },
        onDetailsClick = { podcast -> handleHeroDetailsClick(podcast, callbacks) },
        onArrowClick = callbacks.onHeroArrowClick,
        onToggleSubscription = callbacks.onToggleSubscription,
        onTogglePlayback = callbacks.onTogglePlayback,
        modifier = Modifier,
    )
}

private fun handleHeroDetailsClick(
    podcast: Podcast,
    callbacks: HomeFeedCallbacks,
) {
    val episode = podcast.latestEpisode
    if (episode != null) {
        callbacks.onEpisodeClick?.invoke(episode, podcast, "home_hero_card")
    } else {
        callbacks.onPodcastClick(podcast, "home_hero_card", null, null)
    }
}
