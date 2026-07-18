package cx.aswin.boxlore.feature.info

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession
import cx.aswin.boxlore.core.designsystem.theme.contrastColor
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodrollItem
import cx.aswin.boxlore.feature.info.sections.PodcastInfoHeroSection
import cx.aswin.boxlore.feature.info.sections.stripHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun extractDominantColor(bitmap: Bitmap): Color {
    val palette = Palette.from(bitmap).generate()
    val colorInt =
        palette.vibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: return Color.Transparent
    return Color(colorInt)
}

// Navbar height constant
private val NAVBAR_HEIGHT = 80.dp

// M3 Expressive Easing (Standard decelerate curve)
private val ExpressiveEasing =
    androidx.compose.animation.core
        .CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

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

// region PodcastInfoScreen extraction: auto-scroll target resolution

private data class AutoScrollTarget(
    val jumpIndex: Int,
    val isOngoing: Boolean,
    val jumpEpisode: Episode?,
    val badgeEpisodeId: String?,
)

private fun resolveAutoScrollTarget(
    feedItems: List<FeedItem>,
    completedEpisodeIds: Set<String>,
    ongoingEpisodeIds: Set<String>,
): AutoScrollTarget {
    fun isCompleted(item: FeedItem): Boolean =
        when (item) {
            is FeedItem.NormalEpisode -> completedEpisodeIds.contains(item.episode.id)
            is FeedItem.SingleTrailer -> completedEpisodeIds.contains(item.episode.id)
            is FeedItem.TrailerGroup -> item.trailers.any { completedEpisodeIds.contains(it.first.id) }
        }

    fun isOngoing(item: FeedItem): Boolean =
        when (item) {
            is FeedItem.NormalEpisode -> ongoingEpisodeIds.contains(item.episode.id)
            is FeedItem.SingleTrailer -> ongoingEpisodeIds.contains(item.episode.id)
            // Match any trailer in the group, not only the first.
            is FeedItem.TrailerGroup -> item.trailers.any { ongoingEpisodeIds.contains(it.first.id) }
        }

    fun episodeAt(item: FeedItem): Episode? =
        when (item) {
            is FeedItem.NormalEpisode -> item.episode
            is FeedItem.SingleTrailer -> item.episode
            is FeedItem.TrailerGroup ->
                item.trailers.firstOrNull { ongoingEpisodeIds.contains(it.first.id) }?.first
                    ?: item.trailers.firstOrNull { !completedEpisodeIds.contains(it.first.id) }?.first
        }

    // 1. Look for an in-progress/ongoing episode first
    var targetIndex = feedItems.indexOfFirst { isOngoing(it) }
    val isOngoingMatched = targetIndex != -1

    // 2. If nothing is ongoing, look for the episode just after the last completed one
    if (targetIndex == -1) {
        val lastCompletedIndex = feedItems.indexOfLast { isCompleted(it) }
        if (lastCompletedIndex != -1) {
            targetIndex = lastCompletedIndex + 1
        }
    }

    // 3. Fallback to first episode (index 0) if nothing completed or ongoing
    if (targetIndex == -1) {
        targetIndex = 0
    }

    // Coerce to ensure we stay inside bounds
    val resolvedIndex = targetIndex.coerceIn(0, feedItems.size - 1)

    // UP NEXT tag should go to the episode immediately following the ongoing/in-progress one
    val badgeIndex =
        if (isOngoingMatched && resolvedIndex < feedItems.size - 1) {
            resolvedIndex + 1
        } else {
            resolvedIndex
        }

    val jumpEpisode = feedItems.getOrNull(resolvedIndex)?.let { episodeAt(it) }
    val badgeEpisode = feedItems.getOrNull(badgeIndex)?.let { episodeAt(it) }

    return AutoScrollTarget(
        jumpIndex = resolvedIndex,
        isOngoing = isOngoingMatched,
        jumpEpisode = jumpEpisode,
        badgeEpisodeId = badgeEpisode?.id,
    )
}

// endregion




// region PodcastInfoScreen extraction: toolbar notification/auto-download handlers

private fun handleNotificationsToggle(
    context: android.content.Context,
    podcastNotificationsEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onShowPermissionBlockedWarning: () -> Unit,
    onToggleNotifications: () -> Unit,
) {
    if (!podcastNotificationsEnabled) {
        // Turning notifications ON
        if (!areAppNotificationsEnabled(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                onRequestPermission()
            } else {
                onShowPermissionBlockedWarning()
            }
        } else {
            onToggleNotifications()
        }
    } else {
        // Turning notifications OFF
        onToggleNotifications()
    }
}

