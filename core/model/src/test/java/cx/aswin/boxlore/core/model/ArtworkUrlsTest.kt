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
}
