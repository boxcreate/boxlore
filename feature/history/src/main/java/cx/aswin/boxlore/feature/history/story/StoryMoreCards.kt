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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.insights.InsightsWording
import cx.aswin.boxlore.feature.history.insights.ListeningType
import cx.aswin.boxlore.feature.history.insights.TypeTime
import cx.aswin.boxlore.feature.history.insights.finishFact
import cx.aswin.boxlore.feature.history.insights.partName
import cx.aswin.boxlore.feature.history.insights.typeName
import cx.aswin.boxlore.feature.history.insights.weekdayPlural
import cx.aswin.boxlore.feature.history.insights.whereHeadline
import cx.aswin.boxlore.feature.history.timeline.TimelineTime
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import java.time.format.DateTimeFormatter

/** Whether the listening-type card is the night version (evenings, late nights, all day) or the light day version. */
internal fun TypeTime.isNight(): Boolean = this != TypeTime.MORNING && this != TypeTime.DAYTIME

/**
 * Story 5, listening type (S5): the type's name, the listening clock beside the leading part of the
 * day's share (counting up) and each part's, then how many of the episodes started were finished, as a
 * bar, and Echo dressed for the time of day beside his line. Evenings, late nights and all-day listening
 * get the night card (deep purple, stars); mornings and daytime the light day card.
 */
@Composable
internal fun BoxScope.StoryTypeCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, line: String) {
    val type = story.type ?: return
    val night = type.time.isNight()
    val fg = if (night) palette.onContainer else palette.deep
    if (night) {
        listOf(StoryStar(352f, 330f, 3f, StoryMotion.D2), StoryStar(40f, 640f, 4f, StoryMotion.D3), StoryStar(300f, 610f, 3f, StoryMotion.D4), StoryStar(210f, 300f, 4f, StoryMotion.D2)).forEach { star ->
            Box(Modifier.zIndex(StoryLayers.BACK).at(star.x, star.y).size(star.size.dp).storyPop(clock, star.delay).clip(RoundedCornerShape(2.dp)).background(fg))
        }
    }
    val dial = if (night) {
        StoryClockColors(disc = palette.container, ring = palette.clockRing, top = fg, second = palette.clockRing, rest = palette.clockWedge, hub = palette.deep, labels = fg)
    } else {
        StoryClockColors(disc = palette.shareBlob, ring = palette.podium, top = palette.vivid, second = palette.primary, rest = palette.podium, hub = palette.onContainer, labels = fg)
    }
    StoryHeading(gap = 4f) {
        StoryEyebrow(stringResource(R.string.history_ins_type_title), fg, clock)
        Text(typeName(type), style = StoryType.of(56f, 58f, GoogleSansWeight.semiBold, -0.8f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D1))
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryListeningClock(story.parts, dial, scale = 0.95f, modifier = Modifier.storyBlob(clock, StoryMotion.D2))
            StoryTypeParts(story, type, fg, dial, clock)
        }
        story.finishes?.takeIf { it.started > 0 }?.let { StoryFinishBar(it, type.finishRate, fg, if (night) palette.container else palette.shareBlob, clock) }
    }
    StoryEchoLine(line, StoryLineSpot(176f, 712f, 176f, echoOnRight = false), fg to if (night) palette.deep else palette.onContainer, clock)
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(100f, 700f, fg, StorySparks.Type, clock) }
}

/** The leading part of the day's share, counting up, over "late at night", then each part's share. */
@Composable
private fun StoryTypeParts(story: MonthStory, type: ListeningType, fg: Color, dial: StoryClockColors, clock: StoryClock) {
    Column(Modifier.storyRise(clock, StoryMotion.D3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            StoryCount(InsightsWording.percent(type.topPartShare).toLong(), clock, StoryMotion.D3, { stringResource(R.string.history_story_percent, it.toInt()) }, StoryType.of(44f, 46f, GoogleSansWeight.semiBold), fg)
            Text(StoryText.partPhrase(type.topPart), style = StoryType.of(14f, 19f), color = fg.copy(alpha = 0.9f))
        }
        story.parts.filter { InsightsWording.percent(it.share) > 0 }.forEachIndexed { index, share ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(
                        if (index == 0) {
                            dial.top
                        } else if (index == 1) {
                    dial.second
                } else {
                    dial.rest
                }
                    )
                )
                Text(stringResource(R.string.history_ins_part_share, partName(share.part), InsightsWording.percent(share.share)), style = StoryType.of(15f, 20f), color = fg)
            }
        }
    }
}

