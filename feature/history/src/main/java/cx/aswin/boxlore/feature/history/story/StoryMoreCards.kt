package cx.aswin.boxlore.feature.history.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.insights.TypeTime
import cx.aswin.boxlore.feature.history.insights.typeName
import cx.aswin.boxlore.feature.history.timeline.TimelineTime
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import java.time.format.DateTimeFormatter

/** Whether the listening-type card is the night version (evenings, late nights, all day) or the light day version. */
internal fun TypeTime.isNight(): Boolean = this != TypeTime.MORNING && this != TypeTime.DAYTIME

/**
 * Story 4, listening type (A10): the type's name, the part of the day it comes from and the finish
 * rate, the listening clock, and Echo dressed for the time of day. Evenings, late nights and all-day
 * listening get the night card (deep purple, stars); mornings and daytime the light day card.
 */
@Composable
internal fun BoxScope.StoryTypeCard(story: MonthStory, palette: StoryPalette, clock: StoryClock) {
    val type = story.type ?: return
    val night = type.time.isNight()
    val fg = if (night) palette.onContainer else palette.deep
    if (night) {
        listOf(StoryStar(44f, 380f, 4f, StoryMotion.D2), StoryStar(350f, 330f, 3f, StoryMotion.D3), StoryStar(120f, 470f, 3f, StoryMotion.D4), StoryStar(230f, 400f, 4f, StoryMotion.D2)).forEach { star ->
            Box(Modifier.zIndex(StoryLayers.BACK).at(star.x, star.y).size(star.size.dp).storyPop(clock, star.delay).clip(RoundedCornerShape(2.dp)).background(fg))
        }
    }
    StoryHeading(gap = 4f) {
        StoryEyebrow(stringResource(R.string.history_ins_type_title), fg, clock)
        Text(typeName(type), style = StoryType.of(60f, 62f, GoogleSansWeight.semiBold, -0.8f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D1))
        Text(StoryText.typeLine(type), style = StoryType.of(17f, 24f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D2).widthIn(max = 320.dp).padding(top = 10.dp))
    }
    val dial = if (night) {
        StoryClockColors(disc = palette.container, ring = palette.clockRing, top = fg, second = palette.clockRing, rest = palette.clockWedge, hub = palette.deep, labels = fg)
    } else {
        StoryClockColors(disc = palette.shareBlob, ring = palette.podium, top = palette.vivid, second = palette.primary, rest = palette.podium, hub = palette.onContainer, labels = fg)
    }
    StoryListeningClock(story.parts, dial, Modifier.zIndex(StoryLayers.BACK).at(134f, 330f).storyBlob(clock, StoryMotion.D2))
    StoryBubble(
        text = stringResource(StoryText.typeEchoLine(type.time)),
        background = fg,
        color = if (night) palette.deep else palette.onContainer,
        shape = StoryBubbles.TailBottomStart,
        maxWidth = 150f,
        modifier = Modifier.zIndex(StoryLayers.FRONT).at(196f, 640f).storyPop(clock, StoryMotion.D6),
    )
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(100f, 650f, fg, StorySparks.Type, clock) }
}

private data class StoryStar(val x: Float, val y: Float, val size: Float, val delay: Int)

/**
 * Story 5, records (A11): the month's best run of listening days with a pill a day, the biggest day
 * and the most episodes of a show in one day, and Echo with a trophy on the winner's podium. With no
 * run of two days or more, the biggest day leads instead.
 */
@Composable
internal fun BoxScope.StoryRecordsCard(story: MonthStory, palette: StoryPalette, clock: StoryClock) {
    val locale = currentLocale()
    val fg = palette.onPrimary
    val run = story.bestRun
    val biggestDay = story.biggestDay.first.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    StoryHeading(gap = 4f) {
        StoryEyebrow(stringResource(R.string.history_story_records_eyebrow), fg, clock)
        if (run != null) {
            Text(pluralStringResource(R.plurals.history_story_run_days, run.first, run.first), style = StoryType.of(96f, 96f, GoogleSansWeight.semiBold, -2f), color = fg, maxLines = 1, modifier = Modifier.storyLine(96f).storyRise(clock, StoryMotion.D1))
            val line = if (story.bestRunIsRecord) R.string.history_story_run_line_best else R.string.history_story_run_line
            Text(stringResource(line, TimelineTime.shortDate(run.second, locale), run.third.dayOfMonth.toString()), style = StoryType.of(18f, 25f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D2))
            StoryRunPills(run.first, fg, clock)
        } else {
            Text(durationText(story.biggestDay.second), style = StoryType.of(64f, 68f, GoogleSansWeight.semiBold, -1f), color = fg, maxLines = 1, modifier = Modifier.storyLine(68f).storyRise(clock, StoryMotion.D1))
            Text(stringResource(R.string.history_story_biggest_only, biggestDay), style = StoryType.of(18f, 25f), color = fg, modifier = Modifier.storyRise(clock, StoryMotion.D2))
        }
        Column(Modifier.storyRise(clock, StoryMotion.D6).padding(top = 24.dp).widthIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (run != null) StoryRecordLine(durationText(story.biggestDay.second), stringResource(R.string.history_story_biggest_day, biggestDay), fg)
            story.mostInOneDay?.takeIf { it.episodes > 1 }?.let { most ->
                StoryRecordLine(pluralStringResource(R.plurals.history_story_episodes, most.episodes, most.episodes), stringResource(R.string.history_story_in_one_day, most.showName), fg)
            }
        }
    }
    Box(
        Modifier.zIndex(StoryLayers.BACK).at(222f, 788f).size(170.dp, 130.dp).storyRise(clock, StoryMotion.D3)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(palette.podium).padding(top = 18.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text("1", style = StoryType.of(40f, 48f, GoogleSansWeight.bold), color = fg)
    }
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(306f, 600f, fg, StorySparks.Wide, clock) }
}

