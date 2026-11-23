package cx.aswin.boxlore.feature.info

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
    fun `branded links lead while both groups retain publisher order`() {
        val website = link("example.org")
        val instagram = link("instagram.com")
        val article = link("news.example.org", EpisodeLinkKind.ARTICLE)
        val youtube = link("youtube.com", EpisodeLinkKind.VIDEO)
        val discord = link("discord.gg", EpisodeLinkKind.COMMUNITY)
        val email = EpisodeLink("mailto:hello@example.org", "hello@example.org", EpisodeLinkKind.EMAIL)
        val links = listOf(website, instagram, article, youtube, discord, email)
        assertEquals(listOf(instagram, youtube, discord, website, article, email), episodeLinkListRows(links, columns = 1, expanded = true).flatten())
        assertEquals(listOf(website, instagram, article, youtube, discord, email), links)
    }

    @Test
    fun `lookalike hosts and email addresses cannot gain brand priority from their metadata`() {
        val website = link("example.org")
        val spoof = link("instagram.com.evil.org", EpisodeLinkKind.SOCIAL).copy(platform = "Instagram", handle = "@show")
        val email = EpisodeLink("mailto:hello@x.com", "hello@x.com", EpisodeLinkKind.EMAIL)
        val instagram = link("instagram.com", EpisodeLinkKind.SOCIAL)
        assertEquals(listOf(instagram, website, spoof, email), episodeLinkListRows(listOf(website, spoof, email, instagram), columns = 1, expanded = true).flatten())
    }

    @Test
    fun `a row is named by its title, profile, platform or site, without an action word`() {
        assertEquals("example.org", episodeLinkRowTitle(link("example.org")))
        assertEquals("YouTube", episodeLinkRowTitle(link("youtube.com", EpisodeLinkKind.VIDEO).copy(platform = "YouTube")))
        assertEquals("The oldest maps of home", episodeLinkRowTitle(link("thehistoryblog.co.uk", EpisodeLinkKind.ARTICLE).copy(title = "The oldest maps of home")))
    }

    private fun link(host: String, kind: EpisodeLinkKind = EpisodeLinkKind.WEBSITE) = EpisodeLink("https://$host", host, kind)
}
