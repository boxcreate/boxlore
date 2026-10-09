package cx.aswin.boxlore.core.catalog.shownotes

import cx.aswin.boxlore.core.model.Chapter
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

internal object DescriptionChapters {
    private val timestamp = Regex("""(?<![\d:])(?:(\d{1,3}):)?(\d{1,3}):(\d{2})(?![\d:])""")
    private val separators = Regex("""^[\s\[\]()–—\-:•]+$""")
    private val trimTitle = Regex("""(?:^[\s\[\]()–—\-:•]+)|(?:[\s\[\]()–—\-:•]+$)""")

    fun parse(plain: String, durationSeconds: Int): List<Chapter> {
        val chapters = plain.lines().mapNotNull { parseLine(it, durationSeconds) }
            .distinctBy { it.startTime }.sortedBy { it.startTime }
        return chapters.takeIf { it.size >= 2 }.orEmpty()
    }

    private fun parseLine(line: String, durationSeconds: Int = 0): Chapter? {
        val match = timestamp.find(line) ?: return null
        val before = line.substring(0, match.range.first)
        val after = line.substring(match.range.last + 1)
        val rawTitle = when {
            before.isBlank() || separators.matches(before) -> after
            after.isBlank() || separators.matches(after) -> before
            else -> return null
        }
        val time = seconds(match) ?: return null
        val title = rawTitle.replace(trimTitle, "").trim()
        if (title.isBlank() || (durationSeconds > 0 && time >= durationSeconds)) return null
        return Chapter(startTime = time.toDouble(), title = title)
    }

    private fun seconds(match: MatchResult): Long? {
        val hours = match.groups[1]?.value?.toLongOrNull()
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        if (seconds >= 60 || (hours != null && minutes >= 60)) return null
        return (hours ?: 0L) * 3600L + minutes * 60L + seconds
    }

    fun linkTimestamps(body: Element, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        val valid = chapters.associateBy { it.startTime.toLong() }
        val textNodes = mutableListOf<TextNode>()
        NodeTraversor.traverse(
            object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node is TextNode && node.noteAncestors().none { it.normalName() in setOf("a", "code", "pre") }) textNodes.add(node)
            }
            override fun tail(node: Node, depth: Int) = Unit
        },
            body
        )
        textNodes.forEach { node ->
            val text = node.wholeText
            val matches = timestamp.findAll(text).filter { match ->
                valid[seconds(match)]?.let { chapter -> isChapterTimestamp(node, text, match, chapter) } == true
            }.toList()
            if (matches.isEmpty()) return@forEach
            var offset = 0
            matches.forEach { match ->
                node.before(TextNode(text.substring(offset, match.range.first)))
                node.before(Element("a").attr("href", "play-position:${seconds(match)}").text(match.value))
                offset = match.range.last + 1
            }
            node.before(TextNode(text.substring(offset)))
            node.remove()
        }
    }
    private fun isChapterTimestamp(node: TextNode, text: String, match: MatchResult, chapter: Chapter): Boolean {
        val lineStart = text.lastIndexOf('\n', match.range.first).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', match.range.last + 1).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        parseLine(line)?.let { return it == chapter }
        if (line.trim() != match.value) return false
        return node.noteAncestors().firstOrNull { it.normalName() in setOf("p", "li", "div") }
            ?.wholeText()?.lines()?.any { parseLine(it) == chapter } == true
    }
}
