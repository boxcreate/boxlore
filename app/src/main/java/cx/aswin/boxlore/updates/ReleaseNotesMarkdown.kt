package cx.aswin.boxlore.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

internal data class ReleaseNotesBlock(val text: String, val heading: Int = 0, val marker: String? = null, val callout: Boolean = false)

/** Hide development references while preserving ordinary release-note links. */
internal fun stripReleasePrReferences(notes: String): String = notes.lines().joinToString("\n") { line ->
    line.replace(Regex("\\[[^\\]]*]\\(https://github\\.com/[^/\\s]+/[^/\\s]+/pull/\\d+(?:[?#][^)]*)?\\)"), "")
        .replace(Regex("https://github\\.com/[^/\\s]+/[^/\\s]+/pull/\\d+(?:[?#][^\\s)]*)?"), "")
        .replace(Regex("\\(\\s*\\)|\\(#[0-9]+\\)"), "")
        .trimEnd()
}

/** Listener Markdown subset; unsupported syntax remains readable text, never executable HTML. */
internal fun releaseNotesBlocks(notes: String, stripPrLinks: Boolean = true): List<ReleaseNotesBlock> {
    val blocks = mutableListOf<ReleaseNotesBlock>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks.add(ReleaseNotesBlock(paragraph.joinToString("\n")))
        paragraph.clear()
    }
    val content = if (stripPrLinks) stripReleasePrReferences(notes) else notes
    content.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
        val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line.trim())
        val bullet = Regex("^\\s*(?:[-*+•]|(\\d+)[.)])\\s+(.+)$").matchEntire(line)
        when {
            line.isBlank() -> flush()
            heading != null -> {
                flush()
                blocks.add(ReleaseNotesBlock(heading.groupValues[2], heading.groupValues[1].length))
            }
            bullet != null -> {
                flush()
                blocks.add(ReleaseNotesBlock(bullet.groupValues[2], marker = bullet.groupValues[1].takeIf { it.isNotEmpty() }?.plus(".") ?: "•"))
            }
            line.trimStart().startsWith(">") -> {
                flush()
                blocks.add(ReleaseNotesBlock(line.trimStart().removePrefix(">").trimStart(), callout = true))
            }
            else -> paragraph.add(line)
        }
    }
    flush()
    return blocks
}

private enum class ReleaseInlineFormat(val pattern: Regex) {
    LINK(Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)""")),
    BOLD(Regex("""\*\*(.+?)\*\*|__(.+?)__""")),
    ITALIC(Regex("""\*(.+?)\*|_(.+?)_""")),
    CODE(Regex("""`([^`]+)`""")),
}

/** Retain lookahead, but refresh matches consumed inside a link or code span. */
private fun nextReleaseInlineMatch(
    text: String,
    offset: Int,
    matches: MutableMap<ReleaseInlineFormat, MatchResult?>,
): Pair<ReleaseInlineFormat, MatchResult>? {
    matches.forEach { (format, match) ->
        if (match != null && match.range.first < offset) matches[format] = format.pattern.find(text, offset)
    }
    return matches.entries.filter { it.value != null }
        .minByOrNull { requireNotNull(it.value).range.first }
        ?.let { it.key to requireNotNull(it.value) }
}

internal fun releaseNotesInline(text: String, linkStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    // Insertion order gives links and bold precedence when matches start together.
    val matches = ReleaseInlineFormat.entries.associateWith { it.pattern.find(text) }.toMutableMap()
    var offset = 0
    while (true) {
        val (format, match) = nextReleaseInlineMatch(text, offset, matches) ?: break
        append(text.substring(offset, match.range.first))
        when (format) {
            ReleaseInlineFormat.LINK -> withLink(LinkAnnotation.Url(match.groupValues[2], TextLinkStyles(style = linkStyle))) { append(match.groupValues[1]) }
            ReleaseInlineFormat.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1].ifEmpty { match.groupValues[2] }) }
            ReleaseInlineFormat.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[1].ifEmpty { match.groupValues[2] }) }
            ReleaseInlineFormat.CODE -> withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) { append(match.groupValues[1]) }
        }
        offset = match.range.last + 1
    }
    append(text.substring(offset))
}

@Composable
internal fun ReleaseNotesMarkdown(notes: String, stripPrLinks: Boolean = true) {
    val blocks = remember(notes, stripPrLinks) { releaseNotesBlocks(notes, stripPrLinks) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            val style = when (block.heading) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                in 3..6 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.bodyLarge
            }
            if (block.callout) {
                Surface(shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                    Text(releaseNotesInline(block.text, SpanStyle(color = colors.onPrimaryContainer)), Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = if (block.heading > 0) 8.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.marker?.let { Text(it, style = style, color = colors.onSurfaceVariant) }
                    Text(
                        releaseNotesInline(block.text, SpanStyle(color = colors.primary)),
                        style = style,
                        color = if (block.heading > 0) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
