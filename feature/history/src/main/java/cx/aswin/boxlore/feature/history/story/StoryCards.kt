package cx.aswin.boxlore.feature.history.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacterSpec
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.insights.InsightsWording
import cx.aswin.boxlore.feature.history.insights.perDayText
import cx.aswin.boxlore.feature.history.timeline.TimelineTime
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import cx.aswin.boxlore.feature.history.timeline.timeLeftText

/** A spot on the 400 × 890 story frame, in frame dp. */
internal fun Modifier.at(x: Float, y: Float): Modifier = offset(x.dp, y.dp)

/** Back layer (blobs, covers, charts), Echo, then front (text, bubbles), then the tap areas and buttons. */
internal object StoryLayers {
    const val BACK = 1f
    const val ECHO = 3f
    const val FRONT = 4f
    const val TAPS = 5f
    const val BUTTONS = 6f
    const val CHROME = 7f
}

/**
 * The column of text every card starts with, 20dp in and 96dp down. It sits above the tap areas so
 * its buttons (Resume) take their taps; its text takes none, so taps on it still move the story on.
 */
@Composable
internal fun BoxScope.StoryHeading(gap: Float, content: @Composable () -> Unit) {
    Column(Modifier.zIndex(StoryLayers.BUTTONS).at(20f, 96f).width(360.dp), verticalArrangement = Arrangement.spacedBy(gap.dp)) { content() }
}

@Composable
internal fun StoryEyebrow(text: String, color: Color, clock: StoryClock) {
    Text(text, style = StoryType.eyebrow(), color = color.copy(alpha = 0.85f), modifier = Modifier.storyRise(clock))
}

/** Story 1, hello (A7): the month, its days, shows and busiest weekday, its top five covers, and Echo waving. */
@Composable
internal fun BoxScope.StoryHelloCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, currentYear: Int) {
    val locale = currentLocale()
    val fg = palette.onContainer
    Box(Modifier.zIndex(StoryLayers.BACK).at(-70f, 600f).size(340.dp).storyBlob(clock).clip(StoryBlobShape.Card).background(palette.blob))
    StoryHeading(gap = 4f) {
        val eyebrow = if (story.month.year == currentYear) stringResource(R.string.history_story_hello_eyebrow) else stringResource(R.string.history_story_hello_eyebrow_year, story.month.year)
        StoryEyebrow(eyebrow, fg, clock)
        Text(StoryText.monthTitle(story.month, locale), style = StoryType.of(80f, 84f, GoogleSansWeight.semiBold, -1f), color = fg, maxLines = 1, modifier = Modifier.storyLine(84f).storyRise(clock, StoryMotion.D1))
        val days = pluralStringResource(R.plurals.history_story_days, story.listenedDays, story.listenedDays)
        val shows = pluralStringResource(R.plurals.history_story_shows, story.shows, story.shows)
        Text(
            stringResource(R.string.history_story_hello_line, days, shows, StoryText.weekday(story.busiestWeekday, locale)),
            style = StoryType.of(18f, 25f),
            color = fg,
            modifier = Modifier.storyRise(clock, StoryMotion.D2).widthIn(max = 300.dp).padding(top = 6.dp),
        )
    }
    // The board's order: the outer pair behind, then the inner pair, the top show in front.
    HelloCovers.forEach { spot ->
        val url = story.covers.getOrNull(spot.rank) ?: return@forEach
        StoryCover(url, spot.x, spot.y, spot.size, spot.corner, spot.rotation, Modifier.zIndex(StoryLayers.BACK), motion = Modifier.storyPop(clock, spot.delay), shadow = spot.shadow)
    }
    StoryBubble(
        text = stringResource(R.string.history_story_hello_bubble, StoryText.monthName(story.month, locale)),
        background = fg,
        color = palette.deep,
        shape = StoryBubbles.TailBottomStart,
        maxWidth = 160f,
        modifier = Modifier.zIndex(StoryLayers.FRONT).at(214f, 590f).storyPop(clock, StoryMotion.D5),
    )
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(110f, 610f, fg, StorySparks.Hello, clock) }
}

private data class CoverSpot(val rank: Int, val x: Float, val y: Float, val size: Float, val corner: Float, val rotation: Float, val delay: Int, val shadow: Float = 0f)

