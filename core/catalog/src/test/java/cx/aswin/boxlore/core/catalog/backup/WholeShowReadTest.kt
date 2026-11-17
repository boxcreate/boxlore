package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ServerListScheme
import cx.aswin.boxlore.core.testing.TestFixtures
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Marking a whole show played (the OPML import's "caught up" step): a page that fails partway through
 * a long show is asked again rather than read as the show's end, and later pages keep the route the
 * first page came from.
 */
class WholeShowReadTest {
    private val asked = mutableListOf<Pair<Int, ServerListScheme?>>()

    private fun page(offset: Int, scheme: ServerListScheme? = ServerListScheme.V2, hasMore: Boolean = true) = PodcastRepository.EpisodePage(
        episodes = (offset until offset + PAGE).map { TestFixtures.episode(id = "e$it") },
        hasMore = hasMore,
        scheme = scheme,
    )

    @Test
    fun `a page that fails or throws partway is asked again, not read as the end of the show`() = runTest {
        var failures = 0
        val result =
            WholeShowRead.read { offset, scheme ->
                asked += offset to scheme
                when {
                    offset == PAGE && failures == 0 -> {
                        failures++
                        PodcastRepository.EpisodePage(emptyList(), hasMore = false, failed = true)
                    }
                    offset == PAGE && failures == 1 -> {
                        failures++
                        throw IOException("429 twice")
                    }
                    else -> page(offset, hasMore = offset < 2 * PAGE)
                }
            }

        assertTrue(result.complete)
        assertEquals(3 * PAGE, result.episodes.size, "the older episodes are read too")
        assertEquals(listOf(0, PAGE, PAGE, PAGE, 2 * PAGE), asked.map { it.first })
    }

    @Test
    fun `later pages keep the route the first page came from`() = runTest {
        WholeShowRead.read { offset, scheme ->
            asked += offset to scheme
            // The first page fell back to v1; v2 answers again by the second.
            page(offset, scheme = scheme ?: ServerListScheme.V1, hasMore = offset == 0)
        }

        assertEquals(listOf(0 to null, PAGE to ServerListScheme.V1), asked)
    }

    @Test
    fun `a page that never comes back leaves the read incomplete with what it read`() = runTest {
        val result =
            WholeShowRead.read { offset, _ ->
                asked += offset to null
                if (offset == 0) page(0) else PodcastRepository.EpisodePage(emptyList(), hasMore = false, failed = true)
            }

        assertFalse(result.complete)
        assertEquals(PAGE, result.episodes.size)
        assertEquals(WholeShowRead.ATTEMPTS, asked.count { it.first == PAGE })
    }

    private companion object {
        const val PAGE = 100
    }
}
