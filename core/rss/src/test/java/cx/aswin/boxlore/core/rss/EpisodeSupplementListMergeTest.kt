package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeSupplementListMergeTest {
    @Test
    fun `merge empty inputs stays empty`() {
        assertTrue(
            EpisodeSupplementListMerge.merge(
                emptyList(),
                emptyList(),
                EpisodeSupplementListMerge.Sort.NEWEST,
            ).isEmpty(),
        )
    }

    @Test
    fun `merge keeps distinct same-title extras when dates are far apart`() {
        val day = 24L * 60L * 60L
        val pi =
            listOf(
                TestFixtures.episode(
                    id = "pi-1",
                    title = "News",
                    audioUrl = "https://cdn/pi.mp3",
                    publishedDate = 1_000L,
                ),
            )
        val extras =
            listOf(
                TestFixtures.episode(
                    id = "-9",
                    title = "News",
                    audioUrl = "https://cdn/rss.mp3",
                    publishedDate = 1_000L + day * 10,
                ),
            )
        val merged =
            EpisodeSupplementListMerge.merge(pi, extras, EpisodeSupplementListMerge.Sort.NEWEST)
        assertEquals(listOf("-9", "pi-1"), merged.map { it.id })
    }

    @Test
    fun `unionSearchResults preferred list wins on audio match`() {
        val network =
            listOf(
                TestFixtures.episode(
                    id = "pi-1",
                    title = "PI title",
                    audioUrl = "https://cdn/same.mp3",
                    publishedDate = 50,
                ),
            )
        val supplements =
            listOf(
                TestFixtures.episode(
                    id = "-1",
                    title = "Feed title",
                    audioUrl = "https://cdn/same.mp3",
                    publishedDate = 50,
                ),
            )
        val union =
            EpisodeSupplementListMerge.unionSearchResults(
                preferred = supplements,
                fallback = network,
            )
        assertEquals(listOf("-1"), union.map { it.id })
        assertEquals("Feed title", union.single().title)
        assertTrue(
            EpisodeSupplementListMerge.unionSearchResults(emptyList(), emptyList()).isEmpty(),
        )
    }
}
