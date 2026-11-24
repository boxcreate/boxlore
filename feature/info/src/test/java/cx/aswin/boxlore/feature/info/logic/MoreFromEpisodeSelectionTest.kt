package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoreFromEpisodeSelectionTest {
    @Test
    fun `latest five exclude the current episode before applying the limit`() {
        val episodes = (1..8).map { TestFixtures.episode(id = "$it", publishedDate = it.toLong()) }.reversed()

        assertEquals(listOf("7", "6", "5", "4", "3"), selectMoreFromEpisodes(episodes, "8").map { it.id })
    }

    @Test
    fun `unordered results are presented newest first`() {
        val episodes = listOf(
            TestFixtures.episode(id = "oldest", publishedDate = 100),
            TestFixtures.episode(id = "newest", publishedDate = 300),
            TestFixtures.episode(id = "middle", publishedDate = 200),
        )

        assertEquals(listOf("newest", "middle", "oldest"), selectMoreFromEpisodes(episodes, "current").map { it.id })
    }

    @Test
    fun `duplicate IDs do not consume slots and retain the newest copy`() {
        val episodes = listOf(
            TestFixtures.episode(id = "repeat", title = "Stale", publishedDate = 1),
            TestFixtures.episode(id = "repeat", title = "Latest", publishedDate = 9),
        ) + (2..7).map { TestFixtures.episode(id = "$it", publishedDate = it.toLong()) }

        val result = selectMoreFromEpisodes(episodes, "current")

        assertEquals(listOf("repeat", "7", "6", "5", "4"), result.map { it.id })
        assertEquals("Latest", result.first().title)
    }

    @Test
    fun `equal dates preserve repository order`() {
        val episodes = listOf("third", "first", "second").map { TestFixtures.episode(id = it, publishedDate = 100) }

        assertEquals(episodes, selectMoreFromEpisodes(episodes, "current"))
    }

    @Test
    fun `short and empty catalogs do not repeat or fabricate episodes`() {
        val current = TestFixtures.episode(id = "current", publishedDate = 300)
        val other = TestFixtures.episode(id = "other", publishedDate = 200)

        assertEquals(listOf(other), selectMoreFromEpisodes(listOf(current, other), "current"))
        assertTrue(selectMoreFromEpisodes(listOf(current), "current").isEmpty())
        assertTrue(selectMoreFromEpisodes(emptyList(), "current").isEmpty())
    }

    @Test
    fun `rows show their own picture, else their date, else their number`() {
        val show = "https://images.example.invalid/show.jpg"
        val own = TestFixtures.episode(id = "own", imageUrl = "https://images.example.invalid/own.jpg", publishedDate = 1_700_000_000L)
        assertEquals(MoreFromArt.Image("https://images.example.invalid/own.jpg"), moreFromRowArt(own, show))
        val cover = TestFixtures.episode(id = "cover", imageUrl = show, publishedDate = 1_700_000_000L)
        assertEquals(MoreFromArt.Date(1_700_000_000L), moreFromRowArt(cover, show))
        val numbered = TestFixtures.episode(id = "numbered", imageUrl = null).copy(episodeNumber = 14)
        assertEquals(MoreFromArt.Number(14), moreFromRowArt(numbered, show))
        assertEquals(MoreFromArt.ShowCover, moreFromRowArt(TestFixtures.episode(id = "bare"), show))
    }
}
