package cx.aswin.boxlore.feature.info

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.component.adaptivePlayerOverlayOffset
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.branded.LocalBrandedHeaderMotion
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
import cx.aswin.boxlore.feature.info.branded.brandedShowPageFor
import cx.aswin.boxlore.feature.info.branded.brandedSpotlightEpisode
import cx.aswin.boxlore.feature.info.branded.feedItemsWithoutSpotlight
import cx.aswin.boxlore.feature.info.components.EpisodeListIndicators
import cx.aswin.boxlore.feature.info.components.EpisodeSelectionToolbar
import cx.aswin.boxlore.feature.info.components.EpisodeSelectionToolbarActions
import cx.aswin.boxlore.feature.info.components.EpisodeSelectionToolbarState
import cx.aswin.boxlore.feature.info.components.InfoArtworkBackground
import cx.aswin.boxlore.feature.info.components.MissingEpisodesChip
import cx.aswin.boxlore.feature.info.components.MissingEpisodesConfirmDialog
import cx.aswin.boxlore.feature.info.components.PodcastGenreEditSheet
import cx.aswin.boxlore.feature.info.components.PodcastInfoJumpPillOverlay
import cx.aswin.boxlore.feature.info.components.PodcastInfoLoadError
import cx.aswin.boxlore.feature.info.components.PodcastInfoMarkDialogs
import cx.aswin.boxlore.feature.info.components.PodcastInfoTopOverlay
import cx.aswin.boxlore.feature.info.components.PodcastInfoTopOverlayActions
import cx.aswin.boxlore.feature.info.components.ShowPageSkeleton
import cx.aswin.boxlore.feature.info.components.areAppNotificationsEnabled
import cx.aswin.boxlore.feature.info.components.handleAutoDownloadToggle
import cx.aswin.boxlore.feature.info.components.handleNotificationsToggle
import cx.aswin.boxlore.feature.info.components.handleToolbarWarningAction
import cx.aswin.boxlore.feature.info.logic.EpisodeSelectionRange
import cx.aswin.boxlore.feature.info.logic.FeedItem
import cx.aswin.boxlore.feature.info.logic.PodcastEpisodeSelectionLogic
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import cx.aswin.boxlore.feature.info.logic.groupEpisodes
import cx.aswin.boxlore.feature.info.logic.podcastSearchDisplayEpisodes
import cx.aswin.boxlore.feature.info.logic.resolveAutoScrollTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PodcastInfoScreen(
    podcastId: String,
    viewModel: PodcastInfoViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (Episode, String, Int?) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onPodcastClick: (String) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val pageShow by viewModel.pageShow.collectAsState()
    // The route's show, or the show this route resolved to (a linked feed, a renumbered show); never a previous page's.
    val artworkPodcast = cx.aswin.boxlore.feature.info.logic.RenumberedShowPageLogic.artworkPodcast(uiState, podcastId, pageShow)
    val brandedShowPage = remember(artworkPodcast) { brandedShowPageFor(artworkPodcast) }
    // podcast_detail_viewed goes out once the show loads; a Showcase page also says it was one.
    TrackScreenView(if (brandedShowPage != null) "podcast_showcase" else "podcast_detail", podcastId) { previous ->
        if (brandedShowPage != null) {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackScreenViewed("podcast_showcase", previous, mapOf("podcast_id" to podcastId))
        }
    }
    cx.aswin.boxlore.feature.info.components.ArtworkInfoTheme(
        listOf(artworkPodcast?.let { it.imageUrl.takeIf(String::isNotBlank) ?: it.fallbackImageUrl }),
        isLoading = uiState is PodcastInfoUiState.Loading,
        branded = brandedShowPage,
    ) {
    val brandedPage = LocalBrandedPage.current?.page
    val headerMotion = LocalBrandedHeaderMotion.current
    LaunchedEffect(headerMotion, viewModel) {
        // Only this show's episodes are ever marked playing here.
        headerMotion ?: return@LaunchedEffect
        viewModel.episodePlaybackState.map { states -> states.values.any { it.isPlaying } }.distinctUntilChanged().collect { headerMotion.showPlaying = it }
    }
    val queuedEpisodeIds by viewModel.queuedEpisodeIds.collectAsState()
    val downloadedEpisodeIds by viewModel.downloadedEpisodeIds.collectAsState()
    val downloadingEpisodeIds by viewModel.downloadingEpisodeIds.collectAsState()
    val episodePendingDownloadRemoval by viewModel.episodePendingDownloadRemoval.collectAsState()
    val hideCompleted by viewModel.hideCompletedInShowDetails.collectAsState()
    val isPinnedToHome by viewModel.isPinnedToHome.collectAsState()
    val globalSkipBeginningMs by viewModel.globalSkipBeginningMs.collectAsState()
    val globalSkipEndingMs by viewModel.globalSkipEndingMs.collectAsState()
    val backgroundChecksEnabled by viewModel.autoDownloadBackgroundChecksEnabled.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val isMiniPlayerVisible = bottomContentPadding > appBottomChromeContentPadding(isMiniPlayerVisible = false)

    // Search State. Open search survives activity recreation (fold density changes, process death)
    // and a return from the back stack. The live query lives in the ViewModel; a saved copy runs the
    // search again when a new ViewModel (process death) comes back with an empty query.
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var savedSearchQuery by rememberSaveable { mutableStateOf("") }
    var searchQueryRestorePending by remember { mutableStateOf(isSearchActive && savedSearchQuery.isNotBlank()) }
    // Not saved: a recreated page restores open search without jumping the list or reopening the keyboard.
    var searchOpenRequest by remember { mutableIntStateOf(0) }
    val closeSearch = {
        focusManager.clearFocus()
        isSearchActive = false
        savedSearchQuery = ""
        searchQueryRestorePending = false
        viewModel.searchEpisodes("")
    }
    LaunchedEffect(searchOpenRequest) {
        if (searchOpenRequest > 0) {
            listState.animateScrollToItem(1)
            searchFocusRequester.requestFocus()
        }
    }
    var toolbarWarning by remember { mutableStateOf(ToolbarWarning.NONE) }
    LaunchedEffect(backgroundChecksEnabled) {
        if (backgroundChecksEnabled && toolbarWarning == ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY) {
            toolbarWarning = ToolbarWarning.NONE
        }
    }
    // Open dialogs and sheets survive activity recreation as well as live window changes.
    var showMarkAllPlayedDialog by rememberSaveable { mutableStateOf(false) }
    var showMarkAllUnplayedDialog by rememberSaveable { mutableStateOf(false) }
    var showPodcastPlaybackSettings by rememberSaveable { mutableStateOf(false) }
    var showPodcastGenreEdit by rememberSaveable { mutableStateOf(false) }
    var showMissingEpisodesConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingRssNotificationAction by remember(podcastId) { mutableStateOf<(() -> Unit)?>(null) }
    // Selection is saved as episode IDs. The episode pool cannot be saved, so a recreated page
    // looks its selected episodes up again (see the selection restore below).
    var selectedEpisodeIds by rememberSaveable(podcastId, stateSaver = EpisodeIdListSaver) { mutableStateOf(emptyList<String>()) }
    var selectionAnchorEpisodeId by rememberSaveable(podcastId) { mutableStateOf<String?>(null) }
    // The sort the selection was made in: a reloaded page can open in another order.
    var selectionSortName by rememberSaveable(podcastId) { mutableStateOf<String?>(null) }
    var selectionEpisodePool by remember(podcastId) { mutableStateOf(emptyList<Episode>()) }
    var isLoadingFullSelection by remember(podcastId) { mutableStateOf(false) }
    var selectionRequestGeneration by remember(podcastId) { mutableStateOf(0) }
    var selectionRestorePending by remember(podcastId) { mutableStateOf(selectedEpisodeIds.isNotEmpty()) }
    var isRestoringSelection by remember(podcastId) { mutableStateOf(false) }
    // The page state a lookup failed on; the restore tries again once the page state changes.
    var selectionRestoreFailedOn by remember(podcastId) { mutableStateOf<PodcastInfoUiState?>(null) }
    val selectedEpisodeIdSet = selectedEpisodeIds.toSet()
    val selectionActive = selectedEpisodeIds.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermission = rememberShowNotificationPermission(
        podcastId = podcastId,
        onAllowed = { granted -> if (granted || toolbarWarning == ToolbarWarning.SYSTEM_PERMISSION_BLOCKED) toolbarWarning = ToolbarWarning.NONE },
        onDenied = { toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED },
    )

    // 10-second auto-dismiss for toolbar warning banner
    LaunchedEffect(toolbarWarning) {
        if (toolbarWarning != ToolbarWarning.NONE) {
            delay(10000L)
            toolbarWarning = ToolbarWarning.NONE
        }
    }

    val toggleNotifications: (Boolean, String?) -> Unit = { disclosureAccepted, disclosedFeedUrl ->
        val state = uiState as? PodcastInfoUiState.Success
        if (state != null) {
            handleNotificationsToggle(
                context = context,
                podcastNotificationsEnabled = state.podcast.notificationsEnabled,
                isWarningVisible = toolbarWarning == ToolbarWarning.SYSTEM_PERMISSION_BLOCKED,
                onRequestPermission = {
                    notificationPermission.pendingAction = { viewModel.toggleNotifications(disclosureAccepted, disclosedFeedUrl) }
                    notificationPermission.request()
                },
                onShowPermissionBlockedWarning = {
                    if (!state.podcast.notificationsEnabled) notificationPermission.pendingAction = { viewModel.toggleNotifications(disclosureAccepted, disclosedFeedUrl) }
                    toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED
                },
                onToggleNotifications = { viewModel.toggleNotifications(disclosureAccepted, disclosedFeedUrl) },
            )
        }
    }
    if (pendingRssNotificationAction != null) {
        cx.aswin.boxlore.feature.info.components.RssNotificationDisclosureDialog(
            onConfirm = {
                val action = pendingRssNotificationAction
                pendingRssNotificationAction = null
                action?.invoke()
            },
            onDismiss = { pendingRssNotificationAction = null },
        )
    }

    // Use theme primary color (no dynamic extraction)
    val accentColor = MaterialTheme.colorScheme.primary

    // Handle Back Press for Search
    BackHandler(enabled = isSearchActive) {
        closeSearch()
    }
    val clearEpisodeSelection = {
        selectionRequestGeneration += 1
        selectedEpisodeIds = emptyList()
        selectionAnchorEpisodeId = null
        selectionSortName = null
        selectionEpisodePool = emptyList()
        isLoadingFullSelection = false
        selectionRestorePending = false
        selectionRestoreFailedOn = null
    }
    BackHandler(enabled = selectionActive) { clearEpisodeSelection() }

    TrackScreenSession(
        onSessionResume = viewModel::onScreenResume,
        onSessionExit = viewModel::trackScreenExit,
    )

    LaunchedEffect(podcastId) {
        viewModel.loadPodcast(podcastId)
    }

    var autoScrolledEpisodeId by remember(podcastId) { mutableStateOf<String?>(null) }
    var targetJumpIndex by remember(podcastId) { mutableStateOf(-1) }
    var targetJumpEpisode by remember(podcastId) { mutableStateOf<Episode?>(null) }
    var isTargetOngoing by remember(podcastId) { mutableStateOf(false) }
    val completedEpisodeIds by viewModel.completedEpisodesState.collectAsState()

    // Scroll-linked header and floating title, like Episode Info.
    val titleMotion = rememberShowPageTitleMotion(listState)

    // State for options sheet

    // Liked episodes state
    val likedEpisodeIds by viewModel.likedEpisodesState.collectAsState()

    // Playback state
    val ongoingEpisodeIds by remember(viewModel) {
        viewModel.episodePlaybackState
            .map { map ->
                map.filterValues { it.isResume }.keys
            }.distinctUntilChanged()
    }.collectAsState(initial = emptySet())

    // REWRITE: Structure using Box to allow Overlay
    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is PodcastInfoUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().testTag(PODCAST_INFO_LOADING_TAG)) {
                    ShowPageSkeleton()
                }
            }

            is PodcastInfoUiState.Error -> {
                PodcastInfoLoadError(onRetry = viewModel::retryLoad)
            }

            is PodcastInfoUiState.Success -> {
                // Blurred Background Header; a branded page keeps its own flat background.
                if (brandedPage == null) {
                    InfoArtworkBackground(
                        imageUrl = state.podcast.imageUrl,
                        fallbackUrls = listOf(state.podcast.fallbackImageUrl, state.podcast.latestEpisode?.imageUrl),
                        height = titleMotion.collapsedHeaderHeight + 240.dp,
                        scrollOffset = titleMotion.scrollOffset,
                        scrollFraction = titleMotion.scrollFraction,
                    )
                }

                // Content
                val displayEpisodes =
                    remember(isSearchActive, state.searchQuery, state.searchResults, state.episodes, hideCompleted, completedEpisodeIds) {
                        val rawList = podcastSearchDisplayEpisodes(isSearchActive, state.searchQuery, state.searchResults, state.episodes)
                        if (hideCompleted && !(isSearchActive && state.searchQuery.isNotBlank())) {
                            rawList.filter { it.id !in completedEpisodeIds }
                        } else {
                            rawList
                        }
                    }
                val hideCompletedFill = rememberHideCompletedFill(
                    hideCompleted = hideCompleted,
                    searching = isSearchActive && state.searchQuery.isNotBlank(),
                    visibleCount = displayEpisodes.size,
                    state = state,
                    onLoadMore = viewModel::loadMoreEpisodes,
                )
                val feedItems = remember(displayEpisodes, isSearchActive, state.searchQuery) {
                    if (isSearchActive && state.searchQuery.isNotBlank()) {
                        displayEpisodes.mapIndexed { index, episode -> FeedItem.NormalEpisode(episode, index) }
                    } else {
                        groupEpisodes(displayEpisodes)
                    }
                }
                val selectedEpisodes =
                    remember(state.episodes, displayEpisodes, selectionEpisodePool, selectedEpisodeIds) {
                        PodcastEpisodeSelectionLogic.selectedEpisodes(
                            episodes = selectionEpisodePool + state.episodes + displayEpisodes,
                            selectedIds = selectedEpisodeIds.toSet(),
                        )
                    }
                val markSelectionAsUnplayed =
                    remember(selectedEpisodes, completedEpisodeIds) {
                        PodcastEpisodeSelectionLogic.shouldMarkUnplayed(
                            selectedEpisodes = selectedEpisodes,
                            completedEpisodeIds = completedEpisodeIds,
                        )
                    }
                // Records the order a new selection is made in, for the restore after recreation.
                val noteSelectionSort = {
                    if (selectedEpisodeIds.isEmpty()) selectionSortName = state.currentSort.name
                }
                val subscribeToEditGenreMessage = stringResource(R.string.subscribe_to_edit_genre)
                val handleEditGenreClick = {
                    if (state.isSubscribed) {
                        showPodcastGenreEdit = true
                    } else {
                        Toast.makeText(context, subscribeToEditGenreMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                val selectionLimitMessage = stringResource(R.string.episode_selection_limit)
                val selectFullRange: (EpisodeSelectionRange) -> Unit = { range ->
                    selectionRequestGeneration += 1
                    val requestGeneration = selectionRequestGeneration
                    val rangeSort = state.currentSort
                    isLoadingFullSelection = true
                    coroutineScope.launch {
                        try {
                            val window = viewModel.loadEpisodeSelectionWindow(rangeSort)
                            if (requestGeneration != selectionRequestGeneration) return@launch
                            selectionSortName = rangeSort.name
                            // A loaded window can also settle a restore that failed earlier.
                            selectionRestoreFailedOn = null
                            val eligibleEpisodes =
                                if (hideCompleted) {
                                    window.episodes.filter { it.id !in completedEpisodeIds }
                                } else {
                                    window.episodes
                                }
                            selectionEpisodePool = eligibleEpisodes
                            selectedEpisodeIds =
                                PodcastEpisodeSelectionLogic
                                    .addRange(
                                        selectedIds = selectedEpisodeIds.toSet(),
                                        episodes = eligibleEpisodes,
                                        anchorEpisodeId = selectionAnchorEpisodeId,
                                        range = range,
                                        newestFirst = window.newestFirst,
                                    ).toList()
                            isLoadingFullSelection = false
                            if (window.isTruncated) {
                                snackbarHostState.showSnackbar(selectionLimitMessage)
                            }
                        } finally {
                            if (requestGeneration == selectionRequestGeneration) {
                                isLoadingFullSelection = false
                            }
                        }
                    }
                }

                // After recreation, look up selected episodes the loaded list no longer has (P4,
                // adaptive show page). The lookup waits for the first page to finish loading, so
                // the sort and episodes are real, and reads the window in the sort the selection
                // was made in. A cancelled lookup runs again when the page settles; a failed one
                // keeps every ID and tries again when the page state next changes.
                val canRestoreSelection =
                    PodcastEpisodeSelectionLogic.canRestoreSelection(
                        restorePending = selectionRestorePending,
                        pageLoading = state.isLoadingMore,
                        rangeLoading = isLoadingFullSelection,
                        failedOnCurrentState = selectionRestoreFailedOn === state,
                    )
                if (canRestoreSelection) {
                    val loadedEpisodes by rememberUpdatedState(state.episodes + displayEpisodes)
                    val latestState by rememberUpdatedState(state)
                    LaunchedEffect(Unit) {
                        val unresolved =
                            PodcastEpisodeSelectionLogic.unresolvedIds(selectedEpisodeIds, selectionEpisodePool + loadedEpisodes)
                        if (unresolved.isEmpty()) {
                            selectionRestorePending = false
                            return@LaunchedEffect
                        }
                        selectionRequestGeneration += 1
                        val requestGeneration = selectionRequestGeneration
                        val restoreSort = selectionSortName?.let(EpisodeSort::valueOf) ?: latestState.currentSort
                        isRestoringSelection = true
                        try {
                            val window = viewModel.selectionWindowOrNull(restoreSort)
                            if (requestGeneration != selectionRequestGeneration) return@LaunchedEffect
                            if (window == null) {
                                selectionRestoreFailedOn = latestState
                                return@LaunchedEffect
                            }
                            val restored =
                                PodcastEpisodeSelectionLogic.restoreSelection(
                                    selectedIds = selectedEpisodeIds,
                                    available = selectionEpisodePool + loadedEpisodes,
                                    window = window.episodes,
                                )
                            selectionEpisodePool = restored.pool
                            selectedEpisodeIds = restored.selectedIds
                            selectionRestorePending = false
                            selectionRestoreFailedOn = null
                        } finally {
                            isRestoringSelection = false
                        }
                    }
                }
                // After process death the new ViewModel starts with an empty query: run the saved
                // search again once the first page has loaded, so a later reload cannot clear it.
                LaunchedEffect(state.searchQuery, state.isLoadingMore, isSearchActive) {
                    if (searchQueryRestorePending) {
                        if (state.isLoadingMore) return@LaunchedEffect
                        searchQueryRestorePending = false
                        if (state.searchQuery.isBlank() && isSearchActive) viewModel.searchEpisodes(savedSearchQuery)
                    } else if (isSearchActive) {
                        savedSearchQuery = state.searchQuery
                    }
                }

                LaunchedEffect(state, completedEpisodeIds, feedItems, ongoingEpisodeIds) {
                    if (!isSearchActive && state.currentSort == EpisodeSort.OLDEST && feedItems.isNotEmpty()) {
                        val target = resolveAutoScrollTarget(feedItems, completedEpisodeIds, ongoingEpisodeIds)
                        targetJumpIndex = target.jumpIndex
                        isTargetOngoing = target.isOngoing
                        targetJumpEpisode = target.jumpEpisode
                        autoScrolledEpisodeId = target.badgeEpisodeId
                    } else {
                        targetJumpIndex = -1
                        targetJumpEpisode = null
                        isTargetOngoing = false
                        autoScrolledEpisodeId = null
                    }
                }

                // Saved, so an open description stays open when a fold or resize recreates the page.
                var isDescExpanded by rememberSaveable { mutableStateOf(false) }

                val sortedPersons = remember(state.episodes) { sortedShowPersons(state.episodes) }

                val pullToRefreshState = rememberPullToRefreshState()
                val episodeListModifier =
                    Modifier
                        .fillMaxSize()
                        .testTag(PODCAST_INFO_EPISODE_LIST_TAG)
                        .then(if (isSearchActive) Modifier.imePadding() else Modifier)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        }

                val episodeListIndicators =
                    remember(
                        likedEpisodeIds,
                        queuedEpisodeIds,
                        downloadedEpisodeIds,
                        downloadingEpisodeIds,
                        completedEpisodeIds,
                    ) {
                        EpisodeListIndicators(
                            likedEpisodeIds = likedEpisodeIds,
                            queuedEpisodeIds = queuedEpisodeIds,
                            downloadedEpisodeIds = downloadedEpisodeIds,
                            downloadingEpisodeIds = downloadingEpisodeIds,
                            completedEpisodeIds = completedEpisodeIds,
                        )
                    }

                val spotlightEpisode = brandedPage?.let {
                    brandedSpotlightEpisode(state.episodes, state.currentSort, isSearchActive, hiddenIds = if (hideCompleted) completedEpisodeIds else emptySet())
                }
                val listFeedItems = remember(feedItems, spotlightEpisode) { feedItemsWithoutSpotlight(feedItems, spotlightEpisode) }
                val contentState = EpisodeListContentState(
                    listState = listState,
                    modifier = episodeListModifier,
                    contentPadding = PaddingValues(
                        top = brandedPage?.let { titleMotion.statusBarHeight + it.headerTop } ?: (titleMotion.collapsedHeaderHeight + 16.dp),
                        bottom = if (isSearchActive && titleMotion.keyboardVisible) {
                            16.dp
                        } else {
                            WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + bottomContentPadding +
                            if (selectionActive) 92.dp else 16.dp
                        },
                    ),
                    state = state,
                    sortedPersons = sortedPersons,
                    isDescExpanded = isDescExpanded,
                    accentColor = accentColor,
                    isSystemNotificationsBlocked = notificationPermission.systemBlocked,
                    toolbarWarning = toolbarWarning,
                    feedItems = listFeedItems,
                    episodeListIndicators = episodeListIndicators,
                    autoScrolledEpisodeId = autoScrolledEpisodeId,
                    selectedEpisodeIdSet = selectedEpisodeIdSet,
                    selectionActive = selectionActive,
                    isSearchActive = isSearchActive,
                    searchFocusRequester = searchFocusRequester,
                    hideCompletedFill = hideCompletedFill,
                    spotlightEpisode = spotlightEpisode,
                )

                val contentCallbacks = EpisodeListContentCallbacks(
                    onDescExpandedChange = { isDescExpanded = it },
                    onPlayEpisode = { viewModel.onPlayClick(it) },
                    onPodcastClick = onPodcastClick,
                    onEditGenre = handleEditGenreClick,
                    onSearchChange = { viewModel.searchEpisodes(it) },
                    onSortToggle = { viewModel.toggleSort() },
                    onSubscribeClick = { viewModel.toggleSubscription() },
                    onNotificationsToggle = {
                        cx.aswin.boxlore.feature.info.logic.requestPodcastNotificationAction(
                            podcast = state.podcast,
                            onDisclosureRequired = {
                                notificationPermission.pendingAction = null
                                pendingRssNotificationAction = it
                            },
                            onProceed = toggleNotifications,
                        )
                    },
                    onAutoDownloadToggle = {
                        handleAutoDownloadToggle(
                            podcastAutoDownloadEnabled = state.podcast.autoDownloadEnabled,
                            podcastNotificationsEnabled = state.podcast.notificationsEnabled,
                            backgroundChecksEnabled = backgroundChecksEnabled,
                            currentWarning = toolbarWarning,
                            onWarningChange = { toolbarWarning = it },
                            onToggleAutoDownload = { viewModel.toggleAutoDownload() },
                        )
                    },
                    onSearchFocused = {
                        clearEpisodeSelection()
                        isSearchActive = true
                        searchOpenRequest += 1
                    },
                    onSearchClose = closeSearch,
                    onDismissWarning = { toolbarWarning = ToolbarWarning.NONE },
                    onWarningAction = {
                        val currentWarning = toolbarWarning
                        toolbarWarning = ToolbarWarning.NONE
                        cx.aswin.boxlore.feature.info.logic.requestPodcastNotificationAction(
                            podcast = state.podcast,
                            onDisclosureRequired = {
                                notificationPermission.pendingAction = null
                                pendingRssNotificationAction = it
                            },
                            onProceed = { accepted, feedUrl ->
                                val enableNotifications = { viewModel.enableShowNotifications(accepted, feedUrl) }
                                notificationPermission.pendingAction = if (
                                    !areAppNotificationsEnabled(context) &&
                                    (currentWarning == ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY || !state.podcast.notificationsEnabled)
                                ) {
                                    enableNotifications
                                } else {
                                    null
                                }
                                handleToolbarWarningAction(
                                    warning = currentWarning,
                                    context = context,
                                    onEnableNotifications = enableNotifications,
                                    onRequestNotificationPermission = {
                                        notificationPermission.request()
                                    },
                                    onShowPermissionBlockedWarning = { toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED },
                                )
                            },
                        )
                    },
                    onEpisodeClick = { episode, entryPoint, index ->
                        onEpisodeClick(episode, entryPoint, index)
                        if (isSearchActive) closeSearch()
                    },
                    onToggleSelection = { episode ->
                        noteSelectionSort()
                        val updated = PodcastEpisodeSelectionLogic.toggle(
                            selectedIds = selectedEpisodeIdSet,
                            episodeId = episode.id,
                        )
                        selectedEpisodeIds = updated.toList()
                        selectionAnchorEpisodeId = episode.id.takeIf { updated.isNotEmpty() }
                    },
                    onLongPressSelection = { episode ->
                        focusManager.clearFocus()
                        noteSelectionSort()
                        selectedEpisodeIds = PodcastEpisodeSelectionLogic.toggle(
                            selectedIds = selectedEpisodeIdSet,
                            episodeId = episode.id,
                        ).toList()
                        selectionAnchorEpisodeId = episode.id
                    },
                    onLoadMore = { viewModel.loadMoreEpisodes() },
                    onShowCompleted = viewModel::toggleHideCompleted,
                )

                PullToRefreshBox(
                    isRefreshing = state.isRssRefreshing,
                    onRefresh = viewModel::refreshRssFeed,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = state.isRssRefreshing,
                            modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = titleMotion.collapsedHeaderHeight)
                                .testTag(PODCAST_INFO_REFRESH_INDICATOR_TAG),
                        )
                    },
                ) {
                    PodcastInfoEpisodeList(
                        contentState = contentState,
                        callbacks = contentCallbacks,
                        viewModel = viewModel,
                    )
                }

                // FIXED HEADER. A branded page keeps its tag in the middle of the bar: only the
                // missing-episodes offer, which asks for a choice, replaces it; passing feed-check
                // statuses are left to the list's status line.
                val topBarChip = if (brandedPage != null && state.directFeedChip != DirectFeedChipState.Offer) DirectFeedChipState.Hidden else state.directFeedChip
                val tagShown by animateFloatAsState(if (topBarChip == DirectFeedChipState.Hidden) 1f else 0f, label = "brandedTagShown")
                PodcastInfoTopOverlay(
                    podcast = state.podcast,
                    headerColor = titleMotion.headerColor,
                    collapsedHeaderHeight = titleMotion.collapsedHeaderHeight,
                    hideCompleted = hideCompleted,
                    actions =
                    PodcastInfoTopOverlayActions(
                        onBack = if (selectionActive) clearEpisodeSelection else onBack,
                        onMarkAllPlayed = { showMarkAllPlayedDialog = true },
                        onMarkAllUnplayed = { showMarkAllUnplayedDialog = true },
                        onToggleHideCompleted = { viewModel.toggleHideCompleted() },
                        onPlaybackSettings = { showPodcastPlaybackSettings = true },
                        isSubscribed = state.isSubscribed,
                        isPinnedToHome = isPinnedToHome,
                        onToggleHomePin = viewModel::toggleHomePin,
                        onEditGenre = handleEditGenreClick,
                    ),
                    tagAlpha = if (brandedPage == null) 0f else tagShown * (1f - (titleMotion.scrollFraction / 0.5f).coerceIn(0f, 1f)),
                    missingEpisodesChip =
                    MissingEpisodesChip(
                        visible = titleMotion.scrollFraction < 0.5f,
                        state = topBarChip,
                        onClick = {
                            if (state.directFeedChip == DirectFeedChipState.Offer) {
                                showMissingEpisodesConfirm = true
                            }
                        },
                    ),
                )

                AnimatedVisibility(
                    visible = selectionActive,
                    modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .adaptivePlayerOverlayOffset(isMiniPlayerVisible)
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom =
                            WindowInsets.navigationBars
                                .asPaddingValues()
                                .calculateBottomPadding() + bottomContentPadding + 12.dp,
                        ),
                    enter = slideInVertically { height -> height } + fadeIn(),
                    exit = slideOutVertically { height -> height } + fadeOut(),
                ) {
                    EpisodeSelectionToolbar(
                        state =
                        EpisodeSelectionToolbarState(
                            selectedCount = selectedEpisodeIds.size,
                            canDownload =
                            selectedEpisodes.any {
                                it.id !in downloadedEpisodeIds && it.id !in downloadingEpisodeIds
                            },
                            markAsUnplayed = markSelectionAsUnplayed,
                            canAddToQueue = selectedEpisodes.any { it.id !in queuedEpisodeIds },
                            hasRangeAnchor =
                            selectionAnchorEpisodeId != null &&
                                (selectionEpisodePool + state.episodes + displayEpisodes).any {
                                    it.id == selectionAnchorEpisodeId
                                },
                            isLoadingFullSelection = isLoadingFullSelection || isRestoringSelection,
                            // Also off after a failed lookup, until the selection is whole again.
                            actionsEnabled = !selectionRestorePending,
                        ),
                        actions =
                        EpisodeSelectionToolbarActions(
                            onClear = clearEpisodeSelection,
                            onDownload = {
                                viewModel.downloadEpisodes(selectedEpisodes)
                                clearEpisodeSelection()
                            },
                            onToggleCompletion = {
                                if (markSelectionAsUnplayed) {
                                    viewModel.markEpisodesUncompleted(selectedEpisodes)
                                } else {
                                    viewModel.markEpisodesCompleted(selectedEpisodes)
                                }
                                clearEpisodeSelection()
                            },
                            onPlay = {
                                viewModel.playEpisodes(selectedEpisodes)
                                clearEpisodeSelection()
                            },
                            onAddToQueue = {
                                viewModel.addEpisodesToQueue(selectedEpisodes)
                                clearEpisodeSelection()
                            },
                            onSelectVisible = {
                                val visibleItemKeys =
                                    listState.layoutInfo.visibleItemsInfo
                                        .mapNotNull { item -> item.key as? String }
                                        .toSet()
                                val visibleEpisodes =
                                    PodcastEpisodeSelectionLogic
                                        .visibleEpisodes(feedItems, visibleItemKeys)
                                        .filterNot { hideCompleted && it.id in completedEpisodeIds }
                                noteSelectionSort()
                                selectionEpisodePool =
                                    (selectionEpisodePool + visibleEpisodes).distinctBy(Episode::id)
                                selectedEpisodeIds =
                                    (selectedEpisodeIdSet + visibleEpisodes.map(Episode::id)).toList()
                            },
                            onSelectAll = {
                                selectFullRange(EpisodeSelectionRange.ALL)
                            },
                            onSelectOlder = {
                                selectFullRange(EpisodeSelectionRange.OLDER)
                            },
                            onSelectNewer = {
                                selectFullRange(EpisodeSelectionRange.NEWER)
                            },
                        ),
                    )
                }

                // A restored dialog waits for the reloaded page to offer the feed check again, and
                // is dropped once the check has run, so a confirm always has something to start.
                LaunchedEffect(state.directFeedChip) {
                    if (state.directFeedChip == DirectFeedChipState.Updated) showMissingEpisodesConfirm = false
                }
                if (showMissingEpisodesConfirm && state.directFeedChip == DirectFeedChipState.Offer) {
                    MissingEpisodesConfirmDialog(
                        onDismissRequest = { showMissingEpisodesConfirm = false },
                        onConfirm = { viewModel.loadMissingEpisodes() },
                    )
                }

                if (showPodcastGenreEdit && state.isSubscribed) {
                    val folderNames by viewModel.folderNames.collectAsStateWithLifecycle()
                    PodcastGenreEditSheet(
                        catalogGenre = state.podcast.genre,
                        customGenre = state.podcast.customGenre,
                        customGenreIcon = state.podcast.customGenreIcon,
                        folderNames = folderNames,
                        onDismissRequest = { showPodcastGenreEdit = false },
                        onSave = { newGenre, newIcon ->
                            viewModel.updateCustomGenre(newGenre, newIcon)
                        },
                    )
                }

                if (showPodcastPlaybackSettings) {
                    cx.aswin.boxlore.feature.info.components.PodcastPlaybackSettingsSheet(
                        state =
                        cx.aswin.boxlore.feature.info.components.PodcastPlaybackSettingsState(
                            podcastTitle = state.podcast.title,
                            isSubscribed = state.isSubscribed,
                            globalSkipBeginningMs = globalSkipBeginningMs,
                            globalSkipEndingMs = globalSkipEndingMs,
                            skipBeginningOverrideMs = state.podcast.skipBeginningOverrideMs,
                            skipEndingOverrideMs = state.podcast.skipEndingOverrideMs,
                        ),
                        actions =
                        cx.aswin.boxlore.feature.info.components.PodcastPlaybackSettingsActions(
                            onUseAppDefaultsChange = viewModel::setUseAppPlaybackDefaults,
                            onSkipBeginningOverrideChange = viewModel::setSkipBeginningOverride,
                            onSkipEndingOverrideChange = viewModel::setSkipEndingOverride,
                            onDismissRequest = { showPodcastPlaybackSettings = false },
                        ),
                    )
                }

                // SNACKBAR HOST (Overlay) — placed after jump-pill visibility is known
                LaunchedEffect(state.userMessage) {
                    val message = state.userMessage ?: return@LaunchedEffect
                    snackbarHostState.showSnackbar(message)
                    viewModel.consumeUserMessage()
                }

                // FLOATING TITLE
                Text(
                    text = state.podcast.title,
                    fontSize = titleMotion.titleFontSize,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = titleMotion.titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = titleMotion.titleStartPadding, end = titleMotion.titleEndPadding)
                        .graphicsLayer {
                            translationY = titleMotion.titleTranslationY
                            alpha = titleMotion.titleAlpha
                        },
                )

                // Derived visibility check for floating action pill
                val isTargetVisible by remember(feedItems, listState, targetJumpIndex) {
                    derivedStateOf {
                        if (targetJumpIndex == -1 || feedItems.isEmpty()) {
                            true
                        } else {
                            val listIndex = targetJumpIndex + 2
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            if (visibleItems.isEmpty()) {
                                true
                            } else {
                                val firstVisible = visibleItems.firstOrNull()?.index ?: 0
                                val lastVisible = visibleItems.lastOrNull()?.index ?: 0
                                listIndex in firstVisible..lastVisible || firstVisible >= listIndex
                            }
                        }
                    }
                }

                // Track scroll direction to show/hide FAB
                var isFabVisible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    var lastIndex = listState.firstVisibleItemIndex
                    var lastOffset = listState.firstVisibleItemScrollOffset
                    androidx.compose.runtime
                        .snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { (currentIndex, currentOffset) ->
                            if (currentIndex > lastIndex) {
                                isFabVisible = false
                            } else if (currentIndex < lastIndex) {
                                isFabVisible = true
                            } else if (currentOffset > lastOffset) {
                                isFabVisible = false
                            } else if (currentOffset < lastOffset) {
                                isFabVisible = true
                            }
                            lastIndex = currentIndex
                            lastOffset = currentOffset
                        }
                }

                val jumpPillVisible =
                    targetJumpEpisode != null && !isTargetVisible && isFabVisible && !selectionActive && !isSearchActive
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            bottom =
                            bottomContentPadding + 16.dp +
                                when {
                                    jumpPillVisible -> 56.dp
                                    selectionActive -> 72.dp
                                    else -> 0.dp
                                },
                        ),
                )

                // Floating Jump-To Pill overlay
                val systemBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                PodcastInfoJumpPillOverlay(
                    visible = jumpPillVisible,
                    isOngoing = isTargetOngoing,
                    episodeTitle = targetJumpEpisode?.title ?: "",
                    bottomPadding = systemBottomPadding + bottomContentPadding + 16.dp,
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(targetJumpIndex + 2)
                        }
                    },
                )
            }
        }

        // --- Beautiful M3 Confirmation Dialogs ---
        PodcastInfoMarkDialogs(
            uiState = uiState,
            showMarkAllPlayedDialog = showMarkAllPlayedDialog,
            showMarkAllUnplayedDialog = showMarkAllUnplayedDialog,
            onDismissPlayed = { showMarkAllPlayedDialog = false },
            onDismissUnplayed = { showMarkAllUnplayedDialog = false },
            viewModel = viewModel,
            episodePendingDownloadRemoval = episodePendingDownloadRemoval,
        )
    }
    }
}

/** Test tag on the show page episode list, used by the adaptive transition tests. */
internal const val PODCAST_INFO_EPISODE_LIST_TAG = "podcast_info_episode_list"

/** Test tag on the full-page loading state. */
internal const val PODCAST_INFO_LOADING_TAG = "podcast_info_loading"

/** Test tag on the pull-to-refresh indicator. */
internal const val PODCAST_INFO_REFRESH_INDICATOR_TAG = "podcast_info_refresh_indicator"

/** Saves selected episode IDs as a bundle-safe list. */
private val EpisodeIdListSaver = Saver<List<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it },
)

/**
 * The selection window, in [sort], used to look selected episodes up again after the page is
 * recreated, or null when it cannot be loaded; the restore then keeps every ID and tries again.
 */
@Suppress("TooGenericExceptionCaught") // Any load failure leaves the restore pending.
private suspend fun PodcastInfoViewModel.selectionWindowOrNull(sort: EpisodeSort): PodcastEpisodeSelectionWindow? = try {
    loadEpisodeSelectionWindow(sort)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    android.util.Log.w("PodcastInfoScreen", "Could not restore the episode selection", failure)
    null
}
