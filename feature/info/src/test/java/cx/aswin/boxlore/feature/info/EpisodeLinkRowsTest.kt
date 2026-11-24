package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeLinkRowsTest {
    @Test
    fun `no links reserve no rows and one link uses only one row`() {
        val website = link("example.org")
        assertEquals(emptyList<List<EpisodeLink>>(), episodeLinkRows(emptyList()))
        assertEquals(listOf(listOf(website)), episodeLinkRows(listOf(website)))
    }

    @Test
    fun `two links stack with the recognised link above the generic link`() {
        val website = link("example.org")
        val youtube = link("youtube.com")
        assertEquals(listOf(listOf(youtube), listOf(website)), episodeLinkRows(listOf(website, youtube)))
    }

    @Test
    fun `generic links alternate rows in publisher order without dropping the odd item`() {
        val links = listOf("example.org", "example.net", "example.com", "support.example.org", "news.example.org").map { link(it) }
        assertEquals(listOf(listOf(links[0], links[2], links[4]), listOf(links[1], links[3])), episodeLinkRows(links))
    }

    @Test
    fun `branded links lead in top bottom priority while both groups retain publisher order`() {
        val website = link("example.org")
        val instagram = link("instagram.com")
        val article = link("news.example.org", EpisodeLinkKind.ARTICLE)
        val youtube = link("youtube.com", EpisodeLinkKind.VIDEO)
        val discord = link("discord.gg", EpisodeLinkKind.COMMUNITY)
        val email = EpisodeLink("mailto:hello@example.org", "hello@example.org", EpisodeLinkKind.EMAIL)
        val links = listOf(website, instagram, article, youtube, discord, email)
        assertEquals(listOf(listOf(instagram, discord, article), listOf(youtube, website, email)), episodeLinkRows(links))
        assertEquals(listOf(website, instagram, article, youtube, discord, email), links)
    }

    @Test
    fun `known aliases subdomains and monochrome brands retain their relative order`() {
        val links = listOf("M.INSTAGRAM.COM.", "twitter.com", "podcasts.apple.com", "open.spotify.com", "patreon.com").map { link(it) }
        assertEquals(listOf(listOf(links[0], links[2], links[4]), listOf(links[1], links[3])), episodeLinkRows(links))
    }

    @Test
    fun `lookalike hosts and email addresses cannot gain brand priority from their metadata`() {
        val website = link("example.org")
        val spoof = link("instagram.com.evil.org", EpisodeLinkKind.SOCIAL).copy(platform = "Instagram", handle = "@show")
        val email = EpisodeLink("mailto:hello@x.com", "hello@x.com", EpisodeLinkKind.EMAIL)
        val instagram = link("instagram.com", EpisodeLinkKind.SOCIAL)
        assertEquals(listOf(listOf(instagram, spoof), listOf(website, email)), episodeLinkRows(listOf(website, spoof, email, instagram)))
    }

    private fun link(host: String, kind: EpisodeLinkKind = EpisodeLinkKind.WEBSITE) = EpisodeLink("https://$host", host, kind)
}
