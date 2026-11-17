package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ServerListScheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Home reading a show in order oldest first: a later page that fails (a 429 on page 2) is an error,
 * not the show's end, and every page after the first reads the route the first page came from.
 */
class SeriesOldestPagesTest {
    private val episodes = (1..250).map { TestFixtures.episode(id = "ep-$it", publishedDate = it * 1_000L) }
    private val asked = mutableListOf<Pair<Int, ServerListScheme?>>()

    private fun page(offset: Int, scheme: ServerListScheme?): PodcastRepository.EpisodePage {
        val items = episodes.drop(offset).take(SeriesEpisodesLoader.PAGE_SIZE)
        return PodcastRepository.EpisodePage(items, hasMore = offset + items.size < episodes.size, scheme = scheme)
    }

    @Test
    fun `later pages keep the first page's route`() = runTest {
        val pages =
            SeriesOldestPages { offset, scheme ->
                asked += offset to scheme
                // The first page fell back to v1.
                page(offset, scheme ?: ServerListScheme.V1)
            }

        pages.page(0)
        pages.page(SeriesEpisodesLoader.PAGE_SIZE)

        assertEquals(listOf(0 to null, SeriesEpisodesLoader.PAGE_SIZE to ServerListScheme.V1), asked)
    }

    @Test
    fun `a failed later page makes the scan fail instead of losing the listener's place`() = runTest {
        val pages =
            SeriesOldestPages { offset, _ ->
                if (offset == 0) page(0, ServerListScheme.V2) else PodcastRepository.EpisodePage(emptyList(), hasMore = false, failed = true)
            }
        val loader =
            SeriesEpisodesLoader(
                window = { emptyList<Episode>() },
                oldestPage = pages::page,
                groupTokens = { ids -> ids.associateWith { it } },
            )

        // The place is on page 2, which can't be read: the list says it couldn't load, not that the show ends at page 1.
        val error = runCatching { loader.load("ep-150", emptySet()) }.exceptionOrNull()
        assertTrue(error is IOException, "got $error")
    }
}
