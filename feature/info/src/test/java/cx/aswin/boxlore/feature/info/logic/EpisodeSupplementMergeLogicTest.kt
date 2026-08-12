package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.EpisodeSort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeSupplementMergeLogicTest {
    @Test
    fun `merge keeps PI order preference and appends feed-only extras`() {
        val pi =
            listOf(
                TestFixtures.episode(id = "1", title = "A", audioUrl = "https://cdn/a.mp3", publishedDate = 300),
                TestFixtures.episode(id = "2", title = "B", audioUrl = "https://cdn/b.mp3", publishedDate = 200),
            )
        val supplements =
            listOf(
                TestFixtures.episode(id = "-10", title = "C", audioUrl = "https://cdn/c.mp3", publishedDate = 250),
                TestFixtures.episode(id = "-11", title = "A", audioUrl = "https://cdn/a.mp3", publishedDate = 300),
            )

        val newest = EpisodeSupplementMergeLogic.merge(pi, supplements, EpisodeSort.NEWEST)
        assertEquals(listOf("1", "-10", "2"), newest.map { it.id })

        val oldest = EpisodeSupplementMergeLogic.merge(pi, supplements, EpisodeSort.OLDEST)
        assertEquals(listOf("2", "-10", "1"), oldest.map { it.id })
    }

    @Test
    fun `merge dedupes supplement by audioUrl even when ids differ`() {
        val pi =
            listOf(
                TestFixtures.episode(id = "pi-1", title = "Show", audioUrl = "https://cdn/same.mp3"),
            )
        val supplements =
            listOf(
                TestFixtures.episode(id = "-99", title = "Show (RSS)", audioUrl = "https://cdn/same.mp3"),
            )

        val merged = EpisodeSupplementMergeLogic.merge(pi, supplements, EpisodeSort.NEWEST)
        assertEquals(listOf("pi-1"), merged.map { it.id })
    }

    @Test
    fun `unionSearchResults dedupes by id and sorts newest first`() {
        val network =
            listOf(
                TestFixtures.episode(id = "n1", title = "Net", publishedDate = 100),
                TestFixtures.episode(id = "shared", title = "Shared", publishedDate = 50),
            )
        val supplements =
            listOf(
                TestFixtures.episode(id = "shared", title = "Shared RSS", publishedDate = 50),
                TestFixtures.episode(id = "s1", title = "Supp", publishedDate = 200),
            )

        val union = EpisodeSupplementMergeLogic.unionSearchResults(network, supplements)
        assertEquals(listOf("s1", "n1", "shared"), union.map { it.id })
        assertTrue(union.distinctBy { it.id }.size == union.size)
    }
}
