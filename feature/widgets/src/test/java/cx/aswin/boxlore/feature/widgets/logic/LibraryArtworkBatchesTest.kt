package cx.aswin.boxlore.feature.widgets.logic

import cx.aswin.boxlore.feature.widgets.LibraryWidgetSnapshot
import cx.aswin.boxlore.feature.widgets.WidgetEpisodeRow
import cx.aswin.boxlore.feature.widgets.WidgetShowRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArtworkBatchesTest {
    private fun show(index: Int, url: String? = "https://example.com/show-$index.jpg", cached: String? = null) =
        WidgetShowRow(podcastId = "show-$index", title = "Show $index", artworkUrl = url, artworkCachePath = cached, deepLinkUri = "boxlore://podcast/show-$index")

    private fun episode(index: Int) = WidgetEpisodeRow(
        episodeId = "episode-$index",
        episodeTitle = "Episode $index",
        podcastId = "show-$index",
        podcastTitle = "Show $index",
        artworkUrl = "https://example.com/episode-$index.jpg",
        deepLinkUri = "boxlore://episode/episode-$index",
    )

    private val pathFor: suspend (String?, List<String>) -> String? = { url, _ -> "/cache/${url!!.substringAfterLast('/')}" }

    @Test
    fun fourCoversArePublishedTogetherAndAFifthOnItsOwn() = runTest {
        val four = mutableListOf<LibraryWidgetSnapshot>()
        LibraryArtworkBatches.load(LibraryWidgetSnapshot(subscriptions = List(4) { show(it) }), load = pathFor) { four += it }
        assertEquals(1, four.size)
        assertTrue(four.single().subscriptions.all { it.artworkCachePath != null })

        val five = mutableListOf<LibraryWidgetSnapshot>()
        LibraryArtworkBatches.load(LibraryWidgetSnapshot(subscriptions = List(5) { show(it) }), load = pathFor) { five += it }
        assertEquals(2, five.size)
        assertEquals(4, five[0].subscriptions.count { it.artworkCachePath != null })
        assertEquals(5, five[1].subscriptions.count { it.artworkCachePath != null })
    }

    @Test
    fun showsAndEpisodesShareOneCountAndEachPublishHoldsEveryCoverSoFar() = runTest {
        val published = mutableListOf<LibraryWidgetSnapshot>()
        val snapshot = LibraryWidgetSnapshot(subscriptions = List(3) { show(it) }, newEpisodes = List(3) { episode(it) })

        LibraryArtworkBatches.load(snapshot, load = pathFor) { published += it }

        assertEquals(2, published.size)
        assertEquals(3 to 1, published[0].subscriptions.count { it.artworkCachePath != null } to published[0].newEpisodes.count { it.artworkCachePath != null })
        assertEquals(3 to 3, published[1].subscriptions.count { it.artworkCachePath != null } to published[1].newEpisodes.count { it.artworkCachePath != null })
        assertEquals("/cache/episode-2.jpg", published[1].newEpisodes[2].artworkCachePath)
    }

    @Test
    fun coversThatCannotBeLoadedAreSkippedAndRowsWithACoverOrNoAddressAreNotLoaded() = runTest {
        val asked = mutableListOf<String?>()
        val published = mutableListOf<LibraryWidgetSnapshot>()
        val snapshot =
            LibraryWidgetSnapshot(
                subscriptions = listOf(show(0), show(1, cached = "/cache/already.jpg"), show(2, url = null), show(3)),
            )

        LibraryArtworkBatches.load(
            snapshot,
            load = { url, _ ->
                asked += url
                if (url!!.endsWith("show-0.jpg")) null else "/cache/show-3.jpg"
            },
        ) { published += it }

        assertEquals(listOf<String?>("https://example.com/show-0.jpg", "https://example.com/show-3.jpg"), asked)
        assertEquals(1, published.size)
        assertEquals(listOf(null, "/cache/already.jpg", null, "/cache/show-3.jpg"), published.single().subscriptions.map { it.artworkCachePath })

        // Nothing loaded means nothing to publish.
        val none = mutableListOf<LibraryWidgetSnapshot>()
        LibraryArtworkBatches.load(LibraryWidgetSnapshot(subscriptions = List(3) { show(it) }), load = { _, _ -> null }) { none += it }
        assertTrue(none.isEmpty())
    }

    @Test
    fun aCancelledLoadPublishesNothingMore() = runTest {
        val published = mutableListOf<LibraryWidgetSnapshot>()
        val sixthAsked = CompletableDeferred<Unit>()
        var asked = 0
        val job =
            launch {
                LibraryArtworkBatches.load(
                    LibraryWidgetSnapshot(subscriptions = List(8) { show(it) }),
                    load = { url, fallbacks ->
                        if (++asked == 6) {
                            sixthAsked.complete(Unit)
                            // Never answers: the newer library cancels this load here.
                            CompletableDeferred<String?>().await()
                        } else {
                            pathFor(url, fallbacks)
                        }
                    },
                ) { published += it }
            }
        sixthAsked.await()
        job.cancelAndJoin()

        // The first four went out; the fifth was loaded but never published once the load was cancelled.
        assertEquals(1, published.size)
        assertEquals(4, published.single().subscriptions.count { it.artworkCachePath != null })
    }
}