private val HelloCovers = listOf(
    CoverSpot(3, 22f, 356f, 104f, 24f, -14f, StoryMotion.D3),
    CoverSpot(4, 274f, 356f, 104f, 24f, 14f, StoryMotion.D3),
    CoverSpot(1, 80f, 340f, 104f, 24f, -7f, StoryMotion.D4),
    CoverSpot(2, 216f, 340f, 104f, 24f, 7f, StoryMotion.D4),
    CoverSpot(0, 140f, 330f, 120f, 28f, 0f, StoryMotion.D5, shadow = 10f),
)

/** The time card's chart: a bar a day across the frame, the biggest day three bars wide so Echo can stand on it. */
internal object StoryTimeChart {
    const val LEFT = 16f
    const val WIDTH = 344f
    const val GAP = 4f
    const val TOP = 590f
    const val HEIGHT = 270f
    const val TALLEST = 260f

    /** Where the biggest day's bar is centred, for Echo to stand on. */
    fun biggestCenter(days: Int, biggest: Int): Float {
        val unit = (WIDTH - (days - 1) * GAP) / (days + 2)
        return LEFT + biggest * (unit + GAP) + 1.5f * unit
    }
}

/** Story 2, time listened (A8): the month's total counting up, a day's average against the month before, and a bar a day. */
@Composable
internal fun BoxScope.StoryTimeCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, spec: EchoCharacterSpec?, echo: StoryEchoCast) {
    val locale = currentLocale()
    val fg = Color.White
    StoryHeading(gap = 0f) {
        StoryEyebrow(stringResource(R.string.history_story_time_eyebrow), fg, clock)
        // The count changes about 60 times in its 1.1s and then not at all: only those changes redraw the card.
        val counted by remember(story.totalMs, clock) { derivedStateOf { StoryText.countUp(story.totalMs, clock) } }
        val (hours, minutes) = counted / 60 to counted % 60
        val totalHours = story.totalMs / 3_600_000L
        if (totalHours > 0) {
            Text(stringResource(R.string.history_story_hours, hours), style = StoryType.of(112f, 108f, GoogleSansWeight.semiBold, -2f), color = fg, modifier = Modifier.storyLine(108f).storyRise(clock, StoryMotion.D1))
        }
        Text(stringResource(R.string.history_story_minutes, minutes), style = StoryType.of(if (totalHours > 0) 56f else 112f, if (totalHours > 0) 60f else 108f, GoogleSansWeight.semiBold, -0.5f), color = fg, modifier = Modifier.storyLine(if (totalHours > 0) 60f else 108f).storyRise(clock, StoryMotion.D1))
        Text(StoryText.timeLine(story, locale), style = StoryType.of(18f, 25f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D2).widthIn(max = 320.dp).padding(top = 14.dp))
    }
    StoryDayBars(story, clock)
    Row(Modifier.zIndex(StoryLayers.BACK).at(StoryTimeChart.LEFT, 866f).width(StoryTimeChart.WIDTH.dp).storyRise(clock, StoryMotion.D3), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = StoryType.of(12f, 16f)
        Text(TimelineTime.shortDate(story.month.atDay(1), locale), style = style, color = fg.copy(alpha = 0.8f))
        Text(TimelineTime.shortDate(story.month.atEndOfMonth(), locale), style = style, color = fg.copy(alpha = 0.8f))
    }
    // The bubble sits beside Echo on the biggest day's bar, on whichever side has room.
    val bounds = spec?.let { StoryEchoStage.bounds(it, echo) }
    val bubble = stringResource(R.string.history_story_time_bubble, TimelineTime.shortDate(story.biggestDay.first, locale), durationText(story.biggestDay.second))
    val onLeft = bounds == null || bounds.left > 200f
    Box(
        Modifier.zIndex(StoryLayers.FRONT).at(if (onLeft) 16f else (bounds?.right ?: 0f) + 4f, 470f).width(if (onLeft) ((bounds?.left ?: 244f) - 4f - 16f).dp else (400f - 16f - (bounds?.right ?: 0f) - 4f).dp),
        contentAlignment = if (onLeft) Alignment.TopEnd else Alignment.TopStart,
    ) {
        StoryBubble(bubble, Color.White, palette.onWhite, if (onLeft) StoryBubbles.TailBottomEnd else StoryBubbles.TailBottomStart, 220f, Modifier.storyPop(clock, StoryMotion.D6))
    }
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(bounds?.center ?: 318f, 430f, fg, StorySparks.Time, clock) }
}

