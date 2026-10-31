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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import cx.aswin.boxlore.feature.history.timeline.timeLeftText

/**
 * Story 3, top show (S3): the month's top show by time, its artwork beside its share (counting up),
 * time and finishes, Resume, the runners-up, and Echo in the genre's outfit beside his line.
 */
@Composable
internal fun BoxScope.StoryTopShowCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, onResume: (ListeningHistoryItem) -> Unit, line: String) {
    val top = story.topShow ?: return
    val locale = currentLocale()
    val fg = palette.onGreen
    Box(Modifier.zIndex(StoryLayers.BACK).at(200f, 690f).size(280.dp).storyBlob(clock).clip(StoryBlobShape.Card).background(palette.greenBlob))
    StoryHeading(gap = 4f) {
        StoryEyebrow(stringResource(R.string.history_story_top_eyebrow), fg, clock)
        Text(top.name, style = StoryType.of(44f, 48f, GoogleSansWeight.semiBold, -0.5f), color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.storyRise(clock, StoryMotion.D1))
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp)) {
                StoryCover(top.imageUrl, StoryCoverSpot(0f, 0f, 150f, 30f, rotation = -5f, shadow = 10f), motion = Modifier.storyDrop(clock, StoryMotion.D2))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.storyRise(clock, StoryMotion.D3)) {
                    StoryCount((top.share * 100).toLong(), clock, StoryMotion.D3, { stringResource(R.string.history_story_percent, it.toInt()) }, StoryType.of(44f, 46f, GoogleSansWeight.semiBold, -0.5f), fg)
                    Text(stringResource(R.string.history_story_top_share_of, StoryText.monthName(story.month, locale)), style = StoryType.of(14f, 19f), color = fg)
                }
                Column(Modifier.storyRise(clock, StoryMotion.D4)) {
                    Text(durationText(top.listenedMs), style = StoryType.of(24f, 28f, GoogleSansWeight.semiBold), color = fg, maxLines = 1)
                    if (top.finished > 0) {
                        Text(pluralStringResource(R.plurals.history_story_top_finished_short, top.finished, top.finished), style = StoryType.of(14f, 19f), color = fg)
                    }
                }
            }
        }
        top.resume?.let { episode ->
            StoryResumeButton(episode, top.resumeRemainingMs, palette, Modifier.storyRise(clock, StoryMotion.D4).padding(top = 24.dp), onResume)
        }
        StoryRunnersUp(story, top.listenedMs, fg, clock)
    }
    StoryEchoLine(line, StoryLineSpot(24f, 704f, 196f, echoOnRight = true), fg to palette.greenInk, clock)
    Box(Modifier.zIndex(StoryLayers.FRONT)) { StorySpark(300f, 700f, fg, StorySparks.Wide, clock) }
}

/** "Also on repeat": the next two shows with their time, as bars against the top show's. */
@Composable
private fun StoryRunnersUp(story: MonthStory, topMs: Long, fg: Color, clock: StoryClock) {
    if (story.runnersUp.isEmpty()) return
    Column(Modifier.padding(top = 28.dp).storyRise(clock, StoryMotion.D5), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.history_story_also), style = StoryType.eyebrow(), color = fg.copy(alpha = 0.85f))
        story.runnersUp.forEachIndexed { index, show ->
            Row(Modifier.semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((index + 2).toString(), style = StoryType.of(14f, 20f, GoogleSansWeight.semiBold), color = fg.copy(alpha = 0.8f), modifier = Modifier.width(14.dp))
                Box(Modifier.size(44.dp)) { StoryCover(show.imageUrl, StoryCoverSpot(0f, 0f, 44f, 12f)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(show.name, style = StoryType.of(14f, 19f, GoogleSansWeight.semiBold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(durationText(show.listenedMs), style = StoryType.of(14f, 19f), color = fg.copy(alpha = 0.85f), maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(fg.copy(alpha = 0.18f))) {
                        Box(
                            Modifier.fillMaxWidth((show.listenedMs.toFloat() / topMs).coerceIn(0.02f, 1f)).fillMaxHeight()
                                .storyGrowX(clock, StoryMotion.D5 + index * 120).clip(RoundedCornerShape(4.dp)).background(fg),
                        )
                    }
                }
            }
        }
    }
}

/** "Resume · 32 min left": 48dp, fully round, onGreen with the green card's ink. Talkback also hears the episode. */
@Composable
private fun StoryResumeButton(episode: ListeningHistoryItem, remainingMs: Long, palette: StoryPalette, modifier: Modifier, onResume: (ListeningHistoryItem) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val left = timeLeftText(remainingMs)
    val label = stringResource(R.string.history_story_resume, episode.episodeTitle, left)
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(palette.onGreen)
            .expressiveClickable(shape = shape, feedback = ExpressivePressFeedback.Control) { onResume(episode) }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
            }
            .padding(start = 14.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = palette.greenInk, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.history_story_resume_short, left),
            style = StoryType.of(15f, 20f, GoogleSansWeight.semiBold),
            color = palette.greenInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}
