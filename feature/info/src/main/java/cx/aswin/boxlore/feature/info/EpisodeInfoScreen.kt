package cx.aswin.boxlore.feature.info

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.RemoveDownloadConfirmationDialog
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeInsetsDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneStatusBarsPadding
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneTopInset
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.info.logic.EpisodeHeroForm
import cx.aswin.boxlore.feature.info.logic.EpisodeShowTitle
import cx.aswin.boxlore.feature.info.logic.episodeHeroArtSizeDp
import cx.aswin.boxlore.feature.info.logic.episodeHeroForm
import cx.aswin.boxlore.feature.info.logic.hingeCrossesPage

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
    TrackScreenView("episode_detail", episodeId, send = {}) // episode_detail_viewed goes out once the episode loads
    val showStandIn = stringResource(R.string.episode_info_show_placeholder)
    val shownPodcastTitle = EpisodeShowTitle.displayName(podcastTitle, showStandIn)
    val uiState by viewModel.uiState.collectAsState()
    val moreLikeThisOn by viewModel.moreLikeThisOn.collectAsState()
    val pageEpisode = EpisodeLoadRequests.pageEpisode(uiState, episodeId)
    val isLoadingPage = uiState is EpisodeInfoUiState.Loading || uiState is EpisodeInfoUiState.ReadingFeed
    cx.aswin.boxlore.feature.info.components.ArtworkInfoTheme(listOf(pageEpisode?.imageUrl ?: episodeImageUrl, pageEpisode?.podcastImageUrl), isLoading = isLoadingPage) {
    val likedEpisodeIds by viewModel.likedEpisodeIds.collectAsState()
    val completedEpisodeIds by viewModel.completedEpisodeIds.collectAsState()
    val archivedEpisodeIds by viewModel.archivedEpisodeIds.collectAsState()
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
    val watchActions = cx.aswin.boxlore.core.designsystem.components.LocalWatchEpisodeActions.current
    var watchMessage by remember { mutableStateOf<Int?>(null) }
    // Saved, so an open Share sheet survives recreation (a keyboard attached, a display size change).
    var showShareSheet by rememberSaveable { mutableStateOf(false) }

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

    // Download state of the page's episode: the phone's own ID when the link named another one.
    val downloadEpisodeId = pageEpisode?.id ?: episodeId
    val isDownloaded by remember(downloadEpisodeId) { viewModel.isDownloaded(downloadEpisodeId) }.collectAsState(initial = false)
    val isDownloading by remember(downloadEpisodeId) { viewModel.isDownloading(downloadEpisodeId) }.collectAsState(initial = false)
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
    // The status bar over this pane only: none for the lower half in tabletop or a pane below the top.
    val statusBarHeight = boxlorePaneTopInset()
    val (safeStart, safeEnd) = boxloreHorizontalSafeInsetsDp()
    val collapsedHeaderHeight = 64.dp + statusBarHeight

    // Header background: the page's own colour while scrolling, the deeper container once content runs under it.
    val contentUnder by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val headerColor by animateColorAsState(
        targetValue = episodeHeaderColor(MaterialTheme.colorScheme, scrollFraction, contentUnder),
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

    // Horizontal padding in header: clear of Back and Share (48dp buttons 4dp from the edges, as on
    // the show page) and of the side system buttons or camera cutout on a sideways phone.
    val titleHorizontalPadding = EpisodeHeaderButtonInset + 48.dp + 8.dp

    val isShortPane = boxloreIsShortPane()
    val windowLayout = LocalBoxloreWindowLayout.current
    val paneBounds = LocalBoxlorePaneBounds.current
    val hingeAcross = windowLayout.tabletopFold?.bounds?.let { hinge ->
        hingeCrossesPage(hinge.top, hinge.bottom, paneBounds?.top ?: 0f, paneBounds?.bottom ?: windowLayout.heightDp)
    } == true
    cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme {
        // The page reads at most the reading column's width itself, so its layout never depends on
        // where the artwork theme applies that cap.
        BoxWithConstraints(Modifier.boxloreCardWidth(BoxloreCardFamily.Reading).fillMaxSize()) {
            // The page's own width, not the window's: a detail pane beside a list keeps the phone layout.
            val pageWidthDp = maxWidth.value
            val heroForm = episodeHeroForm(pageWidthDp, isShortPane, hingeAcross)
            val heroArtSize = episodeHeroArtSizeDp(heroForm, pageWidthDp).dp
            // Off the phone layout every block (the hero included) stops at one readable width, on the cards' edges.
            val blockCap = if (heroForm == EpisodeHeroForm.Stacked) Modifier else Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start).widthIn(max = EpisodeBlockMaxWidth)
            val heroEdges = if (heroForm == EpisodeHeroForm.Stacked) Modifier.padding(horizontal = EpisodePageTextGutter) else blockCap.padding(horizontal = EpisodePageBlockGutter)
            KeepPlayRowInView(listState, heroForm)
            when (val state = uiState) {
                is EpisodeInfoUiState.Loading -> {
                    // The page's outline while it loads, with Back already where it will be.
                    Box(modifier = modifier.fillMaxSize()) {
                        cx.aswin.boxlore.feature.info.components.EpisodePageSkeleton(
                            form = heroForm,
                            title = episodeTitle,
                            artSize = heroArtSize,
                            topPadding = collapsedHeaderHeight + 16.dp,
                            railMaxWidth = EpisodeRailMaxWidth,
                            blockModifier = blockCap,
                            modifier = Modifier.boxloreHorizontalSafePadding(),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(collapsedHeaderHeight)
                                .boxlorePaneStatusBarsPadding()
                                .boxloreHorizontalSafePadding(),
                        ) {
                            cx.aswin.boxlore.feature.info.components.EpisodeInfoHeaderButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                label = stringResource(R.string.episode_info_back),
                                onClick = onBack,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = EpisodeHeaderButtonInset),
                            )
                        }
                    }
                }
                is EpisodeInfoUiState.Error ->
                    cx.aswin.boxlore.feature.info.components.InfoStatusMessage(
                        icon = Icons.Rounded.CloudOff,
                        title = stringResource(R.string.episode_info_load_error),
                        body = stringResource(R.string.info_load_error_body),
                        primary = cx.aswin.boxlore.feature.info.components.InfoStatusAction(stringResource(R.string.podcast_info_retry), viewModel::retry),
                        secondary = cx.aswin.boxlore.feature.info.components.InfoStatusAction(stringResource(R.string.episode_info_back), onBack),
                        modifier = modifier,
                    )
                is EpisodeInfoUiState.ReadingFeed ->
                    cx.aswin.boxlore.feature.info.components.EpisodeLinkStatus(true, "", onPodcastClick, onBack, modifier)
                is EpisodeInfoUiState.NotFound ->
                    cx.aswin.boxlore.feature.info.components.EpisodeLinkStatus(false, state.podcastId, onPodcastClick, onBack, modifier)
                is EpisodeInfoUiState.Success -> {
                    Box(modifier = modifier.fillMaxSize()) {
                        cx.aswin.boxlore.feature.info.components.InfoArtworkBackground(
                            imageUrl = state.episode.imageUrl,
                            fallbackUrls = listOf(state.episode.podcastImageUrl),
                            height = collapsedHeaderHeight + heroArtSize + 200.dp,
                            scrollOffset = scrollOffset,
                            scrollFraction = scrollFraction,
                        )
                        // Content List
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().testTag(EpisodeInfoTags.LIST),
                            contentPadding =
                            boxloreHorizontalSafeContentPadding(
                                PaddingValues(
                                    top = collapsedHeaderHeight + 16.dp,
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomContentPadding + 160.dp, // Extra for miniplayer
                                ),
                            ),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            val actionRail: @Composable (Modifier) -> Unit = { railModifier ->
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
                                    modifier = railModifier.testTag(EpisodeInfoTags.RAIL),
                                )
                            }
                            item(key = HERO_KEY) {
                                cx.aswin.boxlore.feature.info.components.EpisodeInfoHero(
                                    episode = state.episode,
                                    podcastTitle = EpisodeShowTitle.displayName(state.podcastTitle, showStandIn),
                                    onPodcastClick = {
                                        viewModel.onPodcastLinkClicked()
                                        onPodcastClick(state.podcastId)
                                    },
                                    completionState = cx.aswin.boxlore.feature.info.components.EpisodeCompletionState(
                                        isCompleted = state.episode.id in completedEpisodeIds,
                                        showTip = showMarkPlayedTip,
                                        isArchived = state.episode.id in archivedEpisodeIds,
                                    ),
                                    onToggleCompletion = viewModel::onToggleCompletion,
                                    modifier = heroEdges,
                                    onMarkPlayedTipDismissed = onMarkPlayedTipDismissed,
                                    form = heroForm,
                                    artSize = heroArtSize,
                                    rail = { actionRail(Modifier.widthIn(max = EpisodeRailMaxWidth)) },
                                )
                            }
                            episodeRailRow(heroForm, actionRail)
                            episodeSections(
                                state = state,
                                blockCap = blockCap,
                                moreLikeThisOn = moreLikeThisOn == true,
                                isMoreFromScrolling = { isMoreFromScrolling },
                                viewModel = viewModel,
                                onPodcastClick = onPodcastClick,
                                onEpisodeClick = onEpisodeClick,
                            )
                        }
                    }

                    // HEADER OVERLAY (Back button + animated background)
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(collapsedHeaderHeight)
                            .background(headerColor)
                            .boxlorePaneStatusBarsPadding()
                            .boxloreHorizontalSafePadding(),
                    ) {
                        // Back Button
                        cx.aswin.boxlore.feature.info.components.EpisodeInfoHeaderButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            label = androidx.compose.ui.res.stringResource(R.string.episode_info_back),
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = EpisodeHeaderButtonInset),
                        )

                        // Watch button: only while a watch is in reach; it sits beside Share.
                        watchActions?.let { actions ->
                            cx.aswin.boxlore.feature.info.components.EpisodeWatchButton(
                                episode = state.episode,
                                actions = actions,
                                onMessage = { watchMessage = it },
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = EpisodeHeaderButtonInset + cx.aswin.boxlore.feature.info.components.EpisodeWatchHeader.ButtonSlot),
                            )
                        }

                        // Share Button
                        cx.aswin.boxlore.feature.info.components.EpisodeInfoHeaderButton(
                            icon = Icons.Rounded.Share,
                            label = androidx.compose.ui.res.stringResource(R.string.episode_info_share),
                            onClick = { showShareSheet = true },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = EpisodeHeaderButtonInset),
                        )
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
                            // With the watch button in the bar the name stops before it, not under it.
                            .padding(
                                start = titleHorizontalPadding + safeStart,
                                end = titleHorizontalPadding + safeEnd + cx.aswin.boxlore.feature.info.components.EpisodeWatchHeader.titleEndExtra(watchActions != null),
                            )
                            .graphicsLayer {
                                translationY = titleTranslationY
                                alpha = titleAlpha
                            },
                    )
                    // What just happened on the watch, right under the bar whose button was tapped, where
                    // the mini player and the navigation bar never cover it. It takes no touches.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        cx.aswin.boxlore.feature.info.components.EpisodeWatchMessage(
                            message = watchMessage,
                            onShown = { watchMessage = null },
                            modifier = Modifier.padding(top = collapsedHeaderHeight + 8.dp),
                        )
                    }
                }
            }
        }

        // Outside the page states, so a reload or a window change never closes an open sheet.
        if (showShareSheet) {
            val currentSuccessState = uiState as? cx.aswin.boxlore.feature.info.EpisodeInfoUiState.Success
            val shareEpisode =
                currentSuccessState?.let(EpisodeLoadRequests::shareEpisode) ?: cx.aswin.boxlore.core.model.Episode(
                    id = episodeId,
                    title = episodeTitle,
                    description = episodeDescription,
                    audioUrl = episodeAudioUrl,
                    imageUrl = episodeImageUrl,
                    duration = episodeDuration,
                    podcastId = podcastId.takeIf(String::isNotBlank),
                )
            cx.aswin.boxlore.core.designsystem.components.ShareBottomSheet(
                id = shareEpisode.id,
                type = "episode",
                title = shareEpisode.title,
                subtitle = shownPodcastTitle,
                imageUrl = shareEpisode.imageUrl ?: shareEpisode.podcastImageUrl,
                onDismissRequest = { showShareSheet = false },
                durationMs = shareEpisode.duration * 1000L,
                currentPositionMs = currentSuccessState?.resumePositionMs ?: 0L,
                showTimestampOption = false,
                episode = shareEpisode,
                onShare = { _, _, timestamp, target ->
                    cx.aswin.boxlore.core.designsystem.share.ShareManager.shareEpisode(
                        context = context,
                        episode = shareEpisode,
                        podcastTitle = shownPodcastTitle,
                        timestampMs = timestamp,
                        target = target,
                    )
                },
            )
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
}