/** A pill a day of the run, 30 × 44 and 5 apart, narrower when the run is longer than ten days; each pops in 90ms after the last. */
@Composable
private fun StoryRunPills(days: Int, color: Color, clock: StoryClock) {
    val description = stringResource(R.string.history_story_run_pills, days)
    val width = minOf(30f, (360f - (days - 1) * 5f) / days)
    Row(Modifier.padding(top = 16.dp).clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(days) { index ->
            Box(Modifier.size(width.dp, 44.dp).storyPop(clock, StoryMotion.pillDelay(index)).clip(RoundedCornerShape((width / 2f).dp)).background(color))
        }
    }
}

/** "**2 h 48 min**" over "Biggest day, Wed, Oct 28". */
@Composable
private fun StoryRecordLine(value: String, description: String, color: Color) {
    val valueFont = rememberGoogleSansFamily(GoogleSansWeight.semiBold.weight)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = valueFont, fontSize = 22.sp, fontWeight = GoogleSansWeight.semiBold)) { append(value) }
            append("\n")
            append(description)
        },
        style = StoryType.of(15f, 21f),
        color = color,
    )
}

/**
 * Story 6, that's a wrap (A12): the share card (the month's time, top show and listening type, with
 * Echo celebrating on it), confetti, and Share and Watch again. The share card is what gets shared.
 */
@Composable
internal fun BoxScope.StoryShareCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, onShare: () -> Unit, onWatchAgain: () -> Unit) {
    val locale = currentLocale()
    val fg = palette.onContainer
    StoryShareArt(story, palette, Modifier.zIndex(StoryLayers.BACK).at(StoryShareSpot.X, StoryShareSpot.Y).storyPop(clock))
    listOf(
        StoryConfetti(40f, 140f, 10f, 10f, 20f, palette.mint, StoryMotion.D3),
        StoryConfetti(350f, 200f, 12f, 8f, -30f, fg, StoryMotion.D4),
        StoryConfetti(30f, 420f, 8f, 12f, 40f, palette.primary, StoryMotion.D5),
        StoryConfetti(356f, 470f, 10f, 10f, -10f, palette.mint, StoryMotion.D3),
    ).forEach { piece ->
        Box(
            Modifier.zIndex(StoryLayers.FRONT).at(piece.x, piece.y).size(piece.width.dp, piece.height.dp).storyPop(clock, piece.delay)
                .graphicsLayer { rotationZ = piece.rotation }.clip(RoundedCornerShape(3.dp)).background(piece.color),
        )
    }
    Box(Modifier.zIndex(StoryLayers.BUTTONS).at(16f, 0f).size(368.dp, 862.dp), contentAlignment = Alignment.BottomCenter) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.history_story_see_you, StoryText.monthName(story.month.plusMonths(1), locale)),
                style = StoryType.of(26f, 32f, GoogleSansWeight.semiBold).copy(textAlign = TextAlign.Center),
                color = fg,
                modifier = Modifier.fillMaxWidth().storyRise(clock, StoryMotion.D3).padding(bottom = 16.dp),
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
 * The share card (A12): 264 × 470, 30dp corners, onContainer with the deep ink; a blob behind, "My
 * October in listening", the total, the top show, and "Listen on boxlore" beside the listening-type
 * pill. Echo (drawn by the story frame) stands on its lower half.
 */
@Composable
internal fun StoryShareArt(story: MonthStory, palette: StoryPalette, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val ink = palette.deep
    val month = StoryText.monthName(story.month, locale)
    val typeLabel = story.type?.let { typeName(it) }
    val description = stringResource(R.string.history_story_share_card_label, month, durationText(story.totalMs), story.topShow?.name.orEmpty(), typeLabel.orEmpty())
    Box(modifier.size(StoryShareSpot.WIDTH.dp, StoryShareSpot.HEIGHT.dp).clip(RoundedCornerShape(30.dp)).background(palette.onContainer).clearAndSetSemantics { contentDescription = description }) {
        Box(Modifier.at(22f, 228f).size(220.dp).graphicsLayer { rotationZ = -8f }.clip(StoryBlobShape.Card).background(palette.shareBlob))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.history_story_share_card_title, month), style = StoryType.of(13f, 18f, GoogleSansWeight.semiBold), color = ink)
            Text(durationText(story.totalMs), style = StoryType.of(36f, 40f, GoogleSansWeight.semiBold, -0.3f), color = ink, maxLines = 1, modifier = Modifier.storyLine(40f))
            story.topShow?.let { top ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OptimizedImage(url = top.imageUrl, proxyWidth = 128, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Column {
                        Text(stringResource(R.string.history_story_share_card_top), style = StoryType.of(11f, 14f), color = ink)
                        Text(top.name, style = StoryType.of(14f, 18f, GoogleSansWeight.semiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 18.dp).width(224.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stringResource(R.string.history_story_share_listen_on), style = StoryType.of(10f, 13f), color = ink)
                Text(stringResource(R.string.history_story_share_brand), style = StoryType.of(19f, 22f, GoogleSansWeight.bold), color = ink)
            }
            typeLabel?.let { label ->
                Box(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(ink).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = StoryType.of(12f, 16f, GoogleSansWeight.semiBold), color = palette.onContainer, maxLines = 1)
                }
            }
        }
    }
}

/** Where the share card sits on the frame. */
internal object StoryShareSpot {
    const val X = 68f
    const val Y = 92f
    const val WIDTH = 264f
    const val HEIGHT = 470f
}
