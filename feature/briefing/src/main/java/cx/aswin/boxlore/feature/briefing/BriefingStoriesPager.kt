package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Suppress("LongParameterList") // Pager keeps the existing playback and navigation callbacks.
@Composable
internal fun BriefingStoriesPager(
    briefing: Briefing,
    chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    isPlaying: Boolean,
    currentPositionMs: Long,
    accentColor: Color,
    onEpisodeClick: (Episode) -> Unit,
    onPlayPauseClick: (Long?) -> Unit,
    onShowSources: () -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { chapters.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    var userClickedPage by remember { mutableStateOf<Int?>(null) }
    val updateUserClickedPage = remember {
        { page: Int? -> userClickedPage = page }
    }

    // Keep one derivedStateOf instance; tick position via mutable state so the
    // chapter scan only invalidates downstream when the chapter index changes.
    var positionMs by remember {
        mutableLongStateOf(currentPositionMs)
    }
    SideEffect {
        positionMs = currentPositionMs
    }
    val activeChapterIndex by remember(chapters) {
        derivedStateOf { activeChapterIndexFor(chapters, positionMs) }
    }

    BriefingPagerEffects(
        briefing = briefing,
        chapters = chapters,
        pagerState = pagerState,
        isDragged = isDragged,
        activeChapterIndex = activeChapterIndex,
        userClickedPage = userClickedPage,
        onUserClickedPageChange = updateUserClickedPage,
    )

    val paragraphs = remember(briefing.script) {
        briefingStoryParagraphs(briefing.script)
    }
    val pagerActions = remember(scope, pagerState, updateUserClickedPage, onEpisodeClick, onPlayPauseClick, onShowSources) {
        BriefingStoriesPagerActions(
            scope = scope,
            pagerState = pagerState,
            onUserClickedPageChange = updateUserClickedPage,
            onEpisodeClick = onEpisodeClick,
            onPlayPauseClick = onPlayPauseClick,
            onShowSources = onShowSources,
        )
    }

    BriefingStoriesPagerContent(
        uiState =
        BriefingStoriesPagerUiState(
            briefing = briefing,
            chapters = chapters,
            paragraphs = paragraphs,
            activeChapterIndex = activeChapterIndex,
            isPlaying = isPlaying,
            accentColor = accentColor,
        ),
        actions = pagerActions,
    )
}

private fun activeChapterIndexFor(
    chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    currentPositionMs: Long,
): Int {
    val positionMs = currentPositionMs.toDouble()
    val index = chapters.indexOfLast { positionMs >= it.startTime * 1000.0 }
    return if (index != -1) index else 0
}

@Stable
private data class BriefingStoriesPagerUiState(
    val briefing: Briefing,
    val chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    val paragraphs: List<String>,
    val activeChapterIndex: Int,
    val isPlaying: Boolean,
    val accentColor: Color,
)

@Stable
internal data class BriefingStoriesPagerActions(
    val scope: kotlinx.coroutines.CoroutineScope,
    val pagerState: androidx.compose.foundation.pager.PagerState,
    val onUserClickedPageChange: (Int?) -> Unit,
    val onEpisodeClick: (Episode) -> Unit,
    val onPlayPauseClick: (Long?) -> Unit,
    val onShowSources: () -> Unit = {},
)

@Composable
private fun BriefingPagerEffects(
    briefing: Briefing,
    chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isDragged: Boolean,
    activeChapterIndex: Int,
    userClickedPage: Int?,
    onUserClickedPageChange: (Int?) -> Unit,
) {
    val latestUserClickedPage = rememberUpdatedState(userClickedPage)

    LaunchedEffect(activeChapterIndex) {
        syncPagerToPlayback(
            chapters = chapters,
            pagerState = pagerState,
            isDragged = isDragged,
            activeChapterIndex = activeChapterIndex,
            userClickedPage = userClickedPage,
            onUserClickedPageChange = onUserClickedPageChange,
        )
    }

    // Capture swipe intent via DragInteraction, then attribute after the page
    // settles (including post-fling), so we don't lose swipes when isDragged clears.
    LaunchedEffect(pagerState, briefing, chapters) {
        var pendingSwipe = false
        var lastSettledPage = pagerState.currentPage
        launch {
            pagerState.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> pendingSwipe = true
                    is DragInteraction.Cancel -> pendingSwipe = false
                    else -> Unit
                }
            }
        }
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (page, scrolling) ->
                if (scrolling) return@collect
                if (page == lastSettledPage) {
                    pendingSwipe = false
                    return@collect
                }
                val swiped = pendingSwipe
                val clicked = latestUserClickedPage.value
                pendingSwipe = false
                trackChapterPageInteraction(
                    briefing = briefing,
                    chapter = chapters.getOrNull(page),
                    page = page,
                    isSwipe = swiped,
                    userClickedPage = clicked,
                )
                lastSettledPage = page
            }
    }
}