/**
 * The Play row moves into the hero in two columns. A page scrolled into the hero or to the row would
 * otherwise keep its offset (or lose its place, the row's item being gone) and leave Play above the
 * screen, so it goes to the top of the hero.
 */
@Composable
private fun KeepPlayRowInView(listState: LazyListState, heroForm: EpisodeHeroForm) {
    val formMemory = remember { EpisodeHeroFormMemory(heroForm) }
    if (formMemory.form != heroForm && heroForm == EpisodeHeroForm.TwoColumn) {
        // The layout measured before this change, read without subscribing to every scroll.
        // The first item under the top bar, not one peeking into the list's top padding.
        val top = Snapshot.withoutReadObservation {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }?.key
        }
        if (top == HERO_KEY || top == RAIL_KEY) listState.requestScrollToItem(0)
    }
    SideEffect { formMemory.form = heroForm }
}

/** The Play row's own list item: beside the cover in the two-column layout, its own row otherwise. */
private fun LazyListScope.episodeRailRow(heroForm: EpisodeHeroForm, actionRail: @Composable (Modifier) -> Unit) {
    if (heroForm == EpisodeHeroForm.TwoColumn) return
    item(key = RAIL_KEY) {
        val railCap = if (heroForm == EpisodeHeroForm.Stacked) Modifier else Modifier.widthIn(max = EpisodeRailMaxWidth)
        actionRail(Modifier.padding(horizontal = EpisodePageBlockGutter).then(railCap))
    }
}

