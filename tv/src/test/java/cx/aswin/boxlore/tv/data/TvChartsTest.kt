package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.SearchFeed
import cx.aswin.boxlore.core.network.model.TrendingFeed
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.tv.model.TvShow
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class TvChartsTest {
    /** A catalog whose charts are two shows per genre, counting what is asked for. */
    private class Charts : TvCatalog {
        val asked = mutableListOf<String?>()

        override suspend fun search(query: String): List<SearchFeed>? = null

        override suspend fun show(showId: Long): V2PodcastResponse? = null

        override suspend fun episodes(showId: Long): List<EpisodeItem>? = null

        override suspend fun trending(country: String, genre: String?): List<TrendingFeed> {
            asked += genre
            val base = if (genre == null) 10L else 20L
            return listOf(TrendingFeed(id = base, title = "Top ${genre ?: "all"}"), TrendingFeed(id = base + 1, title = "Next ${genre ?: "all"}"))
        }
    }

    @Test
    fun `a genre's chart loads once, the top chart loads at start, and followed shows are marked`(@TempDir folder: File) =
        runTest {
            val work = TestScope(StandardTestDispatcher(testScheduler))
            val store = TvLibraryStore(File(folder, "library.json"), work, StandardTestDispatcher(testScheduler))
            store.update { it.subscribe(TvShow("21", "Next comedy", "", null)) }
            val catalog = Charts()
            val library = LiveTvLibrary(store, catalog, feeds = null, scope = work, country = { "us" })

            work.advanceUntilIdle()
            assertEquals(listOf("10", "11"), library.trending.value.map { it.id })

            library.chart("comedy").first()
            work.advanceUntilIdle()
            val comedy = library.chart("comedy").first()
            assertEquals(listOf("20", "21"), comedy.map { it.id })
            assertEquals(listOf(false, true), comedy.map { it.subscribed })
            assertEquals(listOf(null, "comedy"), catalog.asked)
        }

    @Test
    fun `a show page loads while the same show's Daily Mix read is still waiting`(@TempDir folder: File) =
        runTest {
            val work = TestScope(StandardTestDispatcher(testScheduler))
            val store = TvLibraryStore(File(folder, "library.json"), work, StandardTestDispatcher(testScheduler))
            store.update { it.subscribe(TvShow("5", "Show", "", null)) }
            val mixRead = CompletableDeferred<List<TvFeedEpisode>?>()
            val episode = TvFeedEpisode(id = 1, title = "One", audioUrl = "https://example.com/1.mp3", imageUrl = null, durationSeconds = 60, publishedAtSeconds = 1, catalogKey = "k1")
            val feeds =
                TvFeedReader { _, _, limit ->
                    // The start-up Daily Mix read asks for a few and hangs; the page asks for a page.
                    if (limit < 10) mixRead.await() else listOf(episode)
                }
            val catalog = object : TvCatalog by Charts() {
                override suspend fun show(showId: Long) = V2PodcastResponse()
            }
            val library = LiveTvLibrary(store, catalog, feeds, scope = work, country = { "us" }, compute = StandardTestDispatcher(testScheduler))
            work.runCurrent()

            val page = work.async { library.episodes("5").first { it is ShowEpisodes.Loaded } }
            work.advanceUntilIdle()
            assertEquals(listOf("1"), (page.getCompleted() as ShowEpisodes.Loaded).episodes.map { it.id })
            mixRead.complete(null)
        }
}
