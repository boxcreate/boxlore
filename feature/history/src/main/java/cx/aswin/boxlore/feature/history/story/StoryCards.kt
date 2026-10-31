package cx.aswin.boxlore.feature.history.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacterSpec
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.TimelineTime
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import java.time.format.DateTimeFormatter

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
 * The column every card starts with, 20dp in and 96dp down: the stats, top to bottom. It sits above
 * the tap areas so its buttons (Resume) take their taps; its text takes none, so taps on it still move
 * the story on.
 */
@Composable
internal fun BoxScope.StoryHeading(gap: Float, content: @Composable () -> Unit) {
    Column(Modifier.zIndex(StoryLayers.BUTTONS).at(20f, 96f).width(360.dp), verticalArrangement = Arrangement.spacedBy(gap.dp)) { content() }
}

@Composable
internal fun StoryEyebrow(text: String, color: Color, clock: StoryClock) {
    Text(text, style = StoryType.eyebrow(), color = color.copy(alpha = 0.85f), modifier = Modifier.storyRise(clock))
}

/** Echo's line on a card, popping in once he has landed and started his act. */
@Composable
internal fun BoxScope.StoryEchoLine(line: String, spot: StoryLineSpot, colors: Pair<Color, Color>, clock: StoryClock) {
    StoryBubble(
        text = line,
        background = colors.first,
        color = colors.second,
        shape = if (spot.echoOnRight) StoryBubbles.TailBottomEnd else StoryBubbles.TailBottomStart,
        maxWidth = spot.maxWidth,
        modifier = Modifier.zIndex(StoryLayers.FRONT).at(spot.x, spot.y).storyPop(clock, StoryMotion.ECHO_LINE_MS),
    )
}

/** Where a card's line sits: its bubble's corner, widest width, and which side Echo is on (the tail points at him). */
internal data class StoryLineSpot(val x: Float, val y: Float, val maxWidth: Float, val echoOnRight: Boolean)

/** A stat counting up from 0 as its card builds in: [text] turns the count into words. */
@Composable
internal fun StoryCount(value: Long, clock: StoryClock, delay: Int, text: @Composable (Long) -> String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    // The count changes about 60 times in its 1.1s and then not at all: only those changes recompose it.
    val counted by remember(value, clock, delay) { derivedStateOf { StoryText.countUp(value, clock, delay) } }
    Text(text(counted), style = style, color = color, maxLines = 1, modifier = modifier)
}

/**
 * Story 1, hello (S1): the month, a line about it, its hours, days, episodes and finishes counting up,
 * the top covers, and Echo waving beside his line.
 */
@Composable
internal fun BoxScope.StoryHelloCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, currentYear: Int, line: String) {
    val locale = currentLocale()
    val fg = palette.onContainer
    Box(Modifier.zIndex(StoryLayers.BACK).at(-60f, 690f).size(260.dp).storyBlob(clock).clip(StoryBlobShape.Card).background(palette.blob))
    StoryHeading(gap = 2f) {
        val eyebrow = if (story.month.year == currentYear) stringResource(R.string.history_story_hello_eyebrow) else stringResource(R.string.history_story_hello_eyebrow_year, story.month.year)
        StoryEyebrow(eyebrow, fg, clock)
        StoryMonthTitle(StoryText.monthTitle(story.month, locale), fg, clock)
        val days = pluralStringResource(R.plurals.history_story_days, story.listenedDays, story.listenedDays)
        val shows = pluralStringResource(R.plurals.history_story_shows, story.shows, story.shows)
        Text(
            stringResource(R.string.history_story_hello_line, days, shows, StoryText.weekday(story.busiestWeekday, locale)),
            style = StoryType.of(18f, 25f),
            color = fg,
            modifier = Modifier.storyRise(clock, StoryMotion.D2).widthIn(max = 320.dp).padding(top = 4.dp, bottom = 26.dp),
        )
        StoryHelloStats(story, fg, clock)
    }
    // Two covers behind at an angle, the top show in front.
    HelloCovers.forEach { spot ->
        val url = story.covers.getOrNull(spot.rank) ?: return@forEach
        StoryCover(url, spot.cover, Modifier.zIndex(StoryLayers.BACK), motion = Modifier.storyPop(clock, spot.delay))
    }
    StoryEchoLine(line, StoryLineSpot(176f, 712f, 176f, echoOnRight = false), fg to palette.deep, clock)
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(110f, 700f, fg, StorySparks.Hello, clock) }
}

