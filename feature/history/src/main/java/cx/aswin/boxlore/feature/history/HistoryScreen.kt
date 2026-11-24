package cx.aswin.boxlore.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.component.adaptivePlayerOverlayOffset
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.components.BoxloreDropdownMenu
import cx.aswin.boxlore.core.designsystem.components.BoxloreSnackbarHost
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.core.designsystem.components.discoveryTabSwitcherHeight
import cx.aswin.boxlore.core.designsystem.components.rememberBoxloreMenuState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardSpec
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeInsetsDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.layout.boxloreReadingColumn
import cx.aswin.boxlore.core.designsystem.share.ShareManager
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.history.insights.InsightsCallbacks
import cx.aswin.boxlore.feature.history.insights.InsightsLayout
import cx.aswin.boxlore.feature.history.insights.InsightsPlace
import cx.aswin.boxlore.feature.history.insights.InsightsStorySlots
import cx.aswin.boxlore.feature.history.insights.InsightsTab
import cx.aswin.boxlore.feature.history.story.EarlierStories
import cx.aswin.boxlore.feature.history.story.StoryDialog
import cx.aswin.boxlore.feature.history.story.StoryEntryCard
import cx.aswin.boxlore.feature.history.story.StoryText
import cx.aswin.boxlore.feature.history.story.earlierStories
import cx.aswin.boxlore.feature.history.timeline.JumpToDaySheet
import cx.aswin.boxlore.feature.history.timeline.JumpToDayState
import cx.aswin.boxlore.feature.history.timeline.TimelineEpisodeActions
import cx.aswin.boxlore.feature.history.timeline.TimelineTab
import cx.aswin.boxlore.feature.history.timeline.TimelineTabCallbacks
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText

