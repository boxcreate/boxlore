package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowWidth
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How the Brief lays out its stories. Phones, the Fold 8 cover and short panes (a sideways phone, a
 * tabletop's lower half) keep the cover then a pager of stories ([Pager]); portrait tablets and
 * landscape windows too narrow for two panes list every story in the page's one column ([List]); wide
 * windows put the edition at the start and every story in a scrolling list beside it ([TwoPane]),
 * only where that list keeps a readable width.
 */
internal enum class BriefingLayoutMode {
    Pager,
    List,
    TwoPane,
    ;

    companion object {
        fun of(widthDp: Float, heightDp: Float, shortPane: Boolean): BriefingLayoutMode {
            val window = BoxloreWindowLayout(widthDp, heightDp)
            return when {
                shortPane || !window.usesTabletLayout -> Pager
                // Expanded width (840dp and up) in any orientation, or a landscape window whose story list stays readable.
                window.widthClass >= BoxloreWindowWidth.Expanded -> TwoPane
                window.orientation == BoxloreWindowOrientation.Landscape && briefingStoryListRoom(widthDp) >= BRIEFING_STORY_LIST_MIN_DP -> TwoPane
                else -> List
            }
        }
    }
}

/** The two-pane frame stops at this width; its start pane is 360-420dp, its story list 360-640dp. */
internal const val BRIEFING_TWO_PANE_MAX_WIDTH_DP = 1_120f
internal const val BRIEFING_START_PANE_MIN_DP = 360f
internal const val BRIEFING_START_PANE_MAX_DP = 420f
internal const val BRIEFING_STORY_LIST_MIN_DP = 360f
internal const val BRIEFING_STORY_LIST_MAX_DP = 640f

/** The gap between the panes, and the story list's own end padding. */
private const val PANE_GAP_DP = 24f
private const val LIST_END_PADDING_DP = 20f

/** The start pane's width in a two-pane frame [frameWidthDp] wide. */
internal fun briefingStartPaneWidth(frameWidthDp: Float): Float = (frameWidthDp * 0.36f).coerceIn(BRIEFING_START_PANE_MIN_DP, BRIEFING_START_PANE_MAX_DP)

/** How wide the story list's cards would be in two panes on a window [widthDp] wide. */
internal fun briefingStoryListRoom(widthDp: Float): Float {
    val frame = minOf(widthDp, BRIEFING_TWO_PANE_MAX_WIDTH_DP)
    return frame - briefingStartPaneWidth(frame) - PANE_GAP_DP - LIST_END_PADDING_DP
}

/** Where two panes split: the start pane's width and the gap before the story list. */
internal data class BriefingTwoPaneSplit(val start: Dp, val gap: Dp)

/**
 * The two panes' split. On a foldable held like a book, with the frame spanning the hinge, the start
 * pane ends at the hinge and the list starts after it, so no text crosses the fold; otherwise the
 * start pane takes about a third of the frame. [frameLeftDp] is the frame's left edge in the window.
 */
internal fun briefingTwoPaneSplit(window: BoxloreWindowLayout, frameLeftDp: Float, frameWidthDp: Float, rtl: Boolean): BriefingTwoPaneSplit {
    val fallback = BriefingTwoPaneSplit(briefingStartPaneWidth(frameWidthDp).dp, PANE_GAP_DP.dp)
    if (!window.usesHingeSplit) return fallback
    val regions = window.foldSafeRegions().sortedBy { it.left }
    if (regions.size != 2) return fallback
    val frameRight = frameLeftDp + frameWidthDp
    val (first, second) = regions
    if (first.right <= frameLeftDp || second.left >= frameRight) return fallback
    val left = first.right - frameLeftDp
    val right = frameRight - second.left
    val start = if (rtl) right else left
    val list = (if (rtl) left else right) - LIST_END_PADDING_DP
    if (start < BRIEFING_START_PANE_MIN_DP - 40f || list < BRIEFING_STORY_LIST_MIN_DP - 40f) return fallback
    return BriefingTwoPaneSplit(start.dp, (second.left - first.right).dp)
}

/** Test tags of the two panes and of a listed story. */
internal const val BRIEFING_START_PANE_TAG = "briefing_start_pane"
internal const val BRIEFING_STORY_LIST_TAG = "briefing_story_list"
internal const val BRIEFING_LISTED_STORY_TAG = "briefing_listed_story"

/** A listed story's list key. */
internal fun briefingStoryKey(index: Int): String = "story-$index"

/** What a listed story needs from the edition and the page. */
internal class BriefingStoryListModel(
    val briefing: Briefing,
    val chapters: List<Chapter>,
    val paragraphs: List<String>,
    val activeIndex: Int,
    val isPlaying: Boolean,
    val actions: BriefingStoriesPagerActions,
)

/** Builds what a story list needs: the active story and the cards' actions (play, related episodes). */
@Composable
internal fun rememberBriefingStoryListModel(
    briefing: Briefing,
    chapters: List<Chapter>,
    isPlaying: Boolean,
    currentPositionMs: Long,
    onEpisodeClick: (Episode) -> Unit,
    onPlayPauseClick: (Long?) -> Unit,
    onShowSources: () -> Unit,
): BriefingStoryListModel {
    val scope = rememberCoroutineScope()
    // The cards' play action moves a pager to its story; listed stories have no pager on screen.
    val pagerState = rememberPagerState(pageCount = { chapters.size })
    val paragraphs = remember(briefing.script) { briefingStoryParagraphs(briefing.script) }
    val activeIndex = activeChapterIndexFor(chapters, currentPositionMs)
    val actions = remember(scope, pagerState, onEpisodeClick, onPlayPauseClick, onShowSources) {
        BriefingStoriesPagerActions(scope, pagerState, onUserClickedPageChange = {}, onEpisodeClick = onEpisodeClick, onPlayPauseClick = onPlayPauseClick, onShowSources = onShowSources)
    }
    return BriefingStoryListModel(briefing, chapters, paragraphs, activeIndex, isPlaying, actions)
}

/** Every story as a card, 16dp apart, each saying "Story N"; the playing one is marked. */
internal fun LazyListScope.briefingStoryItems(model: BriefingStoryListModel, horizontalPadding: PaddingValues = PaddingValues(0.dp)) {
    model.chapters.forEachIndexed { index, chapter ->
        item(key = briefingStoryKey(index)) {
            Box(Modifier.fillMaxWidth().padding(horizontalPadding).testTag(BRIEFING_LISTED_STORY_TAG)) {
                BriefingStoryCard(
                    BriefingStoryCardState(
                        briefing = model.briefing,
                        chapter = chapter,
                        paragraph = model.paragraphs.getOrNull(index).orEmpty(),
                        page = index,
                        pageCount = model.chapters.size,
                        isActive = model.activeIndex == index && model.isPlaying,
                        accentColor = MaterialTheme.colorScheme.primary,
                        showNumber = true,
                    ),
                    model.actions,
                    fillHeight = false,
                )
            }
        }
    }
}

/** The spacing between listed stories. */
internal val BriefingStoryListSpacing = Arrangement.spacedBy(16.dp)

/**
 * Keeps a story list in step with the page: reports the first story in view as the current story,
 * and when playback moves to another story, brings it into view unless the listener scrolled in the
 * last few seconds. [firstStoryItem] is the list index of story 0; [startAt] is the story to open at.
 */
@Composable
internal fun FollowPlayingStory(
    listState: LazyListState,
    model: BriefingStoryListModel,
    firstStoryItem: Int,
    startAt: Int?,
    onStoryInView: (Int) -> Unit,
    canFollow: (LazyListState) -> Boolean = { true },
) {
    val report by rememberUpdatedState(onStoryInView)
    // Arriving from another layout: open at the story the listener was on before reporting any other.
    // Asked while composing, so the list's first measure in this layout already opens there.
    val target = remember { startAt?.also { listState.requestScrollToItem(firstStoryItem + it) } }
    val arrived = remember { mutableStateOf(target == null) }
    LaunchedEffect(Unit) {
        if (target != null) {
            // Report stories again once the list stands at the story (or as close as its end allows).
            withTimeoutOrNull(ARRIVAL_WAIT_MS) { snapshotFlow { listState.firstVisibleItemIndex }.first { it >= firstStoryItem + target } }
        }
        arrived.value = true
    }
    val lastListenerScroll = remember { mutableLongStateOf(0L) }
    val following = remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && following.longValue == 0L) lastListenerScroll.longValue = System.currentTimeMillis()
        }
    }
    val storyInView by remember(listState, firstStoryItem) {
        derivedStateOf {
            // The first story whose middle is in view: one tucked under the top padding doesn't count.
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { (it.key as? String)?.startsWith("story-") == true && it.offset + it.size / 2 >= 0 }
                ?.index?.minus(firstStoryItem)
        }
    }
    LaunchedEffect(storyInView, arrived.value) { if (arrived.value) storyInView?.let(report) }
    LaunchedEffect(model.activeIndex, model.isPlaying) {
        if (!model.isPlaying || model.activeIndex !in model.chapters.indices || !canFollow(listState)) return@LaunchedEffect
        if (System.currentTimeMillis() - lastListenerScroll.longValue < LISTENER_SCROLL_HOLD_MS) return@LaunchedEffect
        if (storyInView == model.activeIndex) return@LaunchedEffect
        following.longValue = 1L
        try {
            listState.animateScrollToItem(firstStoryItem + model.activeIndex)
        } finally {
            following.longValue = 0L
        }
    }
}

private const val ARRIVAL_WAIT_MS = 1_000L

/** After the listener scrolls the stories, playback leaves the list alone this long. */
private const val LISTENER_SCROLL_HOLD_MS = 4_000L
