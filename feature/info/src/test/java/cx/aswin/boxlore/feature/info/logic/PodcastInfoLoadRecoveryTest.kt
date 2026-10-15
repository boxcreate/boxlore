package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoLoadRecoveryTest {
    private val podcast = TestFixtures.podcast(id = "920666", title = "Local row")

    /** The state Podcast Info shows from Room before /podcast answers. */
    private fun localRowLoading(episodes: List<cx.aswin.boxlore.core.model.Episode> = emptyList(), hasMore: Boolean = true) =
        PodcastInfoUiState.Success(
            podcast = podcast,
            episodes = episodes,
            isSubscribed = true,
            hasMoreEpisodes = hasMore,
            isLoadingMore = true,
        )

    @Test
    fun `failed details keep the local podcast and apply the fetched page`() {
        val page = listOf(TestFixtures.episode(id = "e1"), TestFixtures.episode(id = "e2"))

        val recovered =
            PodcastInfoLoadRecovery.withoutPodcastDetails(
                state = localRowLoading(),
                pageEpisodes = page,
                pageHasMore = true,
                sort = EpisodeSort.OLDEST,
            )

        assertEquals("Local row", recovered.podcast.title)
        assertEquals(page, recovered.episodes)
        assertEquals(page, recovered.piEpisodes)
        assertTrue(recovered.hasMoreEpisodes)
        assertEquals(EpisodeSort.OLDEST, recovered.currentSort)
        assertTrue(recovered.isSubscribed)
        assertFalse(recovered.isLoadingMore, "regression: the list must not spin forever")
    }

    @Test
    fun `failed details with no episodes stop loading and end pagination`() {
        val recovered =
            PodcastInfoLoadRecovery.withoutPodcastDetails(
                state = localRowLoading(),
                pageEpisodes = emptyList(),
                pageHasMore = false,
                sort = EpisodeSort.NEWEST,
            )

        assertTrue(recovered.episodes.isEmpty())
        assertFalse(recovered.isLoadingMore)
        assertFalse(recovered.hasMoreEpisodes, "an empty list must not keep a load-more spinner")
    }

    @Test
    fun `episodes already shown survive an empty fetched page`() {
        val shown = listOf(TestFixtures.episode(id = "rss-1"))

        val recovered =
            PodcastInfoLoadRecovery.withoutPodcastDetails(
                state = localRowLoading(episodes = shown, hasMore = true),
                pageEpisodes = emptyList(),
                pageHasMore = false,
                sort = EpisodeSort.NEWEST,
            )

        assertEquals(shown, recovered.episodes)
        assertTrue(recovered.hasMoreEpisodes)
        assertFalse(recovered.isLoadingMore)
    }

    @Test
    fun `sharedLookup runs the request once for every caller`() = runTest {
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val details =
            sharedLookup {
                calls.incrementAndGet()
                gate.await()
                "details"
            }

        val first = async { details.await() }
        val second = async { details.await() }
        gate.complete(Unit)

        assertEquals("details", first.await())
        assertEquals("details", second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun `sharedLookup is lazy and turns failures into null for all callers`() = runTest {
        val calls = AtomicInteger()
        val details = sharedLookup<String> {
            calls.incrementAndGet()
            error("network down")
        }
        assertEquals(0, calls.get(), "nothing starts until someone awaits")

        assertNull(details.await())
        assertNull(details.await())
        assertEquals(1, calls.get())
    }
}