/**
 * Listening history: an Insights tab, which it opens on, and a Timeline tab (search, Jump back in,
 * filters, each month by day or show). Tapping a row opens the episode; its cover's play disc resumes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod") // The two tabs, their sheets, dialogs and the story in one place.
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (ListeningHistoryItem) -> Unit,
    onExploreClick: () -> Unit,
    onPlayEpisode: (ListeningHistoryItem) -> Unit = {},
    onOpenShow: (podcastId: String) -> Unit = {},
    isMiniPlayerVisible: Boolean = true,
) {
    TrackScreenView("listening_history") { cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHistoryViewed("library_hub_card") }
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val timeline by viewModel.timelineState.collectAsStateWithLifecycle()
    val insights by viewModel.insightsState.collectAsStateWithLifecycle()
    val story by viewModel.storyState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val timelineListState = rememberLazyListState()
    // Insights keeps its place and its Earlier stories while Timeline is open, and through rotation and resizing.
    val insightsListState = rememberLazyListState()
    val insightsPlace = rememberSaveable(saver = InsightsPlace.Saver) { InsightsPlace() }
    var earlierExpanded by rememberSaveable { mutableStateOf(false) }
    // In short windows (a sideways phone, a split half, a tabletop's lower half) the title starts slim,
    // so the list shows first; it opens again once the window is tall and the open tab is at its top.
    val shortPane = boxloreIsShortPane()
    // The large title collapses as either tab scrolls, as before the redesign. Its collapse is known
    // before the bar is first measured, so a page opened in a short window starts slim from its first frame.
    val collapseLimit = with(LocalDensity.current) { (TopAppBarDefaults.LargeAppBarCollapsedHeight - TopAppBarDefaults.LargeAppBarExpandedHeight).toPx() }
    val barState = rememberTopAppBarState(initialHeightOffsetLimit = collapseLimit, initialHeightOffset = if (shortPane) collapseLimit else 0f)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
    // Short windows also stay slim when the other tab opens; tall ones keep the bar where the listener left it.
    LaunchedEffect(shortPane, if (shortPane) tab else null) {
        val list = if (tab == HistoryTab.TIMELINE) timelineListState else insightsListState
        when {
            shortPane -> barState.heightOffset = barState.heightOffsetLimit
            list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 -> barState.heightOffset = 0f
        }
    }

    HistoryScreenEffects(viewModel, snackbarHostState)
    TimelineScrollEffect(viewModel, timelineListState)

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // On large tablets Insights spreads to two columns in a wider grid, and its title follows that grid;
    // Timeline stays a reading list.
    val (safeStart, safeEnd) = boxloreHorizontalSafeInsetsDp()
    val columns = InsightsLayout.columns((maxWidth - safeStart - safeEnd).value)
    val wide = tab == HistoryTab.INSIGHTS && columns > 1
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HistoryTopBar(
                tab = tab,
                wide = wide,
                // Each tab loads only while open: Insights knows the history has rows from its shows by episodes.
                canClear =
                    (timeline as? TimelineUiState.Ready)?.content?.isHistoryEmpty == false ||
                        (insights as? InsightsUiState.Ready)?.content?.early?.showsByEpisodes?.isNotEmpty() == true,
                insightsHaveTiming = (insights as? InsightsUiState.Ready)?.content?.hasEnoughTiming == true,
                onBack = onBack,
                onRequestClearAll = { showClearDialog = true },
                onShareListening = (insights as? InsightsUiState.Ready)?.stories?.let { shelf -> shelf.latest ?: shelf.earlier.firstOrNull() }?.let { month ->
                    { viewModel.openStory(month.month, atShare = true) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // As Explore's: the switcher floats 16dp above the navigation bar and mini player, and the lists scroll fully past it.
        val chrome = appBottomChromeContentPadding(isMiniPlayerVisible, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        val switcherBottom = chrome + 16.dp
        val bottom = switcherBottom + discoveryTabSwitcherHeight()
        // Rows, the fast scroller, the switcher and Undo clear a sideways phone's cutout and side buttons.
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).boxloreHorizontalSafePadding()) {
            val gridMaxWidth = boxloreCardSpec(LocalBoxloreWindowLayout.current, BoxloreCardFamily.Grid).maximumWidthDp
            val tilesPerRow = if (wide) EarlierStories.tilesPerRow(minOf(maxWidth.value, gridMaxWidth) - INSIGHTS_SIDE_PADDING_DP * 2) else EarlierStories.TILES_PER_ROW
            // A reading width on every window, sideways phones included.
            Column(modifier = Modifier.fillMaxSize().then(if (wide) Modifier.boxloreCardWidth(BoxloreCardFamily.Grid) else Modifier.boxloreCardWidth(BoxloreCardFamily.Reading).boxloreReadingColumn())) {
                when (tab) {
                    HistoryTab.TIMELINE ->
                        HistoryTimelineContent(
                            state = timeline,
                            viewModel = viewModel,
                            listState = timelineListState,
                            bottomPadding = bottom,
                            navigation = HistoryNavigation(onEpisodeClick, onExploreClick, onPlayEpisode, onOpenShow),
                            onOpenCalendar = { showCalendar = true },
                        )
                    HistoryTab.INSIGHTS -> HistoryInsightsContent(insights, viewModel, bottom, InsightsListLayout(insightsListState, insightsPlace, columns, tilesPerRow), earlierExpanded, onExploreClick) { earlierExpanded = it }
                }
            }
            HistoryTabs(
                tab,
                Modifier.align(Alignment.BottomCenter).adaptivePlayerOverlayOffset(isMiniPlayerVisible).padding(bottom = switcherBottom),
                viewModel::selectTab,
            )
            // Undo sits 8dp above the switcher, which already clears the bar and the mini player.
            CompositionLocalProvider(LocalBoxloreMiniPlayerVisible provides isMiniPlayerVisible) {
                BoxloreSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter), extraBottom = 16.dp + discoveryTabSwitcherHeight())
            }
        }
    }
    }

    val ready = timeline as? TimelineUiState.Ready
    if (showCalendar && ready != null) {
        JumpToDaySheet(
            state = JumpToDayState(ready.content.listenedByDay, ready.content.playedDays, ready.content.firstListenDate, ready.today),
            onJump = viewModel::jumpTo,
            onDismiss = { showCalendar = false },
        )
    }
    HistoryScreenDialogs(
        showClearDialog = showClearDialog,
        viewModel = viewModel,
        onDismissClearDialog = { showClearDialog = false },
    )
    (story as? StoryUiState.Open)?.let { open -> HistoryStory(open, viewModel, onPlayEpisode) }
}

/** The monthly story, over everything; Share sends its share card through the share sheet. */
@Composable
private fun HistoryStory(open: StoryUiState.Open, viewModel: HistoryViewModel, onPlayEpisode: (ListeningHistoryItem) -> Unit) {
    val context = LocalContext.current
    val month = StoryText.monthName(open.story.month, currentLocale())
    val text = stringResource(R.string.history_story_share_text, month, durationText(open.story.totalMs))
    val title = stringResource(R.string.history_story_share, month)
    StoryDialog(
        story = open.story,
        startAtShare = open.atShare,
        onClose = viewModel::closeStory,
        onPlay = { episode ->
            viewModel.closeStory()
            onPlayEpisode(episode)
        },
        onShare = { card ->
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackShareContent(contentType = "listening_story", surface = "listening_story")
            ShareManager.shareImage(context, card, text, title)
        },
    )
}

