package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.PodcastFeed
import cx.aswin.boxlore.core.network.model.SearchFeed
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.wear.model.ShowEpisodes
import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.model.WatchShow
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        phone: suspend (showId: String, show: WatchShow?, fresh: Boolean) -> PhoneEpisodeList? = { _, _, _ -> null },
        clock: () -> Long = { NOW },
    ): Pair<LiveWatchLibrary, WatchLibraryStore> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = WatchLibraryStore(File(folder, "library.json"), backgroundScope, dispatcher)
        return LiveWatchLibrary(catalog, feeds, store, downloads, backgroundScope, phoneEpisodes = phone, clock = clock) to store
    }

    /** The page's list once loading has settled; fails the test when the page is not showing one. */
    private suspend fun Flow<ShowEpisodes>.listed(): List<WatchEpisode> {
        val state = first()
        check(state is ShowEpisodes.Loaded) { "expected a list, got $state" }
        return state.episodes
    }

    @Test
    fun `search with no connection says so`() =
        runTest {
            val (library, _) = library(FakeCatalog(feeds = null))

            assertEquals(WatchSearchResult.NoConnection("history"), library.search("history"))
        }

    @Test
    fun `before the watch is signed in nothing reaches the catalog`() =
        runTest {
            var signedIn = false
            val catalog = SignedInWatchCatalog(FakeCatalog(feeds = listOf(SearchFeed(id = 7, title = "Radiolab")), details = V2PodcastResponse())) { signedIn }

            assertNull(catalog.search("radio"))
            assertNull(catalog.show(7))
            assertNull(catalog.episodes(7))
            signedIn = true
            assertEquals(listOf(7L), catalog.search("radio")?.map { it.id })
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
            val episodes = flow.listed()

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
            val episodes = flow.listed()

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
                assertEquals(listOf("42"), flow.listed().map { it.id })
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

    // --- show page: loading, failing and fresh lists

    private fun phoneEpisode(id: String, releasedAt: Long) =
        WatchEpisode(id = id, showId = "7", title = "Episode $id", showTitle = "Radiolab", artworkUrl = null, durationMinutes = 30, releasedAtMillis = releasedAt, audioUrl = "https://example.org/$id.mp3")

    private fun feedEpisode(id: Long, releasedAt: Long) =
        WatchFeedEpisode(id = id, title = "Episode $id", audioUrl = "https://example.org/$id.mp3", imageUrl = null, durationSeconds = 1_800, publishedAtSeconds = releasedAt / 1_000)

    private val older = phoneEpisode("1", NOW - 3 * DAY)
    private val old = phoneEpisode("2", NOW - 2 * DAY)

    // Regression: a show whose episodes could not load said "No episodes yet", with no way to try again.
    @Test
    fun `a show page that cannot load says so instead of listing no episodes, and can try again`() =
        runTest {
            val catalog = FakeCatalog(details = radiolab, items = null)
            // The feed read waits on this, so the page can be seen while it loads.
            var feedRead = CompletableDeferred<Unit>()
            val (library, _) =
                library(
                    catalog,
                    feeds = { _, _ ->
                        feedRead.await()
                        null
                    },
                )
            val flow = library.episodes("7")

            assertEquals(ShowEpisodes.Loading, flow.first())
            feedRead.complete(Unit)
            advanceUntilIdle()
            assertEquals(ShowEpisodes.Failed, flow.first())
            advanceUntilIdle()

            catalog.items = listOf(item(42))
            feedRead = CompletableDeferred()
            library.reloadShow("7")
            assertEquals(ShowEpisodes.Loading, flow.first())
            feedRead.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("42"), flow.listed().map { it.id })
        }

    @Test
    fun `a show with no episodes at all still says so`() =
        runTest {
            val (library, _) = library(FakeCatalog(details = radiolab, items = emptyList()))
            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()

            assertEquals(ShowEpisodes.Loaded(emptyList()), flow.first())
        }

    // Regression: a show the API could not look up drew a blank screen.
    @Test
    fun `a show that cannot be looked up fails the page instead of leaving it blank`() =
        runTest {
            // Even with episodes from the phone, a page with no show to name cannot be drawn.
            val (library, _) = library(FakeCatalog(details = null, items = null), phone = { _, _, _ -> PhoneEpisodeList(listOf(old), NOW) })
            val show = library.show("7")
            val flow = library.episodes("7")
            show.first()
            flow.first()
            advanceUntilIdle()

            assertNull(show.first())
            assertEquals(ShowEpisodes.Failed, flow.first())
        }

    @Test
    fun `a show the listener follows is never failed for want of its details`() =
        runTest {
            val (library, store) = library(FakeCatalog(details = null, items = null), phone = { _, _, _ -> PhoneEpisodeList(listOf(old), NOW) })
            store.update { it.subscribe(show) }
            library.show("7").first()
            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()

            assertEquals(listOf("2"), flow.listed().map { it.id })
        }

    // Regression: the phone's saved list was used however long ago the phone read the feed.
    @Test
    fun `an old list on the phone is replaced by the phone's fresh one`() =
        runTest {
            val newest = phoneEpisode("3", NOW - HOUR)
            val asked = mutableListOf<Boolean>()
            var feedReads = 0
            val (library, _) =
                library(
                    FakeCatalog(details = radiolab),
                    feeds = { _, _ ->
                        feedReads++
                        null
                    },
                    phone = { _, _, fresh ->
                        asked += fresh
                        if (fresh) PhoneEpisodeList(listOf(newest, old, older), NOW - 1_000) else PhoneEpisodeList(listOf(old, older), NOW - 2 * DAY)
                    },
                )
            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()

            assertEquals(listOf(false, true), asked)
            assertEquals(0, feedReads)
            assertEquals(listOf("3", "2", "1"), flow.listed().map { it.id })
        }

    @Test
    fun `when the phone cannot freshen an old list the watch reads the feed and adds what is new`() =
        runTest {
            val (library, _) =
                library(
                    FakeCatalog(details = radiolab, items = null),
                    feeds = { _, _ -> listOf(feedEpisode(3, NOW - HOUR), feedEpisode(2, NOW - 2 * DAY)) },
                    // The phone's read failed: it answers with the same old list.
                    phone = { _, _, _ -> PhoneEpisodeList(listOf(old, older), NOW - 2 * DAY) },
                )
            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()

            assertEquals(listOf("3", "2", "1"), flow.listed().map { it.id })
        }

    @Test
    fun `an old list from a phone that gives no read time is checked against the feed`() =
        runTest {
            val (library, _) =
                library(
                    FakeCatalog(details = radiolab, items = null),
                    feeds = { _, _ -> listOf(feedEpisode(3, NOW - HOUR)) },
                    phone = { _, _, _ -> PhoneEpisodeList(listOf(old), readAtMillis = null) },
                )
            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()

            assertEquals(listOf("3", "2"), flow.listed().map { it.id })
        }

    @Test
    fun `a recent list on the phone is used as it is`() =
        runTest {
            val asked = mutableListOf<Boolean>()
            var feedReads = 0
            val (library, _) =
                library(
                    FakeCatalog(details = radiolab),
                    feeds = { _, _ ->
                        feedReads++
                        null
                    },
                    phone = { _, _, fresh ->
                        asked += fresh
                        PhoneEpisodeList(listOf(old, older), NOW - HOUR)
                    },
                )
            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()

            assertEquals(listOf(false), asked)
            assertEquals(0, feedReads)
            assertEquals(listOf("2", "1"), flow.listed().map { it.id })
        }

    @Test
    fun `an old list is looked past once in a while, not on every visit`() =
        runTest {
            var now = NOW
            val asked = mutableListOf<Boolean>()
            var feedReads = 0
            val (library, _) =
                library(
                    FakeCatalog(details = radiolab, items = null),
                    feeds = { _, _ ->
                        feedReads++
                        null
                    },
                    phone = { _, _, fresh ->
                        asked += fresh
                        PhoneEpisodeList(listOf(old), NOW - 2 * DAY)
                    },
                    clock = { now },
                )
            repeat(2) {
                library.episodes("7").first()
                advanceUntilIdle()
            }
            assertEquals(listOf(false, true, false), asked)
            assertEquals(1, feedReads)

            now += ShowEpisodeRules.STALE_AFTER_MS
            library.episodes("7").first()
            advanceUntilIdle()
            assertEquals(listOf(false, true, false, false, true), asked)
            assertEquals(2, feedReads)
        }

    @Test
    fun `a list on the page stays when a later read fails`() =
        runTest {
            val listed = listOf(old)

            assertEquals(ShowEpisodes.Loaded(listed), ShowEpisodeRules.state(EpisodeLoad.Failed, listed, showUnknown = false))
            assertEquals(ShowEpisodes.Failed, ShowEpisodeRules.state(EpisodeLoad.Failed, emptyList(), showUnknown = false))
            assertEquals(ShowEpisodes.Loading, ShowEpisodeRules.state(null, emptyList(), showUnknown = false))
            assertEquals(ShowEpisodes.Failed, ShowEpisodeRules.state(EpisodeLoad.Loaded, listed, showUnknown = true))
        }

    @Test
    fun `a feed read replaces its own range of the page and keeps what is older`() {
        val fromPhone = old.copy(id = "pi-2", catalogKey = "guid-2", audioUrl = "https://example.org/a.mp3")
        val removed = phoneEpisode("gone", NOW - DAY)
        val fromFeed = phoneEpisode("3", NOW - HOUR)
        val renamed = old.copy(id = "2", title = "Renamed", audioUrl = "https://example.org/b.mp3")
        val ancient = phoneEpisode("0", NOW - 9 * DAY)
        val undated = phoneEpisode("u", 0).copy(releasedAtMillis = null)

        val replaced = ShowEpisodeRules.replaced(listOf(removed, fromPhone, older, ancient, undated), listOf(fromFeed, renamed))

        // The phone's copy under another ID and the episode taken off the feed both drop off.
        assertEquals(listOf("3", "2", "1", "0"), replaced.map { it.id })
        assertEquals("Renamed", replaced[1].title)
        // A read with no dates at all has no range to keep anything outside of.
        assertEquals(listOf("3"), ShowEpisodeRules.replaced(listOf(old), listOf(fromFeed.copy(releasedAtMillis = null))).map { it.id })
    }

    @Test
    fun `an older list only adds episodes newer than the page`() {
        val newest = phoneEpisode("3", NOW - HOUR)
        val sameAsOld = old.copy(id = "pi-2", audioUrl = "https://example.org/2.mp3?updated=1")
        val removedElsewhere = phoneEpisode("gone", NOW - DAY - HOUR)

        assertEquals(listOf("3", "2"), ShowEpisodeRules.added(listOf(newest, old), listOf(sameAsOld, removedElsewhere, old)).map { it.id })
        assertEquals(listOf("4", "3"), ShowEpisodeRules.added(listOf(newest), listOf(phoneEpisode("4", NOW), newest)).map { it.id })
        assertEquals(listOf("2", "1"), ShowEpisodeRules.added(emptyList(), listOf(older, old)).map { it.id })
    }

    // Regression: the API's list, under other IDs and addresses, was merged over the phone's list, so each episode showed twice.
    @Test
    fun `the API's list never lands on top of the phone's, so no episode is listed twice`() =
        runTest {
            val phoneRow = phoneEpisode("recipe-2", NOW - 2 * DAY).copy(audioUrl = "https://example.org/2.mp3")
            val (library, _) =
                library(
                    // Same episode on the API: its own ID and key, and an audio address with a changing parameter.
                    FakeCatalog(details = radiolab, items = listOf(item(2, audio = "https://example.org/2.mp3?updated=9").copy(datePublished = (NOW - 2 * DAY) / 1_000))),
                    feeds = { _, _ -> null },
                    // The phone's fresh read failed too: it answers with the same old list.
                    phone = { _, _, _ -> PhoneEpisodeList(listOf(phoneRow), NOW - 2 * DAY) },
                )
            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()

            assertEquals(listOf("recipe-2"), flow.listed().map { it.id })
        }

    @Test
    fun `an episode no longer on the feed drops off the page`() =
        runTest {
            var feed = listOf(feedEpisode(3, NOW - HOUR), feedEpisode(2, NOW - 2 * DAY))
            val (library, _) = library(FakeCatalog(details = radiolab, items = null), feeds = { _, _ -> feed })
            library.episodes("7").first()
            advanceUntilIdle()
            assertEquals(listOf("3", "2"), library.episodes("7").listed().map { it.id })

            feed = listOf(feedEpisode(2, NOW - 2 * DAY))
            val flow = library.episodes("7")
            flow.first()
            advanceUntilIdle()

            assertEquals(listOf("2"), flow.listed().map { it.id })
        }

    // Regression: unfollowing a show the API cannot describe left its open page on an endless spinner.
    @Test
    fun `a show unfollowed from its page stays on the page and can be followed again`() =
        runTest {
            val rssShow = WatchShow(id = "rss:-42", title = "Local feed", publisher = "Me", artworkUrl = "")
            val (library, store) = library(FakeCatalog(details = null, items = null), phone = { _, _, _ -> PhoneEpisodeList(listOf(old), NOW) })
            store.update { it.subscribe(rssShow) }
            advanceUntilIdle()
            val show = library.show(rssShow.id)
            val flow = library.episodes(rssShow.id)
            show.first()
            flow.first()
            advanceUntilIdle()

            library.setSubscribed(rssShow.id, false)
            advanceUntilIdle()
            assertEquals(rssShow.copy(subscribed = false), show.first())
            assertEquals(listOf("2"), flow.listed().map { it.id })

            library.setSubscribed(rssShow.id, true)
            advanceUntilIdle()
            assertEquals(true, show.first()?.subscribed)
        }

    @Test
    fun `a followed show whose details failed is still named after it is unfollowed`() =
        runTest {
            val (library, store) = library(FakeCatalog(details = null, items = null), phone = { _, _, _ -> PhoneEpisodeList(listOf(old), NOW) })
            store.update { it.subscribe(show) }
            advanceUntilIdle()
            val named = library.show("7")
            val flow = library.episodes("7")
            named.first()
            flow.first()
            advanceUntilIdle()

            library.setSubscribed("7", false)
            advanceUntilIdle()

            assertEquals(show.copy(subscribed = false), named.first())
            assertEquals(listOf("2"), flow.listed().map { it.id })
        }

    @Test
    fun `a list is fresh only with a read time inside the window`() {
        assertTrue(ShowEpisodeRules.isFresh(NOW - HOUR, NOW))
        assertTrue(ShowEpisodeRules.isFresh(NOW + HOUR, NOW))
        assertFalse(ShowEpisodeRules.isFresh(NOW - ShowEpisodeRules.STALE_AFTER_MS, NOW))
        assertFalse(ShowEpisodeRules.isFresh(null, NOW))
        assertFalse(ShowEpisodeRules.isFresh(0L, NOW))

        assertTrue(ShowEpisodeRules.wantsFresher(NOW - 2 * DAY, lastTryMillis = null, nowMillis = NOW))
        assertFalse(ShowEpisodeRules.wantsFresher(NOW - 2 * DAY, lastTryMillis = NOW - HOUR, nowMillis = NOW))
        assertFalse(ShowEpisodeRules.wantsFresher(NOW - HOUR, lastTryMillis = null, nowMillis = NOW))
    }

    @Test
    fun `download file names are safe whatever the episode ID`() {
        assertEquals("12345", FileWatchDownloads.safeName("12345"))
        assertEquals("rss_-42", FileWatchDownloads.safeName("rss:-42"))
        assertEquals("______etc", FileWatchDownloads.safeName("../../etc"))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val HOUR = 60L * 60L * 1_000L
        const val DAY = 24L * HOUR
    }
}