/** The month's name at 40–80sp, as large as fits the frame's 360dp (September runs wider than May). */
@Composable
private fun StoryMonthTitle(title: String, color: Color, clock: StoryClock) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = StoryType.of(MONTH_SIZE, MONTH_LINE, GoogleSansWeight.semiBold, -1.2f)
    val size = remember(title, widest, density) {
        val width = measurer.measure(title, widest, softWrap = false, maxLines = 1).size.width.coerceAtLeast(1)
        val room = with(density) { MONTH_ROOM.dp.toPx() }
        (MONTH_SIZE * minOf(1f, room / width)).coerceAtLeast(MONTH_MIN)
    }
    Text(
        title,
        style = widest.copy(fontSize = size.sp),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.storyLine(MONTH_LINE).storyRise(clock, StoryMotion.D1),
    )
}

private const val MONTH_SIZE = 80f
private const val MONTH_MIN = 40f
private const val MONTH_LINE = 80f
private const val MONTH_ROOM = 360f

/** Hours, days, episodes and finished: a 2 × 2 of tiles whose numbers count up. */
@Composable
private fun StoryHelloStats(story: MonthStory, fg: Color, clock: StoryClock) {
    val hours = story.totalMs / 3_600_000L
    val tiles = listOf(
        Triple(if (hours > 0) hours else story.totalMs / 60_000L, if (hours > 0) R.string.history_story_hours else R.string.history_story_minutes, R.string.history_story_stat_listened),
        Triple(story.listenedDays.toLong(), R.string.history_story_count, R.string.history_story_stat_days),
        Triple(story.counts.episodes.toLong(), R.string.history_story_count, R.string.history_story_stat_episodes),
        Triple(story.counts.finished.toLong(), R.string.history_story_count, R.string.history_story_stat_finished),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { column, (value, format, label) ->
                    val delay = StoryMotion.D3 + (row * 2 + column) * STAT_STEP_MS
                    Column(
                        Modifier.weight(1f).storyRise(clock, delay).clip(RoundedCornerShape(22.dp)).background(fg.copy(alpha = 0.11f))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        StoryCount(value, clock, delay, { stringResource(format, it.toInt()) }, StoryType.of(40f, 44f, GoogleSansWeight.semiBold, -0.5f), fg)
                        Text(stringResource(label), style = StoryType.of(14f, 19f), color = fg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private const val STAT_STEP_MS = 80

private data class CoverSpot(val rank: Int, val cover: StoryCoverSpot, val delay: Int)

private val HelloCovers = listOf(
    CoverSpot(2, StoryCoverSpot(214f, 520f, 92f, 22f, 10f, shadow = 8f), StoryMotion.D4),
    CoverSpot(1, StoryCoverSpot(94f, 520f, 92f, 22f, -10f, shadow = 8f), StoryMotion.D4),
    CoverSpot(0, StoryCoverSpot(148f, 506f, 104f, 26f, shadow = 8f), StoryMotion.D5),
)

/** The time card's chart: a bar a day across the frame's foot, the biggest day's in white with Echo on it. */
internal object StoryTimeChart {
    const val LEFT = 16f
    const val WIDTH = 368f
    const val GAP = 4f
    const val TOP = 646f
    const val HEIGHT = 210f
    const val TALLEST = 200f

    private fun unit(days: Int) = (WIDTH - (days - 1) * GAP) / days

    /** Where the biggest day's bar is centred, for Echo to stand on. */
    fun biggestCenter(days: Int, biggest: Int): Float = LEFT + biggest * (unit(days) + GAP) + unit(days) / 2f
}

/**
 * Story 2, time listened (S2): the month's total counting up, a day's average against the month
 * before, the two months' totals as bars, and a bar a day with Echo standing on the biggest.
 */
@Composable
internal fun BoxScope.StoryTimeCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, spec: EchoCharacterSpec?, echo: StoryEchoCast, line: String) {
    val locale = currentLocale()
    val fg = Color.White
    StoryHeading(gap = 0f) {
        StoryEyebrow(stringResource(R.string.history_story_time_eyebrow), fg, clock)
        val counted by remember(story.totalMs, clock) { derivedStateOf { StoryText.countUp(story.totalMs, clock) } }
        val (hours, minutes) = counted / 60 to counted % 60
        val totalHours = story.totalMs / 3_600_000L
        if (totalHours > 0) {
            Text(stringResource(R.string.history_story_hours, hours), style = StoryType.of(112f, 108f, GoogleSansWeight.semiBold, -2f), color = fg, modifier = Modifier.storyLine(108f).storyRise(clock, StoryMotion.D1))
        }
        Text(stringResource(R.string.history_story_minutes, minutes), style = StoryType.of(if (totalHours > 0) 56f else 112f, if (totalHours > 0) 60f else 108f, GoogleSansWeight.semiBold, -0.5f), color = fg, modifier = Modifier.storyLine(if (totalHours > 0) 60f else 108f).storyRise(clock, StoryMotion.D1))
        Text(StoryText.timeLine(story, locale), style = StoryType.of(18f, 25f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D2).widthIn(max = 330.dp).padding(top = 12.dp))
        story.previousMonthMs?.takeIf { it > 0L }?.let { previous -> StoryMonthBars(story, previous, fg, clock) }
    }
    StoryDayBars(story, clock)
    Row(Modifier.zIndex(StoryLayers.BACK).at(StoryTimeChart.LEFT, 862f).width(StoryTimeChart.WIDTH.dp).storyRise(clock, StoryMotion.D3), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = StoryType.of(12f, 16f)
        Text(TimelineTime.shortDate(story.month.atDay(1), locale), style = style, color = fg.copy(alpha = 0.8f))
        Text(TimelineTime.shortDate(story.month.atEndOfMonth(), locale), style = style, color = fg.copy(alpha = 0.8f))
    }
    val bounds = spec?.let { StoryEchoStage.bounds(it, echo) }
    val echoOnRight = (bounds?.center ?: 200f) > 200f
    StoryBiggestDayChip(story, palette, clock, echoOnRight)
    val lineSpot = if (echoOnRight) StoryLineSpot(24f, 500f, 176f, echoOnRight = true) else StoryLineSpot(206f, 500f, 176f, echoOnRight = false)
    StoryEchoLine(line, lineSpot, fg to palette.onWhite, clock)
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(bounds?.center ?: 200f, 560f, fg, StorySparks.Time, clock) }
}

/** The month before and this month as two bars against the bigger of the two, growing in after the total. */
@Composable
private fun StoryMonthBars(story: MonthStory, previousMs: Long, fg: Color, clock: StoryClock) {
    val locale = currentLocale()
    val most = maxOf(previousMs, story.totalMs).toFloat()
    val label = stringResource(
        R.string.history_story_time_compare,
        StoryText.monthName(story.month.minusMonths(1), locale),
        durationText(previousMs),
        StoryText.monthName(story.month, locale),
        durationText(story.totalMs),
    )
    Column(Modifier.padding(top = 26.dp).clearAndSetSemantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple(StoryText.monthName(story.month.minusMonths(1), locale), previousMs, false),
            Triple(StoryText.monthName(story.month, locale), story.totalMs, true),
        ).forEachIndexed { index, (month, ms, current) ->
            val weight = if (current) GoogleSansWeight.semiBold else GoogleSansWeight.regular
            val color = if (current) fg else fg.copy(alpha = 0.85f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(month, style = StoryType.of(14f, 20f, weight), color = color, maxLines = 1, modifier = Modifier.width(84.dp))
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(
                        Modifier.fillMaxWidth((ms / most).coerceIn(0.02f, 1f)).fillMaxHeight().storyGrowX(clock, StoryMotion.D3 + index * 120)
                            .clip(RoundedCornerShape(7.dp)).background(if (current) fg else fg.copy(alpha = 0.4f)),
                    )
                }
                Text(durationText(ms), style = StoryType.of(14f, 20f, weight), color = color, maxLines = 1)
            }
        }
    }
}

/** "Mon, Sep 14 · 2 h 32 min" above the chart, on the side away from Echo. */
@Composable
private fun BoxScope.StoryBiggestDayChip(story: MonthStory, palette: StoryPalette, clock: StoryClock, echoOnRight: Boolean) {
    val day = story.biggestDay.first.format(DateTimeFormatter.ofPattern("EEE, MMM d", currentLocale()))
    val text = stringResource(R.string.history_story_time_bubble, day, durationText(story.biggestDay.second))
    Box(
        Modifier.zIndex(StoryLayers.FRONT).at(if (echoOnRight) 22f else 200f, 604f).width(178.dp),
        contentAlignment = if (echoOnRight) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Text(
            text,
            style = StoryType.of(13f, 18f, GoogleSansWeight.semiBold),
            color = palette.onWhite,
            maxLines = 1,
            modifier = Modifier.storyPop(clock, StoryMotion.D5).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
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
                    .weight(1f)
                    .height(height.dp)
                    .storyGrow(clock, StoryMotion.barDelay(day))
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}
