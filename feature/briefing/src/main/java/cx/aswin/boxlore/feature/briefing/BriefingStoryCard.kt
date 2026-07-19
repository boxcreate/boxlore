package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
internal fun BriefingStoryCard(
    state: BriefingStoryCardState,
    actions: BriefingStoriesPagerActions,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = briefingStoryCardColor(state.isActive)),
        border = BorderStroke(1.dp, if (state.isActive) state.accentColor else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .shadow(6.dp, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                StoryCardHeader(state)
                Spacer(modifier = Modifier.height(12.dp))
                StoryHeadline(state)
                Spacer(modifier = Modifier.height(8.dp))
                StoryParagraph(
                    paragraph = state.paragraph,
                    isActive = state.isActive,
                    modifier = Modifier.weight(1f),
                )
            }
            RelatedEpisodesSection(state, actions.onEpisodeClick)
            Spacer(modifier = Modifier.height(12.dp))
            StoryPlayButton(state, actions)
        }
    }
}

@Composable
private fun briefingStoryCardColor(isActive: Boolean): Color =
    if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
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
            fontWeight = FontWeight.Bold,
            color = if (state.isActive) state.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StoryCountBadge(state: BriefingStoryCardState) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color =
            if (state.isActive) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
    ) {
        Text(
            text = "STORY ${state.page + 1} OF ${state.pageCount}".uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color =
                if (state.isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StoryHeadline(state: BriefingStoryCardState) {
    Text(
        text = state.chapter.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (state.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StoryParagraph(
    paragraph: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = paragraph.trim(),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color =
                    if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
            )
        }
    }
}

@Composable
private fun StoryPlayButton(
    state: BriefingStoryCardState,
    actions: BriefingStoriesPagerActions,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (state.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .expressiveClickable(
                shape = RoundedCornerShape(16.dp),
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
        tint = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun StoryPlayLabel(isActive: Boolean) {
    Text(
        text = if (isActive) "PAUSE STORY" else "PLAY THIS STORY",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary
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