/** Everything under the hero and the Play row, in page order. */
private fun LazyListScope.episodeSections(
    state: EpisodeInfoUiState.Success,
    blockCap: Modifier,
    moreLikeThisOn: Boolean,
    isMoreFromScrolling: () -> Boolean,
    viewModel: EpisodeInfoViewModel,
    onPodcastClick: (String) -> Unit,
    onEpisodeClick: (cx.aswin.boxlore.core.model.Episode) -> Unit,
) {
    state.crossPromotion?.let { crossPromo ->
        item(key = "cross_promo") {
            CrossPromotionCard(
                crossPromotion = crossPromo,
                onPodcastClick = onPodcastClick,
                modifier = blockCap.padding(horizontal = 16.dp),
            )
        }
    }
    // What the episode is about comes before its chapters.
    episodeAbout(state, viewModel::seekToPosition, blockCap)
    if (state.chapters.isNotEmpty()) {
        item(key = "chapters") {
            cx.aswin.boxlore.feature.info.components.EpisodeChaptersSection(
                episodeId = state.episode.id,
                chapters = state.chapters,
                positionMs = state.resumePositionMs,
                onSeekTo = viewModel::seekToPosition,
                modifier = blockCap.padding(horizontal = 16.dp),
            )
        }
    }
    episodeLinks(state, blockCap)
    if (moreLikeThisOn && (state.similarEpisodesLoading || state.similarEpisodes.isNotEmpty())) {
        item(key = "more_like_this") {
            // A carousel: it keeps the page's full width.
            cx.aswin.boxlore.feature.info.sections.EpisodeInfoMoreLikeThisCard(
                state = state,
                onEpisodeClick = onEpisodeClick,
            )
        }
    }
    // Latest episodes from this show, sharing the page's vertical scroll.
    item(key = "more_from_podcast") {
        Box(blockCap.testTag(EpisodeInfoTags.MORE_FROM)) {
            cx.aswin.boxlore.feature.info.sections.EpisodeInfoMoreFromPodcastCard(
                state = state,
                onPodcastClick = onPodcastClick,
                onEpisodeClick = onEpisodeClick,
                onPodcastLinkClicked = viewModel::onPodcastLinkClicked,
                onRelatedEpisodesScrolled = viewModel::onRelatedEpisodesScrolled,
                isSectionScrollEngaged = isMoreFromScrolling(),
                onRelatedEpisodeClicked = viewModel::onRelatedEpisodeClicked,
            )
        }
    }
}

