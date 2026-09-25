package cx.aswin.boxlore.feature.info

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.feature.info.components.EpisodeListIndicators
import cx.aswin.boxlore.feature.info.components.EpisodeSelectionToolbar
import cx.aswin.boxlore.feature.info.components.EpisodeSelectionToolbarActions
import cx.aswin.boxlore.feature.info.components.EpisodeSelectionToolbarState
import cx.aswin.boxlore.feature.info.components.MissingEpisodesChip
import cx.aswin.boxlore.feature.info.components.MissingEpisodesConfirmDialog
import cx.aswin.boxlore.feature.info.components.PodcastGenreEditSheet
import cx.aswin.boxlore.feature.info.components.PodcastInfoBackgroundHeader
import cx.aswin.boxlore.feature.info.components.PodcastInfoJumpPillOverlay
import cx.aswin.boxlore.feature.info.components.PodcastInfoMarkDialogs
import cx.aswin.boxlore.feature.info.components.PodcastInfoSearchOverlay
import cx.aswin.boxlore.feature.info.components.PodcastInfoTopOverlay
import cx.aswin.boxlore.feature.info.components.PodcastInfoTopOverlayActions
import cx.aswin.boxlore.feature.info.components.areAppNotificationsEnabled
import cx.aswin.boxlore.feature.info.components.handleAutoDownloadToggle
import cx.aswin.boxlore.feature.info.components.handleNotificationsToggle
import cx.aswin.boxlore.feature.info.components.handleToolbarWarningAction
import cx.aswin.boxlore.feature.info.logic.EpisodeSelectionRange
import cx.aswin.boxlore.feature.info.logic.PodcastEpisodeSelectionLogic
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import cx.aswin.boxlore.feature.info.logic.groupEpisodes
import cx.aswin.boxlore.feature.info.logic.resolveAutoScrollTarget
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
    val queuedEpisodeIds by viewModel.queuedEpisodeIds.collectAsState()
    val downloadedEpisodeIds by viewModel.downloadedEpisodeIds.collectAsState()
    val downloadingEpisodeIds by viewModel.downloadingEpisodeIds.collectAsState()
    val episodePendingDownloadRemoval by viewModel.episodePendingDownloadRemoval.collectAsState()
    val hideCompleted by viewModel.hideCompletedInShowDetails.collectAsState()
    val isPinnedToHome by viewModel.isPinnedToHome.collectAsState()
    val globalSkipBeginningMs by viewModel.globalSkipBeginningMs.collectAsState()
    val globalSkipEndingMs by viewModel.globalSkipEndingMs.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Search State
    var isSearchActive by remember { mutableStateOf(false) }
    var toolbarWarning by remember { mutableStateOf(ToolbarWarning.NONE) }
    var showMarkAllPlayedDialog by remember { mutableStateOf(false) }
    var showMarkAllUnplayedDialog by remember { mutableStateOf(false) }
    var showPodcastPlaybackSettings by remember { mutableStateOf(false) }
    var showPodcastGenreEdit by remember { mutableStateOf(false) }
    var showMissingEpisodesConfirm by remember { mutableStateOf(false) }
    var selectedEpisodeIds by remember(podcastId) { mutableStateOf(emptyList<String>()) }
    var selectionAnchorEpisodeId by remember(podcastId) { mutableStateOf<String?>(null) }
    var selectionEpisodePool by remember(podcastId) { mutableStateOf(emptyList<Episode>()) }
    var isLoadingFullSelection by remember(podcastId) { mutableStateOf(false) }
    var selectionRequestGeneration by remember(podcastId) { mutableStateOf(0) }
    val selectedEpisodeIdSet = selectedEpisodeIds.toSet()
    val selectionActive = selectedEpisodeIds.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isSystemNotificationsBlocked by remember { mutableStateOf(!areAppNotificationsEnabled(context)) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val blocked = !areAppNotificationsEnabled(context)
                isSystemNotificationsBlocked = blocked
                if (!blocked) {
                    if (toolbarWarning == ToolbarWarning.SYSTEM_PERMISSION_BLOCKED) {
                        toolbarWarning = ToolbarWarning.NONE
                    }
                    pendingPermissionAction?.invoke()
                    pendingPermissionAction = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission Launcher for Android 13+ Notification Permission
    val notifPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            BoxcastPrefs(context).setHasRequestedNotificationPermission(true)
            if (isGranted) {
                isSystemNotificationsBlocked = false
                toolbarWarning = ToolbarWarning.NONE
                pendingPermissionAction?.invoke()
                pendingPermissionAction = null
            } else {
                isSystemNotificationsBlocked = true
                toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED
                pendingPermissionAction = null
            }
        }

    // 10-second auto-dismiss for toolbar warning banner
    LaunchedEffect(toolbarWarning) {
        if (toolbarWarning != ToolbarWarning.NONE) {
            delay(10000L)
            toolbarWarning = ToolbarWarning.NONE
        }
    }

    // Use theme primary color (no dynamic extraction)
    val accentColor = MaterialTheme.colorScheme.primary

    // Handle Back Press for Search
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.searchEpisodes("") // Optional: Clear search on close? Or keep it? Let's clear for now.
    }
    BackHandler(enabled = selectionActive) {
        selectionRequestGeneration += 1
        selectedEpisodeIds = emptyList()
        selectionAnchorEpisodeId = null
        selectionEpisodePool = emptyList()
        isLoadingFullSelection = false
    }

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

    // Scroll state for floating title animation (like Episode Info)
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                1000f // Fully collapsed
            }
        }
    }

    // Scroll fraction: 0 (expanded) -> 1 (collapsed)
    val density = LocalDensity.current
    val morphThreshold = with(density) { 150.dp.toPx() }
    val scrollFraction = (scrollOffset / morphThreshold).coerceIn(0f, 1f)

    // Header dimensions
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedHeaderHeight = 64.dp + statusBarHeight

    // Header background: transparent → surfaceContainer
    // NOTE: Don't lerp from Color.Transparent - it has RGB=0,0,0 causing black flash
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val headerColor by animateColorAsState(
        targetValue = surfaceColor.copy(alpha = scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headerColor",
    )

    // Title animation - floating title like Episode Info
    val titleSizeStart = MaterialTheme.typography.headlineSmall.fontSize
    val titleSizeEnd = MaterialTheme.typography.titleMedium.fontSize
    val titleFontSize =
        androidx.compose.ui.unit
            .lerp(titleSizeStart, titleSizeEnd, scrollFraction)

    // Y position: starts below header (above hero), ends in header
    val bodyTitleYPx = with(density) { collapsedHeaderHeight.toPx() + 16.dp.toPx() }
    val headerTitleYPx = with(density) { (statusBarHeight + 18.dp).toPx() }
    val titleTranslationY by animateFloatAsState(
        targetValue =
        androidx.compose.ui.util
            .lerp(bodyTitleYPx, headerTitleYPx, scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.85f),
        label = "titleY",
    )

    // MaxLines - 3 when expanded, 1 when collapsed (change at 70% for late transition)
    val titleMaxLines = 1
    // Keep alpha at 0 until header collapses, then fade in
    val titleAlpha = if (scrollFraction > 0.8f) (scrollFraction - 0.8f) / 0.2f else 0f

    // Horizontal padding
    val titleStartPadding by animateDpAsState(
        targetValue =
        androidx.compose.ui.unit
            .lerp(20.dp, 56.dp, scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "titleStartPadding",
    )
    val titleEndPadding by animateDpAsState(
        targetValue =
        androidx.compose.ui.unit
            .lerp(20.dp, 112.dp, scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "titleEndPadding",
    )

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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxLoreLoader.Expressive(size = 80.dp)
                }
            }

            is PodcastInfoUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Failed to load podcast", color = MaterialTheme.colorScheme.error)
                }
            }

            is PodcastInfoUiState.Success -> {
                // Blurred Background Header
                PodcastInfoBackgroundHeader(
                    imageUrl = state.podcast.imageUrl.takeIf { it.isNotEmpty() } ?: state.podcast.fallbackImageUrl,
                    collapsedHeaderHeight = collapsedHeaderHeight,
                    scrollOffset = scrollOffset,
                    scrollFraction = scrollFraction,
                )

                // Content
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

                val displayEpisodes =
                    remember(state.searchResults, state.episodes, hideCompleted, completedEpisodeIds) {
                        val rawList = state.searchResults ?: state.episodes
                        if (hideCompleted) {
                            rawList.filter { it.id !in completedEpisodeIds }
                        } else {
                            rawList
                        }
                    }
                val feedItems = remember(displayEpisodes) { groupEpisodes(displayEpisodes) }
                val selectedEpisodes =
                    remember(state.episodes, selectionEpisodePool, selectedEpisodeIds) {
                        PodcastEpisodeSelectionLogic.selectedEpisodes(
                            episodes = selectionEpisodePool + state.episodes,
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
                val clearEpisodeSelection = {
                    selectionRequestGeneration += 1
                    selectedEpisodeIds = emptyList()
                    selectionAnchorEpisodeId = null
                    selectionEpisodePool = emptyList()
                    isLoadingFullSelection = false
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
                    isLoadingFullSelection = true
                    coroutineScope.launch {
                        try {
                            val window = viewModel.loadEpisodeSelectionWindow()
                            if (requestGeneration != selectionRequestGeneration) return@launch
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

                LaunchedEffect(state, completedEpisodeIds, feedItems, ongoingEpisodeIds) {
                    if (state.currentSort == EpisodeSort.OLDEST && feedItems.isNotEmpty()) {
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

                var isDescExpanded by remember { mutableStateOf(false) }

                val podcastPersons =
                    remember(state.episodes) {
                        state.episodes
                            .take(15)
                            .flatMap { it.persons ?: emptyList() }
                            .distinctBy { it.name.lowercase().trim() }
                    }

                val sortedPersons =
                    remember(podcastPersons) {
                        podcastPersons.sortedWith(
                            compareByDescending<Person> {
                                val role = it.role?.lowercase() ?: ""
                                role.contains("host") || role.contains("creator") || role.contains("presenter")
                            }.thenBy { it.name },
                        )
                    }

                val pullToRefreshState = rememberPullToRefreshState()
                val episodeListModifier =
                    Modifier
                        .fillMaxSize()
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

                val contentState = EpisodeListContentState(
                    listState = listState,
                    modifier = episodeListModifier,
                    contentPadding = PaddingValues(
                        top = collapsedHeaderHeight + 16.dp,
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + bottomContentPadding +
                            if (selectionActive) 92.dp else 16.dp,
                    ),
                    state = state,
                    sortedPersons = sortedPersons,
                    isDescExpanded = isDescExpanded,
                    accentColor = accentColor,
                    isSystemNotificationsBlocked = isSystemNotificationsBlocked,
                    toolbarWarning = toolbarWarning,
                    feedItems = feedItems,
                    episodeListIndicators = episodeListIndicators,
                    autoScrolledEpisodeId = autoScrolledEpisodeId,
                    selectedEpisodeIdSet = selectedEpisodeIdSet,
                    selectionActive = selectionActive,
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
                        handleNotificationsToggle(
                            context = context,
                            podcastNotificationsEnabled = state.podcast.notificationsEnabled,
                            isWarningVisible = toolbarWarning == ToolbarWarning.SYSTEM_PERMISSION_BLOCKED,
                            onRequestPermission = {
                                pendingPermissionAction = { viewModel.toggleNotifications() }
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onShowPermissionBlockedWarning = { toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED },
                            onToggleNotifications = { viewModel.toggleNotifications() },
                        )
                    },
                    onAutoDownloadToggle = {
                        handleAutoDownloadToggle(
                            podcastAutoDownloadEnabled = state.podcast.autoDownloadEnabled,
                            podcastNotificationsEnabled = state.podcast.notificationsEnabled,
                            onShowNotificationsRequiredWarning = { toolbarWarning = ToolbarWarning.NOTIFICATIONS_REQUIRED },
                            onToggleAutoDownload = { viewModel.toggleAutoDownload() },
                        )
                    },
                    onSearchFocused = { isSearchActive = true },
                    onDismissWarning = { toolbarWarning = ToolbarWarning.NONE },
                    onWarningAction = {
                        val currentWarning = toolbarWarning
                        toolbarWarning = ToolbarWarning.NONE
                        handleToolbarWarningAction(
                            warning = currentWarning,
                            context = context,
                            viewModel = viewModel,
                            onRequestNotificationPermission = {
                                if (currentWarning == ToolbarWarning.NOTIFICATIONS_REQUIRED) {
                                    pendingPermissionAction = { viewModel.enableBothNotificationsAndAutoDownload() }
                                } else if (!state.podcast.notificationsEnabled) {
                                    pendingPermissionAction = { viewModel.toggleNotifications() }
                                } else {
                                    pendingPermissionAction = null
                                }
                                notifPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            },
                            onShowPermissionBlockedWarning = { toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED },
                        )
                    },
                    onEpisodeClick = onEpisodeClick,
                    onToggleSelection = { episode ->
                        val updated = PodcastEpisodeSelectionLogic.toggle(
                            selectedIds = selectedEpisodeIdSet,
                            episodeId = episode.id,
                        )
                        selectedEpisodeIds = updated.toList()
                        selectionAnchorEpisodeId = episode.id.takeIf { updated.isNotEmpty() }
                    },
                    onLongPressSelection = { episode ->
                        selectedEpisodeIds = PodcastEpisodeSelectionLogic.toggle(
                            selectedIds = selectedEpisodeIdSet,
                            episodeId = episode.id,
                        ).toList()
                        selectionAnchorEpisodeId = episode.id
                    },
                    onLoadMore = { viewModel.loadMoreEpisodes() },
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
                                .padding(top = collapsedHeaderHeight),
                        )
                    },
                ) {
                    PodcastInfoEpisodeList(
                        contentState = contentState,
                        callbacks = contentCallbacks,
                        viewModel = viewModel,
                    )
                }

                // FIXED HEADER
                PodcastInfoTopOverlay(
                    podcast = state.podcast,
                    headerColor = headerColor,
                    collapsedHeaderHeight = collapsedHeaderHeight,
                    hideCompleted = hideCompleted,
                    context = context,
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
                    missingEpisodesChip =
                    MissingEpisodesChip(
                        visible = scrollFraction < 0.5f,
                        state = state.directFeedChip,
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
                                (selectionEpisodePool + state.episodes).any {
                                    it.id == selectionAnchorEpisodeId
                                },
                            isLoadingFullSelection = isLoadingFullSelection,
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

                if (showMissingEpisodesConfirm) {
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
                    fontSize = titleFontSize,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = titleStartPadding, end = titleEndPadding)
                        .graphicsLayer {
                            translationY = titleTranslationY
                            alpha = titleAlpha
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
                    targetJumpEpisode != null && !isTargetVisible && isFabVisible && !selectionActive
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

                // SEARCH OVERLAY (Nested inside Success)
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                ) {
                    PodcastInfoSearchOverlay(
                        podcastImageUrl =
                        state.podcast.imageUrl.takeIf { it.isNotEmpty() }
                            ?: state.podcast.fallbackImageUrl,
                        query = state.searchQuery,
                        onQueryChange = { viewModel.searchEpisodes(it) },
                        onClose = {
                            isSearchActive = false
                            viewModel.searchEpisodes("") // Clear on exit
                        },
                        results = state.searchResults,
                        allEpisodes = state.episodes,
                        onEpisodeClick = { episode, index ->
                            viewModel.recordEpisodeClick(episode.id)
                            onEpisodeClick(episode, "podcast_info_search_results", index)
                        },
                        onPlayClick = { viewModel.onPlayClick(it) },
                        onToggleLike = { viewModel.onToggleLike(it) },
                        onQueueClick = { viewModel.toggleQueue(it) },
                        onDownloadClick = { viewModel.toggleDownload(it) },
                        onToggleCompletion = { viewModel.onToggleCompletion(it) },
                        likedEpisodeIds = likedEpisodeIds,
                        completedEpisodeIds = completedEpisodeIds,
                        queuedEpisodeIds = queuedEpisodeIds,
                        playbackStateFlow = viewModel.episodePlaybackState,
                        isSearching = state.isSearching,
                        accentColor = accentColor,
                        downloadedEpisodeIds = downloadedEpisodeIds,
                        downloadingEpisodeIds = downloadingEpisodeIds,
                    )
                }
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
