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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.insights.InsightsWording
import cx.aswin.boxlore.feature.history.insights.OutputShare
import cx.aswin.boxlore.feature.history.insights.freshHeadline
import cx.aswin.boxlore.feature.history.insights.outputIcon
import cx.aswin.boxlore.feature.history.insights.outputName
import cx.aswin.boxlore.feature.history.insights.whereHeadline

/** Outputs shown on the card: the top three is everything the month's listening is likely to have. */
private const val OUTPUTS_SHOWN = 3

/** Lore and Smart queue show from this share of the listening. */
private const val SOURCE_FROM = 0.01f

/**
 * Story 4, how you listened (S4): where the listening was heard (each output's share, as a bar), new
 * episodes or catching up, what Lore and Smart queue started, and Echo beside his line. The light card:
 * onContainer with the deep ink.
 */
@Composable
internal fun BoxScope.StoryHowCard(story: MonthStory, palette: StoryPalette, clock: StoryClock, line: String) {
    val how = story.how ?: return
    val ink = palette.deep
    Box(Modifier.zIndex(StoryLayers.BACK).at(196f, 708f).size(280.dp).storyBlob(clock).clip(StoryBlobShape.Card).background(palette.shareBlob))
    StoryHeading(gap = 4f) {
        StoryEyebrow(stringResource(R.string.history_story_how_eyebrow), ink, clock)
        how.outputs.firstOrNull()?.let { top ->
            Text(whereHeadline(top), style = StoryType.of(44f, 48f, GoogleSansWeight.semiBold, -0.5f), color = ink, maxLines = 2, modifier = Modifier.storyRise(clock, StoryMotion.D1))
        }
        Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            how.outputs.take(OUTPUTS_SHOWN).forEachIndexed { index, share -> StoryOutputRow(share, index == 0, palette, clock, StoryMotion.D2 + index * 120) }
        }
        StoryFresh(how.sources.newShare, palette, clock)
        Column(Modifier.padding(top = 16.dp).storyRise(clock, StoryMotion.D5), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (how.sources.loreShare >= SOURCE_FROM) StorySourceFact(Icons.Rounded.AutoAwesome, stringResource(R.string.history_ins_from_lore, InsightsWording.percent(how.sources.loreShare)), palette)
            if (how.sources.smartQueueShare >= SOURCE_FROM) {
                StorySourceFact(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.history_ins_from_smart_queue, InsightsWording.percent(how.sources.smartQueueShare)), palette)
            }
        }
    }
    StoryEchoLine(line, StoryLineSpot(24f, 716f, 200f, echoOnRight = true), ink to palette.onContainer, clock)
}

/** An output: its icon (the leading one in ink), name, share counting up and a bar filling to it. */
@Composable
private fun StoryOutputRow(share: OutputShare, lead: Boolean, palette: StoryPalette, clock: StoryClock, delay: Int) {
    val ink = palette.deep
    val percent = InsightsWording.percent(share.share)
    Row(Modifier.storyRise(clock, delay).semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(if (lead) ink else palette.shareBlob), contentAlignment = Alignment.Center) {
            Icon(outputIcon(share.output), contentDescription = null, tint = if (lead) palette.onContainer else ink, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(outputName(share.output), style = StoryType.of(16f, 22f, GoogleSansWeight.semiBold), color = ink, modifier = Modifier.weight(1f))
                StoryCount(percent.toLong(), clock, delay, { stringResource(R.string.history_story_percent, it.toInt()) }, StoryType.of(28f, 30f, GoogleSansWeight.semiBold), ink)
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(palette.shareBlob)) {
                Box(Modifier.fillMaxWidth(share.share.coerceIn(0.02f, 1f)).fillMaxHeight().storyGrowX(clock, delay).clip(RoundedCornerShape(5.dp)).background(palette.vivid))
            }
        }
    }
}

/** New or catching up: the headline, one bar split into new and older, and what counts as new. */
@Composable
private fun StoryFresh(newShare: Float, palette: StoryPalette, clock: StoryClock) {
    val ink = palette.deep
    val newPercent = InsightsWording.percent(newShare)
    val olderPercent = 100 - newPercent
    val newLabel = stringResource(R.string.history_ins_fresh_new, newPercent)
    val olderLabel = stringResource(R.string.history_ins_fresh_older, olderPercent)
    Column(Modifier.padding(top = 22.dp).storyRise(clock, StoryMotion.D4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.history_ins_fresh), style = StoryType.eyebrow(), color = ink.copy(alpha = 0.85f))
        Text(freshHeadline(newShare), style = StoryType.of(28f, 32f, GoogleSansWeight.semiBold), color = ink)
        Row(Modifier.fillMaxWidth().height(16.dp).storyGrowX(clock, StoryMotion.D4).clearAndSetSemantics { contentDescription = "$newLabel, $olderLabel" }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (newPercent > 0) Box(Modifier.weight(newShare.coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 3.dp, bottomEnd = 3.dp)).background(palette.vivid))
            if (olderPercent > 0) Box(Modifier.weight((1f - newShare).coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)).background(palette.shareBlob))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StoryLegendDot(palette.vivid, newLabel, ink, outlined = false)
            StoryLegendDot(palette.shareBlob, olderLabel, ink, outlined = true, outline = palette.vivid)
        }
        Text(stringResource(R.string.history_ins_fresh_hint), style = StoryType.of(12f, 16f), color = ink.copy(alpha = 0.8f))
    }
}

@Composable
private fun StoryLegendDot(color: Color, text: String, ink: Color, outlined: Boolean, outline: Color = color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (outlined) outline else color).padding(if (outlined) 1.dp else 0.dp).clip(CircleShape).background(color))
        Text(text, style = StoryType.of(14f, 19f), color = ink)
    }
}

@Composable
private fun StorySourceFact(icon: ImageVector, text: String, palette: StoryPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = palette.vivid, modifier = Modifier.size(20.dp))
        Text(text, style = StoryType.of(15f, 21f), color = palette.deep)
    }
}
