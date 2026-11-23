package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.SearchFeed
import cx.aswin.boxlore.core.network.model.TrendingFeed
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.tv.model.TvShow
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
}
