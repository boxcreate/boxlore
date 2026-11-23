package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.component.adaptivePlayerOverlayOffset
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.discoveryTabSwitcherHeight
import cx.aswin.boxlore.core.designsystem.components.regionDisplayLabel
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxlorePosterColumns
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.list.ProgressiveSearchScrollLogic
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.ExploreDefaultTab
import cx.aswin.boxlore.feature.explore.components.ExploreBrowseCardSkeleton
import cx.aswin.boxlore.feature.explore.components.ExploreBrowseChromeState
import cx.aswin.boxlore.feature.explore.components.ExploreBrowsePodcastCard
import cx.aswin.boxlore.feature.explore.components.ExploreEmptyState
import cx.aswin.boxlore.feature.explore.components.ExploreFloatingHeader
import cx.aswin.boxlore.feature.explore.components.ExploreHeaderRow
import cx.aswin.boxlore.feature.explore.components.ExploreHeaderSearchField
import cx.aswin.boxlore.feature.explore.components.ExploreHeaderSelectors
import cx.aswin.boxlore.feature.explore.components.ExploreRecommendationsEmptyState
import cx.aswin.boxlore.feature.explore.components.ExploreSectionHeader
import cx.aswin.boxlore.feature.explore.components.ExploreTabSelectorFab
import cx.aswin.boxlore.feature.explore.components.ExploreTopicToolbar
import cx.aswin.boxlore.feature.explore.components.ExploreTopicTransition
import cx.aswin.boxlore.feature.explore.components.exploreNextPageFooter
import cx.aswin.boxlore.feature.explore.components.exploreTopHeaderTitle
import cx.aswin.boxlore.feature.explore.components.exploreTopicResults
import cx.aswin.boxlore.feature.explore.components.rememberExploreTabPagerState
import cx.aswin.boxlore.feature.explore.logic.ExploreBrowseLogic
import cx.aswin.boxlore.feature.explore.logic.ExploreChromeScrollLogic
import cx.aswin.boxlore.feature.explore.logic.ExploreTabPagerLogic
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Main Explore Screen Entry Point
 */
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    entryPoint: String = "bottom_nav",
    onPodcastClick: (String, String, String?, Int?) -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    onNavigateToRegionSettings: () -> Unit = {},
    searchRequested: Boolean = false,
    onSearchRequestHandled: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeRegionCode by viewModel.activeRegionCode.collectAsStateWithLifecycle()
    val isPlayerVisible by remember(viewModel) {
        viewModel.playerState.map { it.currentEpisode != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = remember(viewModel) { viewModel.playerState.value.currentEpisode != null })

    TrackScreenView("explore") {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreScreenViewed(entryPoint)
    }

    ExploreScreenEffects(viewModel)

    ExploreContent(
        uiState = uiState,
        activeRegionCode = activeRegionCode,
        isPlayerVisible = isPlayerVisible,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchTriggered = viewModel::onSearchTriggered,
        onSearchTabSelected = viewModel::setSearchTab,
        onCategorySelected = viewModel::onCategorySelected,
        onPodcastClick = { id, entryPoint, filter, index ->
            viewModel.trackPodcastClicked(index ?: 0)
            onPodcastClick(id, entryPoint, filter, index)
        },
        onEpisodeClick = onEpisodeClick,
        onTabSelected = viewModel::onTabSelected,
        onVibeSelected = viewModel::onVibeSelected,
        onClearVibe = viewModel::clearVibe,
        onRegionClick = onNavigateToRegionSettings,
        onLoadMore = { viewModel.loadMoreTrending() },
        onRetry = viewModel::retry,
        searchRequested = searchRequested,
        onSearchRequestHandled = onSearchRequestHandled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreContent(
    uiState: ExploreUiState,
    activeRegionCode: String = "us",
    isPlayerVisible: Boolean = false,
    onSearchQueryChanged: (String) -> Unit,
    onSearchTriggered: (String) -> Unit = {},
    onSearchTabSelected: (SearchTab) -> Unit = {},
    onCategorySelected: (String) -> Unit,
    onPodcastClick: (String, String, String?, Int?) -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    onTabSelected: (Int) -> Unit,
    onVibeSelected: (String, String) -> Unit,
    onClearVibe: () -> Unit,
    onRegionClick: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: (ExploreRetry) -> Unit = {},
    searchRequested: Boolean = false,
    onSearchRequestHandled: () -> Unit = {},
) {
    // Handle error/loading states
    val state = when (uiState) {
        is ExploreUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                BoxLoreLoader.Expressive(size = 100.dp)
            }
            return
        }
        is ExploreUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.explore_error_message, uiState.message))
            }
            return
        }
        is ExploreUiState.Success -> uiState
    }
    if (state.selectedTab == 1 && state.recommendations.isNotEmpty()) {
        LaunchedEffect(state.recommendations) {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreRecommendationsImpression(
                recommendationsCount = state.recommendations.size,
                episodeIds = state.recommendations.map { it.id }
            )
        }
    }

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val leaveTopic: () -> Unit = {
        searchActive = false
        onSearchQueryChanged("")
        onClearVibe()
        focusManager.clearFocus()
    }
    // Set when the Explore tab is tapped again on this page; opens search like the topic Search button.
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            if (state.currentVibe != null) onClearVibe()
            searchActive = true
            focusSearch = true
            onSearchRequestHandled()
        }
    }
    ExploreTopicTransition(
        state = state,
        onBack = leaveTopic,
    ) { paneState, active, onTopicBack ->
        val state = paneState
        val rawDisplayList = if (state.isSearching) state.searchResults else state.trending
        val displayList = remember(rawDisplayList) {
            ExploreBrowseLogic.projectDisplayList(rawDisplayList)
        }

        val isSearchModeActive = searchActive || state.searchQuery.isNotEmpty() || state.currentVibe != null
        val browsePager = rememberExploreTabPagerState(state.selectedTab, active && !isSearchModeActive && state.hasForYou, onTabSelected)
        // Without Picked for you there is no For you tab, so Explore shows Top on its own.
        val visibleTab = if (state.hasForYou) browsePager.visibleTab else ExploreDefaultTab.INDEX_TOP
        val exploreView =
            when {
                state.currentVibe != null -> "explore_topic"
                isSearchModeActive && state.searchTab == SearchTab.EPISODES -> "explore_search_episodes"
                isSearchModeActive -> "explore_search_shows"
                visibleTab == ExploreDefaultTab.INDEX_TOP -> "explore_top"
                else -> "explore_for_you"
            }
        // The browse pane kept behind a topic isn't on show, so only the active pane counts.
        if (active) {
            TrackScreenView(exploreView, state.currentVibe) { previous ->
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackScreenViewed(exploreView, previous, state.currentVibe?.let { mapOf("topic" to it) }.orEmpty())
            }
        }
        val forYouGridState = rememberLazyStaggeredGridState()
        val topGridState = rememberLazyStaggeredGridState()
        val searchGridState = rememberLazyStaggeredGridState()
        val topicGridState = rememberLazyStaggeredGridState()
        val gridState = when {
            state.currentVibe != null -> topicGridState
            isSearchModeActive -> searchGridState
            visibleTab == 1 -> forYouGridState
            else -> topGridState
        }
        val chromeState = remember { ExploreBrowseChromeState() }
        var expandedHeaderHeight by remember { mutableStateOf(148.dp) }
        val nestedScrollConnection = remember(chromeState, isSearchModeActive) {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    chromeState.onScroll(consumed.y, enabled = !isSearchModeActive)
                    return Offset.Zero
                }
            }
        }
        LaunchedEffect(isSearchModeActive, state.selectedTab, state.currentCategory) {
            chromeState.expand()
        }
        LaunchedEffect(gridState, chromeState) {
            androidx.compose.runtime.snapshotFlow { !gridState.canScrollBackward }
                .distinctUntilChanged()
                .collect { atTop -> if (atTop) chromeState.expand() }
        }
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val density = LocalDensity.current
        val searchFocusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(state.currentVibe, focusSearch, active) {
            if (active && state.currentVibe == null && focusSearch) {
                searchFocusRequester.requestFocus()
                // An already focused field keeps a dismissed keyboard hidden.
                keyboard?.show()
                focusSearch = false
            }
        }
        LaunchedEffect(state.currentVibe) {
            if (state.currentVibe != null) topicGridState.scrollToItem(0)
        }

        val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomChromeHeight =
            appBottomChromeContentPadding(isMiniPlayerVisible = isPlayerVisible, systemNavigationInset = systemNavBarHeight)
        // Clearance above navbar/mini-player for the tab FAB.
        val tabFabBottomPadding = bottomChromeHeight + 16.dp
        // Extra FAB height so list content can scroll fully past the overlay.
        // In search the keyboard is up, so results scroll above it (its inset already counts the nav bar).
        val imeBottom = if (isSearchModeActive) WindowInsets.ime.asPaddingValues().calculateBottomPadding() else 0.dp
        val listBottomPadding = if (!isSearchModeActive) {
            tabFabBottomPadding + discoveryTabSwitcherHeight() + 16.dp
        } else {
            ExploreChromeScrollLogic.searchBottomPaddingDp(imeBottom.value, bottomChromeHeight.value).dp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .nestedScroll(nestedScrollConnection)
        ) {
            // Infinite scroll trigger using snapshotFlow to avoid rapid-fire recomposition loops
            androidx.compose.runtime.LaunchedEffect(gridState, active) {
                if (!active) return@LaunchedEffect
                androidx.compose.runtime.snapshotFlow {
                    val layoutInfo = gridState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    totalItems > 0 && lastVisible >= totalItems - 6
                }
                    .distinctUntilChanged()
                    .collect { nearEnd ->
                        if (nearEnd) onLoadMore()
                    }
            }

            val distinctVibes = remember(state.suggestedVibes) {
                LazyListKeyPolicy.deduplicateById(state.suggestedVibes) { it.first }
            }

            val alsoFoundDistinct = remember(state.alsoFoundResults, displayList) {
                ExploreBrowseLogic.filterAlsoFound(state.alsoFoundResults, displayList)
            }

            val pinSnapshot =
                if (state.searchTab == SearchTab.SHOWS && state.searchQuery.isNotEmpty()) {
                    ProgressiveSearchScrollLogic.Snapshot(
                        query = state.searchQuery,
                        topResultId = displayList.firstOrNull()?.id,
                        hasAlsoFoundSection = alsoFoundDistinct.isNotEmpty(),
                    )
                } else {
                    null
                }
            var previousPin by remember {
                mutableStateOf<ProgressiveSearchScrollLogic.Snapshot?>(null)
            }
            LaunchedEffect(pinSnapshot, active) {
                if (!active) return@LaunchedEffect
                if (
                    pinSnapshot != null &&
                    ProgressiveSearchScrollLogic.shouldPinToTop(previousPin, pinSnapshot)
                ) {
                    gridState.scrollToItem(0)
                }
                previousPin = pinSnapshot
            }

            val rawGridItems = ExploreBrowseLogic.projectGridItems(
                displayList = displayList,
                isSearching = state.isSearching,
                hasCurrentVibe = state.currentVibe != null,
            )
            val gridItems = remember(rawGridItems) {
                val titleDistinct = rawGridItems.distinctBy { podcast ->
                    val titleKey = podcast.title.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
                    val artistKey = podcast.artist.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
                    if (titleKey.isNotEmpty() && artistKey.isNotEmpty()) {
                        "$titleKey|$artistKey"
                    } else {
                        podcast.id
                    }
                }
                LazyListKeyPolicy.deduplicateById(titleDistinct) { it.id }
            }

            val semanticPodcastsDistinct = remember(state.semanticPodcastResults) {
                LazyListKeyPolicy.deduplicateById(state.semanticPodcastResults) { it.id }
            }

            val distinctSemanticEpisodes = remember(state.semanticSearchResults) {
                LazyListKeyPolicy.deduplicateById(state.semanticSearchResults) { it.id }
            }

            val distinctRecommendations = remember(state.recommendations) {
                LazyListKeyPolicy.deduplicateById(state.recommendations) { it.id }
            }

            // One poster rule with Home: 2 on phones and the Fold 8 cover, more on wide compact windows and tablets.
            val window = LocalBoxloreWindowLayout.current
            val posterColumns = boxlorePosterColumns(window, boxloreContentWidthDp(), gapDp = 12f)
            // Tablets lead with a featured row; compact windows keep the full-width first card.
            val tabletColumns = if (window.usesTabletLayout) posterColumns else 0
            val gridPadding = boxloreHorizontalSafeContentPadding(
                PaddingValues(start = 16.dp, end = 16.dp, top = expandedHeaderHeight, bottom = listBottomPadding),
            )
            val feed: @Composable (Int, LazyStaggeredGridState) -> Unit = { browseTab, pageGridState ->
                ShimmerScope(active = if (state.currentVibe != null) state.isLoading else !isSearchModeActive && if (browseTab == 1) state.isRecommendationsLoading else state.isLoading) {
                    LazyVerticalStaggeredGrid(
                        state = pageGridState,
                        columns = if (window.isSpecified) {
                            StaggeredGridCells.Fixed(posterColumns)
                        } else {
                            // Isolated previews and tests have no app shell to size the window.
                            StaggeredGridCells.Adaptive(150.dp)
                        },
                        contentPadding = gridPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp,
                        modifier = Modifier.fillMaxSize().testTag("explore_feed_$browseTab")
                    ) {
                        if (state.currentVibe != null) {
                            exploreTopicResults(
                                title = state.currentVibe,
                                podcasts = gridItems,
                                loading = state.isLoading,
                                onBack = onTopicBack,
                                onPodcastClick = { podcast, index ->
                                    onPodcastClick(podcast.id, "explore_vibe", state.currentCategory, index)
                                },
                            )
                        } else if (isSearchModeActive) {
                            if (state.searchTab == SearchTab.EPISODES) {
                                exploreEpisodeSearchItems(
                                    content = ExploreEpisodeSearchContent(
                                        query = state.searchQuery,
                                        episodes = distinctSemanticEpisodes,
                                        podcasts = semanticPodcastsDistinct,
                                        loading = state.isSemanticLoading,
                                        searched = state.hasPerformedSemanticSearch,
                                        category = state.currentCategory,
                                    ),
                                    columns = tabletColumns,
                                    onExampleClick = onSearchQueryChanged,
                                    onPodcastClick = onPodcastClick,
                                    onEpisodeClick = onEpisodeClick,
                                )
                            } else {
                                exploreShowSearchItems(
                                    content = ExploreShowSearchContent(
                                        query = state.searchQuery,
                                        moods = distinctVibes,
                                        matches = gridItems,
                                        alsoFound = alsoFoundDistinct,
                                        loading = state.isLoading,
                                        failed = state.showSearchFailed,
                                        category = state.currentCategory,
                                    ),
                                    hasCatalog = displayList.isNotEmpty(),
                                    onMoodClick = { vibe ->
                                        searchActive = false
                                        focusManager.clearFocus()
                                        onVibeSelected(vibe.first, vibe.second)
                                    },
                                    onPodcastClick = onPodcastClick,
                                    onRetry = { onRetry(ExploreRetry.SHOW_SEARCH) },
                                )
                            }
                        } else {
                            // Not searching: standard tab content
                            // Scrollable headings remain part of the feed.
                            if (browseTab == 0) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    val headerTitle = exploreTopHeaderTitle(state.currentCategory)
                                    ExploreSectionHeader(
                                        title = headerTitle,
                                        regionLabel = regionDisplayLabel(activeRegionCode),
                                        onRegionClick = onRegionClick,
                                    )
                                }
                            }

                            if (browseTab == 1) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    ExploreSectionHeader(
                                        title = stringResource(R.string.explore_for_you),
                                        icon = Icons.Rounded.AutoAwesome,
                                    )
                                }
                                // Preserve the recommendation order; opt into Home's title-only discovery cards.
                                val recs = distinctRecommendations
                                val showContent = recs.isNotEmpty()
                                val showSkeletons = state.isRecommendationsLoading && recs.isEmpty()
                                val showEmptyState = !state.isRecommendationsLoading && recs.isEmpty()

                                if (showSkeletons) {
                                    if (tabletColumns >= 3) {
                                        tabletExploreSkeletonItems(tabletColumns)
                                    } else {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        ExploreBrowseCardSkeleton(featured = true)
                                    }
                                    items(4) {
                                        ExploreBrowseCardSkeleton(featured = false)
                                    }
                                    }
                                } else if (showEmptyState) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        ExploreRecommendationsEmptyState()
                                    }
                                } else if (showContent) {
                                    if (tabletColumns >= 3) {
                                        tabletRecommendationItems(recs, tabletColumns, onEpisodeClick)
                                    } else {
                                    item(
                                        key = LazyListKeyPolicy.safeKey(recs.firstOrNull()?.id, prefix = "rec_hero"),
                                        span = StaggeredGridItemSpan.FullLine,
                                    ) {
                                        val heroEp = recs[0]
                                        val parentPodcast = Podcast(
                                            id = heroEp.podcastId ?: "",
                                            title = heroEp.podcastTitle ?: "Podcast",
                                            artist = "",
                                            imageUrl = heroEp.podcastImageUrl?.takeIf { it.isNotBlank() } ?: heroEp.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                                            description = "",
                                            genre = heroEp.podcastGenre ?: "Podcast"
                                        )
                                        CuratedEpisodeCard(
                                            podcast = parentPodcast,
                                            episode = heroEp,
                                            showSubtitle = false,
                                            presentation = FeedMediaCardPresentation.ExpressiveFeatured,
                                            onClick = {
                                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreRecommendationCardTapped(
                                                    episodeId = heroEp.id,
                                                    episodeTitle = heroEp.title,
                                                    podcastId = parentPodcast.id,
                                                    podcastName = parentPodcast.title,
                                                    positionIndex = 0
                                                )
                                                onEpisodeClick(heroEp, parentPodcast)
                                            }
                                        )
                                    }

                                    itemsIndexed(
                                        recs.drop(1),
                                        key = { index, episode -> LazyListKeyPolicy.safeKey(episode.id, index, prefix = "rec") }
                                    ) { index, episode ->
                                        val parentPodcast = Podcast(
                                            id = episode.podcastId ?: "",
                                            title = episode.podcastTitle ?: "Podcast",
                                            artist = "",
                                            imageUrl = episode.podcastImageUrl?.takeIf { it.isNotBlank() }
                                                ?: episode.imageUrl?.takeIf { it.isNotBlank() }
                                                ?: "",
                                            description = "",
                                            genre = episode.podcastGenre ?: "Podcast"
                                        )
                                        CuratedEpisodeCard(
                                            podcast = parentPodcast,
                                            episode = episode,
                                            showSubtitle = false,
                                            presentation = FeedMediaCardPresentation.ExpressivePoster,
                                            onClick = {
                                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreRecommendationCardTapped(
                                                    episodeId = episode.id,
                                                    episodeTitle = episode.title,
                                                    podcastId = parentPodcast.id,
                                                    podcastName = parentPodcast.title,
                                                    positionIndex = index + 1
                                                )
                                                onEpisodeClick(episode, parentPodcast)
                                            },
                                        )
                                    }
                                    }
                                }
                            } else {
                                // Trending Tab Content
                                val showContent = displayList.isNotEmpty()
                                val showSkeletons = state.isLoading && displayList.isEmpty()
                                val showEmptyState = !state.isLoading && displayList.isEmpty()

                                if (showSkeletons) {
                                    if (tabletColumns >= 3) {
                                        tabletExploreSkeletonItems(tabletColumns)
                                    } else {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        ExploreBrowseCardSkeleton(featured = true)
                                    }
                                    items(4) {
                                        ExploreBrowseCardSkeleton(featured = false)
                                    }
                                    }
                                } else if (showEmptyState) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        ExploreEmptyState(searchMode = false, failed = state.topChartFailed) { onRetry(ExploreRetry.TOP_CHART) }
                                    }
                                } else if (showContent) {
                                    if (tabletColumns >= 3) {
                                        tabletTrendingItems(displayList[0], gridItems, tabletColumns, state.currentCategory, onPodcastClick)
                                    } else {
                                    // Featured Hero Card (P1 only, when not searching and has content)
                                    item(
                                        key = LazyListKeyPolicy.safeKey(displayList.firstOrNull()?.id, prefix = "trending_hero"),
                                        span = StaggeredGridItemSpan.FullLine,
                                    ) {
                                        ExploreBrowsePodcastCard(
                                            podcast = displayList[0],
                                            featured = true,
                                            onClick = { onPodcastClick(displayList[0].id, "explore_hero", state.currentCategory, 0) },
                                            showGenre = state.currentCategory == "All",
                                            rank = 1,
                                        )
                                    }

                                    val showGenreChip = state.currentCategory == "All"
                                    orderedExploreItems(gridItems, { index, podcast -> LazyListKeyPolicy.safeKey(podcast.id, index, prefix = "grid") }) { podcast, index ->
                                        ExploreBrowsePodcastCard(
                                            podcast = podcast,
                                            featured = false,
                                            showGenre = showGenreChip,
                                            // The chart's place: the featured show is 1, so the grid starts at 2.
                                            rank = index + 2,
                                            onClick = { onPodcastClick(podcast.id, "explore_grid", state.currentCategory, index + 1) }
                                        )
                                    }
                                    }

                                    exploreNextPageFooter(state.isLoadingMore, state.nextTopChartPageFailed) { onRetry(ExploreRetry.NEXT_TOP_CHART_PAGE) }
                                }
                            }
                        }
                    }
                }
            }
            if (isSearchModeActive) {
                feed(state.selectedTab, gridState)
            } else if (!state.hasForYou) {
                feed(ExploreDefaultTab.INDEX_TOP, topGridState)
            } else {
                HorizontalPager(
                    state = browsePager.pager,
                    modifier = Modifier.fillMaxSize().testTag("explore_browse_pager"),
                ) { page ->
                    val tab = ExploreTabPagerLogic.tabForPage(page)
                    feed(tab, if (tab == 1) forYouGridState else topGridState)
                }
            }
            if (state.currentVibe != null) {
                ExploreTopicToolbar(
                    onBack = onTopicBack,
                    onSearch = {
                        onClearVibe()
                        searchActive = true
                        focusSearch = true
                    },
                    modifier = Modifier.align(Alignment.TopCenter).boxloreHorizontalSafePadding().padding(horizontal = 16.dp, vertical = 12.dp)
                        .onSizeChanged { size -> expandedHeaderHeight = with(density) { size.height.toDp() } + 24.dp },
                )
            } else {
                ExploreFloatingHeader(
                    state = chromeState,
                    onExpandedHeightChanged = { expandedHeaderHeight = it },
                    modifier = Modifier.align(Alignment.TopCenter),
                    search = {
                        ExploreHeaderSearchField(
                            query = state.searchQuery,
                            searchTab = state.searchTab,
                            active = searchActive,
                            focusRequester = searchFocusRequester,
                            onActiveChange = { searchActive = it },
                            onQueryChange = onSearchQueryChanged,
                            onSearch = {
                                onSearchTriggered(state.searchQuery)
                                searchActive = false
                                focusManager.clearFocus()
                            },
                            onBack = {
                                searchActive = false
                                onSearchQueryChanged("")
                                onClearVibe()
                                focusManager.clearFocus()
                            },
                        )
                    },
                    selectors = {
                        ExploreHeaderSelectors(
                            row = when {
                                isSearchModeActive -> ExploreHeaderRow.SearchModes
                                visibleTab == 0 -> ExploreHeaderRow.Genres
                                visibleTab == 1 && state.suggestedVibes.isNotEmpty() -> ExploreHeaderRow.Moods
                                else -> ExploreHeaderRow.None
                            },
                            searchTab = state.searchTab,
                            onSearchTabSelected = onSearchTabSelected,
                            category = state.currentCategory,
                            onCategorySelected = onCategorySelected,
                            moods = state.suggestedVibes,
                            onMoodSelected = { id, name ->
                                searchActive = false
                                focusManager.clearFocus()
                                onVibeSelected(id, name)
                            },
                        )
                    },
                )
            }

            // Centered Bottom FAB for switching between For You and Top (Material 3 Segmented Control FAB)
            AnimatedVisibility(
                visible = !isSearchModeActive && state.hasForYou,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val animatedBottomOffset by animateDpAsState(
                    targetValue = tabFabBottomPadding,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "fab_bottom_offset"
                )

                ExploreTabSelectorFab(
                    selectedTab = browsePager.visibleTab,
                    onTabSelected = browsePager::selectTab,
                    modifier = Modifier
                        .adaptivePlayerOverlayOffset(isMiniPlayerVisible = isPlayerVisible)
                        .boxloreHorizontalSafePadding()
                        .padding(bottom = animatedBottomOffset)
                )
            }
        }
    }
}