private fun LazyListScope.episodeAbout(
    state: EpisodeInfoUiState.Success,
    onSeekTo: (Long) -> Unit,
    blockCap: Modifier,
) {
    val notes = state.showNotes ?: return
    if (notes.plainText.isBlank()) return
    item(key = "about") {
        Box(blockCap.testTag(EpisodeInfoTags.ABOUT)) {
            EpisodeDescriptionCard(
                notes = notes,
                location = state.location,
                license = state.license,
                persons = state.episode.persons,
                onSeekTo = onSeekTo,
            )
        }
    }
}

private fun LazyListScope.episodeLinks(state: EpisodeInfoUiState.Success, blockCap: Modifier) {
    val notes = state.showNotes ?: return
    if (notes.links.isEmpty()) return
    item(key = "links") {
        Box(blockCap) {
            EpisodeLinksSection(notes.links, EpisodeShowTitle.displayName(state.podcastTitle, stringResource(R.string.episode_info_show_placeholder)))
        }
    }
}

/** Cards, rows and the Play row start here; text without a card (the title, headings) starts at the text gutter. */
internal val EpisodePageBlockGutter = 16.dp
internal val EpisodePageTextGutter = 20.dp

/** Back and Share sit this far from the page edges, matching the show page's top bar. */
private val EpisodeHeaderButtonInset = 4.dp