/** "33 of 41 episodes finished": a bar filling to the share, and the plain fraction. */
@Composable
private fun StoryFinishBar(finishes: StoryFinishes, rate: Float?, fg: Color, track: Color, clock: StoryClock) {
    Column(Modifier.padding(top = 26.dp).storyRise(clock, StoryMotion.D4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StoryCount(finishes.finished.toLong(), clock, StoryMotion.D4, { stringResource(R.string.history_story_finished_of, it.toInt(), finishes.started) }, StoryType.of(44f, 46f, GoogleSansWeight.semiBold), fg)
            Text(stringResource(R.string.history_story_finished_label), style = StoryType.of(15f, 20f), color = fg, modifier = Modifier.padding(bottom = 6.dp))
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(track)) {
            Box(Modifier.fillMaxWidth((finishes.finished.toFloat() / finishes.started).coerceIn(0.02f, 1f)).fillMaxHeight().storyGrowX(clock, StoryMotion.D4).clip(RoundedCornerShape(5.dp)).background(fg))
        }
        rate?.let { Text(finishFact(it), style = StoryType.of(16f, 22f), color = fg, modifier = Modifier.padding(top = 4.dp)) }
    }
}

private data class StoryStar(val x: Float, val y: Float, val size: Float, val delay: Int)

/**
 * Story 6, records (S6): the month's best run of listening days (counting up) with a pill a day and how
 * it compares with the best ever, then the biggest day, longest sitting, most episodes of a show in a day
 * and the big weekday as tiles, and Echo with a trophy on the podium. With no run of two days or more, the
 * biggest day leads instead.
 */
@Composable
internal fun BoxScope.StoryRecordsCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, line: String) {
    val locale = currentLocale()
    val fg = palette.onPrimary
    val run = story.bestRun
    val biggestDay = story.biggestDay.first.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    StoryHeading(gap = 4f) {
        StoryEyebrow(stringResource(R.string.history_story_records_eyebrow), fg, clock)
        if (run != null) {
            StoryCount(run.first.toLong(), clock, StoryMotion.D1, { pluralStringResource(R.plurals.history_story_run_days, it.toInt(), it.toInt()) }, StoryType.of(96f, 96f, GoogleSansWeight.semiBold, -2f), fg, Modifier.storyLine(96f).storyRise(clock, StoryMotion.D1))
            val from = TimelineTime.shortDate(run.second, locale)
            val to = run.third.dayOfMonth.toString()
            val sentence = if (story.bestRunIsRecord) stringResource(R.string.history_story_run_line_best, from, to) else stringResource(R.string.history_story_run_line_best_is, from, to, story.bestRunEver)
            Text(sentence, style = StoryType.of(18f, 25f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D2))
            StoryRunPills(run.first, fg, clock)
        } else {
            Text(durationText(story.biggestDay.second), style = StoryType.of(64f, 68f, GoogleSansWeight.semiBold, -1f), color = fg, maxLines = 1, modifier = Modifier.storyLine(68f).storyRise(clock, StoryMotion.D1))
            Text(stringResource(R.string.history_story_biggest_only, biggestDay), style = StoryType.of(18f, 25f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D2))
        }
        StoryRecordTiles(story, run != null, biggestDay, fg, clock)
    }
    Box(
        Modifier.zIndex(StoryLayers.BACK).at(232f, 806f).size(160.dp, 110.dp).storyRise(clock, StoryMotion.D3)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(palette.podium).padding(top = 14.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text("1", style = StoryType.of(34f, 40f, GoogleSansWeight.bold), color = fg)
    }
    StoryEchoLine(line, StoryLineSpot(24f, 640f, 196f, echoOnRight = true), fg to palette.onContainer, clock)
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(316f, 660f, fg, StorySparks.Wide, clock) }
}

