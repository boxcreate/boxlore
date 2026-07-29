package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import cx.aswin.boxlore.core.designsystem.components.AnimatedShapesFallback
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.designsystem.components.regionDisplayLabel
import cx.aswin.boxlore.core.designsystem.component.ExploreTabSelectorFabHeight
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.navigationStyleUsesExternalSystemNavigationInset
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession

import cx.aswin.boxlore.core.model.Episode
import androidx.compose.ui.graphics.Brush
import cx.aswin.boxlore.feature.explore.components.ExploreEmptyState
import cx.aswin.boxlore.feature.explore.components.ExploreEpisodeBentoCard
import cx.aswin.boxlore.feature.explore.components.ExploreEpisodeHeroCard
import cx.aswin.boxlore.feature.explore.components.ExploreEpisodesSearchEmptyState
import cx.aswin.boxlore.feature.explore.components.ExploreEpisodesSearchIdleState
import cx.aswin.boxlore.feature.explore.components.ExploreGenreSelector
import cx.aswin.boxlore.feature.explore.components.ExploreHeroCard
import cx.aswin.boxlore.feature.explore.components.ExploreMoodResultsHeader
import cx.aswin.boxlore.feature.explore.components.ExplorePodcastCard
import cx.aswin.boxlore.feature.explore.components.ExploreRelatedShowsRail
import cx.aswin.boxlore.feature.explore.components.ExploreRecommendationsEmptyState
import cx.aswin.boxlore.feature.explore.components.ExploreSectionHeader
import cx.aswin.boxlore.feature.explore.components.ExploreSuggestedMoodsHeader
import cx.aswin.boxlore.feature.explore.components.ExploreTabSelectorFab
import cx.aswin.boxlore.feature.explore.components.ExploreVibeCard
import cx.aswin.boxlore.feature.explore.components.ExploreVibeChipRow
import cx.aswin.boxlore.feature.explore.components.SearchTabSelector

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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeRegionCode by viewModel.activeRegionCode.collectAsStateWithLifecycle()
    val isPlayerVisible by remember(viewModel) {
        viewModel.playerState.map { it.currentEpisode != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreScreenViewed(entryPoint)
    }

    TrackScreenSession(
        onSessionResume = viewModel::onScreenResume,
        onSessionExit = viewModel::trackScreenExit
    )

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
        onLoadMore = { viewModel.loadMoreTrending() }
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
    onLoadMore: () -> Unit = {}
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
                Text(text = "Error: ${uiState.message}")
            }
            return
        }
        is ExploreUiState.Success -> uiState
    }
    val displayList = if (state.isSearching) state.searchResults else state.trending

    val isRecommendationsFallback = state.isRecommendationsFallback

    if (state.selectedTab == 1 && state.recommendations.isNotEmpty()) {
        LaunchedEffect(state.recommendations) {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreRecommendationsImpression(
                recommendationsCount = state.recommendations.size,
                episodeIds = state.recommendations.map { it.id }
            )
        }
    }
    
    // Genre expansion state
    var isGenreExpanded by rememberSaveable { mutableStateOf(false) }

    // Scroll handling: Collapse genre cloud on scroll
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If scrolling down (content moving up) and expanded, collapse it
                if (available.y < -5f && isGenreExpanded) {
                    isGenreExpanded = false
                }
                return Offset.Zero
            }
        }
    }

    var searchActive by rememberSaveable { mutableStateOf(false) }
    val isSearchModeActive = searchActive || state.searchQuery.isNotEmpty() || state.currentVibe != null
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomChromeHeight =
        appBottomChromeContentPadding(isMiniPlayerVisible = isPlayerVisible) +
            if (navigationStyleUsesExternalSystemNavigationInset(LocalNavigationStyle.current)) {
                systemNavBarHeight
            } else {
                0.dp
            }
    // Clearance above navbar/mini-player for the tab FAB.
    val tabFabBottomPadding = bottomChromeHeight + 16.dp
    // Extra FAB height so list content can scroll fully past the overlay.
    val listBottomPadding = if (!isSearchModeActive) {
        tabFabBottomPadding + ExploreTabSelectorFabHeight + 16.dp
    } else {
        bottomChromeHeight + 24.dp
    }

    // Box layout to hold everything
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Column layout to keep search bar fixed at top
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .nestedScroll(nestedScrollConnection)
        ) {
        // FIXED HEADER: Search + Genre Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp)
                .animateContentSize() // Smooth resize when cloud collapses/expands
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search Bar (Always Visible)
            DockedSearchBar(
                expanded = false,
                onExpandedChange = { searchActive = it },
                inputField = {
                    SearchBarDefaults.InputField(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChanged,
                        onSearch = { 
                            onSearchTriggered(state.searchQuery)
                            searchActive = false 
                            focusManager.clearFocus()
                        },
                        expanded = false,
                        onExpandedChange = { searchActive = it },
                        placeholder = { Text("Search podcasts...") },
                        leadingIcon = { 
                            if (searchActive || state.searchQuery.isNotEmpty() || state.currentVibe != null) {
                                IconButton(onClick = {
                                    searchActive = false
                                    onSearchQueryChanged("")
                                    onClearVibe()
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = null) 
                            }
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) { }
            
            // Search Tab Selector (Whenever search mode is active, but not browsing a vibe)
            if (isSearchModeActive && state.currentVibe == null) {
                Spacer(modifier = Modifier.height(12.dp))
                SearchTabSelector(
                    selectedTab = state.searchTab,
                    onTabSelected = onSearchTabSelected,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            }
            
            // Expandable Genre Cloud (Trending tab only)
            if (!isSearchModeActive && state.selectedTab == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                ExploreGenreSelector(
                    selectedCategory = state.currentCategory,
                    onCategorySelected = onCategorySelected
                )
            }

            // Vibe catchers (For You) — same sticky header slot / spacing as genres
            if (!isSearchModeActive && state.selectedTab == 1 && state.suggestedVibes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ExploreVibeChipRow(
                    vibes = state.suggestedVibes,
                    onVibeSelected = { id, name ->
                        searchActive = false
                        focusManager.clearFocus()
                        onVibeSelected(id, name)
                    },
                )
            }

            // Bottom spacing under sticky chrome (search / genres / mood chips)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // SCROLLABLE CONTENT: Grid
        val gridState = rememberLazyStaggeredGridState()

        // Infinite scroll trigger using snapshotFlow to avoid rapid-fire recomposition loops
        androidx.compose.runtime.LaunchedEffect(gridState) {
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

        val distinctVibes = remember(state.suggestedVibes) { state.suggestedVibes.distinctBy { it.first } }

        val alsoFoundDistinct = remember(state.alsoFoundResults, displayList) {
            val catalogIds = displayList.map { it.id }.toSet()
            state.alsoFoundResults
                .distinctBy { it.id }
                .filter { it.id !in catalogIds }
        }

        val rawGridItems = if (!state.isSearching && displayList.isNotEmpty() && state.currentVibe == null) displayList.drop(1) else displayList
        val gridItems = remember(rawGridItems) {
            rawGridItems.distinctBy { podcast ->
                val titleKey = podcast.title.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
                val artistKey = podcast.artist.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
                if (titleKey.isNotEmpty() && artistKey.isNotEmpty()) {
                    "$titleKey|$artistKey"
                } else {
                    podcast.id
                }
            }
        }

        val semanticPodcastsDistinct = remember(state.semanticPodcastResults) {
            state.semanticPodcastResults.distinctBy { it.id }
        }

        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 0.dp,
                bottom = listBottomPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
            modifier = Modifier.weight(1f)
        ) {
            if (isSearchModeActive) {
                if (state.searchTab == SearchTab.EPISODES) {
                    if (state.searchQuery.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            ExploreEpisodesSearchIdleState(
                                onExampleClick = onSearchQueryChanged,
                            )
                        }
                    } else {
                        val eps = state.semanticSearchResults
                        val pods = semanticPodcastsDistinct
                        val showContent = eps.isNotEmpty() || pods.isNotEmpty()
                        val showLoader = state.isSemanticLoading
                        val showEmptyState =
                            !state.isSemanticLoading && state.hasPerformedSemanticSearch && !showContent

                        when {
                            showLoader && !showContent -> {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BoxLoreLoader.Expressive(size = 80.dp)
                                    }
                                }
                            }
                            showEmptyState -> {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    ExploreEpisodesSearchEmptyState()
                                }
                            }
                            showContent -> {
                            if (pods.isNotEmpty()) {
                                item(
                                    key = "semantic_related_shows_rail",
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    ExploreRelatedShowsRail(
                                        podcasts = pods,
                                        onPodcastClick = { podcast, index ->
                                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackSearchResultTapped(
                                                surface = "explore",
                                                resultType = "podcast",
                                                podcastId = podcast.id,
                                                episodeId = null,
                                                positionIndex = index,
                                                searchQuery = state.searchQuery,
                                                searchMode = "concept_semantic",
                                            )
                                            onPodcastClick(
                                                podcast.id,
                                                "explore_search_concept",
                                                state.currentCategory,
                                                index,
                                            )
                                        },
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                            }

                            if (eps.isNotEmpty()) {
                                item(
                                    key = "semantic_episodes_header",
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    Text(
                                        text = "Episodes",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = GoogleSansWeight.bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            top = if (pods.isNotEmpty()) 8.dp else 0.dp,
                                            bottom = 8.dp,
                                        ),
                                    )
                                }
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    val heroEp = eps[0]
                                    val parentPodcast = Podcast(
                                        id = heroEp.podcastId ?: "",
                                        title = heroEp.podcastTitle ?: "Podcast",
                                        artist = "",
                                        imageUrl = heroEp.podcastImageUrl?.takeIf { it.isNotBlank() } ?: heroEp.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                                        description = "",
                                        genre = heroEp.podcastGenre ?: "Podcast"
                                    )
                                    ExploreEpisodeHeroCard(
                                        episode = heroEp,
                                        isFallback = isRecommendationsFallback,
                                        labelText = "FEATURED RESULT",
                                        onClick = {
                                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackSearchResultTapped(
                                                surface = "explore",
                                                resultType = "episode",
                                                podcastId = parentPodcast.id,
                                                episodeId = heroEp.id,
                                                positionIndex = 0,
                                                searchQuery = state.searchQuery,
                                                searchMode = "concept_semantic",
                                            )
                                            onEpisodeClick(heroEp, parentPodcast)
                                        }
                                    )
                                }

                                itemsIndexed(eps.drop(1), key = { _, episode -> "search_semantic_${episode.id}" }) { index, episode ->
                                    val parentPodcast = Podcast(
                                        id = episode.podcastId ?: "",
                                        title = episode.podcastTitle ?: "Podcast",
                                        artist = "",
                                        imageUrl = episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: episode.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                                        description = "",
                                        genre = episode.podcastGenre ?: "Podcast"
                                    )
                                    ExploreEpisodeBentoCard(
                                        episode = episode,
                                        onClick = {
                                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackSearchResultTapped(
                                                surface = "explore",
                                                resultType = "episode",
                                                podcastId = parentPodcast.id,
                                                episodeId = episode.id,
                                                positionIndex = index + 1,
                                                searchQuery = state.searchQuery,
                                                searchMode = "concept_semantic",
                                            )
                                            onEpisodeClick(episode, parentPodcast)
                                        }
                                    )
                                }
                            }

                            if (state.isSemanticLoading) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BoxLoreLoader.Expressive(size = 48.dp)
                                    }
                                }
                            }
                            }
                        }
                    }
                } else {
                    // SearchTab.SHOWS
                    if (state.searchQuery.isEmpty() && state.currentVibe == null) {
                        if (state.suggestedVibes.isNotEmpty()) {
                            item(
                                key = "suggested_moods_header",
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                ExploreSuggestedMoodsHeader()
                            }
                            items(distinctVibes, key = { "vibe_${it.first}" }) { vibe ->
                                ExploreVibeCard(vibe = vibe, onClick = { 
                                    searchActive = false
                                    focusManager.clearFocus()
                                    onVibeSelected(vibe.first, vibe.second) 
                                })
                            }
                        }
                    } else {
                        val vibeTitle = state.currentVibe
                        if (vibeTitle != null) {
                            item(
                                key = "mood_results_header",
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                ExploreMoodResultsHeader(title = vibeTitle)
                            }
                        }

                        val alsoFound = alsoFoundDistinct
                        val hasCatalog = displayList.isNotEmpty()
                        val hasAlsoFound = alsoFound.isNotEmpty() && state.currentVibe == null
                        val showContent = hasCatalog || hasAlsoFound
                        val showSkeletons = state.isLoading && !showContent
                        val showEmptyState = !state.isLoading && !showContent

                        if (showSkeletons) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val loaderSize = if (state.isSearching) 100.dp else 80.dp
                                    BoxLoreLoader.Expressive(size = loaderSize)
                                }
                            }
                        } else if (showEmptyState) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ExploreEmptyState()
                            }
                        } else if (showContent) {
                            val showGenreChip = state.currentCategory == "All" && state.currentVibe == null
                            if (hasCatalog && hasAlsoFound) {
                                item(
                                    key = "matches_header",
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    Text(
                                        text = "Matches",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = GoogleSansWeight.bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                            itemsIndexed(gridItems, key = { _, it -> "grid_${it.id}" }) { index, podcast ->
                                val cardHeight = 160.dp
                                val entryPointStr = if (state.currentVibe != null) "explore_vibe" else "explore_search"
                                ExplorePodcastCard(
                                    podcast = podcast,
                                    cardHeight = cardHeight,
                                    showGenreChip = showGenreChip,
                                    onClick = { onPodcastClick(podcast.id, entryPointStr, state.currentCategory, index) }
                                )
                            }

                            if (hasAlsoFound) {
                                item(
                                    key = "also_found_header",
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    Text(
                                        text = "Also found",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = GoogleSansWeight.bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    )
                                }
                                itemsIndexed(alsoFound, key = { _, podcast -> "also_${podcast.id}" }) { index, podcast ->
                                    ExplorePodcastCard(
                                        podcast = podcast,
                                        cardHeight = 160.dp,
                                        showGenreChip = showGenreChip,
                                        onClick = {
                                            onPodcastClick(
                                                podcast.id,
                                                "explore_search_also_found",
                                                state.currentCategory,
                                                gridItems.size + index,
                                            )
                                        },
                                    )
                                }
                            }
                            
                            if (state.isLoading) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BoxLoreLoader.Expressive(size = 48.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Not searching: standard tab content
                // 1. Unified Section Header (Only visible on Trending)
                if (state.selectedTab == 0) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        val headerTitle = if (state.currentCategory == "All") {
                            "Top charts"
                        } else {
                            "Top in ${state.currentCategory}"
                        }
                        ExploreSectionHeader(
                            title = headerTitle,
                            regionLabel = regionDisplayLabel(activeRegionCode),
                            onRegionClick = onRegionClick,
                        )
                    }
                }

                if (state.selectedTab == 1) {
                    // For You — staggered bento layout; title-only posters (no show name) like Home taste cards
                    val recs = state.recommendations
                    val showContent = recs.isNotEmpty()
                    val showSkeletons = state.isRecommendationsLoading && recs.isEmpty()
                    val showEmptyState = !state.isRecommendationsLoading && recs.isEmpty()

                    if (showSkeletons) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxLoreLoader.Expressive(size = 80.dp)
                            }
                        }
                    } else if (showEmptyState) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            ExploreRecommendationsEmptyState()
                        }
                    } else if (showContent) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            val heroEp = recs[0]
                            val parentPodcast = Podcast(
                                id = heroEp.podcastId ?: "",
                                title = heroEp.podcastTitle ?: "Podcast",
                                artist = "",
                                imageUrl = heroEp.podcastImageUrl?.takeIf { it.isNotBlank() } ?: heroEp.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                                description = "",
                                genre = heroEp.podcastGenre ?: "Podcast"
                            )
                            ExploreEpisodeHeroCard(
                                episode = heroEp,
                                isFallback = isRecommendationsFallback,
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

                        itemsIndexed(recs.drop(1), key = { _, it -> "rec_${it.id}" }) { index, episode ->
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
                } else {
                    // Trending Tab Content
                    val showContent = displayList.isNotEmpty()
                    val showSkeletons = state.isLoading && displayList.isEmpty()
                    val showEmptyState = !state.isLoading && displayList.isEmpty()

                    if (showSkeletons) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxLoreLoader.Expressive(size = 80.dp)
                            }
                        }
                    } else if (showEmptyState) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            ExploreEmptyState()
                        }
                    } else if (showContent) {
                        // Featured Hero Card (P1 only, when not searching and has content)
                        item(span = StaggeredGridItemSpan.FullLine) {
                            ExploreHeroCard(
                                podcast = displayList[0],
                                onClick = { onPodcastClick(displayList[0].id, "explore_hero", state.currentCategory, 0) },
                                showGenreChip = state.currentCategory == "All"
                            )
                        }

                        val showGenreChip = state.currentCategory == "All"
                        itemsIndexed(gridItems, key = { _, it -> "grid_${it.id}" }) { index, podcast ->
                            val cardHeight = 160.dp
                            ExplorePodcastCard(
                                podcast = podcast,
                                cardHeight = cardHeight,
                                showGenreChip = showGenreChip,
                                onClick = { onPodcastClick(podcast.id, "explore_grid", state.currentCategory, index + 1) }
                            )
                        }

                        // Loading indicator for pagination
                        if (state.isLoadingMore) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BoxLoreLoader.Expressive(size = 40.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Centered Bottom FAB for switching between For You and Top (Material 3 Segmented Control FAB)
    if (!isSearchModeActive) {
        val animatedBottomOffset by animateDpAsState(
            targetValue = tabFabBottomPadding,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "fab_bottom_offset"
        )

        ExploreTabSelectorFab(
            selectedTab = state.selectedTab,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = animatedBottomOffset)
        )
    }
}
}
