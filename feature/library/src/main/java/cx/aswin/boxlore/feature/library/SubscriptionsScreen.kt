package cx.aswin.boxlore.feature.library

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.adaptivePlayerOverlayOffset
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.component.navigationStyleUsesExternalSystemNavigationInset
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreSearchField
import cx.aswin.boxlore.core.designsystem.components.BoxloreSnackbarHost
import cx.aswin.boxlore.core.designsystem.components.BoxloreTooltip
import cx.aswin.boxlore.core.designsystem.components.discoveryTabSwitcherHeight
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.layout.boxlorePosterColumns
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.feature.library.subscriptions.AddToFoldersHost
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizeDisableDialog
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizeLook
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuKey
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuTarget
import cx.aswin.boxlore.feature.library.subscriptions.ExpressiveTabSwitcher
import cx.aswin.boxlore.feature.library.subscriptions.FolderEditorPage
import cx.aswin.boxlore.feature.library.subscriptions.FolderInterSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderIntraSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderMembershipChange
import cx.aswin.boxlore.feature.library.subscriptions.FolderPagesActions
import cx.aswin.boxlore.feature.library.subscriptions.FolderPagesData
import cx.aswin.boxlore.feature.library.subscriptions.FolderShowMove
import cx.aswin.boxlore.feature.library.subscriptions.FolderShowsSelectionSheet
import cx.aswin.boxlore.feature.library.subscriptions.LatestQueueOutcome
import cx.aswin.boxlore.feature.library.subscriptions.LatestSortActions
import cx.aswin.boxlore.feature.library.subscriptions.LatestSortSheet
import cx.aswin.boxlore.feature.library.subscriptions.LatestSwipeAction
import cx.aswin.boxlore.feature.library.subscriptions.LatestTabActions
import cx.aswin.boxlore.feature.library.subscriptions.LatestTabConfig
import cx.aswin.boxlore.feature.library.subscriptions.LatestTabContent
import cx.aswin.boxlore.feature.library.subscriptions.MoveShowKey
import cx.aswin.boxlore.feature.library.subscriptions.MoveToFolderSheet
import cx.aswin.boxlore.feature.library.subscriptions.OpenFolderPage
import cx.aswin.boxlore.feature.library.subscriptions.ReorderMode
import cx.aswin.boxlore.feature.library.subscriptions.ShowsTabActions
import cx.aswin.boxlore.feature.library.subscriptions.ShowsTabConfig
import cx.aswin.boxlore.feature.library.subscriptions.ShowsTabContent
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionContextMenuActions
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionContextMenuSheet
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionReorderBar
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortActions
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortConfig
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortSheet
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionsTabSelectorFab
import cx.aswin.boxlore.feature.library.subscriptions.genreFolders
import cx.aswin.boxlore.feature.library.subscriptions.latestQueueMessage
import cx.aswin.boxlore.feature.library.subscriptions.rememberFolderPagesState
import cx.aswin.boxlore.feature.library.subscriptions.rememberSubscriptionsScreenState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

