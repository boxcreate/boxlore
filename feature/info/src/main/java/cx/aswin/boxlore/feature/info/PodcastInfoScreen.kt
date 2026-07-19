package cx.aswin.boxlore.feature.info

import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.feature.info.components.EpisodeFeedItemRow
import cx.aswin.boxlore.feature.info.components.EpisodeListIndicators
import cx.aswin.boxlore.feature.info.components.EpisodeToolbar
import cx.aswin.boxlore.feature.info.components.MarkAllEpisodesDialog
import cx.aswin.boxlore.feature.info.components.PodcastInfoSearchOverlay
import cx.aswin.boxlore.feature.info.components.PodcastInfoTopOverlay
import cx.aswin.boxlore.feature.info.components.PodcastInfoTopOverlayActions
import cx.aswin.boxlore.feature.info.components.ToolbarWarningBanner
import cx.aswin.boxlore.feature.info.components.handleAutoDownloadToggle
import cx.aswin.boxlore.feature.info.components.handleNotificationsToggle
import cx.aswin.boxlore.feature.info.components.handleToolbarWarningAction
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import cx.aswin.boxlore.feature.info.logic.groupEpisodes
import cx.aswin.boxlore.feature.info.logic.resolveAutoScrollTarget
import cx.aswin.boxlore.feature.info.sections.PodcastInfoHeroSection
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
    val hideCompleted by viewModel.hideCompletedInShowDetails.collectAsState()
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

    // Permission Launcher for Android 13+ Notification Permission
    val notifPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                viewModel.enableBothNotificationsAndAutoDownload()
                toolbarWarning = ToolbarWarning.NONE
            } else {
                toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED
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
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(collapsedHeaderHeight + 240.dp)
                            .graphicsLayer {
                                translationY = -scrollOffset * 0.5f
                                alpha = 1f - scrollFraction
                            },
                ) {
                    OptimizedImage(
                        url = state.podcast.imageUrl.takeIf { it.isNotEmpty() } ?: state.podcast.fallbackImageUrl,
                        proxyWidth = 200,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .alpha(0.5f)
                                .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                        contentScale = ContentScale.Crop,
                    )
                    // Gradient overlay to blend into the background
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background,
                                            ),
                                    ),
                                ),
                    )
                }

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

                @Composable
                fun EpisodeLazyColumn() {
                    LazyColumn(
                        state = listState,
                        modifier = episodeListModifier,
                        contentPadding =
                            PaddingValues(
                                top = collapsedHeaderHeight + 16.dp,
                                bottom =
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomContentPadding + 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // HERO SECTION: Centered Layout
                        item {
                            PodcastInfoHeroSection(
                                state = state,
                                sortedPersons = sortedPersons,
                                isDescExpanded = isDescExpanded,
                                onDescExpandedChange = { isDescExpanded = it },
                                onPlayEpisode = { viewModel.onPlayClick(it) },
                                onPodcastClick = onPodcastClick,
                                context = context,
                            )
                        }

                        // EPISODE TOOLBAR
                        item(key = "toolbar") {
                            EpisodeToolbar(
                                searchQuery = state.searchQuery,
                                onSearchChange = { viewModel.searchEpisodes(it) },
                                isSearching = state.isSearching,
                                currentSort = state.currentSort,
                                onSortToggle = { viewModel.toggleSort() },
                                isSubscribed = state.isSubscribed,
                                onSubscribeClick = { viewModel.toggleSubscription() },
                                accentColor = accentColor,
                                supportsReleaseAutomation = !state.podcast.isRss,
                                notificationsEnabled = state.podcast.notificationsEnabled,
                                onNotificationsToggle = {
                                    handleNotificationsToggle(
                                        context = context,
                                        podcastNotificationsEnabled = state.podcast.notificationsEnabled,
                                        onRequestPermission = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        onShowPermissionBlockedWarning = { toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED },
                                        onToggleNotifications = { viewModel.toggleNotifications() },
                                    )
                                },
                                autoDownloadEnabled = state.podcast.autoDownloadEnabled,
                                onAutoDownloadToggle = {
                                    handleAutoDownloadToggle(
                                        podcastAutoDownloadEnabled = state.podcast.autoDownloadEnabled,
                                        podcastNotificationsEnabled = state.podcast.notificationsEnabled,
                                        onShowNotificationsRequiredWarning = { toolbarWarning = ToolbarWarning.NOTIFICATIONS_REQUIRED },
                                        onToggleAutoDownload = { viewModel.toggleAutoDownload() },
                                    )
                                },
                                genre = state.podcast.genre,
                                onSearchFocused = { isSearchActive = true },
                            )
                        }

                        // TOOLBAR WARNING BANNER (Space Reveal)
                        if (toolbarWarning != ToolbarWarning.NONE) {
                            item(key = "toolbar_warning") {
                                ToolbarWarningBanner(
                                    warning = toolbarWarning,
                                    onDismiss = { toolbarWarning = ToolbarWarning.NONE },
                                    onAction = {
                                        val currentWarning = toolbarWarning
                                        toolbarWarning = ToolbarWarning.NONE
                                        handleToolbarWarningAction(
                                            warning = currentWarning,
                                            context = context,
                                            viewModel = viewModel,
                                            onRequestNotificationPermission = {
                                                notifPermissionLauncher.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                )
                                            },
                                            onShowPermissionBlockedWarning = { toolbarWarning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED },
                                        )
                                    },
                                )
                            }
                        }

                        // Episodes
                        itemsIndexed(feedItems, key = { _, item -> item.id }) { itemIndex, feedItem ->
                            EpisodeFeedItemRow(
                                feedItem = feedItem,
                                viewModel = viewModel,
                                accentColor = accentColor,
                                indicators = episodeListIndicators,
                                autoScrolledEpisodeId = autoScrolledEpisodeId,
                                onEpisodeClick = onEpisodeClick,
                            )

                            if (state.searchResults == null &&
                                itemIndex == feedItems.lastIndex &&
                                state.hasMoreEpisodes &&
                                !state.isLoadingMore
                            ) {
                                LaunchedEffect(displayEpisodes.size) {
                                    viewModel.loadMoreEpisodes()
                                }
                            }
                        }

                        if (state.isLoadingMore && !state.isRssRefreshing) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BoxLoreLoader.CircularWavy(size = 32.dp)
                                }
                            }
                        }

                        if (state.searchResults?.isEmpty() == true) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No episodes found",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.podcast.isRss) {
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
                        EpisodeLazyColumn()
                    }
                } else {
                    EpisodeLazyColumn()
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
                            onBack = onBack,
                            onMarkAllPlayed = { showMarkAllPlayedDialog = true },
                            onMarkAllUnplayed = { showMarkAllUnplayedDialog = true },
                            onToggleHideCompleted = { viewModel.toggleHideCompleted() },
                            onPlaybackSettings = { showPodcastPlaybackSettings = true },
                        ),
                )

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

                // SNACKBAR HOST (Overlay)

                // FLOATING TITLE
                Text(
                    text = state.podcast.title,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
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

                // Floating Jump-To Pill overlay
                val systemBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = systemBottomPadding + bottomContentPadding + 16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AnimatedVisibility(
                        visible = targetJumpEpisode != null && !isTargetVisible && isFabVisible,
                        enter =
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.8f),
                            ) + fadeIn(),
                        exit =
                            slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.8f),
                            ) + fadeOut(),
                    ) {
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(targetJumpIndex + 2)
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shadowElevation = 6.dp,
                            modifier =
                                Modifier
                                    .padding(horizontal = 24.dp)
                                    .widthIn(max = 320.dp)
                                    .height(48.dp),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = if (isTargetOngoing) Icons.Rounded.PlayArrow else Icons.Rounded.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isTargetOngoing) "Resume: " else "Jump to: ",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                                Text(
                                    text = targetJumpEpisode?.title ?: "",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    modifier =
                                        Modifier
                                            .weight(1f, fill = false)
                                            .basicMarquee(iterations = Int.MAX_VALUE),
                                )
                            }
                        }
                    }
                }

                // SEARCH OVERLAY (Nested inside Success)
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                ) {
                    PodcastInfoSearchOverlay(
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
        if (showMarkAllPlayedDialog) {
            val currentState = uiState
            if (currentState is PodcastInfoUiState.Success) {
                MarkAllEpisodesDialog(
                    podcastTitle = currentState.podcast.title,
                    markAsPlayed = true,
                    onDismiss = { showMarkAllPlayedDialog = false },
                    onConfirm = {
                        showMarkAllPlayedDialog = false
                        viewModel.markAllAsCompleted()
                    },
                )
            }
        }

        if (showMarkAllUnplayedDialog) {
            val currentState = uiState
            if (currentState is PodcastInfoUiState.Success) {
                MarkAllEpisodesDialog(
                    podcastTitle = currentState.podcast.title,
                    markAsPlayed = false,
                    onDismiss = { showMarkAllUnplayedDialog = false },
                    onConfirm = {
                        showMarkAllUnplayedDialog = false
                        viewModel.markAllAsUncompleted()
                    },
                )
            }
        }
    }
}