private suspend fun syncPagerToPlayback(
    chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isDragged: Boolean,
    activeChapterIndex: Int,
    userClickedPage: Int?,
    onUserClickedPageChange: (Int?) -> Unit,
) {
    if (isDragged) return
    if (userClickedPage == activeChapterIndex) {
        onUserClickedPageChange(null)
        return
    }
    if (userClickedPage == null && activeChapterIndex in chapters.indices) {
        pagerState.animateScrollToPage(activeChapterIndex)
    }
}

private fun trackChapterPageInteraction(
    briefing: Briefing,
    chapter: cx.aswin.boxlore.core.model.Chapter?,
    page: Int,
    isSwipe: Boolean,
    userClickedPage: Int?,
) {
    if (chapter == null || (!isSwipe && userClickedPage == null)) return
    val swiped = isSwipe && userClickedPage == null
    AnalyticsHelper.trackDailyBriefingInteraction(
        action = if (swiped) "chapter_swiped" else "chapter_clicked",
        region = briefing.region,
        date = briefing.date,
        extraProps =
        mapOf(
            "chapter_index" to page,
            "chapter_title" to chapter.title,
            "method" to if (swiped) "swipe" else "click",
        ),
    )
}

@Composable
private fun BriefingStoriesPagerContent(
    uiState: BriefingStoriesPagerUiState,
    actions: BriefingStoriesPagerActions,
) {
    if (uiState.chapters.isEmpty()) {
        LoadingScriptPlaceholder()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Story ${actions.pagerState.currentPage + 1} of ${uiState.chapters.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(onClick = {
                    val page = actions.pagerState.currentPage - 1
                    actions.onUserClickedPageChange(page)
                    actions.scope.launch { actions.pagerState.animateScrollToPage(page) }
                }, enabled = actions.pagerState.currentPage > 0) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous story")
                }
                FilledTonalIconButton(onClick = {
                    val page = actions.pagerState.currentPage + 1
                    actions.onUserClickedPageChange(page)
                    actions.scope.launch { actions.pagerState.animateScrollToPage(page) }
                }, enabled = actions.pagerState.currentPage < uiState.chapters.lastIndex) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next story")
                }
            }
        }
        BriefingChapterPager(uiState, actions)
    }
}

private val PagerSidePadding = 24.dp

/** Cards never grow past this share of the screen; longer stories scroll inside their card. */
private const val STORY_CARD_MAX_SCREEN_FRACTION = 0.7f

/**
 * Sized to the tallest story at the current width and text size, up to a comfortable reading
 * height (70% of the screen), so cards keep one height while swiping and only long stories scroll.
 */
@Composable
private fun BriefingChapterPager(
    uiState: BriefingStoriesPagerUiState,
    actions: BriefingStoriesPagerActions,
) {
    fun cardState(page: Int) = BriefingStoryCardState(
        briefing = uiState.briefing,
        chapter = uiState.chapters[page],
        paragraph = uiState.paragraphs.getOrNull(page) ?: "",
        page = page,
        pageCount = uiState.chapters.size,
        isActive = uiState.activeChapterIndex == page && uiState.isPlaying,
        accentColor = uiState.accentColor,
    )
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * STORY_CARD_MAX_SCREEN_FRACTION).dp
    val scrollStoryAsOne = storyCardScrollsAsOne(LocalConfiguration.current.screenHeightDp)
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val availableWidth = (constraints.maxWidth - (PagerSidePadding * 2).roundToPx()).coerceAtLeast(0)
        val pageWidth = if (tablet) availableWidth.coerceAtMost(720.dp.roundToPx()) else availableWidth
        val tallest = subcompose("measure") {
            uiState.chapters.indices.forEach { page ->
                // Measured only, never placed; hidden from accessibility so stories are not announced twice.
                Box(Modifier.clearAndSetSemantics {}) { BriefingStoryCard(cardState(page), actions, fillHeight = false) }
            }
        }.maxOfOrNull { it.measure(Constraints(minWidth = pageWidth, maxWidth = pageWidth)).height }
            ?.let { tallestStory -> minOf(tallestStory, maxCardHeight.roundToPx()) } ?: 0
        val pager = subcompose("pager") {
            HorizontalPager(
                state = actions.pagerState,
                pageSize = if (tablet) PageSize.Fixed(pageWidth.toDp()) else PageSize.Fill,
                contentPadding = PaddingValues(horizontal = PagerSidePadding),
                pageSpacing = 16.dp,
                key = { page -> stableChapterKey(uiState.chapters[page]) },
                modifier = Modifier.fillMaxWidth().height(tallest.toDp()),
            ) { page ->
                BriefingStoryCard(cardState(page), actions, scrollAsOne = scrollStoryAsOne)
            }
        }.single().measure(constraints.copy(minHeight = 0))
        layout(pager.width, pager.height) { pager.place(0, 0) }
    }
}

private fun stableChapterKey(
    chapter: cx.aswin.boxlore.core.model.Chapter,
): String = "${chapter.startTime}:${chapter.title}:${chapter.url.orEmpty()}:${chapter.img.orEmpty()}"

@Composable
private fun LoadingScriptPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Story text is unavailable. You can still listen to the brief.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