/** The month's other records as tiles: two side by side, then full-width ones. */
@Composable
private fun StoryRecordTiles(story: MonthStory, withBiggestDay: Boolean, biggestDay: String, fg: Color, clock: StoryClock) {
    val locale = currentLocale()
    val pair = listOfNotNull(
        if (withBiggestDay) durationText(story.biggestDay.second) to stringResource(R.string.history_story_rec_biggest, biggestDay) else null,
        story.longestSitting?.let { (ms, show) -> durationText(ms) to stringResource(R.string.history_story_rec_sitting, show) },
    )
    val most = story.mostInOneDay?.takeIf { it.episodes > 1 }
    Column(Modifier.padding(top = 22.dp).storyRise(clock, StoryMotion.D6), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pair.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (value, label) -> StoryRecordTile(value, label, fg, Modifier.weight(1f), wide = false) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        most?.let {
            val day = TimelineTime.shortDate(it.date, locale)
            StoryRecordTile(it.episodes.toString(), stringResource(R.string.history_story_rec_binge, it.showName, day), fg, Modifier.fillMaxWidth(), wide = true, valueSize = 40f)
        }
        story.bigWeekday?.let { (day, ms) ->
            StoryRecordTile(weekdayPlural(day), stringResource(R.string.history_story_rec_weekday, durationText(ms)), fg, Modifier.fillMaxWidth(), wide = true)
        }
    }
}

/** A record: its value large, its description below, or beside it on a [wide] tile. */
@Composable
private fun StoryRecordTile(value: String, label: String, fg: Color, modifier: Modifier, wide: Boolean, valueSize: Float = 28f) {
    val content: @Composable () -> Unit = {
        Text(value, style = StoryType.of(valueSize, valueSize + 4f, GoogleSansWeight.semiBold, -0.3f), color = fg, maxLines = 1)
        Text(label, style = StoryType.of(14f, 19f), color = fg, maxLines = 3)
    }
    val tile = modifier.clip(RoundedCornerShape(22.dp)).background(fg.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 14.dp).semantics(mergeDescendants = true) { }
    if (wide) {
        Row(tile, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(tile, verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

/** A pill a day of the run, up to 30 × 40 and 4 apart, narrower when the run is longer; each pops in 90ms after the last. */
@Composable
private fun StoryRunPills(days: Int, color: Color, clock: StoryClock) {
    val description = stringResource(R.string.history_story_run_pills, days)
    val width = minOf(30f, (360f - (days - 1) * 4f) / days)
    Row(Modifier.padding(top = 14.dp).clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(days) { index ->
            Box(Modifier.size(width.dp, 40.dp).storyPop(clock, StoryMotion.pillDelay(index)).clip(RoundedCornerShape((minOf(width, 26f) / 2f).dp)).background(color))
        }
    }
}

/**
 * Story 7, that's a wrap (S7): the share card (the month's time, days, episodes and finishes, top show,
 * listening type and where it was heard, with Echo celebrating on it), confetti, Echo's closing line, and
 * Share and Watch again. The share card is what gets shared.
 */
@Composable
internal fun BoxScope.StoryShareCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, onShare: () -> Unit, onWatchAgain: () -> Unit, line: String) {
    val locale = currentLocale()
    val fg = palette.onContainer
    StoryShareArt(story, palette, Modifier.zIndex(StoryLayers.BACK).at(StoryShareSpot.X, StoryShareSpot.Y).storyPop(clock))
    listOf(
        StoryConfetti(26f, 140f, 10f, 10f, 20f, palette.mint, StoryMotion.D3),
        StoryConfetti(360f, 200f, 12f, 8f, -30f, fg, StoryMotion.D4),
        StoryConfetti(24f, 440f, 8f, 12f, 40f, palette.primary, StoryMotion.D5),
        StoryConfetti(362f, 500f, 10f, 10f, -10f, palette.mint, StoryMotion.D3),
    ).forEach { piece ->
        Box(
            Modifier.zIndex(StoryLayers.FRONT).at(piece.x, piece.y).size(piece.width.dp, piece.height.dp).storyPop(clock, piece.delay)
                .graphicsLayer { rotationZ = piece.rotation }.clip(RoundedCornerShape(3.dp)).background(piece.color),
        )
    }
    Box(Modifier.zIndex(StoryLayers.BUTTONS).at(16f, 0f).size(368.dp, 862.dp), contentAlignment = Alignment.BottomCenter) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                line,
                style = StoryType.of(24f, 30f, GoogleSansWeight.semiBold).copy(textAlign = TextAlign.Center),
                color = fg,
                modifier = Modifier.fillMaxWidth().storyRise(clock, StoryMotion.ECHO_LINE_MS).padding(bottom = 14.dp),
            )
            val shareShape = RoundedCornerShape(28.dp)
            Row(
                Modifier.fillMaxWidth().height(56.dp).storyRise(clock, StoryMotion.D4).clip(shareShape).background(fg)
                    .expressiveClickable(shape = shareShape, feedback = ExpressivePressFeedback.Control, onClick = onShare)
                    .semantics(mergeDescendants = true) { role = Role.Button },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = palette.deep, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.history_story_share, StoryText.monthName(story.month, locale)), style = StoryType.of(16f, 22f, GoogleSansWeight.semiBold), color = palette.deep)
            }
            val againShape = RoundedCornerShape(24.dp)
            Box(
                Modifier.fillMaxWidth().height(48.dp).storyRise(clock, StoryMotion.D5).clip(againShape)
                    .expressiveClickable(shape = againShape, feedback = ExpressivePressFeedback.Control, onClick = onWatchAgain)
                    .semantics(mergeDescendants = true) { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.history_story_watch_again), style = StoryType.of(15f, 20f, GoogleSansWeight.semiBold), color = fg)
            }
        }
    }
}

