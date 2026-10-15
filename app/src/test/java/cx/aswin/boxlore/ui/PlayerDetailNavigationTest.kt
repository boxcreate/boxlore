package cx.aswin.boxlore.ui

import android.app.Application
import android.net.Uri
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerDetailNavigationTest {
    @Test
    fun `player detail preserves opaque IDs encoding and podcast fallback`() {
        val episode = TestFixtures.episode(id = "rss:episode/one", title = "A & B", podcastId = "-123").copy(podcastTitle = null)
        val podcast = TestFixtures.podcast(title = "A show", imageUrl = "https://example.invalid/cover?size=2")
        val segments = playerEpisodeDetailRoute(episode, podcast).substringBefore('?').split('/').map(Uri::decode)
        assertEquals(listOf("episode", episode.id, episode.title, episode.description, podcast.imageUrl, episode.audioUrl, "3600", "-123", "A show"), segments)
        assertEquals("player_ui", Uri.parse("boxlore://${playerEpisodeDetailRoute(episode, podcast)}").getQueryParameter("entryPoint"))
    }

    @Test
    fun `brief routes and bounded description preserve current player navigation`() {
        assertEquals("briefing?region=us", playerEpisodeDetailRoute(TestFixtures.episode(id = "briefing_us_2026-10-15"), null))
        assertEquals("briefing?region=us", playerPodcastDetailRoute(TestFixtures.podcast(id = "briefing_us")))
        val episode = TestFixtures.episode(description = "x".repeat(700))
        assertEquals(500, Uri.decode(playerEpisodeDetailRoute(episode, null).split('/')[3]).length)
        assertEquals("podcast/rss%3A-123?entryPoint=player_ui", playerPodcastDetailRoute(TestFixtures.podcast(id = "rss:-123")))
    }
}
