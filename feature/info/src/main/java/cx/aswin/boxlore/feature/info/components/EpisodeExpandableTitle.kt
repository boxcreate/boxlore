package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import kotlin.math.roundToInt

@Composable
internal fun EpisodeExpandableTitle(episodeId: String, title: String) {
    ExpandableInfoTitle(itemId = episodeId, title = title, collapsedLines = 3)
}

@Composable
internal fun ExpandableInfoTitle(itemId: String, title: String, collapsedLines: Int, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    var expanded by rememberSaveable(itemId, title) { mutableStateOf(false) }
    val style = MaterialTheme.typography.headlineMedium.copy(fontWeight = GoogleSansWeight.bold, textAlign = textAlign)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val textConstraints = Constraints(maxWidth = constraints.maxWidth)
        val collapsed = measurer.measure(title, style, maxLines = collapsedLines, overflow = TextOverflow.Ellipsis, constraints = textConstraints)
        if (!collapsed.hasVisualOverflow) {
            Text(title, style = style, modifier = Modifier.fillMaxWidth().semantics { heading() })
        } else {
            val layout = if (expanded) measurer.measure(title, style, constraints = textConstraints) else collapsed
            val lastLineStart = layout.getLineStart(layout.lineCount - 1)
            InfoTitleWithControl(title, lastLineStart, layout, style, if (expanded) Int.MAX_VALUE else collapsedLines - 1) { expanded = !expanded }
        }
    }
}

@Composable
private fun InfoTitleWithControl(title: String, lastLineStart: Int, paragraph: TextLayoutResult, style: TextStyle, prefixLines: Int, onToggle: () -> Unit) {
    val expanded = prefixLines == Int.MAX_VALUE
    Layout(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            heading()
            contentDescription = title
        }.expressiveClickable(onClick = onToggle),
        content = {
            Text(
                title.substring(0, lastLineStart).trimEnd(),
                style = style,
                maxLines = prefixLines,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
            Text(
                title.substring(lastLineStart),
                style = style,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
            IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        stringResource(if (expanded) R.string.episode_info_show_less else R.string.episode_info_full_title),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val control = measurables[2].measure(Constraints.fixed(48.dp.roundToPx(), 48.dp.roundToPx()))
        val prefix = measurables[0].measure(Constraints(maxWidth = width))
        val tail = measurables[1].measure(Constraints(maxWidth = (width - control.width).coerceAtLeast(0)))
        val placement = episodeTitleControlPlacement(
            paragraphHeight = paragraph.size.height,
            paragraphFinalBaseline = paragraph.getLineBaseline(paragraph.lineCount - 1).roundToInt(),
            tail = EpisodeTitleLineMetrics(tail.height, tail[FirstBaseline], tail[LastBaseline]),
            controlHeight = control.height,
        )
        layout(width, placement.height) {
            val tailX = if (style.textAlign == TextAlign.Center) (width - tail.width - control.width) / 2 else 0
            prefix.placeRelative(0, 0)
            tail.placeRelative(tailX, placement.tailY)
            control.placeRelative(if (style.textAlign == TextAlign.Center) tailX + tail.width else width - control.width, placement.controlY)
        }
    }
}

internal data class EpisodeTitleLineMetrics(val height: Int, val firstBaseline: Int, val lastBaseline: Int)

internal data class EpisodeTitleControlPlacement(val height: Int, val tailY: Int, val controlY: Int)

/** Preserve the paragraph's baselines; the 48dp touch target must never stretch a text line. */
internal fun episodeTitleControlPlacement(paragraphHeight: Int, paragraphFinalBaseline: Int, tail: EpisodeTitleLineMetrics, controlHeight: Int): EpisodeTitleControlPlacement {
    val tailY = paragraphFinalBaseline - tail.firstBaseline
    val lastLineTop = tail.lastBaseline - tail.firstBaseline
    val controlY = tailY + lastLineTop + (tail.height - lastLineTop - controlHeight) / 2
    return EpisodeTitleControlPlacement(maxOf(paragraphHeight, tailY + tail.height), tailY, controlY)
}