private fun handleAutoDownloadToggle(
    podcastAutoDownloadEnabled: Boolean,
    podcastNotificationsEnabled: Boolean,
    onShowNotificationsRequiredWarning: () -> Unit,
    onToggleAutoDownload: () -> Unit,
) {
    if (!podcastAutoDownloadEnabled) {
        // Turning auto-download ON
        if (!podcastNotificationsEnabled) {
            onShowNotificationsRequiredWarning()
        } else {
            onToggleAutoDownload()
        }
    } else {
        // Turning auto-download OFF
        onToggleAutoDownload()
    }
}

// endregion

// region PodcastInfoScreen extraction: toolbar warning banner

private fun toolbarWarningTitle(warning: ToolbarWarning): String =
    when (warning) {
        ToolbarWarning.NOTIFICATIONS_REQUIRED -> "Action Required"
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Notifications Disabled"
        else -> "Notice"
    }

private fun toolbarWarningMessage(warning: ToolbarWarning): String =
    when (warning) {
        ToolbarWarning.NOTIFICATIONS_REQUIRED -> "In order for us to download the latest episode of this show when it arrives, you need to toggle notifications on as well."
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Notification permissions are disabled in system settings. Please allow notifications and try again. We promise we will never spam."
        else -> ""
    }

private fun toolbarWarningActionText(warning: ToolbarWarning): String =
    when (warning) {
        ToolbarWarning.NOTIFICATIONS_REQUIRED -> "Enable Both"
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Go to Settings"
        else -> ""
    }

private fun handleToolbarWarningAction(
    warning: ToolbarWarning,
    context: android.content.Context,
    viewModel: PodcastInfoViewModel,
    onRequestNotificationPermission: () -> Unit,
    onShowPermissionBlockedWarning: () -> Unit,
) {
    when (warning) {
        ToolbarWarning.NOTIFICATIONS_REQUIRED -> {
            if (areAppNotificationsEnabled(context)) {
                viewModel.enableBothNotificationsAndAutoDownload()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                onRequestNotificationPermission()
            } else {
                onShowPermissionBlockedWarning()
            }
        }
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> openAppNotificationSettings(context)
        else -> {}
    }
}

