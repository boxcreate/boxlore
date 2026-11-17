package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The continuation window around an episode of a show on the server's list: read in the asked
 * sort until the episode turns up, then the episode and the ones after it, never the newest few
 * passed off as what follows.
 */
class ShowEpisodeWindowSearchTest {
    /** Episode `ep$n` published at `n`, for n in 1..[count]. */
    private fun show(count: Int): List<Episode> = (1..count).map { TestFixtures.episode(id = "ep$it", publishedDate = it.toLong()) }

    private class Served(private val list: List<Episode>, private val firstSize: Int, private val pageSize: Int) {
        val offsets = mutableListOf<Int>()
        var failAt: Int? = null

        fun first() = page(0, firstSize)

        fun load(offset: Int): ShowEpisodeWindowSearch.Page? {
            offsets += offset
            if (offset == failAt) return null
            return page(offset, pageSize)
        }

        private fun page(offset: Int, size: Int): ShowEpisodeWindowSearch.Page {
            val slice = list.drop(offset).take(size)
            return ShowEpisodeWindowSearch.Page(slice, hasMore = offset + slice.size < list.size)
        }
    }

    private suspend fun around(anchor: String, served: Served, newestFirst: Boolean) =
        ShowEpisodeWindowSearch.around(anchor, newestFirst, bound = 200, firstPage = served.first()) { served.load(it) }

    @Test
    fun `oldest first, continuing from episode 500 of 1000 offers 501 next`() = runTest {
        // Reported: continuation read only the newest 200, so from an older episode it jumped hundreds ahead.
        val served = Served(show(1_000), firstSize = 200, pageSize = 1_000)

        val window = requireNotNull(around("ep500", served, newestFirst = false))

        assertEquals("ep500", window.first().id)
        assertEquals("ep501", window[1].id)
        assertEquals((500..699).map { "ep$it" }, window.map { it.id })
        assertEquals(listOf(200), served.offsets, "one page past the first")
    }

    @Test
    fun `oldest first, pages that map to few episodes are read on until the window is full`() = runTest {
        // Reviewed: only one page past the episode's page was read, so rows without audio left a short window.
        val list = show(300)
        var reads = 0
        val firstPage = ShowEpisodeWindowSearch.Page(list.take(100), hasMore = true)
        val window =
            requireNotNull(
                ShowEpisodeWindowSearch.around("ep100", newestFirst = false, bound = 150, firstPage = firstPage) { offset ->
                    reads++
                    // Page 100 lists 100 rows but none of them maps to an episode.
                    val episodes = if (offset == 100) emptyList() else list.drop(offset).take(100)
                    ShowEpisodeWindowSearch.Page(episodes, hasMore = offset + 100 < list.size, sourceCount = 100)
                },
            )

        assertEquals(listOf("ep100") + (201..300).map { "ep$it" }, window.map { it.id })
        assertEquals(2, reads, "both pages after the episode's page")
    }

    @Test
    fun `newest first, the window still runs forward in time from the episode`() = runTest {
        val served = Served(show(1_000).reversed(), firstSize = 200, pageSize = 1_000)

        val window = requireNotNull(around("ep300", served, newestFirst = true))

        assertEquals("ep300", window.first().id)
        assertEquals((300..499).map { "ep$it" }, window.map { it.id })
    }

    @Test
    fun `newest first, an episode near the newest keeps only the episodes after it`() = runTest {
        val served = Served(show(1_000).reversed(), firstSize = 200, pageSize = 1_000)

        val window = requireNotNull(around("ep995", served, newestFirst = true))

        assertEquals((995..1_000).map { "ep$it" }, window.map { it.id })
        assertTrue(served.offsets.isEmpty(), "found on the first page")
    }

    @Test
    fun `oldest first, an episode near the end of a page reads the next page to fill the window`() = runTest {
        val served = Served(show(1_500), firstSize = 200, pageSize = 1_000)

        val window = requireNotNull(around("ep1150", served, newestFirst = false))

        assertEquals((1_150..1_349).map { "ep$it" }, window.map { it.id })
        assertEquals(listOf(200, 1_200), served.offsets)
    }

    @Test
    fun `an episode the list doesn't hold gives an empty window, not the newest few`() = runTest {
        val served = Served(show(1_000).reversed(), firstSize = 200, pageSize = 1_000)

        assertEquals(emptyList<Episode>(), around("gone", served, newestFirst = true))
        assertEquals(listOf(200), served.offsets, "read to the end of the show and no further")
    }

    @Test
    fun `a page that fails before the episode turns up gives no window`() = runTest {
        val served = Served(show(3_000), firstSize = 200, pageSize = 1_000).apply { failAt = 1_200 }

        assertNull(around("ep2500", served, newestFirst = false))
    }

    @Test
    fun `oldest first, a failed page after the episode gives no window rather than a short one`() = runTest {
        // Reviewed: the failed next page read as the show's end, so a one-episode window was kept as the whole rest.
        val served = Served(show(3_000), firstSize = 200, pageSize = 1_000).apply { failAt = 1_200 }

        assertNull(around("ep1200", served, newestFirst = false), "ep1200 is the last row of the 200..1199 page")
        assertEquals(listOf(200, 1_200), served.offsets)
    }

    @Test
    fun `a list already in hand gives the episode and the ones after it, or nothing`() {
        val newest = show(200).reversed()

        assertEquals(listOf("ep199", "ep200"), ShowEpisodeWindowSearch.inList("ep199", newest, bound = 200).map { it.id })
        assertEquals(emptyList<Episode>(), ShowEpisodeWindowSearch.inList("ep5000", newest, bound = 200))
    }
}