@Composable
private fun BoxScope.StoryDayBars(story: MonthStory, clock: StoryClock) {
    val locale = currentLocale()
    val biggest = story.biggestDay.first.dayOfMonth - 1
    val most = story.biggestDay.second.coerceAtLeast(1L)
    val label = stringResource(R.string.history_story_time_chart, StoryText.monthName(story.month, locale), TimelineTime.shortDate(story.biggestDay.first, locale))
    Row(
        Modifier
            .zIndex(StoryLayers.BACK)
            .at(StoryTimeChart.LEFT, StoryTimeChart.TOP)
            .size(StoryTimeChart.WIDTH.dp, StoryTimeChart.HEIGHT.dp)
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(StoryTimeChart.GAP.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        story.perDay.forEachIndexed { day, ms ->
            val (height, color) = when {
                day == biggest -> StoryTimeChart.TALLEST to Color.White
                ms == null -> 3f to Color.White.copy(alpha = 0.08f)
                ms <= 0L -> 3f to Color.White.copy(alpha = 0.18f)
                else -> (StoryTimeChart.TALLEST * ms / most).coerceAtLeast(6f) to Color.White.copy(alpha = 0.4f)
            }
            Box(
                Modifier
                    .weight(if (day == biggest) 3f else 1f)
                    .height(height.dp)
                    .storyGrow(clock, StoryMotion.barDelay(day))
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

/** Story 3, top show (A9): the month's top show by time, its share and finishes, its artwork, and Echo in the genre's outfit. */
@Composable
internal fun BoxScope.StoryTopShowCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, onResume: (ListeningHistoryItem) -> Unit) {
    val top = story.topShow ?: return
    val locale = currentLocale()
    val fg = palette.onGreen
    Box(Modifier.zIndex(StoryLayers.BACK).at(160f, 640f).size(320.dp).storyBlob(clock).clip(StoryBlobShape.Card).background(palette.greenBlob))
    StoryHeading(gap = 4f) {
        StoryEyebrow(stringResource(R.string.history_story_top_eyebrow), fg, clock)
        Text(top.name, style = StoryType.of(46f, 50f, GoogleSansWeight.semiBold, -0.5f), color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.storyRise(clock, StoryMotion.D1))
        val share = stringResource(R.string.history_story_top_line, durationText(top.listenedMs), InsightsWording.percent(top.share), StoryText.monthName(story.month, locale))
        val finished = if (top.finished > 0) " " + pluralStringResource(R.plurals.history_story_top_finished, top.finished, top.finished) else ""
        Text(share + finished, style = StoryType.of(18f, 25f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D2).padding(top = 4.dp))
        top.resume?.let { episode ->
            StoryResumeButton(episode, top.resumeRemainingMs, palette, Modifier.storyRise(clock, StoryMotion.D3).padding(top = 16.dp), onResume)
        }
    }
    StoryCover(top.imageUrl, 20f, 400f, 210f, 32f, 0f, Modifier.zIndex(StoryLayers.BACK), motion = Modifier.storyDrop(clock, StoryMotion.D3), shadow = 12f)
    StoryBubble(
        text = stringResource(StoryText.genreLine(story.showOutfit)),
        background = fg,
        color = palette.greenInk,
        shape = StoryBubbles.TailBottomStart,
        maxWidth = 150f,
        modifier = Modifier.zIndex(StoryLayers.FRONT).at(226f, 520f).storyPop(clock, StoryMotion.D6),
    )
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(290f, 640f, fg, StorySparks.Wide, clock) }
}

/** "Resume Ricky · 41 min left": 48dp, fully round, onGreen with the green card's ink. */
@Composable
private fun StoryResumeButton(episode: ListeningHistoryItem, remainingMs: Long, palette: StoryPalette, modifier: Modifier, onResume: (ListeningHistoryItem) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(palette.onGreen)
            .expressiveClickable(shape = shape, feedback = ExpressivePressFeedback.Control) { onResume(episode) }
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(start = 14.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = palette.greenInk, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.history_story_resume, episode.episodeTitle, timeLeftText(remainingMs)),
            style = StoryType.of(15f, 20f, GoogleSansWeight.semiBold),
            color = palette.greenInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