@Composable
private fun ToolbarWarningBanner(
    warning: ToolbarWarning,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    AnimatedVisibility(
        visible = warning != ToolbarWarning.NONE,
        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
    ) {
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(16.dp),
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = toolbarWarningTitle(warning),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = toolbarWarningMessage(warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                )

                val actionText = toolbarWarningActionText(warning)

                if (actionText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = onAction,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = actionText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region PodcastInfoScreen extraction: episode feed item row

/** Per-episode-list membership sets used to render like/queue/download/completed state on rows. */
private data class EpisodeListIndicators(
    val likedEpisodeIds: Set<String> = emptySet(),
    val queuedEpisodeIds: Set<String> = emptySet(),
    val downloadedEpisodeIds: Set<String> = emptySet(),
    val downloadingEpisodeIds: Set<String> = emptySet(),
    val completedEpisodeIds: Set<String> = emptySet(),
)

@Composable
private fun EpisodeFeedItemRow(
    feedItem: FeedItem,
    viewModel: PodcastInfoViewModel,
    accentColor: Color,
    indicators: EpisodeListIndicators,
    autoScrolledEpisodeId: String?,
    onEpisodeClick: (Episode, String, Int?) -> Unit,
) {
    when (feedItem) {
        is FeedItem.NormalEpisode -> {
            val index = feedItem.globalIndex
            val episode = feedItem.episode

            EpisodePlayStateWrapper(
                episodeId = episode.id,
                playbackStateFlow = viewModel.episodePlaybackState,
            ) { playState ->
                EpisodeListItem(
                    episode = episode,
                    isLiked = indicators.likedEpisodeIds.contains(episode.id),
                    accentColor = accentColor,
                    // Playback State
                    isPlaying = playState?.isPlaying == true,
                    isResume = playState?.isResume == true,
                    progress = playState?.progress ?: 0f,
                    timeLeft = playState?.timeLeft,
                    // Download State
                    isDownloaded = indicators.downloadedEpisodeIds.contains(episode.id),
                    isDownloading = indicators.downloadingEpisodeIds.contains(episode.id),
                    isQueued = indicators.queuedEpisodeIds.contains(episode.id),
                    isCompleted = indicators.completedEpisodeIds.contains(episode.id),
                    isUpNext = episode.id == autoScrolledEpisodeId,
                    onClick = {
                        viewModel.recordEpisodeClick(episode.id)
                        onEpisodeClick(episode, "podcast_info_episodes_list", index)
                    },
                    onPlayClick = { viewModel.onPlayClick(episode) },
                    onToggleLike = { viewModel.onToggleLike(episode) },
                    onQueueClick = { viewModel.toggleQueue(episode) },
                    onDownloadClick = { viewModel.toggleDownload(episode) },
                    onMarkPlayedClick = { viewModel.onToggleCompletion(episode) },
                    showMarkPlayedButton = false, // Hide in list view
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        is FeedItem.SingleTrailer -> {
            SingleTrailerCard(
                episode = feedItem.episode,
                globalIndex = feedItem.globalIndex,
                playbackStateFlow = viewModel.episodePlaybackState,
                onEpisodeClick = { ep, globalIndex ->
                    viewModel.recordEpisodeClick(ep.id)
                    onEpisodeClick(ep, "podcast_info_episodes_list", globalIndex)
                },
                onPlayClick = { ep -> viewModel.onPlayClick(ep) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        is FeedItem.TrailerGroup -> {
            TrailerStackCard(
                group = feedItem,
                playbackStateFlow = viewModel.episodePlaybackState,
                onEpisodeClick = { ep, globalIndex ->
                    viewModel.recordEpisodeClick(ep.id)
                    onEpisodeClick(ep, "podcast_info_episodes_list", globalIndex)
                },
                onPlayClick = { ep -> viewModel.onPlayClick(ep) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

// endregion

// region PodcastInfoScreen extraction: top overlay (back/share/menu) and dialogs

/** Groups [PodcastInfoTopOverlay]'s menu actions so the composable stays under the Sonar param limit. */
private data class PodcastInfoTopOverlayActions(
    val onBack: () -> Unit,
    val onMarkAllPlayed: () -> Unit,
    val onMarkAllUnplayed: () -> Unit,
    val onToggleHideCompleted: () -> Unit,
    val onPlaybackSettings: () -> Unit,
)

@Composable
private fun PodcastInfoTopOverlay(
    podcast: Podcast,
    headerColor: Color,
    collapsedHeaderHeight: Dp,
    hideCompleted: Boolean,
    context: android.content.Context,
    actions: PodcastInfoTopOverlayActions,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(collapsedHeaderHeight)
                .background(headerColor)
                .statusBarsPadding(),
    ) {
        IconButton(
            onClick = actions.onBack,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Share and More Options Dropdown Menu (Top Right)
        var showMenu by remember { mutableStateOf(false) }
        var showShareSheet by remember { mutableStateOf(false) }
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showShareSheet = true },
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Share,
                        contentDescription = "Share Podcast",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (showShareSheet) {
                cx.aswin.boxlore.core.designsystem.components.ShareBottomSheet(
                    id = podcast.id,
                    type = "podcast",
                    title = podcast.title,
                    subtitle = podcast.artist,
                    imageUrl = podcast.imageUrl,
                    onDismissRequest = { showShareSheet = false },
                    onShare = { _, _, _, target ->
                        cx.aswin.boxlore.core.designsystem.share.ShareManager.sharePodcast(
                            context = context,
                            podcast = podcast,
                            target = target,
                        )
                    },
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(20.dp),
                offset = DpOffset(x = (-12).dp, y = 4.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("Mark all as played") },
                    onClick = {
                        showMenu = false
                        actions.onMarkAllPlayed()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Mark all as unplayed") },
                    onClick = {
                        showMenu = false
                        actions.onMarkAllUnplayed()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (hideCompleted) "Show completed episodes" else "Hide completed episodes") },
                    onClick = {
                        showMenu = false
                        actions.onToggleHideCompleted()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (hideCompleted) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text("Playback for this show") },
                    onClick = {
                        showMenu = false
                        actions.onPlaybackSettings()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Tune, contentDescription = null)
                    },
                )
            }
        }
    }
}

@Composable
private fun MarkAllEpisodesDialog(
    podcastTitle: String,
    markAsPlayed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (markAsPlayed) Icons.Rounded.DoneAll else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (markAsPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = if (markAsPlayed) "Mark all as played?" else "Mark all as unplayed?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                text =
                    if (markAsPlayed) {
                        "This will mark all episodes of \"$podcastTitle\" as played."
                    } else {
                        "This will reset all episodes of \"$podcastTitle\" to unplayed."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (markAsPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    ),
                shape = ExpressiveShapes.Pill,
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

// endregion

@Composable
fun EpisodeListItem(
    episode: Episode,
    isLiked: Boolean,
    accentColor: Color,
    // Playback State
    isPlaying: Boolean,
    isResume: Boolean,
    progress: Float,
    timeLeft: String?,
    // Download State
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isQueued: Boolean,
    isCompleted: Boolean,
    isUpNext: Boolean = false,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleLike: () -> Unit,
    onQueueClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMarkPlayedClick: () -> Unit,
    showMarkPlayedButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .expressiveClickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors =
            androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation =
            androidx.compose.material3.CardDefaults
                .outlinedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp), // Generous padding inside the card
        ) {
            // 1. Content Row (Image + Text)
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Artwork with completion checkmark
                Box(modifier = Modifier.size(76.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        OptimizedImage(
                            url = episode.imageUrl,
                            proxyWidth = 200, // 76dp thumbnails
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    if (isCompleted) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text Content
                Column(modifier = Modifier.weight(1f)) {
                    if (isUpNext) {
                        Surface(
                            shape = ExpressiveShapes.Pill,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            Text(
                                "UP NEXT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // Metadata
                    fun formatDuration(seconds: Int): String {
                        val hours = seconds / 3600
                        val minutes = (seconds % 3600) / 60
                        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    }

                    fun formatRelativeDate(timestampSeconds: Long): String {
                        if (timestampSeconds == 0L) return ""
                        val now = System.currentTimeMillis() / 1000
                        val diff = now - timestampSeconds
                        return when {
                            diff < 3600 -> "${diff / 60}m ago"
                            diff < 86400 -> "${diff / 3600}h ago"
                            diff < 604800 -> "${diff / 86400}d ago"
                            diff < 2592000 -> "${diff / 604800}w ago"
                            diff < 31536000 -> "${diff / 2592000}mo ago"
                            else -> "${diff / 31536000}y ago"
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Podcast 2.0: Season/Episode number
                        val seLabel =
                            buildString {
                                episode.seasonNumber?.let { append("S$it ") }
                                episode.episodeNumber?.let { append("E$it") }
                            }.trim()
                        if (seLabel.isNotEmpty()) {
                            Text(
                                text = seLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        Text(
                            text = formatRelativeDate(episode.publishedDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            text = formatDuration(episode.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Podcast 2.0: Episode type badge
                        if (episode.episodeType != null && episode.episodeType != "full") {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Surface(
                                shape = ExpressiveShapes.Pill,
                                color =
                                    if (episode.episodeType == "trailer") {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                            ) {
                                Text(
                                    text = episode.episodeType!!.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (episode.episodeType == "trailer") {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        // Video Badge inside Metadata Row
                        if (episode.enclosureType?.startsWith("video/") == true) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Video",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Title
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )

                    // Description Preview
                    val stripped = stripHtml(episode.description)
                    if (stripped.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stripped,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Control Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween, // Push play button to edge
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
            ) {
                // Secondary Controls (Tonal squircle for premium feel on card)
                cx.aswin.boxlore.core.designsystem.components.AdvancedPlayerControls(
                    isLiked = isLiked,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    colorScheme = MaterialTheme.colorScheme,
                    onLikeClick = onToggleLike,
                    onDownloadClick = onDownloadClick,
                    onQueueClick = onQueueClick,
                    style = cx.aswin.boxlore.core.designsystem.components.ControlStyle.TonalSquircle,
                    overrideColor = accentColor,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    showAddQueueIcon = true,
                    isQueued = isQueued,
                    showShareButton = false,
                    isPlayed = isCompleted,
                    showMarkPlayedButton = showMarkPlayedButton,
                    onMarkPlayedClick = onMarkPlayedClick,
                    controlSize = 40.dp,
                )

                // Play Button
                cx.aswin.boxlore.core.designsystem.components.ExpressivePlayButton(
                    onClick = onPlayClick,
                    state =
                        cx.aswin.boxlore.core.designsystem.components.ExpressivePlayButtonState(
                            isPlaying = isPlaying,
                            isResume = isResume,
                            progress = progress,
                            timeText = timeLeft,
                        ),
                    accentColor = accentColor,
                    modifier =
                        Modifier
                            .height(44.dp)
                            .padding(start = 16.dp)
                            .weight(1f),
                )
            }
        }
    }
}

/**
 * A custom non-overlapping icon button for the toolbar to bypass minimum touch target overlap.
 */
@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String?,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .expressiveClickable(onClick = onClick, shape = ExpressiveShapes.Pill)
                .background(containerColor, ExpressiveShapes.Pill),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Episode Toolbar - M3 Expressive
 * Contains: Search, Sort Toggle, Subscribe Button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isSearching: Boolean,
    currentSort: EpisodeSort,
    onSortToggle: () -> Unit,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    accentColor: Color,
    supportsReleaseAutomation: Boolean = true,
    notificationsEnabled: Boolean = false,
    onNotificationsToggle: () -> Unit = {},
    autoDownloadEnabled: Boolean = false,
    onAutoDownloadToggle: () -> Unit = {},
    genre: String = "",
    onSearchFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // --- Genre-themed celebration icon ---
    val genreLower = genre.lowercase()
    val celebrationIcon: ImageVector =
        when {
            genreLower.contains("music") -> Icons.Rounded.MusicNote
            genreLower.contains("comedy") -> Icons.Rounded.SentimentVerySatisfied
            genreLower.contains("sport") -> Icons.Rounded.EmojiEvents
            genreLower.contains("science") -> Icons.Rounded.Science
            genreLower.contains("tech") -> Icons.Rounded.Computer
            genreLower.contains("news") -> Icons.Rounded.Newspaper
            genreLower.contains("health") -> Icons.Rounded.MonitorHeart
            genreLower.contains("history") -> Icons.Rounded.AccountBalance
            genreLower.contains("arts") -> Icons.Rounded.Palette
            genreLower.contains("education") -> Icons.Rounded.School
            genreLower.contains("tv") || genreLower.contains("film") -> Icons.Rounded.Movie
            genreLower.contains("fiction") -> Icons.Rounded.AutoStories
            genreLower.contains("religion") || genreLower.contains("spiritual") -> Icons.Rounded.SelfImprovement
            genreLower.contains("family") || genreLower.contains("kids") -> Icons.Rounded.ChildCare
            genreLower.contains("leisure") -> Icons.Rounded.Weekend
            genreLower.contains("business") -> Icons.Rounded.Work
            genreLower.contains("government") -> Icons.Rounded.Gavel
            genreLower.contains("society") || genreLower.contains("culture") -> Icons.Rounded.Groups
            genreLower.contains("crime") -> Icons.Rounded.Fingerprint
            else -> Icons.Rounded.Favorite // Fallback: heart
        }

    // --- 3-state machine: IDLE → CELEBRATING → DONE ---
    // 0 = normal, 1 = celebrating (genre icon), 2 = done (subscribed)
    var celebrationPhase by remember { mutableIntStateOf(if (isSubscribed) 2 else 0) }
    var prevSubscribed by remember { mutableStateOf(isSubscribed) }

    // Detect subscribe transition
    LaunchedEffect(isSubscribed) {
        if (isSubscribed && !prevSubscribed) {
            // Just subscribed → celebration
            celebrationPhase = 1
            kotlinx.coroutines.delay(900L) // Hold the genre icon
            celebrationPhase = 2
        } else if (!isSubscribed) {
            kotlinx.coroutines.delay(120L)
            celebrationPhase = 0
        }
        prevSubscribed = isSubscribed
    }

    // Celebration icon animation
    val celebScale = remember { Animatable(0f) }
    val celebRotation = remember { Animatable(0f) }

    LaunchedEffect(celebrationPhase) {
        if (celebrationPhase == 1) {
            // Reset
            celebScale.snapTo(0f)
            celebRotation.snapTo(-30f)
            // Scale in with overshoot (parallel with rotation)
            launch {
                celebScale.animateTo(
                    1.3f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                )
                celebScale.animateTo(
                    1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                )
            }
            // Rotate in
            celebRotation.animateTo(
                0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }

    // Screen configuration for layout optimization on narrow screens
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isSmallScreen = screenWidth < 360

    val spacing = if (isSmallScreen) 6.dp else 8.dp
    val buttonHeight = if (isSmallScreen) 40.dp else 48.dp
    val buttonSize = if (isSmallScreen) 40.dp else 48.dp
    val iconSize = if (isSmallScreen) 20.dp else 22.dp
    val textStyle = MaterialTheme.typography.labelLarge
    val subIconSize = if (isSmallScreen) 18.dp else 20.dp
    val subIconGap = if (isSmallScreen) 6.dp else 8.dp

    val targetHorizontalPadding =
        if (celebrationPhase == 2) {
            if (isSmallScreen) 10.dp else 16.dp
        } else {
            if (isSmallScreen) 16.dp else 24.dp
        }
    val animatedHorizontalPadding by animateDpAsState(
        targetValue = targetHorizontalPadding,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "horizontalPadding",
    )

    val sortRotation by animateFloatAsState(
        targetValue = if (currentSort == EpisodeSort.NEWEST) 0f else 180f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "sortRotation",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Subscribe Button
        val subInteractionSource =
            remember {
                androidx.compose.foundation.interaction
                    .MutableInteractionSource()
            }
        val isSubPressed by subInteractionSource.collectIsPressedAsState()
        val subScale by animateFloatAsState(
            targetValue = if (isSubPressed) 0.9f else 1f,
            animationSpec = if (isSubPressed) cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.QuickSpring else cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.BouncySpring,
            label = "subScale",
        )

        // Animate container color smoothly
        val containerColor by animateColorAsState(
            targetValue =
                when (celebrationPhase) {
                    1 -> accentColor // Keep accent during celebration
                    2 -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> accentColor
                },
            animationSpec = tween(400),
            label = "containerColor",
        )
        // Pick text color based on container luminance for guaranteed contrast
        val onAccent = accentColor.contrastColor()
        val contentColor by animateColorAsState(
            targetValue =
                when (celebrationPhase) {
                    1 -> onAccent
                    2 -> MaterialTheme.colorScheme.onSurface
                    else -> onAccent
                },
            animationSpec = tween(400),
            label = "contentColor",
        )

        FilledTonalButton(
            onClick = onSubscribeClick,
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
            shape = ExpressiveShapes.Pill,
            contentPadding = PaddingValues(horizontal = animatedHorizontalPadding, vertical = 10.dp),
            interactionSource = subInteractionSource,
            modifier =
                Modifier
                    .weight(1f)
                    .height(buttonHeight)
                    .graphicsLayer {
                        scaleX = subScale
                        scaleY = subScale
                    },
        ) {
            // Content: AnimatedContent for the 3 phases
            AnimatedContent(
                targetState = celebrationPhase,
                transitionSpec = {
                    fadeIn(tween(150)).togetherWith(fadeOut(tween(100)))
                },
                contentAlignment = Alignment.Center,
                label = "subContent",
            ) { phase ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    when (phase) {
                        0 -> {
                            // Normal "Subscribe" state
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(subIconSize),
                            )
                            Spacer(modifier = Modifier.width(subIconGap))
                            Text(
                                text = "Subscribe",
                                style = textStyle,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        1 -> {
                            // Celebration: genre icon with bounce + rotate
                            Icon(
                                imageVector = celebrationIcon,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .graphicsLayer {
                                            scaleX = celebScale.value
                                            scaleY = celebScale.value
                                            rotationZ = celebRotation.value
                                        },
                            )
                        }
                        else -> {
                            // Normal "Subscribed" state (text-only)
                            Text(
                                text = "Subscribed",
                                style = textStyle,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = celebrationPhase == 2 && supportsReleaseAutomation,
            transitionSpec = {
                (
                    fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
                        scaleIn(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                            initialScale = 0.85f,
                        )
                ).togetherWith(
                    fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
                        scaleOut(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                            targetScale = 0.85f,
                        ),
                ) using
                    androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        )
                    }
            },
            contentAlignment = Alignment.CenterEnd,
            label = "actionsGroup",
        ) { showExtraActions ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showExtraActions) {
                    // Notification Toggle Button (Bell icon)
                    val bellInteractionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        }
                    val isBellPressed by bellInteractionSource.collectIsPressedAsState()
                    val bellScale by animateFloatAsState(
                        targetValue = if (isBellPressed) 0.9f else 1f,
                        animationSpec = if (isBellPressed) cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.QuickSpring else cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.BouncySpring,
                        label = "bellScale",
                    )

                    val bellContainerColor by animateColorAsState(
                        targetValue =
                            if (notificationsEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        animationSpec = tween(300),
                        label = "bellContainerColor",
                    )

                    val bellContentColor by animateColorAsState(
                        targetValue =
                            if (notificationsEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        animationSpec = tween(300),
                        label = "bellContentColor",
                    )

                    ToolbarIconButton(
                        icon = if (notificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                        contentDescription = "Toggle notifications",
                        containerColor = bellContainerColor,
                        contentColor = bellContentColor,
                        onClick = onNotificationsToggle,
                        modifier =
                            Modifier
                                .size(buttonSize)
                                .graphicsLayer {
                                    scaleX = bellScale
                                    scaleY = bellScale
                                },
                        iconSize = iconSize,
                    )

                    // Auto-Download Toggle Button
                    val downloadInteractionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        }
                    val isDownloadPressed by downloadInteractionSource.collectIsPressedAsState()
                    val downloadScale by animateFloatAsState(
                        targetValue = if (isDownloadPressed) 0.9f else 1f,
                        animationSpec = if (isDownloadPressed) cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.QuickSpring else cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.BouncySpring,
                        label = "downloadScale",
                    )

                    val downloadContainerColor by animateColorAsState(
                        targetValue =
                            if (autoDownloadEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        animationSpec = tween(300),
                        label = "downloadContainerColor",
                    )

                    val downloadContentColor by animateColorAsState(
                        targetValue =
                            if (autoDownloadEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        animationSpec = tween(300),
                        label = "downloadContentColor",
                    )

                    val cloudDownloadIcon = ImageVector.vectorResource(cx.aswin.boxlore.feature.info.R.drawable.ic_cloud_download)
                    ToolbarIconButton(
                        icon = cloudDownloadIcon,
                        contentDescription = "Toggle auto-download",
                        containerColor = downloadContainerColor,
                        contentColor = downloadContentColor,
                        onClick = onAutoDownloadToggle,
                        modifier =
                            Modifier
                                .size(buttonSize)
                                .graphicsLayer {
                                    scaleX = downloadScale
                                    scaleY = downloadScale
                                },
                        iconSize = iconSize,
                    )
                }

                // Sort Button
                ToolbarIconButton(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "Sort",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onSortToggle,
                    modifier =
                        Modifier
                            .graphicsLayer { rotationX = sortRotation }
                            .size(buttonSize),
                    iconSize = iconSize,
                )

                // Search Button
                ToolbarIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "Search",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onSearchFocused,
                    modifier = Modifier.size(buttonSize),
                    iconSize = iconSize,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastInfoSearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    results: List<Episode>?,
    allEpisodes: List<Episode>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    onToggleLike: (Episode) -> Unit,
    onQueueClick: (Episode) -> Unit,
    onDownloadClick: (Episode) -> Unit,
    onToggleCompletion: (Episode) -> Unit,
    likedEpisodeIds: Set<String>,
    completedEpisodeIds: Set<String>,
    queuedEpisodeIds: Set<String>,
    playbackStateFlow: kotlinx.coroutines.flow.Flow<Map<String, cx.aswin.boxlore.feature.info.PodcastInfoViewModel.EpisodePlaybackState>>,
    isSearching: Boolean,
    accentColor: Color,
    downloadedEpisodeIds: Set<String>,
    downloadingEpisodeIds: Set<String>,
) {
    val focusRequester =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                // Unified "M3 Style" Search Bar Component
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(56.dp),
                    // Standard M3 Search Height
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = androidx.compose.foundation.shape.CircleShape, // Full Pill
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Leading Icon (Back) acts as Navigation
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        // Input Field
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search episodes...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }

                            androidx.compose.foundation.text.BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                cursorBrush =
                                    androidx.compose.ui.graphics
                                        .SolidColor(accentColor),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                            )
                        }

                        // Trailing Icon (Clear)
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        val safeResults = results ?: emptyList()
        val displayList = if (query.isEmpty()) emptyList() else safeResults

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
        ) {
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader.Expressive(
                        size = 64.dp,
                    )
                }
            } else if (query.isNotEmpty() && displayList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No episodes found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (displayList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = 16.dp,
                            bottom = 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(displayList, key = { _, ep -> ep.id }) { index, episode ->
                        val isDownloaded = downloadedEpisodeIds.contains(episode.id)
                        val isDownloading = downloadingEpisodeIds.contains(episode.id)
                        val isCompleted = completedEpisodeIds.contains(episode.id)

                        EpisodePlayStateWrapper(
                            episodeId = episode.id,
                            playbackStateFlow = playbackStateFlow,
                        ) { playState ->
                            EpisodeListItem(
                                episode = episode,
                                isLiked = likedEpisodeIds.contains(episode.id),
                                accentColor = accentColor,
                                isPlaying = playState?.isPlaying == true,
                                isResume = playState?.isResume == true,
                                progress = playState?.progress ?: 0f,
                                timeLeft = playState?.timeLeft,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                isQueued = queuedEpisodeIds.contains(episode.id),
                                isCompleted = isCompleted,
                                onClick = {
                                    onEpisodeClick(episode, index)
                                    onClose() // Close search on nav
                                },
                                onPlayClick = { onPlayClick(episode) },
                                onToggleLike = { onToggleLike(episode) },
                                onQueueClick = { onQueueClick(episode) },
                                onDownloadClick = { onDownloadClick(episode) },
                                onMarkPlayedClick = { onToggleCompletion(episode) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class FeedItem {
    abstract val id: String

    data class NormalEpisode(
        val episode: Episode,
        val globalIndex: Int,
    ) : FeedItem() {
        override val id: String = episode.id
    }

    data class SingleTrailer(
        val episode: Episode,
        val globalIndex: Int,
    ) : FeedItem() {
        override val id: String = episode.id
    }

    data class TrailerGroup(
        val trailers: List<Pair<Episode, Int>>,
    ) : FeedItem() {
        override val id: String = "trailer_group_${trailers.firstOrNull()?.first?.id ?: hashCode()}"
    }
}

fun groupEpisodes(episodes: List<Episode>): List<FeedItem> {
    val result = mutableListOf<FeedItem>()
    val currentTrailers = mutableListOf<Pair<Episode, Int>>()

    episodes.forEachIndexed { index, episode ->
        if (episode.episodeType == "trailer") {
            currentTrailers.add(episode to index)
        } else {
            if (currentTrailers.isNotEmpty()) {
                if (currentTrailers.size == 1) {
                    val (tEp, tIdx) = currentTrailers.first()
                    result.add(FeedItem.SingleTrailer(tEp, tIdx))
                } else {
                    result.add(FeedItem.TrailerGroup(currentTrailers.toList()))
                }
                currentTrailers.clear()
            }
            result.add(FeedItem.NormalEpisode(episode, index))
        }
    }

    if (currentTrailers.isNotEmpty()) {
        if (currentTrailers.size == 1) {
            val (tEp, tIdx) = currentTrailers.first()
            result.add(FeedItem.SingleTrailer(tEp, tIdx))
        } else {
            result.add(FeedItem.TrailerGroup(currentTrailers.toList()))
        }
    }

    return result
}

@Composable
fun SingleTrailerCard(
    episode: Episode,
    globalIndex: Int,
    playbackStateFlow: kotlinx.coroutines.flow.Flow<Map<String, PodcastInfoViewModel.EpisodePlaybackState>>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    EpisodePlayStateWrapper(episodeId = episode.id, playbackStateFlow = playbackStateFlow) { playState ->
        val isPlaying = playState?.isPlaying == true
        val isResume = playState?.isResume == true

        OutlinedCard(
            modifier =
                modifier
                    .fillMaxWidth()
                    .expressiveClickable { onEpisodeClick(episode, globalIndex) },
            shape = MaterialTheme.shapes.large,
            colors =
                androidx.compose.material3.CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation =
                androidx.compose.material3.CardDefaults
                    .outlinedCardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Play Button
                Surface(
                    shape = CircleShape,
                    color = if (isPlaying || isResume) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier =
                        Modifier
                            .size(40.dp) // Slightly larger play button for a better hit target
                            .expressiveClickable(isolate = true) { onPlayClick(episode) },
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint =
                            if (isPlaying ||
                                isResume
                            ) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(10.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = ExpressiveShapes.Pill,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = "Trailer",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        val durationText =
                            if (episode.duration > 0) {
                                val h = episode.duration / 3600
                                val m = (episode.duration % 3600) / 60
                                if (h > 0) "${h}hr ${m}min" else "${m}min"
                            } else {
                                ""
                            }

                        if (durationText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrailerStackCard(
    group: FeedItem.TrailerGroup,
    playbackStateFlow: kotlinx.coroutines.flow.Flow<Map<String, PodcastInfoViewModel.EpisodePlaybackState>>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.large,
        colors =
            androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation =
            androidx.compose.material3.CardDefaults
                .outlinedCardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Header Row (Always visible)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .expressiveClickable { isExpanded = !isExpanded }
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Promotional Trailers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${group.trailers.size} trailers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expanded Content (Mini-Trailers)
            if (isExpanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    group.trailers.forEachIndexed { index, (episode, globalIndex) ->
                        EpisodePlayStateWrapper(episodeId = episode.id, playbackStateFlow = playbackStateFlow) { playState ->
                            val isPlaying = playState?.isPlaying == true
                            val isResume = playState?.isResume == true

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .expressiveClickable { onEpisodeClick(episode, globalIndex) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Play Button
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (isPlaying ||
                                            isResume
                                        ) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .expressiveClickable(isolate = true) { onPlayClick(episode) },
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint =
                                            if (isPlaying ||
                                                isResume
                                            ) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Text
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = episode.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val durationText =
                                        if (episode.duration > 0) {
                                            val h = episode.duration / 3600
                                            val m = (episode.duration % 3600) / 60
                                            if (h > 0) "${h}hr ${m}min" else "${m}min"
                                        } else {
                                            "Trailer"
                                        }

                                    Text(
                                        text = durationText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (index < group.trailers.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(start = 64.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun EpisodePlayStateWrapper(
    episodeId: String,
    playbackStateFlow: kotlinx.coroutines.flow.Flow<Map<String, PodcastInfoViewModel.EpisodePlaybackState>>,
    content: @Composable (PodcastInfoViewModel.EpisodePlaybackState?) -> Unit,
) {
    val playStateFlow =
        remember(episodeId, playbackStateFlow) {
            playbackStateFlow.map { it[episodeId] }.distinctUntilChanged()
        }
    val playState by playStateFlow.collectAsState(initial = null)
    content(playState)
}

private enum class ToolbarWarning {
    NONE,
    NOTIFICATIONS_REQUIRED,
    SYSTEM_PERMISSION_BLOCKED,
}

private fun areAppNotificationsEnabled(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (androidx.core.content.ContextCompat
                .checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }
    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        notificationManager.areNotificationsEnabled()
    } else {
        androidx.core.app.NotificationManagerCompat
            .from(context)
            .areNotificationsEnabled()
    }
}

private fun openAppNotificationSettings(context: android.content.Context) {
    val intent =
        android.content.Intent().apply {
            when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> {
                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                else -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
            }
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
    context.startActivity(intent)
}
