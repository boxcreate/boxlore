package cx.aswin.boxlore.feature.info

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeLinkBrandIconTest {
    @Test
    fun `social profiles use their recognizable platform logo`() {
        val expected = mapOf(
            "www.instagram.com" to R.drawable.ic_link_instagram,
            "m.facebook.com" to R.drawable.ic_link_facebook,
            "fb.com" to R.drawable.ic_link_facebook,
            "twitter.com" to R.drawable.ic_link_x,
            "X.COM." to R.drawable.ic_link_x,
            "threads.com" to R.drawable.ic_link_threads,
            "threads.net" to R.drawable.ic_link_threads,
            "linkedin.com" to R.drawable.ic_link_linkedin,
            "bsky.app" to R.drawable.ic_link_bluesky,
            "tiktok.com" to R.drawable.ic_link_tiktok,
            "twitch.tv" to R.drawable.ic_link_twitch,
        )
        expected.forEach { (host, resource) -> assertEquals(resource, episodeLinkBrandIcon(host), host) }
    }

    @Test
    fun `video community and support URLs keep brand identity across aliases`() {
        assertEquals(R.drawable.ic_link_youtube, episodeLinkBrandIcon("youtu.be"))
        assertEquals(R.drawable.ic_link_youtube, episodeLinkBrandIcon("music.youtube.com"))
        assertEquals(R.drawable.ic_link_discord, episodeLinkBrandIcon("discord.gg"))
        assertEquals(R.drawable.ic_link_discord, episodeLinkBrandIcon("discord.com"))
        assertEquals(R.drawable.ic_link_reddit, episodeLinkBrandIcon("old.reddit.com"))
        assertEquals(R.drawable.ic_link_spotify, episodeLinkBrandIcon("open.spotify.com"))
        assertEquals(R.drawable.ic_link_applepodcasts, episodeLinkBrandIcon("podcasts.apple.com"))
        assertEquals(R.drawable.ic_link_patreon, episodeLinkBrandIcon("patreon.com"))
        assertEquals(R.drawable.ic_link_kofi, episodeLinkBrandIcon("ko-fi.com"))
        assertEquals(R.drawable.ic_link_buymeacoffee, episodeLinkBrandIcon("buymeacoffee.com"))
    }

    @Test
    fun `lookalike sites email addresses and unknown hosts retain the generic purpose icon`() {
        listOf("notinstagram.com", "instagram.com.example.org", "youtube.com.evil.test", "examplex.com", "hello@x.com", "radiolab.org", "apple.com", "").forEach {
            assertNull(episodeLinkBrandIcon(it), it)
        }
    }
}
