package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.shownotes.ShowNotesParser
import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeLinkLabelTest {
    @Test
    fun `generic platform titles never repeat platform as the profile name`() {
        listOf("Instagram" to "Instagram", "Facebook" to "Visit Facebook", "Threads" to "Threads", "X" to "Twitter").forEach { (platform, title) ->
            val link = EpisodeLink("https://example.org", "example.org", EpisodeLinkKind.SOCIAL, title = title, platform = platform)
            val rootLabel = episodeLinkLabelSpec(link, "Show")
            assertEquals(R.string.episode_info_link_open_on, rootLabel.resource)
            assertEquals(listOf(platform), rootLabel.arguments)
            assertNull(episodeLinkActionTitle(link))
            val profileLabel = episodeLinkLabelSpec(link.copy(handle = "@radiolab"), "Show")
            assertEquals(R.string.episode_info_link_view_on, profileLabel.resource)
            assertEquals(listOf("@radiolab", platform), profileLabel.arguments)
        }
    }

    @Test
    fun `specific publisher action labels remain intact`() {
        val link = EpisodeLink("https://instagram.com/p/episode", "instagram.com", EpisodeLinkKind.SOCIAL, title = "View Radiolab behind the scenes", platform = "Instagram")
        assertEquals("View Radiolab behind the scenes", episodeLinkActionTitle(link))
    }

    @Test
    fun `unlabelled websites name the actual destination rather than website`() {
        val label = episodeLinkLabelSpec(EpisodeLink("https://radiolab.org", "radiolab.org", EpisodeLinkKind.WEBSITE), "Radiolab")
        assertEquals(R.string.episode_info_link_visit, label.resource)
        assertEquals(listOf("radiolab.org"), label.arguments)
    }

    @Test
    fun `article labels retain publisher context and recognised profiles show only their username`() {
        val article = episodeLinkLabelSpec(EpisodeLink("https://example.org/news/research", "example.org", EpisodeLinkKind.ARTICLE, title = "The original research"), "Show")
        assertEquals(R.string.episode_info_link_read, article.resource)
        assertEquals(listOf("The original research"), article.arguments)
        val social = episodeLinkLabelSpec(EpisodeLink("https://instagram.com/radiolab", "instagram.com", EpisodeLinkKind.SOCIAL, platform = "Instagram", handle = "@radiolab"), "Radiolab")
        assertEquals(R.string.episode_info_link_profile, social.resource)
        assertEquals(listOf("@radiolab"), social.arguments)
    }

    @Test
    fun `parsed social usernames replace publisher titles and action prefixes only with a recognised icon`() {
        val profiles = listOf(
            "https://instagram.com/radiolab", "https://facebook.com/radiolab", "https://threads.net/@radiolab",
            "https://twitter.com/radiolab", "https://linkedin.com/company/radiolab", "https://tiktok.com/@radiolab",
            "https://twitch.tv/radiolab", "https://youtube.com/@radiolab", "https://youtube.com/c/radiolab",
        )
        profiles.forEach { url ->
            val link = ShowNotesParser.parse("<a href='$url'>View Radiolab behind the scenes</a>").links.single()
            val label = episodeLinkLabelSpec(link, "Radiolab")
            assertEquals(R.string.episode_info_link_profile, label.resource, url)
            assertEquals(listOf("@radiolab"), label.arguments, url)
            assertNull(episodeLinkActionTitle(link), url)
        }
        val bluesky = ShowNotesParser.parse("https://bsky.app/profile/radiolab.org").links.single()
        assertEquals(listOf("@radiolab.org"), episodeLinkLabelSpec(bluesky, "Radiolab").arguments)
    }

    @Test
    fun `missing usernames and nonprofile routes retain descriptive labels`() {
        listOf("https://instagram.com/", "https://instagram.com/accounts/login", "https://instagram.com/p/123", "https://facebook.com/profile.php?id=123", "https://youtube.com/channel/UC123").forEach { url ->
            val link = ShowNotesParser.parse(url).links.single()
            assertNull(episodeLinkProfileHandle(link), url)
            assertEquals(R.string.episode_info_link_open_on, episodeLinkLabelSpec(link, "Show").resource, url)
        }
        val blank = EpisodeLink("https://instagram.com/", "instagram.com", EpisodeLinkKind.SOCIAL, platform = "Instagram", handle = " ")
        assertNull(episodeLinkProfileHandle(blank))
        assertEquals(R.string.episode_info_link_open_on, episodeLinkLabelSpec(blank, "Show").resource)
    }

    @Test
    fun `unknown platforms and lookalike domains cannot shorten to just a claimed username`() {
        listOf("example.org", "instagram.com.evil.org").forEach { host ->
            val link = EpisodeLink("https://$host/show", host, EpisodeLinkKind.SOCIAL, platform = "Instagram", handle = "@show")
            val label = episodeLinkLabelSpec(link, "Show")
            assertNull(episodeLinkProfileHandle(link))
            assertEquals(R.string.episode_info_link_view_on, label.resource)
            assertEquals(listOf("@show", "Instagram"), label.arguments)
        }
    }

    @Test
    fun `video podcast support and email actions describe their destination`() {
        val video = episodeLinkLabelSpec(EpisodeLink("https://youtube.com/watch?v=1", "youtube.com", EpisodeLinkKind.VIDEO, platform = "YouTube"), "Show")
        assertEquals(listOf("YouTube"), video.arguments)
        assertEquals(R.string.episode_info_link_watch_on, video.resource)
        val podcast = episodeLinkLabelSpec(EpisodeLink("https://open.spotify.com/show/1", "open.spotify.com", EpisodeLinkKind.PODCAST, platform = "Spotify", title = "Another Show"), "Host")
        assertEquals(listOf("Another Show"), podcast.arguments)
        assertEquals(R.string.episode_info_link_listen, podcast.resource)
        val support = episodeLinkLabelSpec(EpisodeLink("https://patreon.com/radiolab", "patreon.com", EpisodeLinkKind.SUPPORT, platform = "Patreon", handle = "radiolab"), "Radiolab")
        assertEquals(listOf("radiolab", "Patreon"), support.arguments)
        val email = episodeLinkLabelSpec(EpisodeLink("mailto:hello@radiolab.org", "hello@radiolab.org", EpisodeLinkKind.EMAIL), "Radiolab")
        assertEquals(R.string.episode_info_link_email, email.resource)
        assertEquals(listOf("hello@radiolab.org"), email.arguments)
    }
}
