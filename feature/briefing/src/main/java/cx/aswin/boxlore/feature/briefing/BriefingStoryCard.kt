package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Briefing
import kotlinx.coroutines.launch

@Stable
internal data class BriefingStoryCardState(
    val briefing: Briefing,
    val chapter: cx.aswin.boxlore.core.model.Chapter,
    val paragraph: String,
    val page: Int,
    val pageCount: Int,
    val isActive: Boolean,
    val accentColor: Color,
    /** Show "Story N" on the card: in the list on wide windows, where no counter sits above the stories. */
    val showNumber: Boolean = false,
)

/** Below this window height a pager card scrolls as one piece instead of pinning its actions. */
internal const val STORY_CARD_PINNED_MIN_WINDOW_DP = 480

/**
 * Whether a pager card scrolls as one piece. Only short windows (a phone on its side, a split
 * screen) do, because pinned actions there would leave no room for the heading and story.
 */
internal fun storyCardScrollsAsOne(windowHeightDp: Int): Boolean = windowHeightDp < STORY_CARD_PINNED_MIN_WINDOW_DP

/**
 * One story. In the pager ([fillHeight]) the card takes the pager's height and pins related
 * episodes and Play to the bottom; a story taller than that scrolls inside its text area. In a
 * short window ([scrollAsOne]) the whole card scrolls instead, so the heading, story and actions
 * all stay reachable. Without [fillHeight] the card wraps its full content so the pager can
 * measure it.
 */
@Composable
internal fun BriefingStoryCard(
    state: BriefingStoryCardState,
    actions: BriefingStoriesPagerActions,
    fillHeight: Boolean = true,
    scrollAsOne: Boolean = false,
) {
    val height = if (fillHeight) Modifier.fillMaxHeight() else Modifier
    val scrollCard = fillHeight && scrollAsOne
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Reading).fillMaxWidth().then(height),
    ) {
        // In a list of stories, the playing one carries a strip along its top.
        if (state.showNumber && state.isActive) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.primaryContainer).testTag(PLAYING_STORY_STRIP_TAG))
        }
        Column(
            modifier = Modifier.fillMaxWidth().then(height)
                .then(if (scrollCard) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(18.dp),
        ) {
            if (fillHeight && !scrollCard) {
                Column(Modifier.weight(1f)) {
                    StoryCardTop(state)
                    StoryParagraph(paragraph = state.paragraph, scrollable = true, modifier = Modifier.weight(1f, fill = false))
                }
            } else {
                StoryCardTop(state)
                StoryParagraph(paragraph = state.paragraph, scrollable = false)
            }
            Spacer(modifier = Modifier.height(16.dp))
            RelatedEpisodesSection(state, actions.onEpisodeClick)
            Spacer(modifier = Modifier.height(12.dp))
            StoryPlayButton(state, actions)
        }
    }
}

@Composable
private fun StoryCardTop(state: BriefingStoryCardState) {
    StoryCardHeader(state)
    Spacer(modifier = Modifier.height(12.dp))
    StoryHeadline(state)
    Spacer(modifier = Modifier.height(10.dp))
}

/**
 * The story's time at the card's start, in the accent while it plays. The pager's counter above is
 * the only "Story N of M" on screen; TalkBack still hears the number with the time. In the list on
 * wide windows the card says "Story N" itself.
 */
@Composable
private fun StoryCardHeader(state: BriefingStoryCardState) {
    val time = formatChapterTime(state.chapter.startTime.toLong())
    val position = androidx.compose.ui.res.stringResource(cx.aswin.boxlore.feature.briefing.R.string.briefing_screen_story_position, state.page + 1, state.pageCount)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$position, $time" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.showNumber) {
            Text(
                stringResource(R.string.briefing_story_label, state.page + 1),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.bold,
            color = if (state.isActive) state.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StoryHeadline(state: BriefingStoryCardState) {
    Text(
        text = state.chapter.title,
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StoryParagraph(paragraph: String, scrollable: Boolean, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val behaviour = if (scrollable) Modifier.fadeWhileMoreBelow(scrollState).verticalScroll(scrollState) else Modifier
    Text(
        text = paragraph.trim(),
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 24.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.then(behaviour),
    )
}

/** Fades the last line while more of the story is below, so a long story reads as scrollable. */
private fun Modifier.fadeWhileMoreBelow(scrollState: ScrollState): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        if (scrollState.canScrollForward) {
            val fade = 28.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = size.height - fade, endY = size.height),
                blendMode = BlendMode.DstOut,
            )
        }
    }

/**
 * Play or pause this story: a tonal button at the card's end (one main action per page is the
 * Brief's Listen), filled with the accent's container while this story plays.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoryPlayButton(
    state: BriefingStoryCardState,
    actions: BriefingStoriesPagerActions,
) {
    val actionLabel = stringResource(if (state.isActive) R.string.briefing_pause_story_named else R.string.briefing_play_story_named, state.chapter.title)
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        FilledTonalButton(
            onClick = { handleStoryPlayClick(state, actions) },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (state.isActive) scheme.primaryContainer else scheme.secondaryContainer,
                contentColor = if (state.isActive) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
            ),
            modifier = Modifier.heightIn(min = 48.dp).testTag(STORY_PLAY_BUTTON_TAG).semantics { contentDescription = actionLabel },
        ) {
            Icon(if (state.isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(if (state.isActive) R.string.briefing_pause_story else R.string.briefing_play_story), fontWeight = GoogleSansWeight.bold)
        }
    }
}

/** The playing listed story's strip's test tag. */
internal const val PLAYING_STORY_STRIP_TAG = "briefing_playing_story"

/** A story card's play button's test tag. */
internal const val STORY_PLAY_BUTTON_TAG = "briefing_story_play"

private fun handleStoryPlayClick(
    state: BriefingStoryCardState,
    actions: BriefingStoriesPagerActions,
) {
    val storyProps = mapOf(
        "chapter_index" to state.page,
        "chapter_title" to state.chapter.title,
        "start_time_seconds" to state.chapter.startTime.toLong()
    )
    if (state.isActive) {
        trackStoryPlayButton(state, action = "story_pause_clicked", storyProps = storyProps)
        actions.onPlayPauseClick(null)
        return
    }

    actions.onUserClickedPageChange(state.page)
    actions.scope.launch {
        actions.pagerState.animateScrollToPage(state.page)
    }
    trackStoryPlayButton(state, action = "story_play_clicked", storyProps = storyProps)
    actions.onPlayPauseClick(state.chapter.startTime.toLong() * 1000L)
}

private fun trackStoryPlayButton(
    state: BriefingStoryCardState,
    action: String,
    storyProps: Map<String, Any>,
) {
    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
        action = action,
        region = state.briefing.region,
        date = state.briefing.date,
        extraProps = storyProps
    )
}
