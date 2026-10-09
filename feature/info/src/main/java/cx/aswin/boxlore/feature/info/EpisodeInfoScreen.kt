package cx.aswin.boxlore.feature.info

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.RemoveDownloadConfirmationDialog
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EpisodeInfoScreen(
    episodeId: String,
    episodeTitle: String,
    episodeDescription: String,
    episodeImageUrl: String,
    episodeAudioUrl: String,
    episodeDuration: Int,
    podcastId: String,
    podcastTitle: String,
    viewModel: EpisodeInfoViewModel,
    onBack: () -> Unit,
    onPodcastClick: (String) -> Unit,
    onEpisodeClick: (cx.aswin.boxlore.core.model.Episode) -> Unit,
    onPlay: () -> Unit,
    entryPointContext: android.os.Bundle? = null,
    showMarkPlayedTip: Boolean = false,
    onMarkPlayedTipDismissed: () -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val likedEpisodeIds by viewModel.likedEpisodeIds.collectAsState()
    val completedEpisodeIds by viewModel.completedEpisodeIds.collectAsState()
    val queuedEpisodeIds by viewModel.queuedEpisodeIds.collectAsState()
    val listState = rememberLazyListState()
    val isMoreFromScrolling by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val section = layout.visibleItemsInfo.firstOrNull { it.key == "more_from_podcast" }
            cx.aswin.boxlore.feature.info.logic.relatedSectionScrollEngaged(
                listState.isScrollInProgress,
                section?.offset,
                section?.size ?: 0,
                layout.viewportStartOffset,
                layout.viewportEndOffset,
            )
        }
    }
    val context = LocalContext.current
    val density = LocalDensity.current

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.trackScreenExit()
                    androidx.lifecycle.Lifecycle.Event.ON_START -> viewModel.onScreenResume()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.trackScreenExit() // Fallback if disposed directly
        }
    }

    LaunchedEffect(episodeId) {
        viewModel.loadEpisode(
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            episodeDescription = episodeDescription,
            episodeImageUrl = episodeImageUrl,
            episodeAudioUrl = episodeAudioUrl,
            episodeDuration = episodeDuration,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            entryPointContext = entryPointContext,
        )
    }

    // Download State
    val isDownloaded by viewModel.isDownloaded(episodeId).collectAsState(initial = false)
    val isDownloading by viewModel.isDownloading(episodeId).collectAsState(initial = false)
    val showRemoveDownloadDialog by viewModel.showRemoveDownloadDialog.collectAsState()

    // Scroll-driven animation state
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                1000f // Fully collapsed
            }
        }
    }

    val morphThreshold = with(density) { 180.dp.toPx() }
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

    // Title animation - header only fade-in
    val titleSizeStart = MaterialTheme.typography.titleLarge.fontSize
    val titleSizeEnd = MaterialTheme.typography.titleMedium.fontSize
    val titleFontSize =
        androidx.compose.ui.unit
            .lerp(titleSizeStart, titleSizeEnd, scrollFraction)

    // Y position fixed in header
    val headerTitleYPx = with(density) { (statusBarHeight + 18.dp).toPx() }
    val titleTranslationY by animateFloatAsState(
        targetValue = headerTitleYPx,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.85f),
        label = "titleY",
    )

    // MaxLines: 1 when in header
    val titleMaxLines = 1
    // Fade in only when header collapses
    val titleAlpha = if (scrollFraction > 0.8f) (scrollFraction - 0.8f) / 0.2f else 0f

    // Horizontal padding in header
    val titleHorizontalPadding by animateDpAsState(
        targetValue = 76.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "titlePadding",
    )

    cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme {
        when (val state = uiState) {
            is EpisodeInfoUiState.Loading -> {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxLoreLoader.Expressive(size = 80.dp)
                }
            }
            is EpisodeInfoUiState.Error -> {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.episode_info_load_error), color = MaterialTheme.colorScheme.error)
                }
            }
            is EpisodeInfoUiState.Success -> {
                Box(modifier = modifier.fillMaxSize()) {
                    // Blurred Background Header
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(collapsedHeaderHeight + 400.dp)
                            .clipToBounds()
                            .graphicsLayer {
                                translationY = -scrollOffset * 0.5f
                                alpha = 1f - scrollFraction
                            },
                    ) {
                        OptimizedImage(
                            url = state.episode.imageUrl?.takeIf(String::isNotBlank) ?: state.episode.podcastImageUrl,
                            proxyWidth = 400,
                            contentDescription = null,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .alpha(0.48f)
                                .blur(80.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
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
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            MaterialTheme.colorScheme.background,
                                        ),
                                    ),
                                ),
                        )
                    }
                    // Content List
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                        PaddingValues(
                            top = collapsedHeaderHeight + 16.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomContentPadding + 160.dp, // Extra for miniplayer
                        ),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        item {
                            cx.aswin.boxlore.feature.info.components.EpisodeInfoHero(
                                episode = state.episode,
                                podcastTitle = state.podcastTitle,
                                onPodcastClick = {
                                    viewModel.onPodcastLinkClicked()
                                    onPodcastClick(state.podcastId)
                                },
                                completionState = cx.aswin.boxlore.feature.info.components.EpisodeCompletionState(
                                    isCompleted = state.episode.id in completedEpisodeIds,
                                    showTip = showMarkPlayedTip,
                                ),
                                onToggleCompletion = viewModel::onToggleCompletion,
                                modifier = Modifier.padding(horizontal = 22.dp),
                                onMarkPlayedTipDismissed = onMarkPlayedTipDismissed,
                            )
                        }
                        item {
                            cx.aswin.boxlore.feature.info.components.EpisodeActionRail(
                                state = cx.aswin.boxlore.feature.info.components.EpisodeActionRailState(
                                    isPlaying = state.isPlaying,
                                    isPlaybackLoading = state.isPlaybackLoading,
                                    isLiked = state.episode.id in likedEpisodeIds,
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading,
                                    isQueued = state.episode.id in queuedEpisodeIds,
                                    isCompleted = state.episode.id in completedEpisodeIds,
                                    positionMs = state.resumePositionMs,
                                    durationMs = state.durationMs,
                                ),
                                callbacks = cx.aswin.boxlore.feature.info.components.EpisodeActionRailCallbacks(
                                    onMainActionClick = { viewModel.onMainActionClick(entryPointContext) },
                                    onLikeClick = { viewModel.onToggleLike(state.episode) },
                                    onDownloadClick = { viewModel.toggleDownload(state.episode) },
                                    onQueueClick = viewModel::toggleQueue,
                                ),
                                modifier = Modifier.padding(horizontal = 22.dp),
                            )
                        }

                        // CROSS-PROMOTION CARD
                        state.crossPromotion?.let { crossPromo ->
                            item {
                                CrossPromotionCard(
                                    crossPromotion = crossPromo,
                                    onPodcastClick = onPodcastClick,
                                    modifier =
                                    Modifier
                                        .padding(horizontal = 16.dp),
                                )
                            }
                        }

                        if (state.chapters.isNotEmpty()) {
                            item {
                                cx.aswin.boxlore.feature.info.components.EpisodeChaptersSection(
                                    episodeId = state.episode.id,
                                    chapters = state.chapters,
                                    positionMs = state.resumePositionMs,
                                    onSeekTo = viewModel::seekToPosition,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        state.showNotes?.let { notes ->
                            if (notes.plainText.isNotBlank()) {
                                item {
                                    EpisodeDescriptionCard(
                                        notes = notes,
                                        location = state.location,
                                        license = state.license,
                                        persons = state.episode.persons,
                                        onSeekTo = viewModel::seekToPosition,
                                    )
                                }
                            }
                            if (notes.links.isNotEmpty()) {
                                item { EpisodeLinksSection(notes.links, state.podcastTitle) }
                            }
                        }

                        // Contextual "MORE LIKE THIS" RECOMMENDATIONS SECTION -> Card
                        if (state.similarEpisodesLoading || state.similarEpisodes.isNotEmpty()) {
                            item {
                                cx.aswin.boxlore.feature.info.sections.EpisodeInfoMoreLikeThisCard(
                                    state = state,
                                    onEpisodeClick = onEpisodeClick,
                                )
                            }
                        }

                        // Latest episodes from this show, sharing the page's vertical scroll.
                        item(key = "more_from_podcast") {
                            cx.aswin.boxlore.feature.info.sections.EpisodeInfoMoreFromPodcastCard(
                                state = state,
                                onPodcastClick = onPodcastClick,
                                onEpisodeClick = onEpisodeClick,
                                onPodcastLinkClicked = viewModel::onPodcastLinkClicked,
                                onRelatedEpisodesScrolled = viewModel::onRelatedEpisodesScrolled,
                                isSectionScrollEngaged = isMoreFromScrolling,
                                onRelatedEpisodeClicked = viewModel::onRelatedEpisodeClicked,
                            )
                        }
                    }
                }

                // HEADER OVERLAY (Back button + animated background)
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(collapsedHeaderHeight)
                        .background(headerColor)
                        .statusBarsPadding(),
                ) {
                    // Back Button
                    cx.aswin.boxlore.feature.info.components.EpisodeInfoHeaderButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = androidx.compose.ui.res.stringResource(R.string.episode_info_back),
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                    )

                    // Share Button
                    var showShareSheet by remember { mutableStateOf(false) }
                    cx.aswin.boxlore.feature.info.components.EpisodeInfoHeaderButton(
                        icon = Icons.Rounded.Share,
                        label = androidx.compose.ui.res.stringResource(R.string.episode_info_share),
                        onClick = { showShareSheet = true },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    )

                    if (showShareSheet) {
                        val currentSuccessState = uiState as? cx.aswin.boxlore.feature.info.EpisodeInfoUiState.Success
                        val shareEpisode =
                            currentSuccessState?.episode ?: cx.aswin.boxlore.core.model.Episode(
                                id = episodeId,
                                title = episodeTitle,
                                description = episodeDescription,
                                audioUrl = episodeAudioUrl,
                                imageUrl = episodeImageUrl,
                                duration = episodeDuration,
                            )
                        cx.aswin.boxlore.core.designsystem.components.ShareBottomSheet(
                            id = shareEpisode.id,
                            type = "episode",
                            title = shareEpisode.title,
                            subtitle = podcastTitle,
                            imageUrl = shareEpisode.imageUrl ?: shareEpisode.podcastImageUrl,
                            onDismissRequest = { showShareSheet = false },
                            durationMs = shareEpisode.duration * 1000L,
                            currentPositionMs = currentSuccessState?.resumePositionMs ?: 0L,
                            showTimestampOption = false,
                            onShare = { _, _, timestamp, target ->
                                cx.aswin.boxlore.core.designsystem.share.ShareManager.shareEpisode(
                                    context = context,
                                    episode = shareEpisode,
                                    podcastTitle = podcastTitle,
                                    timestampMs = timestamp,
                                    target = target,
                                )
                            },
                        )
                    }
                }

                // FLOATING TITLE - physically moves from body to header
                Text(
                    text = state.episode.title,
                    fontSize = titleFontSize,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = titleHorizontalPadding)
                        .graphicsLayer {
                            translationY = titleTranslationY
                            alpha = titleAlpha
                        },
                )
            }
        }

        if (showRemoveDownloadDialog) {
            RemoveDownloadConfirmationDialog(
                episodeTitle = (uiState as? EpisodeInfoUiState.Success)?.episode?.title ?: episodeTitle,
                onConfirm = viewModel::confirmDownloadRemoval,
                onDismiss = viewModel::dismissDownloadRemoval,
            )
    }
    }
}
