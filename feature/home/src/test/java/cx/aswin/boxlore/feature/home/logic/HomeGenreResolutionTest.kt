package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeGenreResolutionTest {

    @Test
    fun `effectiveGenre returns customGenre when subscribed and not blank`() {
        val podcast = Podcast(
            id = "pod-1",
            title = "Test Podcast",
            artist = "Host",
            description = "Desc",
            imageUrl = "https://example.com/art.png",
            feedUrl = "https://example.com/feed.xml",
            genre = "Technology",
            customGenre = "Deep Tech",
            customGenreIcon = "code",
            subscribedAt = 1000L,
        )
        assertEquals("Deep Tech", podcast.effectiveGenre)
        assertEquals("code", podcast.customGenreIcon)
    }

    @Test
    fun `effectiveGenre falls back to catalog genre when customGenre is null or blank`() {
        val podcastNull = Podcast(
            id = "pod-1",
            title = "Test Podcast",
            artist = "Host",
            description = "Desc",
            imageUrl = "https://example.com/art.png",
            feedUrl = "https://example.com/feed.xml",
            genre = "News",
            customGenre = null,
            subscribedAt = 1000L,
        )
        assertEquals("News", podcastNull.effectiveGenre)

        val podcastBlank = podcastNull.copy(customGenre = "   ")
        assertEquals("News", podcastBlank.effectiveGenre)
    }

    @Test
    fun `subscribedGenres extraction for recommendation queries adapts to canonical custom genre`() {
        val pod1 = Podcast(
            id = "1",
            title = "One",
            artist = "A",
            description = "",
            imageUrl = "",
            feedUrl = "",
            genre = "Comedy",
            customGenre = "Film",
            subscribedAt = 1000L,
        )
        val pod2 = Podcast(
            id = "2",
            title = "Two",
            artist = "B",
            description = "",
            imageUrl = "",
            feedUrl = "",
            genre = "Technology",
            customGenre = null,
            subscribedAt = 1000L,
        )
        val pod3 = Podcast(
            id = "3",
            title = "Three",
            artist = "C",
            description = "",
            imageUrl = "",
            feedUrl = "",
            genre = "Business",
            customGenre = "Favorites",
            subscribedAt = 1000L,
        )
        val genres = listOf(pod1, pod2, pod3).mapNotNull { it.recommendationGenre }.distinct()
        assertEquals(listOf("TV & Film", "Technology", "Business"), genres)
    }
}
