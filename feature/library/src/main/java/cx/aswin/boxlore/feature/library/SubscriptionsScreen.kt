package cx.aswin.boxlore.feature.library

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.adaptivePlayerOverlayOffset
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.component.navigationStyleUsesExternalSystemNavigationInset
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizeDisableDialog
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizeEnableDialog
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuTarget
import cx.aswin.boxlore.feature.library.subscriptions.ExpressiveTabSwitcher
import cx.aswin.boxlore.feature.library.subscriptions.FolderDialogActions
import cx.aswin.boxlore.feature.library.subscriptions.FolderInterSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderIntraSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderShowsSelectionSheet
import cx.aswin.boxlore.feature.library.subscriptions.LatestSortMenuItems
import cx.aswin.boxlore.feature.library.subscriptions.LatestTabActions
import cx.aswin.boxlore.feature.library.subscriptions.LatestTabConfig
import cx.aswin.boxlore.feature.library.subscriptions.LatestTabContent
import cx.aswin.boxlore.feature.library.subscriptions.MoveToFolderSheet
import cx.aswin.boxlore.feature.library.subscriptions.ReorderMode
import cx.aswin.boxlore.feature.library.subscriptions.ShowsTabActions
import cx.aswin.boxlore.feature.library.subscriptions.ShowsTabConfig
import cx.aswin.boxlore.feature.library.subscriptions.ShowsTabContent
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionContextMenuActions
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionContextMenuSheet
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionFolderDialog
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionReorderBar
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortActions
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortConfig
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortSheet
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionsTabSelectorFab
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionsTabSelectorFabHeight
import cx.aswin.boxlore.feature.library.subscriptions.extractDistinctGenres
import cx.aswin.boxlore.feature.library.subscriptions.resolveSortedFolderShows
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
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        var searchQuery by remember { mutableStateOf("") }
        var isSearchActive by remember { mutableStateOf(false) }
        var isGridView by rememberSaveable { mutableStateOf(true) }
        val useSmartRank by viewModel.useSmartRank.collectAsStateWithLifecycle()
        val hideCompletedInSubs by viewModel.hideCompletedInSubs.collectAsStateWithLifecycle()
        val pinnedPodcastIds by viewModel.pinnedPodcastIds.collectAsStateWithLifecycle()
        val autoOrganizeFolders by viewModel.autoOrganizeFolders.collectAsStateWithLifecycle()
        val folderSort by viewModel.folderSort.collectAsStateWithLifecycle()
        val folderManualOrder by viewModel.folderManualOrder.collectAsStateWithLifecycle()
        val intraFolderSort by viewModel.intraFolderSort.collectAsStateWithLifecycle()
        val folders by viewModel.folders.collectAsStateWithLifecycle()
        val subscriptionsTabStyle by viewModel.subscriptionsTabStyle.collectAsStateWithLifecycle()
        var showSortMenu by remember { mutableStateOf(false) }
        var showSortSheet by rememberSaveable { mutableStateOf(false) }
        var showFolderEditSheet by remember { mutableStateOf(false) }
        var editingFolder by remember { mutableStateOf<cx.aswin.boxlore.core.model.SubscriptionFolder?>(null) }
        var activeFolderId by rememberSaveable { mutableStateOf<String?>(null) }
        var contextMenuTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
        var reorderMode by remember { mutableStateOf<ReorderMode>(ReorderMode.Inactive) }
        var selectedFolderForShowSelection by remember { mutableStateOf<SubscriptionFolder?>(null) }
        var moveShowTarget by remember { mutableStateOf<Pair<SubscriptionFolder?, Podcast>?>(null) }
        var confirmDeleteFolder by remember { mutableStateOf<SubscriptionFolder?>(null) }
        var confirmUnsubscribePodcast by remember { mutableStateOf<Podcast?>(null) }
        var tempFolderOrder by remember { mutableStateOf<List<String>>(emptyList()) }
        var tempRootOrder by remember { mutableStateOf<List<String>>(emptyList()) }
        var pendingAutoOrganizeTarget by remember { mutableStateOf<Boolean?>(null) }

        val isFloatingTabs = subscriptionsTabStyle == SubscriptionsTabStyle.FLOATING

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
            tabFabBottomPadding + SubscriptionsTabSelectorFabHeight + 12.dp
        } else {
            null
        }

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

        BackHandler(enabled = activeFolderId != null) {
            activeFolderId = null
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

        LaunchedEffect(Unit) {
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
        val suggestedGenres = remember(successState?.subscribedPodcasts) {
            extractDistinctGenres(successState?.subscribedPodcasts.orEmpty())
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBgColor)
                    ) {
                        TopAppBar(
                            title = {
                                if (isSearchActive) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        placeholder = { Text("Search shows...") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent
                                        ),
                                        singleLine = true
                                    )
                                } else {
                                    Text(
                                        text = "Subscriptions",
                                        fontWeight = GoogleSansWeight.bold
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            ),
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (isSearchActive) {
                                        isSearchActive = false
                                        searchQuery = ""
                                    } else {
                                        onBack()
                                    }
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = if (isSearchActive) "Close Search" else "Back"
                                    )
                                }
                            },
                            actions = {
                                if (isSearchActive) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                        }
                                    }
                                } else {
                                    if (hasSubscribedPodcasts) {
                                        IconButton(onClick = {
                                            isGridView = !isGridView
                                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsLayoutToggled(isGridView)
                                        }) {
                                            Icon(
                                                imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                                contentDescription = if (isGridView) "List View" else "Grid View"
                                            )
                                        }
                                    }
                                    if (hasSubscribedPodcasts) {
                                        Box {
                                            IconButton(
                                                onClick = {
                                                    if (pagerState.currentPage == 0) {
                                                        showSortSheet = true
                                                    } else {
                                                        showSortMenu = true
                                                    }
                                                },
                                            ) {
                                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                                            }
                                            if (pagerState.currentPage != 0) {
                                                DropdownMenu(
                                                    expanded = showSortMenu,
                                                    onDismissRequest = { showSortMenu = false },
                                                    shape = RoundedCornerShape(20.dp),
                                                    offset = DpOffset(x = (-12).dp, y = 4.dp),
                                                ) {
                                                    LatestSortMenuItems(
                                                        useSmartRank = useSmartRank,
                                                        onUseSmartRankChange = { useSmart ->
                                                            viewModel.setUseSmartRank(useSmart)
                                                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged(
                                                                if (useSmart) "smart_sort" else "chronological",
                                                                "latest",
                                                            )
                                                        },
                                                        hideCompleted = hideCompletedInSubs,
                                                        onHideCompletedChange = viewModel::setHideCompletedInSubs,
                                                        onDismiss = { showSortMenu = false },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                                    }
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )

                        val latestCount = successState?.subscribedPodcasts?.count { it.latestEpisode != null } ?: 0

                        if (!isFloatingTabs) {
                            ExpressiveTabSwitcher(
                                tabs = listOf("Shows", "New Episodes"),
                                selectedIndex = pagerState.currentPage,
                                badge = if (latestCount > 0) mapOf(1 to latestCount) else emptyMap(),
                                onTabSelected = { index ->
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).imePadding()) {
                    when (uiState) {
                        is LibraryUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is LibraryUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Error loading subscriptions")
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
                                        ),
                                        actions = ShowsTabActions(
                                            onExploreClick = onExploreClick,
                                            onPodcastClick = {
                                                viewModel.subPodcastsClickedCount++
                                                onPodcastClick(it)
                                            },
                                            onPodcastLongClick = { podcast ->
                                                contextMenuTarget = ContextMenuTarget.RootPodcast(podcast)
                                            },
                                            onReorder = { newOrder ->
                                                tempRootOrder = newOrder
                                            },
                                            onReorderFolders = { newOrder ->
                                                tempFolderOrder = newOrder
                                            },
                                            onNewFolderClick = {
                                                editingFolder = null
                                                showFolderEditSheet = true
                                            },
                                            onFolderClick = { folderId ->
                                                activeFolderId = folderId
                                            },
                                            onFolderLongClick = { folder ->
                                                contextMenuTarget = ContextMenuTarget.Folder(folder)
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
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isFloatingTabs && !isSearchActive && reorderMode == ReorderMode.Inactive) {
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

            if (showFolderEditSheet || editingFolder != null) {
                FolderEditSheet(
                    initialFolder = editingFolder,
                    suggestedGenres = suggestedGenres,
                    isAutoOrganizeEnabled = autoOrganizeFolders,
                    onAutoOrganizeChange = { target ->
                        showFolderEditSheet = false
                        editingFolder = null
                        pendingAutoOrganizeTarget = target
                    },
                    onDismissRequest = {
                        showFolderEditSheet = false
                        editingFolder = null
                        moveShowTarget = null
                    },
                    onSave = { name, icon, displaySize, linkedGenre, showPodcastGrid ->
                        val target = editingFolder
                        if (target != null) {
                            viewModel.updateFolder(
                                target.copy(
                                    name = name,
                                    icon = icon,
                                    displaySize = displaySize,
                                    linkedGenre = linkedGenre,
                                    showPodcastGrid = showPodcastGrid,
                                ),
                            )
                        } else {
                            val targetMove = moveShowTarget
                            if (targetMove != null) {
                                viewModel.createFolderAndMovePodcast(
                                    name = name,
                                    icon = icon,
                                    displaySize = displaySize,
                                    linkedGenre = linkedGenre,
                                    showPodcastGrid = showPodcastGrid,
                                    podcastId = targetMove.second.id,
                                    fromFolderId = targetMove.first?.id,
                                )
                                moveShowTarget = null
                            } else {
                                viewModel.createFolder(
                                    name = name,
                                    icon = icon,
                                    displaySize = displaySize,
                                    linkedGenre = linkedGenre,
                                    showPodcastGrid = showPodcastGrid,
                                )
                            }
                        }
                        showFolderEditSheet = false
                        editingFolder = null
                    },
                    onDelete = editingFolder?.let { folder ->
                        {
                            viewModel.deleteFolder(folder.id)
                            showFolderEditSheet = false
                            editingFolder = null
                        }
                    },
                )
            }

            val activeFolder = folders.find { it.id == activeFolderId }
            if (activeFolder != null) {
                val allPodcasts = successState?.subscribedPodcasts.orEmpty()
                val activeFolderShows = remember(
                    activeFolder,
                    allPodcasts,
                    intraFolderSort,
                    successState?.currentSort,
                    successState?.smartOrderIds,
                ) {
                    resolveSortedFolderShows(
                        folder = activeFolder,
                        podcasts = allPodcasts,
                        intraFolderSort = intraFolderSort,
                        sort = successState?.currentSort,
                        smartOrderIds = successState?.smartOrderIds.orEmpty(),
                    )
                }
                SubscriptionFolderDialog(
                    folder = activeFolder,
                    podcasts = activeFolderShows,
                    actions = FolderDialogActions(
                        onDismissRequest = { activeFolderId = null },
                        onPodcastClick = { podcastId ->
                            activeFolderId = null
                            onPodcastClick(podcastId)
                        },
                        onEditFolder = {
                            editingFolder = activeFolder
                            activeFolderId = null
                        },
                        onPodcastLongClick = { podcast ->
                            contextMenuTarget = ContextMenuTarget.FolderPodcast(activeFolder, podcast)
                        },
                        onSaveReorder = { orderedIds ->
                            viewModel.reorderFolderShows(activeFolder.id, orderedIds)
                            viewModel.setIntraFolderSort(FolderIntraSort.Manual)
                            reorderMode = ReorderMode.Inactive
                        },
                        onCancelReorder = {
                            reorderMode = ReorderMode.Inactive
                        },
                    ),
                    isReorderMode = reorderMode is ReorderMode.FolderShows &&
                        (reorderMode as ReorderMode.FolderShows).folderId == activeFolder.id,
                )
            }

            val currentContextMenu = contextMenuTarget
            if (currentContextMenu != null) {
                SubscriptionContextMenuSheet(
                    target = currentContextMenu,
                    actions = SubscriptionContextMenuActions(
                        onEditFolder = { folder ->
                            editingFolder = folder
                            showFolderEditSheet = true
                        },
                        onAddShowsToFolder = { folder ->
                            selectedFolderForShowSelection = folder
                        },
                        onReorderFolders = {
                            tempFolderOrder = if (folderSort == FolderInterSort.Manual && folderManualOrder.isNotEmpty()) {
                                folderManualOrder
                            } else {
                                folders.map { it.id }
                            }
                            reorderMode = ReorderMode.Folders
                        },
                        onDeleteFolder = { folder ->
                            confirmDeleteFolder = folder
                        },
                        onRemoveFromFolder = { folder, podcast ->
                            viewModel.removePodcastFromFolder(podcast.id, folder.id)
                        },
                        onMoveToAnotherFolder = { folder, podcast ->
                            moveShowTarget = folder to podcast
                        },
                        onReorderFolderShows = { folder ->
                            reorderMode = ReorderMode.FolderShows(folder.id, folder.name)
                        },
                        onUnsubscribePodcast = { podcast ->
                            confirmUnsubscribePodcast = podcast
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
                    onDismissRequest = { contextMenuTarget = null },
                )
            }

            val folderForSelection = selectedFolderForShowSelection
            if (folderForSelection != null && successState != null) {
                FolderShowsSelectionSheet(
                    folder = folderForSelection,
                    allSubscribedPodcasts = successState.subscribedPodcasts,
                    onSave = { selectedIds ->
                        viewModel.setFolderShows(folderForSelection.id, selectedIds)
                        selectedFolderForShowSelection = null
                    },
                    onDismissRequest = { selectedFolderForShowSelection = null },
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
                        moveShowTarget = null
                    },
                    onCreateNewFolder = {
                        editingFolder = null
                        showFolderEditSheet = true
                    },
                    onDismissRequest = { moveShowTarget = null },
                )
            }

            val folderToDelete = confirmDeleteFolder
            if (folderToDelete != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmDeleteFolder = null },
                    title = { Text("Delete folder?") },
                    text = {
                        Text(
                            "Shows in '${folderToDelete.name}' will stay in your library and won't be deleted or unsubscribed.",
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.deleteFolder(folderToDelete.id)
                                if (activeFolderId == folderToDelete.id) {
                                    activeFolderId = null
                                }
                                confirmDeleteFolder = null
                            },
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { confirmDeleteFolder = null },
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }

            val podcastToUnsubscribe = confirmUnsubscribePodcast
            if (podcastToUnsubscribe != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmUnsubscribePodcast = null },
                    title = { Text("Unsubscribe?") },
                    text = {
                        Text(
                            "Are you sure you want to unsubscribe from '${podcastToUnsubscribe.title}'? It will be removed from your subscriptions and folders.",
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.unsubscribe(podcastToUnsubscribe)
                                confirmUnsubscribePodcast = null
                            },
                        ) {
                            Text("Unsubscribe", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { confirmUnsubscribePodcast = null },
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }

            if (pendingAutoOrganizeTarget == true) {
                AutoOrganizeEnableDialog(
                    onProceed = { displaySize, showPodcastGrid ->
                        viewModel.setAutoOrganizeFolders(
                            enabled = true,
                            displaySize = displaySize,
                            showPodcastGrid = showPodcastGrid,
                        )
                        pendingAutoOrganizeTarget = null
                    },
                    onCancel = {
                        pendingAutoOrganizeTarget = null
                    },
                )
            } else if (pendingAutoOrganizeTarget == false) {
                AutoOrganizeDisableDialog(
                    onProceed = {
                        viewModel.setAutoOrganizeFolders(enabled = false)
                        pendingAutoOrganizeTarget = null
                    },
                    onCancel = {
                        pendingAutoOrganizeTarget = null
                    },
                )
            }

            if (showSortSheet && successState != null) {
                SubscriptionSortSheet(
                    config = SubscriptionSortConfig(
                        currentSort = successState.currentSort,
                        folderSort = folderSort,
                        intraFolderSort = intraFolderSort,
                        autoOrganizeFolders = autoOrganizeFolders,
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
                        onAutoOrganizeFoldersChange = { target ->
                            pendingAutoOrganizeTarget = target
                        },
                        onDismiss = { showSortSheet = false },
                    ),
                )
            }
        }
    }
}
