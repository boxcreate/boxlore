package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.EpisodeInfoUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The episode page's Try these episodes loader, driven by the LeaveMeAlone switch. */
@OptIn(ExperimentalCoroutinesApi::class)
class SimilarEpisodesLoaderTest {
    private val similar = listOf(Episode("similar_1", "Similar episode", "", ""))
    private val state = MutableStateFlow<EpisodeInfoUiState>(page("e1"))
    private val on = MutableStateFlow(true)
    private var requests = 0

    @Test
    fun `off makes no request and drops the loading card`() = runTest {
        on.value = false
        val job = collect(counting { similar })

        assertEquals(0, requests)
        assertFalse(success().similarEpisodesLoading)
        assertTrue(success().similarEpisodes.isEmpty())
        job.cancel()
    }

    @Test
    fun `on loads the open episode's similar episodes`() = runTest {
        val job = collect(counting { similar })

        assertEquals(1, requests)
        assertEquals(similar, success().similarEpisodes)
        assertFalse(success().similarEpisodesLoading)
        job.cancel()
    }

    @Test
    fun `switching off during a request cancels it and its reply never shows`() = runTest {
        val reply = CompletableDeferred<List<Episode>>()
        val job = collect(counting { reply.await() })
        assertTrue(success().similarEpisodesLoading)

        on.value = false
        runCurrent()
        reply.complete(similar)
        runCurrent()

        assertTrue(success().similarEpisodes.isEmpty())
        assertFalse(success().similarEpisodesLoading)
        job.cancel()
    }

    @Test
    fun `switching back on loads it again`() = runTest {
        on.value = false
        val job = collect(counting { similar })
        on.value = true
        runCurrent()

        assertEquals(1, requests)
        assertEquals(similar, success().similarEpisodes)
        job.cancel()
    }

    @Test
    fun `a reply for an episode no longer shown leaves the new page alone`() = runTest {
        val reply = CompletableDeferred<List<Episode>>()
        val job = collect { reply.await() }
        state.value = page("e2")

        reply.complete(similar)
        runCurrent()

        assertEquals("e2", success().episode.id)
        assertTrue(success().similarEpisodes.isEmpty())
        assertTrue(success().similarEpisodesLoading)
        job.cancel()
    }

    @Test
    fun `a failed request ends loading and reports the error`() = runTest {
        val errors = mutableListOf<Exception>()
        val job = launch {
            collectSimilarEpisodes("e1", on, state, fetch = { error("offline") }, onError = { errors += it })
        }
        runCurrent()

        assertEquals(1, errors.size)
        assertFalse(success().similarEpisodesLoading)
        assertTrue(success().similarEpisodes.isEmpty())
        job.cancel()
    }

    private fun TestScope.collect(fetch: suspend () -> List<Episode>) =
        launch { collectSimilarEpisodes("e1", on, state, fetch) }.also { runCurrent() }

    private fun counting(fetch: suspend () -> List<Episode>): suspend () -> List<Episode> = {
        requests++
        fetch()
    }

    private fun success() = state.value as EpisodeInfoUiState.Success

    private fun page(id: String) = EpisodeInfoUiState.Success(episode = Episode(id, "Episode $id", "", ""), podcastId = "p1", podcastTitle = "Show")
}