/** The hero's list item, the page's first. */
private const val HERO_KEY = "hero"

/** The Play row's list item, right after the hero (absent in the two-column layout). */
private const val RAIL_KEY = "rail"

/** The hero form the page last committed, to notice a change while composing the next one. */
private class EpisodeHeroFormMemory(var form: EpisodeHeroForm)

/** Off the phone layout the Play row stops here instead of stretching across the page. */
private val EpisodeRailMaxWidth = 480.dp

/** Off the phone layout cards and rows stop here, keeping lines a readable length. */
private val EpisodeBlockMaxWidth = 672.dp

/** Test tags for the episode page's list and Play row. */
internal object EpisodeInfoTags {
    const val LIST = "episode_info_list"
    const val RAIL = "episode_info_rail"
    const val ABOUT = "episode_info_about"
    const val MORE_FROM = "episode_info_more_from"
}

/**
 * The episode top bar's fill: the page's `surface`, fading in with [scrollFraction], then the deeper
 * `surfaceContainer` once content runs under the bar. Never fades from `Color.Transparent`, whose
 * black would flash.
 */
internal fun episodeHeaderColor(scheme: androidx.compose.material3.ColorScheme, scrollFraction: Float, contentUnder: Boolean): androidx.compose.ui.graphics.Color =
    if (contentUnder) scheme.surfaceContainer else scheme.surface.copy(alpha = scrollFraction.coerceIn(0f, 1f))