/**
 * The large "Listening history" title that collapses as the page scrolls (headlineMedium bold to
 * titleLarge semiBold), with back, and the tab's action: ⋮ (Clear all) on the Timeline and while
 * Insights fills in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // The tab, its layout and actions, and the shared scroll behaviour.
@Composable
private fun HistoryTopBar(
    tab: HistoryTab,
    wide: Boolean,
    canClear: Boolean,
    insightsHaveTiming: Boolean,
    onBack: () -> Unit,
    onRequestClearAll: () -> Unit,
    onShareListening: (() -> Unit)?,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val menu = rememberBoxloreMenuState("history_more")
    val clearAll = stringResource(R.string.history_clear_all)
    val headline = MaterialTheme.typography.headlineMedium
    val title = MaterialTheme.typography.titleLarge
    val titleStyle =
        lerp(
            // Drawn at the boards' weights (the theme's styles carry theirs in the font).
            start = historyTextStyle(headline.fontSize.value, headline.lineHeight.value, GoogleSansWeight.bold),
            stop = historyTextStyle(title.fontSize.value, title.lineHeight.value, GoogleSansWeight.semiBold),
            fraction = scrollBehavior.state.collapsedFraction,
        )
    LargeTopAppBar(
        // Lined up with the page below it: the grid of paired cards, or the reading column.
        modifier = Modifier.boxloreCardWidth(if (wide) BoxloreCardFamily.Grid else BoxloreCardFamily.Utility).testTag(HISTORY_TOP_BAR_TAG),
        title = {
            Text(
                text = stringResource(R.string.history_title),
                style = titleStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.history_back))
            }
        },
        actions = {
            // Board A4: Share your listening opens the latest story at its share card.
            if (tab == HistoryTab.INSIGHTS && insightsHaveTiming && onShareListening != null) {
                IconButton(onClick = onShareListening) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.history_share_listening))
                }
            }
            // Boards A1 and A6: ⋮ on the Timeline and on Insights while timing fills in.
            if (canClear && (tab == HistoryTab.TIMELINE || !insightsHaveTiming)) {
                Box {
                    IconButton(onClick = menu::open) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.history_more_options))
                    }
                    BoxloreDropdownMenu(menu) {
                        group { item(clearAll, onClick = onRequestClearAll, leadingIcon = Icons.Rounded.ClearAll, destructive = true) }
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/** Insights pads its cards this much on each side. */
private const val INSIGHTS_SIDE_PADDING_DP = 16f

/** The top bar's test tag. */
const val HISTORY_TOP_BAR_TAG = "history_top_bar"

/** The Insights | Timeline switcher's test tag. */
const val HISTORY_TABS_TAG = "history_tabs"

/** Insights | Timeline: the floating switcher Explore uses, centred at the foot of the page. */
@Composable
private fun HistoryTabs(tab: HistoryTab, modifier: Modifier, onSelect: (HistoryTab) -> Unit) {
    DiscoveryTabSwitcher(
        // In HistoryTab order: Insights first.
        tabs =
        HistoryTab.entries.map { entry ->
            when (entry) {
                HistoryTab.INSIGHTS -> DiscoveryTab(stringResource(R.string.history_tab_insights), Icons.Rounded.AutoAwesome)
                HistoryTab.TIMELINE -> DiscoveryTab(stringResource(R.string.history_tab_timeline), Icons.Rounded.History)
            }
        },
        selectedIndex = tab.ordinal,
        onTabSelected = { onSelect(HistoryTab.entries[it]) },
        modifier = modifier.testTag(HISTORY_TABS_TAG),
    )
}