val LocalLastSeenEpisodes = compositionLocalOf<Map<String, String>> { emptyMap() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onPodcastClick: (String) -> Unit,
    onExploreClick: () -> Unit,
    onPlayEpisode: ((Episode, Podcast) -> Unit)? = null,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)? = null,
    onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)? = null,
    isPlayerActive: Boolean = false,
    initialTab: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = initialTab) { 2 }
    val lastSeenEpisodes by viewModel.lastSeenEpisodes.collectAsStateWithLifecycle()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLastSeenEpisodes provides lastSeenEpisodes
    ) {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        // Search, open sheets and questions, and a reorder in progress survive recreation.
        val screenState = rememberSubscriptionsScreenState()
        var searchQuery by screenState::searchQuery
        var isSearchActive by screenState::isSearchActive
        val isGridView by viewModel.subscriptionsGridView.collectAsStateWithLifecycle()
        val isRefreshingNewEpisodes by viewModel.isRefreshingNewEpisodes.collectAsStateWithLifecycle()
        val pendingPlayedEpisodeIds by viewModel.pendingPlayedEpisodeIds.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val useSmartRank by viewModel.useSmartRank.collectAsStateWithLifecycle()
        val hideCompletedInSubs by viewModel.hideCompletedInSubs.collectAsStateWithLifecycle()
        val pinnedPodcastIds by viewModel.pinnedPodcastIds.collectAsStateWithLifecycle()
        val autoOrganizeFolders by viewModel.autoOrganizeFolders.collectAsStateWithLifecycle()
        val autoOrganizeLook by viewModel.autoOrganizeLook.collectAsStateWithLifecycle()
        val folderSort by viewModel.folderSort.collectAsStateWithLifecycle()
        val folderManualOrder by viewModel.folderManualOrder.collectAsStateWithLifecycle()
        val intraFolderSort by viewModel.intraFolderSort.collectAsStateWithLifecycle()
        val folders by viewModel.folders.collectAsStateWithLifecycle()
        val foldersLoaded by viewModel.foldersLoaded.collectAsStateWithLifecycle()
        val subscriptionsTabStyle by viewModel.subscriptionsTabStyle.collectAsStateWithLifecycle()
        var showSortSheet by screenState::sortSheetOpen
        val folderPages = rememberFolderPagesState()
        var reorderMode by screenState::reorderMode
        var tempFolderOrder by screenState::tempFolderOrder
        var tempRootOrder by screenState::tempRootOrder
        var confirmAutoOrganizeOff by screenState::confirmAutoOrganizeOff
        val loadedPodcasts = (uiState as? LibraryUiState.Success)?.subscribedPodcasts
        // Open targets are kept by ID and looked up in today's data; one that went away closes quietly.
        // Folder targets wait for the folders: after the app was closed in the background the shows can load first.
        LaunchedEffect(loadedPodcasts, folders, foldersLoaded) {
            if (loadedPodcasts != null) screenState.dropMissing(folders.takeIf { foldersLoaded }, loadedPodcasts)
        }
        val contextMenuTarget = screenState.contextMenu?.resolve(folders, loadedPodcasts.orEmpty())
        val selectedFolderForShowSelection = screenState.addShowsFolderId?.let { id -> folders.firstOrNull { it.id == id } }
        val moveShowTarget = screenState.moveShow?.let { move ->
            loadedPodcasts?.firstOrNull { it.id == move.podcastId }?.let { podcast -> folders.firstOrNull { it.id == move.fromFolderId } to podcast }
        }
        val confirmDeleteFolder = screenState.confirmDeleteFolderId?.let { id -> folders.firstOrNull { it.id == id } }
        val confirmUnsubscribePodcast = screenState.confirmUnsubscribeId?.let { id -> loadedPodcasts?.firstOrNull { it.id == id } }

        val isFloatingTabs = subscriptionsTabStyle == SubscriptionsTabStyle.FLOATING
        val shortPane = boxloreIsShortPane()
        // In a short window the whole header (bar and docked tabs) scrolls away with the list.
        val shortHeaderBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        // A window that becomes short starts with that header tucked away, so the shows come first; scrolling up brings it back.
        val shortHeaderStart = remember(shortPane) { ShortHeaderStart(tucked = shortPane && !screenState.isSearchActive) }
        val keyboard = LocalSoftwareKeyboardController.current

        val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val navigationStyle = LocalNavigationStyle.current
        val externalNavInset = if (navigationStyleUsesExternalSystemNavigationInset(navigationStyle)) {
            systemNavBarHeight
        } else {
            0.dp
        }
        val bottomChromeHeight = appBottomChromeContentPadding(isMiniPlayerVisible = isPlayerActive) + externalNavInset
        val tabFabBottomPadding = bottomChromeHeight + 16.dp
        val playAllBottomPadding = if (isFloatingTabs) {
            tabFabBottomPadding + discoveryTabSwitcherHeight() + 12.dp
        } else {
            null
        }

        val pinCapacityMessage = stringResource(R.string.library_pin_capacity, cx.aswin.boxlore.core.prefs.HomePinnedShows.MAX)
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        BackHandler(enabled = isSearchActive) {
            if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
            }
        }

        BackHandler(enabled = reorderMode != ReorderMode.Inactive) {
            reorderMode = ReorderMode.Inactive
            tempFolderOrder = emptyList()
            tempRootOrder = emptyList()
        }

        LaunchedEffect(isSearchActive) {
            if (isSearchActive) {
                focusRequester.requestFocus()
            } else {
                focusManager.clearFocus()
            }
        }

        val isScrolled = scrollBehavior.state.overlappedFraction > 0.01f || scrollBehavior.state.collapsedFraction > 0.01f

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    if (isSearchActive) {
                        viewModel.subDidSearch = true
                        viewModel.subFinalSearchQuery = searchQuery
                    }
                    viewModel.trackSubscriptionsExit()
                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                    // Live `/sync` — including open-app-to Subscriptions — not Room cache only.
                    viewModel.onScreenResume()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        TrackScreenView("subscriptions") {
            val initialTabName = if (initialTab == 0) "shows" else "latest"
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsViewed(
                sourceEntryPoint = "library_hub_card",
                initialTab = initialTabName
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage != initialTab) {
                viewModel.subTabSwitchesCount++
            }
        }
        val headerBgColor by animateColorAsState(
            targetValue = if (isScrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "headerBg"
        )

        val successState = uiState as? LibraryUiState.Success
        val hasSubscribedPodcasts = successState != null && successState.subscribedPodcasts.isNotEmpty()
        val isFolderOpen = folders.any { it.id == folderPages.openFolderId }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                // The open folder covers the tabs, so screen readers skip what's under it.
                modifier = Modifier
                    .nestedScroll(if (shortPane) shortHeaderBehavior.nestedScrollConnection else scrollBehavior.nestedScrollConnection)
                    .then(if (isFolderOpen) Modifier.clearAndSetSemantics {} else Modifier),
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBgColor)
                            // In a short window the bar and docked tabs scroll away with the list and come back on the way up.
                            .then(if (shortPane) Modifier.collapseWith(shortHeaderBehavior.state, shortHeaderStart) else Modifier)
                            .boxloreCardWidth(BoxloreCardFamily.Grid),
                    ) {
                        TopAppBar(
                            title = {
                                if (isSearchActive) {
                                    BoxloreSearchField(
                                        query = searchQuery,
                                        onQueryChange = { searchQuery = it },
                                        onSearch = { keyboard?.hide() },
                                        placeholder = stringResource(R.string.library_search_shows),
                                        modifier = Modifier.focusRequester(focusRequester),
                                        active = true,
                                        onBack = screenState::closeSearch,
                                        maxWidth = null,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.library_hub_subscriptions),
                                        fontWeight = GoogleSansWeight.bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            ),
                            navigationIcon = {
                                // While searching, the search box carries its own Back.
                                if (!isSearchActive) {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.library_back))
                                    }
                                }
                            },
                            actions = {
                                if (!isSearchActive) {
                                    if (hasSubscribedPodcasts) {
                                        val layoutLabel = stringResource(if (isGridView) R.string.library_list_view else R.string.library_grid_view)
                                        BoxloreTooltip(layoutLabel) {
                                            IconButton(onClick = {
                                                val showGrid = !isGridView
                                                viewModel.setSubscriptionsGridView(showGrid)
                                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsLayoutToggled(showGrid)
                                            }) {
                                                Icon(
                                                    imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                                    contentDescription = layoutLabel,
                                                )
                                            }
                                        }
                                        val sortLabel = stringResource(R.string.library_sort)
                                        BoxloreTooltip(sortLabel) {
                                            IconButton(onClick = { showSortSheet = true }) {
                                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = sortLabel)
                                            }
                                        }
                                    }
                                    val searchLabel = stringResource(R.string.library_search)
                                    BoxloreTooltip(searchLabel) {
                                        IconButton(onClick = { isSearchActive = true }) {
                                            Icon(Icons.Rounded.Search, contentDescription = searchLabel)
                                        }
                                    }
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )

                        val latestCount = successState?.subscribedPodcasts?.count { it.latestEpisode != null } ?: 0

                        if (!isFloatingTabs) {
                            ExpressiveTabSwitcher(
                                tabs = listOf(stringResource(R.string.library_tab_shows), stringResource(R.string.library_tab_new_episodes)),
                                selectedIndex = pagerState.currentPage,
                                badge = if (latestCount > 0) mapOf(1 to latestCount) else emptyMap(),
                                onTabSelected = { index ->
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                // Clear of a sideways phone's cutout and side buttons, like the bar above it.
                                modifier = Modifier.boxloreHorizontalSafePadding().padding(horizontal = 16.dp, vertical = 6.dp).testTag(SUBSCRIPTIONS_TAB_SWITCHER_TAG)
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).imePadding()) {
                    when (uiState) {
                        is LibraryUiState.Loading -> LibraryLoading {
                            if (isGridView && pagerState.currentPage == 0) {
                                LibraryGridSkeleton(columns = boxlorePosterColumns(LocalBoxloreWindowLayout.current, libraryGridWidthDp(), compactColumns = 3, gapDp = 12f))
                            } else {
                                LibraryRowsSkeleton()
                            }
                        }
                        is LibraryUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.library_subscriptions_error_loading))
                            }
                        }
                        is LibraryUiState.Success -> {
                            val success = uiState as LibraryUiState.Success
                            val allPodcasts = success.subscribedPodcasts
                            val podcasts = if (searchQuery.isBlank()) {
                                allPodcasts
                            } else {
                                allPodcasts.filter {
                                    it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.artist.contains(searchQuery, ignoreCase = true)
                                }
                            }
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (page) {
                                    0 -> ShowsTabContent(
                                        podcasts = podcasts,
                                        folders = folders,
                                        config = ShowsTabConfig(
                                            isGridView = isGridView,
                                            canReorder = searchQuery.isBlank(),
                                            reorderMode = reorderMode,
                                            pinnedPodcastIds = pinnedPodcastIds,
                                            sort = success.currentSort,
                                            manualOrder = if (reorderMode is ReorderMode.RootShows && tempRootOrder.isNotEmpty()) {
                                                tempRootOrder
                                            } else {
                                                success.manualOrder
                                            },
                                            folderSort = folderSort,
                                            folderManualOrder = if (reorderMode is ReorderMode.Folders && tempFolderOrder.isNotEmpty()) {
                                                tempFolderOrder
                                            } else {
                                                folderManualOrder
                                            },
                                            intraFolderSort = intraFolderSort,
                                            smartOrderIds = success.smartOrderIds,
                                            floatingTabsClearance = if (isFloatingTabs) discoveryTabSwitcherHeight() + 16.dp else 0.dp,
                                        ),
                                        actions = ShowsTabActions(
                                            onExploreClick = onExploreClick,
                                            onPodcastClick = {
                                                viewModel.subPodcastsClickedCount++
                                                onPodcastClick(it)
                                            },
                                            onPodcastLongClick = { podcast ->
                                                screenState.contextMenu = ContextMenuKey.of(ContextMenuTarget.RootPodcast(podcast))
                                            },
                                            onReorder = { newOrder ->
                                                tempRootOrder = newOrder
                                            },
                                            onReorderFolders = { newOrder ->
                                                tempFolderOrder = newOrder
                                            },
                                            onNewFolderClick = { folderPages.openNewFolder() },
                                            onFolderClick = folderPages::openFolder,
                                            onFolderLongClick = { folder ->
                                                screenState.contextMenu = ContextMenuKey.of(ContextMenuTarget.Folder(folder))
                                            },
                                        ),
                                    )
                                    1 -> {
                                        LatestTabContent(
                                            podcasts = podcasts,
                                            allHistory = success.allHistory,
                                            config = LatestTabConfig(
                                                useSmartRank = useSmartRank,
                                                hideCompleted = hideCompletedInSubs,
                                                isPlayerActive = isPlayerActive,
                                                playAllBottomPadding = playAllBottomPadding,
                                                isRefreshing = isRefreshingNewEpisodes,
                                                pendingPlayedEpisodeIds = pendingPlayedEpisodeIds,
                                                dismissedEpisodeIds = success.dismissedEpisodeIds,
                                            ),
                                            scoreEpisodes = viewModel::scoreLatestEpisodes,
                                            actions =
                                            LatestTabActions(
                                                onExploreClick = onExploreClick,
                                                onEpisodeClick = { ep, pod, entry ->
                                                    viewModel.subEpisodesClickedCount++
                                                    onEpisodeClick?.invoke(ep, pod, entry)
                                                },
                                                onPlayEpisode = onPlayEpisode,
                                                onPlayEpisodes = onPlayEpisodes,
                                                onRefresh = viewModel::refreshNewEpisodes,
                                                onSwipeAction = { action, episode, podcast ->
                                                    coroutineScope.launch {
                                                        handleLatestSwipe(viewModel, snackbarHostState, context, action, episode, podcast)
                                                    }
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val floatingTabsCovered = isSearchActive || reorderMode != ReorderMode.Inactive || isFolderOpen
            if (isFloatingTabs && !floatingTabsCovered) {
                val animatedBottomOffset by animateDpAsState(
                    targetValue = tabFabBottomPadding,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "subscriptions_tab_fab_bottom_offset",
                )
                val latestCount = successState?.subscribedPodcasts?.count { it.latestEpisode != null } ?: 0

                SubscriptionsTabSelectorFab(
                    selectedTab = pagerState.currentPage,
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    badgeCount = latestCount,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .adaptivePlayerOverlayOffset(isMiniPlayerVisible = isPlayerActive)
                        .padding(bottom = animatedBottomOffset),
                )
            }

            // boxlore's notice sits above the bar and mini player on its own; this lifts it over the tabs and Play All too.
            CompositionLocalProvider(LocalBoxloreMiniPlayerVisible provides (LocalBoxloreMiniPlayerVisible.current || isPlayerActive)) {
                BoxloreSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    extraBottom = (if (isFloatingTabs) discoveryTabSwitcherHeight() + FLOATING_TABS_GAPS else 0.dp) + SNACKBAR_ABOVE_PLAY_ALL,
                )
            }

            if (reorderMode is ReorderMode.Folders || reorderMode is ReorderMode.RootShows) {
                SubscriptionReorderBar(
                    reorderMode = reorderMode,
                    onSave = {
                        when (reorderMode) {
                            ReorderMode.Folders -> {
                                if (tempFolderOrder.isNotEmpty()) {
                                    viewModel.reorderFolders(tempFolderOrder)
                                }
                                viewModel.setFolderSort(FolderInterSort.Manual)
                            }
                            ReorderMode.RootShows -> {
                                if (tempRootOrder.isNotEmpty()) {
                                    viewModel.reorderSubscriptions(tempRootOrder)
                                }
                                viewModel.setSubscriptionSort(SubscriptionSort.Manual)
                            }
                            else -> Unit
                        }
                        reorderMode = ReorderMode.Inactive
                        tempFolderOrder = emptyList()
                        tempRootOrder = emptyList()
                    },
                    onCancel = {
                        reorderMode = ReorderMode.Inactive
                        tempFolderOrder = emptyList()
                        tempRootOrder = emptyList()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomChromeHeight + 8.dp),
                )
            }

            // Remembered so the folder pages skip recomposing when nothing they show has changed.
            val folderPagesData = remember(
                successState?.subscribedPodcasts,
                folders,
                successState?.currentSort,
                successState?.smartOrderIds,
                intraFolderSort,
                autoOrganizeFolders,
                autoOrganizeLook,
                reorderMode,
            ) {
                FolderPagesData(
                    podcasts = successState?.subscribedPodcasts.orEmpty(),
                    folders = folders,
                    sort = successState?.currentSort,
                    smartOrderIds = successState?.smartOrderIds.orEmpty(),
                    intraFolderSort = intraFolderSort,
                    autoOrganizeOn = autoOrganizeFolders,
                    autoOrganizeLook = autoOrganizeLook,
                    reorderMode = reorderMode,
                )
            }
            val currentOnPodcastClick by rememberUpdatedState(onPodcastClick)
            val folderPagesActions = remember(viewModel) {
                object : FolderPagesActions {
                    override fun openShow(podcastId: String) = currentOnPodcastClick(podcastId)

                    override fun showMenu(target: ContextMenuTarget) {
                        screenState.contextMenu = ContextMenuKey.of(target)
                    }

                    override fun addShows(folder: SubscriptionFolder) {
                        screenState.addShowsFolderId = folder.id
                    }

                    override fun openSort() {
                        showSortSheet = true
                    }

                    override fun setReorderMode(mode: ReorderMode) {
                        reorderMode = mode
                    }

                    override fun saveFolderOrder(folderId: String, orderedIds: List<String>) {
                        viewModel.reorderFolderShows(folderId, orderedIds)
                        viewModel.setIntraFolderSort(FolderIntraSort.Manual)
                        reorderMode = ReorderMode.Inactive
                    }

                    override fun deleteFolder(folder: SubscriptionFolder) {
                        screenState.confirmDeleteFolderId = folder.id
                    }

                    override fun createFolder(params: FolderSaveParams, podcastIds: List<String>, move: FolderShowMove?) {
                        viewModel.createFolder(
                            name = params.name,
                            icon = params.icon,
                            displaySize = params.displaySize,
                            linkedGenre = params.linkedGenre,
                            showPodcastGrid = params.showPodcastGrid,
                            podcastIds = podcastIds,
                        )
                        if (move != null && move.podcastId in podcastIds) {
                            viewModel.removePodcastFromFolder(move.podcastId, move.fromFolderId)
                        }
                    }

                    override fun updateFolder(folder: SubscriptionFolder) = viewModel.updateFolder(folder)

                    override fun changeMembership(podcastId: String, change: FolderMembershipChange) {
                        change.addTo.forEach { folderId -> viewModel.addPodcastToFolder(podcastId, folderId) }
                        change.removeFrom.forEach { folderId -> viewModel.removePodcastFromFolder(podcastId, folderId) }
                    }

                    override fun turnOnAutoOrganize(look: AutoOrganizeLook) = viewModel.setAutoOrganizeFolders(
                        enabled = true,
                        displaySize = look.fixedSize,
                        showPodcastGrid = look.tileShowsCovers,
                    )

                    override fun turnOffAutoOrganize() {
                        confirmAutoOrganizeOff = true
                    }

                    override fun setAutoOrganizeLook(look: AutoOrganizeLook) = viewModel.setAutoOrganizeLook(look)

                    override fun applyLookToGenreFolders(look: AutoOrganizeLook) = viewModel.applyAutoOrganizeLookToGenreFolders(look)
                }
            }
            OpenFolderPage(folderPages, folderPagesData, folderPagesActions, bottomPadding = bottomChromeHeight)
            FolderEditorPage(folderPages, folderPagesData, folderPagesActions)
            AddToFoldersHost(folderPages, folderPagesData, folderPagesActions)

            val currentContextMenu = contextMenuTarget
            if (currentContextMenu != null) {
                SubscriptionContextMenuSheet(
                    target = currentContextMenu,
                    actions = SubscriptionContextMenuActions(
                        onEditFolder = folderPages::openEdit,
                        onAddShowsToFolder = { folder ->
                            screenState.addShowsFolderId = folder.id
                        },
                        onReorderFolders = {
                            folderPages.closeAll()
                            tempFolderOrder = if (folderSort == FolderInterSort.Manual && folderManualOrder.isNotEmpty()) {
                                folderManualOrder
                            } else {
                                folders.map { it.id }
                            }
                            reorderMode = ReorderMode.Folders
                        },
                        onDeleteFolder = { folder ->
                            screenState.confirmDeleteFolderId = folder.id
                        },
                        onRemoveFromFolder = { folder, podcast ->
                            viewModel.removePodcastFromFolder(podcast.id, folder.id)
                        },
                        onMoveToAnotherFolder = { folder, podcast ->
                            // A show outside folders can join several; one in a folder moves.
                            if (folder == null) folderPages.addToFoldersPodcastId = podcast.id else screenState.moveShow = MoveShowKey(folder.id, podcast.id)
                        },
                        onReorderFolderShows = { folder ->
                            reorderMode = ReorderMode.FolderShows(folder.id, folder.name)
                        },
                        onUnsubscribePodcast = { podcast ->
                            screenState.confirmUnsubscribeId = podcast.id
                        },
                        onTogglePin = { podcast ->
                            coroutineScope.launch {
                                if (!viewModel.toggleHomePin(podcast.id)) {
                                    snackbarHostState.showSnackbar(
                                        pinCapacityMessage,
                                    )
                                }
                            }
                        },
                        onReorderRootShows = {
                            val unfiledPodcasts = (uiState as? LibraryUiState.Success)?.subscribedPodcasts.orEmpty()
                                .filter { pod -> folders.none { folder -> pod.id in folder.podcastIds } }
                            val manualOrder = (uiState as? LibraryUiState.Success)?.manualOrder.orEmpty()
                            val currentSort = (uiState as? LibraryUiState.Success)?.currentSort
                            tempRootOrder = if (currentSort == SubscriptionSort.Manual && manualOrder.isNotEmpty()) {
                                val unfiledIds = unfiledPodcasts.map { it.id }.toSet()
                                val ordered = manualOrder.filter { it in unfiledIds }
                                val remainder = unfiledPodcasts.map { it.id }.filter { it !in manualOrder }
                                ordered + remainder
                            } else {
                                unfiledPodcasts.map { it.id }
                            }
                            reorderMode = ReorderMode.RootShows
                        },
                    ),
                    onDismissRequest = { screenState.contextMenu = null },
                    pinnedPodcastIds = pinnedPodcastIds,
                )
            }

            val folderForSelection = selectedFolderForShowSelection
            if (folderForSelection != null && successState != null) {
                FolderShowsSelectionSheet(
                    folder = folderForSelection,
                    allSubscribedPodcasts = successState.subscribedPodcasts,
                    onSave = { selectedIds ->
                        viewModel.setFolderShows(folderForSelection.id, selectedIds)
                        screenState.addShowsFolderId = null
                    },
                    onDismissRequest = { screenState.addShowsFolderId = null },
                )
            }

            val moveTarget = moveShowTarget
            if (moveTarget != null) {
                MoveToFolderSheet(
                    podcast = moveTarget.second,
                    currentFolderId = moveTarget.first?.id,
                    availableFolders = folders,
                    onSelectFolder = { targetFolderId ->
                        viewModel.movePodcastToFolder(
                            podcastId = moveTarget.second.id,
                            fromFolderId = moveTarget.first?.id,
                            toFolderId = targetFolderId,
                        )
                        screenState.moveShow = null
                    },
                    onCreateNewFolder = { podcast ->
                        folderPages.openNewFolder(withShowId = podcast.id, movesFrom = moveTarget.first?.id)
                        screenState.moveShow = null
                    },
                    onDismissRequest = { screenState.moveShow = null },
                )
            }

            val folderToDelete = confirmDeleteFolder
            if (folderToDelete != null) {
                TrackScreenView("delete_folder_confirmation")
                BoxloreAlertDialog(
                    onDismissRequest = { screenState.confirmDeleteFolderId = null },
                    title = { Text(stringResource(R.string.library_folder_delete_title)) },
                    text = {
                        Text(
                            stringResource(R.string.library_folder_delete_body, folderToDelete.name),
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.deleteFolder(folderToDelete.id)
                                folderPages.onFolderDeleted(folderToDelete.id)
                                screenState.confirmDeleteFolderId = null
                            },
                        ) {
                            Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { screenState.confirmDeleteFolderId = null },
                        ) {
                            Text(stringResource(R.string.library_cancel))
                        }
                    },
                )
            }

            val podcastToUnsubscribe = confirmUnsubscribePodcast
            if (podcastToUnsubscribe != null) {
                TrackScreenView("unsubscribe_confirmation")
                BoxloreAlertDialog(
                    onDismissRequest = { screenState.confirmUnsubscribeId = null },
                    title = { Text(stringResource(R.string.library_unsubscribe_title)) },
                    text = {
                        Text(
                            stringResource(R.string.library_unsubscribe_body, podcastToUnsubscribe.title),
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.unsubscribe(podcastToUnsubscribe)
                                screenState.confirmUnsubscribeId = null
                            },
                        ) {
                            Text(stringResource(R.string.library_unsubscribe), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { screenState.confirmUnsubscribeId = null },
                        ) {
                            Text(stringResource(R.string.library_cancel))
                        }
                    },
                )
            }

            if (confirmAutoOrganizeOff) {
                AutoOrganizeDisableDialog(
                    onProceed = {
                        viewModel.setAutoOrganizeFolders(enabled = false)
                        confirmAutoOrganizeOff = false
                    },
                    onCancel = { confirmAutoOrganizeOff = false },
                )
            }

            if (showSortSheet && successState != null && pagerState.currentPage != 0) {
                LatestSortSheet(
                    useSmartRank = useSmartRank,
                    hideCompleted = hideCompletedInSubs,
                    actions = LatestSortActions(
                        onUseSmartRankChange = { useSmart ->
                            viewModel.setUseSmartRank(useSmart)
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged(
                                if (useSmart) "smart_sort" else "chronological",
                                "latest",
                            )
                        },
                        onHideCompletedChange = viewModel::setHideCompletedInSubs,
                        onDismiss = { showSortSheet = false },
                    ),
                )
            } else if (showSortSheet && successState != null) {
                SubscriptionSortSheet(
                    config = SubscriptionSortConfig(
                        currentSort = successState.currentSort,
                        folderSort = folderSort,
                        intraFolderSort = intraFolderSort,
                        autoOrganizeFolders = autoOrganizeFolders,
                        genreFolderCount = genreFolders(folders).size,
                    ),
                    actions = SubscriptionSortActions(
                        onSortChange = { sort ->
                            viewModel.setSubscriptionSort(sort)
                            val analyticsName = when (sort) {
                                SubscriptionSort.SmartRank -> "smart_sort"
                                SubscriptionSort.RecentlyUpdated -> "recently_updated"
                                SubscriptionSort.Alphabetical -> "alphabetical"
                                SubscriptionSort.MostListened -> "most_listened"
                                SubscriptionSort.Manual -> "manual"
                            }
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged(
                                analyticsName,
                                "shows",
                            )
                        },
                        onFolderSortChange = viewModel::setFolderSort,
                        onIntraFolderSortChange = viewModel::setIntraFolderSort,
                        onOpenAutoOrganize = {
                            showSortSheet = false
                            folderPages.openAutoOrganize()
                        },
                        onDismiss = { showSortSheet = false },
                    ),
                )
            }
        }
    }
}

/** Room between the Undo message and the Play All button below it. */
private val SNACKBAR_ABOVE_PLAY_ALL = 72.dp

/** The floating tabs' gaps: 16dp above the bar and mini player, 12dp under Play All. */
private val FLOATING_TABS_GAPS = 28.dp

/**
 * Runs a New Episodes swipe and shows its message. Queueing is saved at once and Undo takes it
 * back out. Played is held while the message shows, because saving it clears the listening
 * position; it is saved however the message closes (timeout, a newer message, leaving the
 * screen) unless the listener taps Undo.
 */
private suspend fun handleLatestSwipe(
    viewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState,
    context: Context,
    action: LatestSwipeAction,
    episode: Episode,
    podcast: Podcast,
) {
    snackbarHostState.currentSnackbarData?.dismiss()
    when (action) {
        LatestSwipeAction.AddToQueue -> {
            val outcome = viewModel.addLatestToQueue(episode, podcast)
            val result = snackbarHostState.showSnackbar(
                message = context.getString(latestQueueMessage(outcome)),
                actionLabel = if (outcome == LatestQueueOutcome.Added) context.getString(R.string.library_undo) else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoAddLatestToQueue(episode.id)
        }
        LatestSwipeAction.MarkPlayed -> {
            viewModel.holdLatestAsPlayed(episode.id)
            var undone = false
            try {
                undone = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.library_marked_as_played),
                    actionLabel = context.getString(R.string.library_undo),
                    duration = SnackbarDuration.Short,
                ) == SnackbarResult.ActionPerformed
            } finally {
                if (undone) viewModel.undoLatestPlayed(episode.id) else viewModel.commitLatestPlayed(episode, podcast)
            }
        }
    }
}

/**
 * Lays the header out [state]'s scroll away: it shrinks from the top as the list scrolls up and
 * comes back on the way down. Used only in short windows, where the bar and tabs would leave too
 * little room for the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.collapseWith(state: TopAppBarState, start: ShortHeaderStart): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val limit = -placeable.height.toFloat()
    if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
    if (start.tucked) {
        // Once the header's height is known: start fully tucked away.
        state.heightOffset = limit
        start.tucked = false
    }
    val offset = state.heightOffset.roundToInt().coerceIn(-placeable.height, 0)
    layout(placeable.width, placeable.height + offset) { placeable.place(0, offset) }
}

/** Whether a short window's header still has to start tucked away; read once, when its height is first known. */
private class ShortHeaderStart(var tucked: Boolean)

/** Test tag for Subscriptions' docked Shows / New Episodes switcher. */
internal const val SUBSCRIPTIONS_TAB_SWITCHER_TAG = "subscriptions_tab_switcher"
