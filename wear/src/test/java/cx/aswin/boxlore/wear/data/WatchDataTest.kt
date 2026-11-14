package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.PodcastFeed
import cx.aswin.boxlore.core.network.model.SearchFeed
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.model.WatchShow
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class WatchDataTest {
    @TempDir
    lateinit var folder: File

    private val show = WatchShow(id = "7", title = "Radiolab", publisher = "WNYC", artworkUrl = "https://example.org/show.jpg")

    private fun item(id: Long, audio: String? = "https://example.org/$id.mp3", duration: Int? = 1_800) =
        EpisodeItem(id = id, title = "Episode $id", enclosureUrl = audio, duration = duration, datePublished = 1_793_923_200, feedId = 7)

    private val radiolab = V2PodcastResponse(feed = PodcastFeed(id = 7, title = "Radiolab"), fetchUrl = "https://example.org/feed.xml", epNs = 7)

    // --- mapping

    @Test
    fun `a search result becomes a show, preferring the larger artwork`() {
        val feed = SearchFeed(id = 7, title = "Radiolab", author = "WNYC", image = "small.jpg", artwork = "large.jpg")

        val mapped = WatchMapping.show(feed, subscribed = true)

        assertEquals(WatchShow("7", "Radiolab", "WNYC", "large.jpg", subscribed = true), mapped)
    }

    @Test
    fun `show details fall back to the plain image and an empty publisher`() {
        val mapped = WatchMapping.show(PodcastFeed(id = 7, title = "Radiolab", image = "small.jpg", artwork = " "), subscribed = false)

        assertEquals(WatchShow("7", "Radiolab", "", "small.jpg", subscribed = false), mapped)
    }

    @Test
    fun `an episode takes its audio, length, date and show from the answer`() {
        val mapped = WatchMapping.episode(item(42), show)!!

        assertEquals("42", mapped.id)
        assertEquals("7", mapped.showId)
        assertEquals("Radiolab", mapped.showTitle)
        assertEquals("https://example.org/show.jpg", mapped.artworkUrl)
        assertEquals(30, mapped.durationMinutes)
        assertEquals(1_793_923_200_000L, mapped.releasedAtMillis)
        assertEquals("https://example.org/42.mp3", mapped.audioUrl)
    }

    @Test
    fun `an episode with no usable audio address is left out`() {
        assertNull(WatchMapping.episode(item(1, audio = null), show))
        assertNull(WatchMapping.episode(item(1, audio = "  "), show))
        assertNull(WatchMapping.episode(item(1, audio = "file:///sdcard/a.mp3"), show))
    }

    @Test
    fun `lengths round up to whole minutes and unknown lengths are zero`() {
        assertEquals(1, WatchMapping.minutes(1))
        assertEquals(1, WatchMapping.minutes(60))
        assertEquals(2, WatchMapping.minutes(61))
        assertEquals(0, WatchMapping.minutes(0))
        assertEquals(0, WatchMapping.minutes(null))
        assertEquals(0, WatchMapping.minutes(-5))
    }

    // --- store

    @Test
    fun `the library survives a restart`() =
        runTest {
            val file = File(folder, WatchLibraryStore.FILE_NAME)
            val first = WatchLibraryStore(file, TestScope(StandardTestDispatcher(testScheduler)), StandardTestDispatcher(testScheduler), writeDelayMs = 0)
            first.update { it.subscribe(show).playNext(WatchMapping.episode(item(42), show)!!) }
            advanceUntilIdle()

            val second = WatchLibraryStore(file, TestScope(StandardTestDispatcher(testScheduler)), StandardTestDispatcher(testScheduler))

            assertEquals(listOf("7"), second.state.value.shows.map { it.id })
            assertEquals(listOf("42"), second.state.value.upNext.map { it.id })
        }

    @Test
    fun `a damaged file starts an empty library`() =
        runTest {
            val file = File(folder, WatchLibraryStore.FILE_NAME).apply { writeText("{ not json") }

            val store = WatchLibraryStore(file, TestScope(StandardTestDispatcher(testScheduler)), StandardTestDispatcher(testScheduler))

            assertEquals(WatchLibraryState(), store.state.value)
        }

    @Test
    fun `a download that was running when the app stopped is not shown as running`() =
        runTest {
            val file = File(folder, WatchLibraryStore.FILE_NAME)
            val episode = WatchMapping.episode(item(42), show)!!
            val first = WatchLibraryStore(file, TestScope(StandardTestDispatcher(testScheduler)), StandardTestDispatcher(testScheduler), writeDelayMs = 0)
            first.update { it.progress(episode, 60_000, 1_800_000).download(episode, WatchDownload.InProgress(0.3f)) }
            advanceUntilIdle()

            val second = WatchLibraryStore(file, TestScope(StandardTestDispatcher(testScheduler)), StandardTestDispatcher(testScheduler))

            assertEquals(WatchDownload.None, second.state.value.episodes.getValue("42").download)
        }

    // --- library

    private class FakeCatalog(
        var feeds: List<SearchFeed>? = emptyList(),
        var details: V2PodcastResponse? = null,
        var items: List<EpisodeItem>? = emptyList(),
    ) : WatchCatalog {
        override suspend fun search(query: String): List<SearchFeed>? = feeds

        override suspend fun show(showId: Long): V2PodcastResponse? = details

        override suspend fun episodes(showId: Long): List<EpisodeItem>? = items
    }

    private class FakeDownloads : WatchDownloads {
        val started = mutableListOf<String>()
        val removed = mutableListOf<String>()

        override fun start(episode: WatchEpisode) {
            started += episode.id
        }

        override fun remove(episodeId: String) {
            removed += episodeId
        }

        override fun file(episodeId: String): File? = null
    }

    private fun TestScope.library(
        catalog: WatchCatalog,
        downloads: WatchDownloads = FakeDownloads(),
        feeds: WatchFeedReader = WatchFeedReader { _, _ -> null },
    ): Pair<LiveWatchLibrary, WatchLibraryStore> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = WatchLibraryStore(File(folder, "library.json"), backgroundScope, dispatcher)
        return LiveWatchLibrary(catalog, feeds, store, downloads, backgroundScope) to store
    }

    @Test
    fun `search with no connection says so`() =
        runTest {
            val (library, _) = library(FakeCatalog(feeds = null))

            assertEquals(WatchSearchResult.NoConnection("history"), library.search("history"))
        }

    @Test
    fun `search marks shows the listener already subscribes to`() =
        runTest {
            val catalog = FakeCatalog(feeds = listOf(SearchFeed(id = 7, title = "Radiolab"), SearchFeed(id = 8, title = "Serial")))
            val (library, store) = library(catalog)
            store.update { it.subscribe(show) }

            val result = library.search("radio") as WatchSearchResult.Shows

            assertEquals(listOf(true, false), result.shows.map { it.subscribed })
        }

    @Test
    fun `a show found by search can be subscribed to and then leads Your shows`() =
        runTest {
            val (library, store) = library(FakeCatalog(feeds = listOf(SearchFeed(id = 7, title = "Radiolab"))))
            library.search("radio")

            library.setSubscribed("7", true)

            assertEquals(listOf("7"), store.state.value.shows.map { it.id })
            assertTrue(library.show("7").first()!!.subscribed)
        }

    @Test
    fun `subscribing to a show the watch has never seen does nothing`() =
        runTest {
            val (library, store) = library(FakeCatalog())

            library.setSubscribed("99", true)

            assertTrue(store.state.value.shows.isEmpty())
        }

    @Test
    fun `a show's episodes load from the API and carry saved progress`() =
        runTest {
            val catalog = FakeCatalog(details = radiolab, items = listOf(item(42), item(43, audio = null)))
            val (library, store) = library(catalog)
            store.update { it.progress(WatchMapping.episode(item(42), show)!!, 600_000, 1_800_000) }

            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()
            val episodes = flow.first()

            assertEquals(listOf("42"), episodes.map { it.id })
            assertEquals(20, episodes.single().minutesLeft)
            assertEquals("Radiolab", library.show("7").first()?.title)
        }

    @Test
    fun `episodes come from the show's own feed when it can be read`() =
        runTest {
            val fromFeed = WatchFeedEpisode(id = 900, title = "Fresh today", audioUrl = "https://example.org/fresh.mp3", imageUrl = null, durationSeconds = 600, publishedAtSeconds = 1_793_923_200)
            val catalog = FakeCatalog(details = radiolab, items = listOf(item(42)))
            val (library, _) = library(catalog, feeds = { _, _ -> listOf(fromFeed) })

            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()
            val episodes = flow.first()

            assertEquals(listOf("900"), episodes.map { it.id })
            assertEquals("Radiolab", episodes.single().showTitle)
            assertEquals(10, episodes.single().durationMinutes)
        }

    @Test
    fun `the API's list stands in when the feed is unreadable or empty`() =
        runTest {
            val catalog = FakeCatalog(details = radiolab, items = listOf(item(42)))
            val (unreadable, _) = library(catalog, feeds = { _, _ -> null })
            val (empty, _) = library(catalog, feeds = { _, _ -> emptyList() })

            listOf(unreadable, empty).forEach { library ->
                val flow = library.episodes("7")
                flow.first()
                advanceUntilIdle()
                assertEquals(listOf("42"), flow.first().map { it.id })
            }
        }

    @Test
    fun `episode actions reach the downloads and the queue`() =
        runTest {
            val downloads = FakeDownloads()
            val catalog = FakeCatalog(details = radiolab, items = listOf(item(42)))
            val (library, store) = library(catalog, downloads)
            library.episodes("7").first()
            advanceUntilIdle()

            library.setDownloaded("42", true)
            library.setDownloaded("42", false)
            library.playNext("42")
            library.markPlayed("42")

            assertEquals(listOf("42"), downloads.started)
            // Removed once by hand, and once more when the episode is marked played.
            assertEquals(listOf("42", "42"), downloads.removed)
            assertEquals(listOf("42"), store.state.value.upNext.map { it.id })
            assertTrue("42" in store.state.value.played)
        }

    @Test
    fun `download file names are safe whatever the episode ID`() {
        assertEquals("12345", FileWatchDownloads.safeName("12345"))
        assertEquals("rss_-42", FileWatchDownloads.safeName("rss:-42"))
        assertEquals("______etc", FileWatchDownloads.safeName("../../etc"))
    }
}
