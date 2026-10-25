package cx.aswin.boxlore.navigation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** The verified App Link filters in the manifest and [WebAppLinks] must claim the same hosts and paths. */
class WebAppLinksManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test fun `manifest App Link filters match WebAppLinks, one host per filter`() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest)
        val filters = document.getElementsByTagName("intent-filter").elements()
            .filter { it.getAttributeNS(android, "autoVerify") == "true" }

        val claimed = filters.associate { filter ->
            val data = filter.getElementsByTagName("data").elements()
            val hosts = data.mapNotNull { it.attr("host") }
            assertEquals("each verified filter declares exactly one host: $hosts", 1, hosts.size)
            assertEquals(listOf("https"), data.mapNotNull { it.attr("scheme") })
            hosts.single() to data.mapNotNull { it.attr("pathPrefix") }.toSet()
        }

        assertEquals(WebAppLinks.claimedPaths.mapValues { it.value.toSet() }, claimed)
    }

    @Test fun `every episode share pattern, with and without the show, is on a claimed host and path`() {
        val patterns = WebAppLinks.shareBases.flatMap { base ->
            listOf("$base?type=episode&id={episodeId}", "$base?type=episode&id={episodeId}&t={t}") + EpisodeShowLinks.sharePatterns(base)
        }
        assertEquals(WebAppLinks.shareBases.size * 6, patterns.size)
        patterns.forEach { pattern ->
            val uri = java.net.URI(pattern.replace("{", "").replace("}", ""))
            val prefixes = WebAppLinks.claimedPaths[uri.host].orEmpty()
            assertEquals(pattern, true, prefixes.any { uri.path.startsWith(it) })
            if ("show=" in pattern) assertEquals(pattern, "podcastId", uri.query.substringAfter("show=").substringBefore('&'))
        }
    }

    private fun Element.attr(name: String): String? = getAttributeNS(android, name).takeIf { it.isNotEmpty() }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }
}
