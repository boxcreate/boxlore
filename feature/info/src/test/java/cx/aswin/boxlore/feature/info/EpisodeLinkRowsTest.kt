package cx.aswin.boxlore.feature.info

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeLinkRowsTest {
    @Test
    fun `no links reserve no rows and one link uses one row`() {
        val website = link("example.org")
        assertEquals(emptyList<List<EpisodeLink>>(), episodeLinkListRows(emptyList(), columns = 1, expanded = false))
        assertEquals(listOf(listOf(website)), episodeLinkListRows(listOf(website), columns = 2, expanded = false))
    }

    @Test
    fun `eleven links show four until expanded, in one or two columns`() {
        val links = (1..11).map { link("site$it.example.org") }
        assertEquals(links.take(4).map { listOf(it) }, episodeLinkListRows(links, columns = 1, expanded = false))
        assertEquals(11, episodeLinkListRows(links, columns = 1, expanded = true).size)
        assertEquals(listOf(links.subList(0, 2), links.subList(2, 4)), episodeLinkListRows(links, columns = 2, expanded = false))
        val twoColumns = episodeLinkListRows(links, columns = 2, expanded = true)
        assertEquals(6, twoColumns.size)
        assertEquals(listOf(links[10]), twoColumns.last())
    }

    @Test
    fun `the episode's own pages lead, then videos, then the show's profiles, with brands first inside each group`() {
        val website = link("example.org")
        val instagram = link("instagram.com", EpisodeLinkKind.SOCIAL)
        val article = link("news.example.org", EpisodeLinkKind.ARTICLE)
        val youtube = link("youtube.com", EpisodeLinkKind.VIDEO)
        val discord = link("discord.gg", EpisodeLinkKind.COMMUNITY)
        val email = EpisodeLink("mailto:hello@example.org", "hello@example.org", EpisodeLinkKind.EMAIL)
        val links = listOf(website, instagram, article, youtube, discord, email)
        assertEquals(listOf(website, article, youtube, instagram, discord, email), episodeLinkListRows(links, columns = 1, expanded = true).flatten())
        assertEquals(listOf(website, instagram, article, youtube, discord, email), links)
    }

    @Test
    fun `four branded profiles never hide the episode's article behind Show all links`() {
        val profiles = listOf("youtube.com" to EpisodeLinkKind.VIDEO, "instagram.com" to EpisodeLinkKind.SOCIAL, "patreon.com" to EpisodeLinkKind.SUPPORT, "discord.gg" to EpisodeLinkKind.COMMUNITY)
            .map { (host, kind) -> link(host, kind) }
        val article = link("thehistoryblog.co.uk", EpisodeLinkKind.ARTICLE).copy(title = "The oldest maps of home")
        val collapsed = episodeLinkListRows(profiles + article, columns = 1, expanded = false).flatten()
        assertEquals(article, collapsed.first())
        assertEquals(4, collapsed.size)
    }

    @Test
    fun `only the grid's outer corners are round, and the expand row closes it`() {
        val round = 24.dp
        val tight = 5.dp
        fun corners(shape: androidx.compose.foundation.shape.RoundedCornerShape) =
            listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).map { it.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)) }
        // Two columns, two rows of links, then the expand row.
        assertEquals(listOf(round, tight, tight, tight).map { it.value }, corners(linkCellShape(0, 0, rows = 3, columns = 2)))
        assertEquals(listOf(tight, round, tight, tight).map { it.value }, corners(linkCellShape(0, 1, rows = 3, columns = 2)))
        assertEquals(listOf(tight, tight, tight, tight).map { it.value }, corners(linkCellShape(1, 1, rows = 3, columns = 2)))
        assertEquals(listOf(tight, tight, round, round).map { it.value }, corners(linkCellShape(2, 0, rows = 3, columns = 1)))
    }

    @Test
    fun `lookalike hosts and email addresses cannot gain brand priority from their metadata`() {
        val website = link("example.org")
        val spoof = link("instagram.com.evil.org", EpisodeLinkKind.SOCIAL).copy(platform = "Instagram", handle = "@show")
        val email = EpisodeLink("mailto:hello@x.com", "hello@x.com", EpisodeLinkKind.EMAIL)
        val instagram = link("instagram.com", EpisodeLinkKind.SOCIAL)
        assertEquals(listOf(website, instagram, spoof, email), episodeLinkListRows(listOf(website, spoof, email, instagram), columns = 1, expanded = true).flatten())
    }

    @Test
    fun `a row is named by its title, profile, platform or site, without an action word`() {
        assertEquals("example.org", episodeLinkRowTitle(link("example.org")))
        assertEquals("YouTube", episodeLinkRowTitle(link("youtube.com", EpisodeLinkKind.VIDEO).copy(platform = "YouTube")))
        assertEquals("The oldest maps of home", episodeLinkRowTitle(link("thehistoryblog.co.uk", EpisodeLinkKind.ARTICLE).copy(title = "The oldest maps of home")))
    }

    private fun link(host: String, kind: EpisodeLinkKind = EpisodeLinkKind.WEBSITE) = EpisodeLink("https://$host", host, kind)
}