private data class StoryConfetti(val x: Float, val y: Float, val width: Float, val height: Float, val rotation: Float, val color: Color, val delay: Int)

/**
 * The share card (S7): 288 × 500, 30dp corners, onContainer with the deep ink; a blob behind, "My
 * September in listening", the total, days, episodes and finishes, the top show, the listening type and
 * where it was heard as pills, and "Listen on boxlore". Echo (drawn by the story frame) stands on its
 * lower right.
 */
@Composable
internal fun StoryShareArt(story: MonthStory, palette: StoryPalette, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val ink = palette.deep
    val month = StoryText.monthName(story.month, locale)
    // Only when the story has its listening type card: too little timed listening calls no type.
    val typeLabel = story.type?.takeIf { StoryCard.TYPE in StoryLogic.cards(story) }?.let { typeName(it) }
    val where = story.how?.outputs?.firstOrNull()?.takeIf { it.share >= WHERE_PILL_FROM }?.let { whereHeadline(it) }
    val description = stringResource(R.string.history_story_share_card_label, month, durationText(story.totalMs), story.topShow?.name.orEmpty(), typeLabel.orEmpty())
    Box(modifier.size(StoryShareSpot.WIDTH.dp, StoryShareSpot.HEIGHT.dp).clip(RoundedCornerShape(30.dp)).background(palette.onContainer).clearAndSetSemantics { contentDescription = description }) {
        Box(Modifier.at(120f, 300f).size(240.dp).graphicsLayer { rotationZ = -8f }.clip(StoryBlobShape.Card).background(palette.shareBlob))
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.history_story_share_card_title, month), style = StoryType.of(13f, 18f, GoogleSansWeight.semiBold), color = ink)
            Text(durationText(story.totalMs), style = StoryType.of(44f, 46f, GoogleSansWeight.semiBold, -0.5f), color = ink, maxLines = 1, modifier = Modifier.storyLine(46f))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StoryShareStat(story.listenedDays, R.string.history_story_share_days, ink)
                StoryShareStat(story.counts.episodes, R.string.history_story_share_episodes, ink)
                StoryShareStat(story.counts.finished, R.string.history_story_share_finished, ink)
            }
            story.topShow?.let { top ->
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OptimizedImage(url = top.imageUrl, proxyWidth = 128, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)))
                    Column {
                        Text(stringResource(R.string.history_story_share_card_top), style = StoryType.of(11f, 14f), color = ink)
                        Text(top.name, style = StoryType.of(15f, 19f, GoogleSansWeight.semiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                typeLabel?.let { StorySharePill(it, ink, palette.onContainer) }
                where?.let { StorySharePill(it, palette.shareBlob, ink) }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.history_story_share_listen_on), style = StoryType.of(10f, 13f), color = ink)
            Text(stringResource(R.string.history_story_share_brand), style = StoryType.of(19f, 22f, GoogleSansWeight.bold), color = ink)
        }
    }
}

/** The share card's pills show where the listening was heard only when one output leads it. */
private const val WHERE_PILL_FROM = 0.5f

@Composable
private fun StoryShareStat(value: Int, label: Int, ink: Color) {
    Column {
        Text(value.toString(), style = StoryType.of(22f, 26f, GoogleSansWeight.semiBold), color = ink)
        Text(stringResource(label), style = StoryType.of(11f, 14f), color = ink)
    }
}

@Composable
private fun StorySharePill(text: String, background: Color, color: Color) {
    Box(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(background).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = StoryType.of(12f, 16f, GoogleSansWeight.semiBold), color = color, maxLines = 1)
    }
}

/** Where the share card sits on the frame. */
internal object StoryShareSpot {
    const val X = 56f
    const val Y = 88f
    const val WIDTH = 288f
    const val HEIGHT = 500f
}
