package cx.aswin.boxlore.core.catalog.shownotes

import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import cx.aswin.boxlore.core.model.ShowNotes
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** Parses publisher HTML locally; never fetches linked pages or guesses a relative URL's origin. */
object ShowNotesParser {
    private val urlRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")
    private val genericTitles = setOf("click here", "here", "link", "read more", "learn more", "listen now", "listen here", "subscribe here")
    private val websiteTitle = Regex("""(?i)^(?:visit )?(?:(?:our|the) )?website$""")
    private val urlTitle = Regex("""(?i)^(?:https?://|www\.).*""")
    private fun isGenericTitle(title: String): Boolean = title.lowercase(java.util.Locale.ROOT) in genericTitles || websiteTitle.matches(title) || urlTitle.matches(title)

    private val trackingNames = setOf("fbclid", "gclid", "mc_cid", "mc_eid")
    private val infrastructure = setOf("podtrac.com", "chartable.com", "feedburner.com", "podcastindex.org")
    private val mediaExtensions = Regex("""(?i)\.(?:mp3|m4a|mp4|wav|ogg|rss|xml)$""")

    fun parse(description: String?, baseUrl: String? = null, durationSeconds: Int = 0): ShowNotes {
        val body = Jsoup.parseBodyFragment(description.orEmpty(), baseUrl?.toHttpUrlOrNull()?.toString().orEmpty()).body()
        body.select("script,style,noscript").remove()
        val plain = plainText(body)
        val links = extractLinks(body)
        val chapters = DescriptionChapters.parse(plain, durationSeconds)
        DescriptionChapters.linkTimestamps(body, chapters)
        body.ownerDocument()?.outputSettings()?.prettyPrint(false)
        return ShowNotes(plain, links, chapters, body.html())
    }

    private fun plainText(body: Element): String {
        val text = StringBuilder()
        NodeTraversor.traverse(
            object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when {
                    node is TextNode -> text.append(node.wholeText)
                    node is Element && (node.isBlock || node.normalName() == "br") -> text.append('\n')
                }
            }
            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.isBlock) text.append('\n')
            }
        },
            body
        )
        return text.toString().replace('\u00a0', ' ').lines()
            .map { it.replace(Regex("[\\t ]+"), " ").trim() }
            .filter(String::isNotBlank).joinToString("\n")
    }

    private fun extractLinks(body: Element): List<EpisodeLink> {
        val links = linkedMapOf<String, EpisodeLink>()
        body.select("a[href]").forEach { anchor ->
            val href = anchor.attr("href").trim()
            val absolute = if (href.startsWith("mailto:", true)) href else anchor.absUrl("href").ifBlank { href }
            val title = anchor.text().trim().takeIf { it.isNotBlank() && !isGenericTitle(it) }
            val parsed = link(absolute, title, context(anchor))
            if (parsed != null) {
                anchor.attr("href", parsed.url)
                links.putIfAbsent(canonicalKey(parsed), parsed)
            } else if (!href.startsWith("play-position:")) {
                anchor.removeAttr("href")
            }
        }
        NodeTraversor.traverse(
            object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node !is TextNode || node.noteAncestors().any { it.normalName() in setOf("a", "code", "pre") }) return
                urlRegex.findAll(node.wholeText).forEach { match ->
                    link(trimPunctuation(match.value), null, context(node))?.let { links.putIfAbsent(canonicalKey(it), it) }
                }
            }
            override fun tail(node: Node, depth: Int) = Unit
        },
            body
        )
        return links.values.toList()
    }

    private fun context(node: Node): String = node.noteAncestors().firstOrNull { it.normalName() in setOf("p", "li", "div") }
        ?.text()?.take(500).orEmpty()

    private fun link(raw: String, title: String?, context: String): EpisodeLink? {
        if (raw.startsWith("mailto:", true)) {
            val address = raw.substringAfter(':').substringBefore('?')
            if (!address.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) return null
            return EpisodeLink(raw, address, EpisodeLinkKind.EMAIL, title, context = context)
        }
        val url = (if (raw.startsWith("www.", true)) "https://$raw" else raw).toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (infrastructure.any { EpisodeLinkClassifier.matchesHost(url.host, it) } || mediaExtensions.containsMatchIn(url.encodedPath)) return null
        return EpisodeLinkClassifier.classify(url, title, context)
    }

    private fun canonicalKey(link: EpisodeLink): String {
        val url = link.url.toHttpUrlOrNull() ?: return link.url
        val canonical = url.newBuilder().query(null)
        url.queryParameterNames.filterNot { it.startsWith("utm_", true) || it.lowercase(java.util.Locale.ROOT) in trackingNames }
            .sorted().forEach { name ->
                url.queryParameterValues(name).sortedWith(compareBy { it.orEmpty() }).forEach { canonical.addQueryParameter(name, it) }
            }
        // Paths, query values, and meaningful fragments identify distinct resources.
        return canonical.build().toString()
    }

    private fun trimPunctuation(raw: String): String {
        var result = raw.trimEnd('.', ',', ';', '!', ':', ']')
        while (result.endsWith(')') && result.count { it == ')' } > result.count { it == '(' }) result = result.dropLast(1)
        return result
    }
}

internal fun Node.noteAncestors(): Sequence<Element> = generateSequence(parentNode()) { it.parentNode() }.filterIsInstance<Element>()
