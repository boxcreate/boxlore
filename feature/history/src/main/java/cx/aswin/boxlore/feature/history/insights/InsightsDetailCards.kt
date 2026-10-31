package cx.aswin.boxlore.feature.history.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.TimelineType

/** Shares from this make the headline "Mostly …". */
private const val MOSTLY_FROM = 0.5f

/** New episodes lead from this share, and older ones below [CATCHING_UP_BELOW]; in between it's about half. */
private const val NEW_FROM = 0.58f
private const val CATCHING_UP_BELOW = 0.42f

/** Lore and the Smart Queue show from this share of the listening. */
private const val SOURCE_FROM = 0.01f

/** Where you listen: the outputs by share, the largest first, each with its icon and a bar against the largest. */
@Composable
internal fun WhereYouListenCard(outputs: List<OutputShare>) {
    if (outputs.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val top = outputs.first()
    InsightsCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.history_ins_where), style = TimelineType.header, color = colors.onSurface)
            Text(whereHeadline(top), style = InsightsType.title18, color = colors.onSurface)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            outputs.forEachIndexed { index, share -> OutputRow(share, fraction = share.share / top.share, isTop = index == 0) }
        }
    }
}

@Composable
private fun OutputRow(share: OutputShare, fraction: Float, isTop: Boolean) {
    val colors = MaterialTheme.colorScheme
    val name = outputName(share.output)
    val percent = InsightsWording.percent(share.share)
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (isTop) colors.primaryContainer else colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(outputIcon(share.output), contentDescription = null, tint = if (isTop) colors.onPrimaryContainer else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = TimelineType.label, color = colors.onSurface, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.history_ins_percent, percent), style = TimelineType.meta, color = colors.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceVariant)) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primary),
                )
            }
        }
    }
}

/**
 * New or catching up: [newShare] of the listening with a known release date, episodes out a week or less,
 * as one bar (older on primaryContainer), then what Lore and Smart queue started.
 */
@Composable
internal fun NewOrCatchingUpCard(sources: ListeningSources, newShare: Float) {
    val colors = MaterialTheme.colorScheme
    val newPercent = InsightsWording.percent(newShare)
    val olderPercent = 100 - newPercent
    val newLabel = stringResource(R.string.history_ins_fresh_new, newPercent)
    val olderLabel = stringResource(R.string.history_ins_fresh_older, olderPercent)
    InsightsCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.history_ins_fresh), style = TimelineType.header, color = colors.onSurface)
            Text(freshHeadline(newShare), style = InsightsType.title18, color = colors.onSurface)
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics { contentDescription = "$newLabel, $olderLabel" },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (newPercent > 0) SplitPart(newShare, colors.primary, start = true, end = olderPercent == 0)
            if (olderPercent > 0) SplitPart(1f - newShare, colors.primaryContainer, start = newPercent == 0, end = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(colors.primary, newLabel)
            LegendDot(colors.primaryContainer, olderLabel, outlined = true)
        }
        Text(stringResource(R.string.history_ins_fresh_hint), style = TimelineType.meta, color = colors.onSurfaceVariant)
        val started =
            listOfNotNull(
                (Icons.Rounded.AutoAwesome to R.string.history_ins_from_lore).takeIf { sources.loreShare >= SOURCE_FROM }?.let { it to sources.loreShare },
                (Icons.AutoMirrored.Rounded.QueueMusic to R.string.history_ins_from_smart_queue).takeIf { sources.smartQueueShare >= SOURCE_FROM }?.let { it to sources.smartQueueShare },
            )
        if (started.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                started.forEach { (fact, share) -> SourceFact(fact.first, stringResource(fact.second, InsightsWording.percent(share))) }
            }
        }
    }
}

@Composable
private fun RowScope.SplitPart(share: Float, color: Color, start: Boolean, end: Boolean) {
    val startCorner = if (start) 8.dp else 3.dp
    val endCorner = if (end) 8.dp else 3.dp
    Box(
        Modifier
            .weight(share.coerceAtLeast(0.01f))
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = startCorner, bottomStart = startCorner, topEnd = endCorner, bottomEnd = endCorner))
            .background(color),
    )
}

@Composable
private fun LegendDot(color: Color, text: String, outlined: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // An outlined dot, as the other legends draw their primaryContainer part.
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(color)
                .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
        )
        Text(text, style = InsightsType.body13, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SourceFact(icon: ImageVector, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(text, style = InsightsType.body14, color = colors.onSurface)
    }
}

@Composable
internal fun whereHeadline(top: OutputShare): String {
    if (top.share < MOSTLY_FROM) return stringResource(R.string.history_ins_where_spread)
    return stringResource(
        when (top.output) {
            ListeningOutput.HEADPHONES -> R.string.history_ins_where_mostly_headphones
            ListeningOutput.BLUETOOTH -> R.string.history_ins_where_mostly_bluetooth
            ListeningOutput.CAR -> R.string.history_ins_where_mostly_car
            ListeningOutput.SPEAKER -> R.string.history_ins_where_mostly_speaker
            ListeningOutput.CAST -> R.string.history_ins_where_mostly_cast
        },
    )
}

@Composable
internal fun freshHeadline(newShare: Float): String = stringResource(
    when {
        newShare >= NEW_FROM -> R.string.history_ins_fresh_mostly_new
        newShare < CATCHING_UP_BELOW -> R.string.history_ins_fresh_mostly_back
        else -> R.string.history_ins_fresh_half
    },
)

@Composable
internal fun outputName(output: ListeningOutput): String = stringResource(
    when (output) {
        ListeningOutput.HEADPHONES -> R.string.history_ins_output_headphones
        ListeningOutput.BLUETOOTH -> R.string.history_ins_output_bluetooth
        ListeningOutput.CAR -> R.string.history_ins_output_car
        ListeningOutput.SPEAKER -> R.string.history_ins_output_speaker
        ListeningOutput.CAST -> R.string.history_ins_output_cast
    },
)

internal fun outputIcon(output: ListeningOutput): ImageVector = when (output) {
    ListeningOutput.HEADPHONES -> Icons.Rounded.Headphones
    ListeningOutput.BLUETOOTH -> Icons.Rounded.Bluetooth
    ListeningOutput.CAR -> Icons.Rounded.DirectionsCar
    ListeningOutput.SPEAKER -> Icons.Rounded.PhoneAndroid
    ListeningOutput.CAST -> Icons.Rounded.Cast
}
