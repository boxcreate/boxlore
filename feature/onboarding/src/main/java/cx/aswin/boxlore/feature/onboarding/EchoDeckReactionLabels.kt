package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/**
 * The lines a label [labelWidth] wide at its smallest size takes in [room]: one where it fits,
 * then as many as it needs, up to [EchoReactionMaxLines].
 */
internal fun echoReactionLines(labelWidth: Float, room: Float): Int =
    if (labelWidth <= room || room <= 0f) 1 else ceil(labelWidth / room).toInt().coerceIn(2, EchoReactionMaxLines)

/** A wrapped label's line height, and the room above and below its lines. */
internal const val EchoReactionLineHeight = 1.15f
internal val EchoReactionWrapRoom = 10.dp
private const val EchoReactionMaxLines = 3

/**
 * The icon and the label, which steps down to 12sp on one line. A label that still doesn't fit
 * beside the icon (large text, long languages) drops the icon, and one too long even for the whole
 * button wraps (the row grows to fit it), so it is never cut.
 */
@Composable
internal fun EchoReactionContent(reaction: EchoDeckReaction, style: TextStyle) {
    val label = stringResource(echoReactionLabel(reaction))
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val smallest = style.copy(fontSize = EchoReactionMinFont)
    val labelPx = remember(label, smallest, density) { measurer.measure(label, smallest, maxLines = 1, softWrap = false).size.width }
    val fullPx = remember(label, style, density) { measurer.measure(label, style, maxLines = 1, softWrap = false).size.width }
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val room = constraints.maxWidth
        val showIcon = labelPx + with(density) { EchoReactionIconRoom.roundToPx() } <= room
        // One line wherever it fits at its smallest size; wrapped, it keeps its full size.
        val lines = if (labelPx <= room) 1 else echoReactionLines(fullPx.toFloat(), room.toFloat())
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                Icon(echoReactionIcon(reaction), null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                // Wrapped lines keep a tight line height, so they fit the button at the largest text.
                style = style.copy(textAlign = TextAlign.Center, lineHeight = if (lines > 1) EchoReactionLineHeight.em else style.lineHeight),
                maxLines = lines,
                autoSize = TextAutoSize.StepBased(minFontSize = EchoReactionMinFont, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            )
        }
    }
}

/** The icon and the gap after it. */
internal val EchoReactionIconRoom: Dp = 22.dp + 8.dp

/**
 * The widest reaction label at [size] (by default the smallest it steps down to), with the current
 * text scale and language.
 */
@Composable
internal fun rememberEchoDeckLabelWidth(size: TextUnit = EchoReactionMinFont): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.titleMedium.copy(fontSize = size)
    val labels = EchoDeckReaction.entries.map { stringResource(echoReactionLabel(it)) }
    return remember(labels, style, density) {
        with(density) { labels.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }.toDp() }
    }
}

internal val EchoReactionMinFont = 12.sp
