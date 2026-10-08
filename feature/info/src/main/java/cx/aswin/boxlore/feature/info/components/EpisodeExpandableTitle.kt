package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R

@Composable
internal fun EpisodeExpandableTitle(episodeId: String, title: String) {
    var expanded by rememberSaveable(episodeId, title) { mutableStateOf(false) }
    val style = MaterialTheme.typography.headlineMedium.copy(fontWeight = GoogleSansWeight.bold)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val textConstraints = Constraints(maxWidth = constraints.maxWidth)
        val collapsed = measurer.measure(title, style, maxLines = 3, overflow = TextOverflow.Ellipsis, constraints = textConstraints)
        if (!collapsed.hasVisualOverflow) {
            Text(title, style = style, modifier = Modifier.fillMaxWidth().semantics { heading() })
        } else {
            val layout = if (expanded) measurer.measure(title, style, constraints = textConstraints) else collapsed
            val lastLineStart = layout.getLineStart(layout.lineCount - 1)
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = title
                }.expressiveClickable { expanded = !expanded },
            ) {
                // The first two collapsed lines retain the entire width. Only the final line makes room for the control.
                Text(
                    title.substring(0, lastLineStart).trimEnd(),
                    style = style,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                )
                EpisodeTitleLastLine(title.substring(lastLineStart), style, expanded) { expanded = !expanded }
            }
        }
    }
}

@Composable
private fun EpisodeTitleLastLine(text: String, style: TextStyle, expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = style,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
        IconButton(onClick = onToggle, modifier = Modifier.align(Alignment.Bottom).size(48.dp)) {
            Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    stringResource(if (expanded) R.string.episode_info_show_less else R.string.episode_info_full_title),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
