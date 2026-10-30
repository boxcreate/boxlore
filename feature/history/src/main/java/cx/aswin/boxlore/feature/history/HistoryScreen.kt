package cx.aswin.boxlore.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcherStyle
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.share.ShareManager
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.history.insights.InsightsCallbacks
import cx.aswin.boxlore.feature.history.insights.InsightsTab
import cx.aswin.boxlore.feature.history.story.EarlierStories
import cx.aswin.boxlore.feature.history.story.StoryDialog
import cx.aswin.boxlore.feature.history.story.StoryEntryCard
import cx.aswin.boxlore.feature.history.story.StoryText
import cx.aswin.boxlore.feature.history.timeline.JumpToDaySheet
import cx.aswin.boxlore.feature.history.timeline.JumpToDayState
import cx.aswin.boxlore.feature.history.timeline.TimelineEpisodeActions
import cx.aswin.boxlore.feature.history.timeline.TimelineFilter
import cx.aswin.boxlore.feature.history.timeline.TimelineGrouping
import cx.aswin.boxlore.feature.history.timeline.TimelineTab
import cx.aswin.boxlore.feature.history.timeline.TimelineTabCallbacks
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import cx.aswin.boxlore.feature.history.timeline.timelineIndexFor
import kotlinx.coroutines.flow.first

/**
 * Listening history: an Insights tab, which it opens on, and a Timeline tab (search, Jump back in,
 * filters, each month by day or show). Tapping a row opens the episode; its cover's play disc resumes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (ListeningHistoryItem) -> Unit,
    onExploreClick: () -> Unit,
    onPlayEpisode: (ListeningHistoryItem) -> Unit = {},
    onOpenShow: (podcastId: String) -> Unit = {},
) {
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val timeline by viewModel.timelineState.collectAsStateWithLifecycle()
    val insights by viewModel.insightsState.collectAsStateWithLifecycle()
    val story by viewModel.storyState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val timelineListState = rememberLazyListState()
    // The large title collapses as either tab scrolls, as before the redesign.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    HistoryScreenEffects(viewModel, snackbarHostState)
    LaunchedEffect(viewModel) {
        viewModel.scrollRequests.collect { date ->
            // Wait for the reset to every episode by day before working out where the day is.
            val ready =
                viewModel.timelineState.first {
                    it is TimelineUiState.Ready && it.filter == TimelineFilter.ALL && it.grouping == TimelineGrouping.DAYS && it.query.isEmpty()
                } as TimelineUiState.Ready
            timelineIndexFor(ready.entries, date)?.let { timelineListState.animateScrollToItem(it) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HistoryTopBar(
                tab = tab,
                canClear = (timeline as? TimelineUiState.Ready)?.content?.isHistoryEmpty == false,
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
        val bottom = appBottomChromeContentPadding(isMiniPlayerVisible = true) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).boxloreCardWidth(BoxloreCardFamily.Reading),
        ) {
            HistoryTabs(tab, viewModel::selectTab)
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
                HistoryTab.INSIGHTS -> HistoryInsightsContent(insights, viewModel, bottom)
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
        onShare = { card -> ShareManager.shareImage(context, card, text, title) },
    )
}

/**
 * The large "Listening history" title that collapses as the page scrolls (headlineMedium bold to
 * titleLarge semiBold), with back, and the tab's action: ⋮ (Clear all) on the Timeline and while
 * Insights fills in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopBar(
    tab: HistoryTab,
    canClear: Boolean,
    insightsHaveTiming: Boolean,
    onBack: () -> Unit,
    onRequestClearAll: () -> Unit,
    onShareListening: (() -> Unit)?,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val titleStyle =
        lerp(
            start = MaterialTheme.typography.headlineMedium.copy(fontWeight = GoogleSansWeight.bold),
            stop = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.semiBold),
            fraction = scrollBehavior.state.collapsedFraction,
        )
    LargeTopAppBar(
        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
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
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.history_more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_clear_all)) },
                            onClick = {
                                menuOpen = false
                                onRequestClearAll()
                            },
                            leadingIcon = { Icon(Icons.Rounded.ClearAll, contentDescription = null) },
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/** Insights | Timeline: the Subscriptions switcher at 56dp with a surfaceContainer track and 4dp inset. */
@Composable
private fun HistoryTabs(tab: HistoryTab, onSelect: (HistoryTab) -> Unit) {
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
        floating = false,
        style = DiscoveryTabSwitcherStyle(containerColor = MaterialTheme.colorScheme.surfaceContainer, minHeight = 56.dp, inset = 4.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
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
    when (state) {
        TimelineUiState.Loading -> HistoryLoading()
        is TimelineUiState.Ready ->
            if (state.content.isHistoryEmpty) {
                HistoryEmptyState(onExploreClick = navigation.onExploreClick)
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

@Composable
private fun HistoryLoading() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

/** The Insights tab: the period's stories, or what history alone can say while timing fills in. */
@Composable
private fun HistoryInsightsContent(state: InsightsUiState, viewModel: HistoryViewModel, bottomPadding: androidx.compose.ui.unit.Dp,) {
    when (state) {
        InsightsUiState.Loading -> HistoryLoading()
        is InsightsUiState.Ready ->
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
                header = {
                    state.stories.latest?.let { latest ->
                        item(key = "story") { StoryEntryCard(latest, state.stories.latestIsNew, onOpen = { viewModel.openStory(latest.month) }) }
                    }
                },
                footer = {
                    if (state.stories.earlier.isNotEmpty()) {
                        item(key = "earlier-stories") { EarlierStories(state.stories.earlier, onOpen = { viewModel.openStory(it.month) }) }
                    }
                },
            )
    }
}
