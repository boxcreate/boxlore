package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtworkUrlsTest {
    @Test
    fun `blank malformed unsupported and duplicate sources cannot hide valid show artwork`() {
        assertEquals(
            listOf("https://cdn.example/show.jpg?token=a%2Bb&sig=ab", "content://artwork/1"),
            artworkUrls(null, " ", "broken", "javascript:bad", "https://", "http://cdn.example/show.jpg?token=a%2Bb&sig=ab", "https://cdn.example/show.jpg?token=a%2Bb&sig=ab", "content://artwork/1"),
        )
    }

    @Test
    fun `episodes and shows retain their distinct source order`() {
        val episode = Episode("1", "Episode", "", "", imageUrl = "https://art/episode", podcastImageUrl = "https://art/embedded")
        val podcast = Podcast("2", "Show", "Creator", "https://art/show", fallbackImageUrl = "https://art/fallback", latestEpisode = episode)
        assertEquals(listOf("https://art/episode", "https://art/embedded", "https://art/show", "https://art/fallback"), episode.artworkUrls(podcast))
        assertEquals(listOf("https://art/show", "https://art/fallback", "https://art/episode"), podcast.artworkUrls())
        assertEquals(listOf("file:///tmp/a.png", "/tmp/b.png", "android.resource://app/123"), artworkUrls("file:///tmp/a.png", "/tmp/b.png", "android.resource://app/123"))
    }

    // Regression: a player-only link saved into listening history outlived its cover and blanked Home.
    @Test
    fun `player-only artwork links are never kept, so the show's cover takes over`() {
        val local = "content://cx.aswin.boxlore.collage/local/38e72bd3ab3019a5bb9f93a3417dd2dcd604bc446a28153b388cc681156acf93"
        val remote = "content://cx.aswin.boxlore.collage/art/abc"
        assertEquals(true, isPlaybackArtworkLink(local))
        assertEquals(true, isPlaybackArtworkLink(" $remote "))
        assertEquals(false, isPlaybackArtworkLink("content://artwork/1"))
        assertEquals(false, isPlaybackArtworkLink("https://megaphone.imgix.net/show.jpg"))
        assertEquals(false, isPlaybackArtworkLink(null))
        assertEquals(null, persistableArtworkUrl(local))
        assertEquals(null, persistableArtworkUrl("  "))
        assertEquals("https://art/show", persistableArtworkUrl(" https://art/show "))
        assertEquals(listOf("https://art/show"), artworkUrls(local, remote, "https://art/show"))
        val podcast = Podcast("415434", "The Vergecast", "The Verge", local, fallbackImageUrl = local)
        assertEquals(emptyList<String>(), podcast.artworkUrls())
    }
}
