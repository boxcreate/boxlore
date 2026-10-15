package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
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

@Composable
private fun StoryCardHeader(state: BriefingStoryCardState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StoryCountBadge(state)
        Text(
            text = formatChapterTime(state.chapter.startTime.toLong()),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.bold,
            color = if (state.isActive) state.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StoryCountBadge(state: BriefingStoryCardState) {
    Text(
        stringResource(R.string.briefing_story_label, state.page + 1),
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.primary
    )
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

@Composable
private fun StoryPlayButton(
    state: BriefingStoryCardState,
    actions: BriefingStoriesPagerActions,
) {
    val actionLabel = stringResource(if (state.isActive) R.string.briefing_pause_story_named else R.string.briefing_play_story_named, state.chapter.title)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (state.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = actionLabel }
            .expressiveClickable(
                feedback = ExpressivePressFeedback.Control,
                shape = RoundedCornerShape(28.dp),
                onClick = { handleStoryPlayClick(state, actions) }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            StoryPlayIcon(state.isActive)
            Spacer(modifier = Modifier.width(8.dp))
            StoryPlayLabel(state.isActive)
        }
    }
}

@Composable
private fun StoryPlayIcon(isActive: Boolean) {
    Icon(
        imageVector = if (isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        contentDescription = null,
        tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun StoryPlayLabel(isActive: Boolean) {
    Text(
        text = stringResource(if (isActive) R.string.briefing_pause_story else R.string.briefing_play_story),
        fontWeight = GoogleSansWeight.bold,
        style = MaterialTheme.typography.labelLarge,
        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
    )
}

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