/** Where Listening history leads: an episode's page, Explore, playback and a show's page. */
private data class HistoryNavigation(
    val onEpisodeClick: (ListeningHistoryItem) -> Unit,
    val onExploreClick: () -> Unit,
    val onPlayEpisode: (ListeningHistoryItem) -> Unit,
    val onOpenShow: (String) -> Unit,
)

@Composable
private fun HistoryTimelineContent(
    state: TimelineUiState,
    viewModel: HistoryViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    navigation: HistoryNavigation,
    onOpenCalendar: () -> Unit,
) {
    TrackScreenView("history_timeline")
    when (state) {
        TimelineUiState.Loading -> HistoryTimelineLoading(PaddingValues(bottom = bottomPadding + 16.dp))
        is TimelineUiState.Ready ->
            if (state.content.isHistoryEmpty) {
                HistoryEmptyState(onExploreClick = navigation.onExploreClick, contentPadding = PaddingValues(bottom = bottomPadding + 16.dp))
            } else {
                TimelineTab(
                    state = state.tabState,
                    entries = state.entries,
                    callbacks =
                    TimelineTabCallbacks(
                        onQueryChange = viewModel::setQuery,
                        onFilterChange = viewModel::setFilter,
                        onGroupingChange = viewModel::setGrouping,
                        onOpenCalendar = onOpenCalendar,
                        onShowAllInProgress = viewModel::showAllInProgress,
                        onOpenShow = navigation.onOpenShow,
                        episode =
                        TimelineEpisodeActions(
                            onOpen = {
                                viewModel.episodesClickedCount++
                                navigation.onEpisodeClick(it.item)
                            },
                            onPlay = { navigation.onPlayEpisode(it.item) },
                            onGoToShow = { navigation.onOpenShow(it.item.podcastId) },
                            onRemove = { viewModel.removeHistoryItem(it.item.episodeId) },
                        ),
                    ),
                    listState = listState,
                    contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                )
            }
    }
}

/** The Insights tab: the period's stories, or what history alone can say while timing fills in. */
/** The Insights list's screen-held state (its scroll and the card in view) and its layout. */
private class InsightsListLayout(val listState: LazyListState, val place: InsightsPlace, val columns: Int, val tilesPerRow: Int)

@Suppress("LongParameterList") // The tab's state, the screen-held list state and its layout.
@Composable
private fun HistoryInsightsContent(
    state: InsightsUiState,
    viewModel: HistoryViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
    layout: InsightsListLayout,
    earlierExpanded: Boolean,
    onExploreClick: () -> Unit,
    onExpandEarlier: (Boolean) -> Unit,
) {
    when (state) {
        InsightsUiState.Loading -> HistoryInsightsLoading(PaddingValues(bottom = bottomPadding + 16.dp))
        is InsightsUiState.Ready -> {
            // One page view whether the tab shows its cards or the empty page, so clearing the history
            // (cards to empty page) is not counted as another visit.
            TrackScreenView("history_insights")
            if (state.content.isEmptyHistory) {
                HistoryEmptyState(onExploreClick = onExploreClick, contentPadding = PaddingValues(bottom = bottomPadding + 16.dp))
            } else {
                InsightsTab(
                    content = state.content,
                    period = state.period,
                    today = state.today,
                    callbacks =
                    InsightsCallbacks(
                        onPeriodChange = viewModel::setPeriod,
                        onOpenDay = { date ->
                            viewModel.selectTab(HistoryTab.TIMELINE)
                            viewModel.jumpTo(date)
                        },
                        onReviewInProgress = viewModel::showAllInProgress,
                    ),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
                    listState = layout.listState,
                    stories =
                    InsightsStorySlots(
                        header = {
                            state.stories.latest?.let { latest ->
                                item(key = "story") { StoryEntryCard(latest, state.stories.latestIsNew, onOpen = { viewModel.openStory(latest.month) }) }
                            }
                        },
                        footer = {
                            earlierStories(state.stories.earlier, state.today, earlierExpanded, layout.tilesPerRow, onExpand = onExpandEarlier, onOpen = { viewModel.openStory(it.month) })
                        },
                        headerItems = if (state.stories.latest != null) 1 else 0,
                    ),
                    columns = layout.columns,
                    place = layout.place,
                )
            }
        }
    }
}
