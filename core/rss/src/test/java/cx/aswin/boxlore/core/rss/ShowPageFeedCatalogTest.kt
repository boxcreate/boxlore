package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.ReadOutcome
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The show page's stored feed copy through the real catalog write on a real Room database, with
 * feed documents served over an interceptor and parsed by the real parser: the newest 100 first,
 * the archive later with the same IDs, the prefix 304 rule, oldest-first feeds, and the cleanup that
 * never touches history, downloads, the queue or aliases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowPageFeedCatalogTest : RoomCatalogTestBase() {
    private val requests = CopyOnWriteArrayList<Request>()
    private var document: String = feedXml(150)
    private var status = 200
    private val persisted = mutableListOf<String>()

    private val client =
        RssFeedClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                val answer = if (chain.request().header("If-None-Match") == ETAG && status == 200) 304 else status
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(answer).message("test")
                    .header("ETag", ETAG).body(document.toResponseBody("application/rss+xml".toMediaType())).build()
            }.build(),
        )

    private val catalog by lazy {
        LocalEpisodeCatalogRepository(database, client, DownloadCacheRelinker { _, _ -> false }, { persisted += it }, RecipeIdHash.RECIPE)
    }

    private val pages: ShowPageFeedPort get() = catalog.showPages

    @Test
    fun theNewestHundredComeFirstAndTheArchiveKeepsTheirIds() = runBlocking {
        val newest = pages.read(request(Scope.NEWEST))

        assertEquals(ReadOutcome.Stored(100, complete = false), newest)
        val first = rows(SHOW)
        assertEquals(100, first.size)
        assertEquals("new items take their recipe IDs", EpisodeRecipeId.forNamespace(SHOW.toLong(), "g0").toString(), first["g0"])
        val cached = requireNotNull(pages.cached(SHOW))
        assertFalse(cached.archiveComplete)
        assertFalse("an unfollowed copy is never the live list", catalog.isReady(SHOW))
        assertTrue("a show page read schedules nothing", persisted.isEmpty())

        val archive = pages.read(request(Scope.ARCHIVE))

        assertEquals(ReadOutcome.Stored(150, complete = true), archive)
        val all = rows(SHOW)
        assertEquals(150, all.size)
        for ((key, id) in first) assertEquals("$key keeps its ID", id, all[key])
        assertTrue(requireNotNull(pages.cached(SHOW)).archiveComplete)
        assertFalse(catalog.isReady(SHOW))
        assertTrue(database.localEpisodeCatalogDao().getFeed(SHOW)!!.lastOpenedAt > 0L)
    }

    @Test
    fun a304ToTheStoredNewestIsNeverTakenAsTheArchive() = runBlocking {
        pages.read(request(Scope.NEWEST))
        assertEquals("the validators of the whole document were kept", ETAG, database.localEpisodeCatalogDao().getFeed(SHOW)!!.feedEtag)

        assertEquals(ReadOutcome.NotModified, pages.read(request(Scope.NEWEST)))
        assertEquals(ETAG, requests.last().header("If-None-Match"))

        val archive = pages.read(request(Scope.ARCHIVE))

        assertNull("an archive read of a partial copy goes out without validators", requests.last().header("If-None-Match"))
        assertEquals(ReadOutcome.Stored(150, complete = true), archive)
        // Once the archive is whole, its validators answer an archive read too.
        assertEquals(ReadOutcome.NotModified, pages.read(request(Scope.ARCHIVE)))
    }

    @Test
    fun theStartOfAnOldestFirstFeedIsNotItsNewestSoTheWholeFeedIsRead() = runBlocking {
        document = feedXml(150, oldestFirst = true)

        assertEquals(ReadOutcome.Stored(150, complete = true), pages.read(request(Scope.NEWEST)))
        assertEquals(LocalFeedOrder.OLDEST_FIRST, database.localEpisodeCatalogDao().getFeed(SHOW)!!.feedOrder)
        assertTrue(requireNotNull(pages.cached(SHOW)).oldestFirst)
    }

    @Test
    fun aFailedReadSaysWhatItSawAndStoresNothing() = runBlocking {
        status = 404
        assertEquals(ReadOutcome.Failed("http_404"), pages.read(request(Scope.NEWEST)))
        assertNull(pages.cached(SHOW))
        assertEquals(ReadOutcome.Failed("other"), pages.read(request(Scope.NEWEST).copy(podcastId = "rss:abc")))
    }

    @Test
    fun aTransportDocumentIsStoredWithoutRecordingTheTransportAddress() = runBlocking {
        val transport = ShowPageFeedPort.Document(feedXml(20).encodeToByteArray(), "https://api.example/v2/feed/$SHOW", "t-etag", complete = true)

        assertEquals(ReadOutcome.Stored(20, complete = true), pages.store(request(Scope.NEWEST), transport))
        assertEquals("", database.localEpisodeCatalogDao().getFeed(SHOW)!!.feedUrl)
    }

    @Test
    fun cleanupRemovesOnlyUnfollowedCopiesAndKeepsListenerState() = runBlocking {
        document = feedXml(5)
        pages.read(request(Scope.ARCHIVE))
        pages.read(request(Scope.ARCHIVE, show = OTHER_SHOW))
        pages.read(request(Scope.ARCHIVE, show = THIRD_SHOW))
        follow(THIRD_SHOW)
        val played = rows(SHOW).getValue("g1")
        history(played, SHOW)
        completedDownload(played, SHOW, "Ep 1")
        queue(played, SHOW, "https://cdn.example/g1.mp3", "Ep 1")
        val aliasesBefore = database.episodeIdentityDao().aliasesForShow(SHOW).size

        val removed = pages.cleanUp(nowMillis = System.currentTimeMillis() + 31L * DAY, keepPodcastId = OTHER_SHOW)

        assertEquals(1, removed)
        assertNull(pages.cached(SHOW))
        assertNotNull("the show being opened stays", pages.cached(OTHER_SHOW))
        assertNotNull("a followed show's catalog stays", pages.cached(THIRD_SHOW))
        assertNotNull(database.listeningHistoryDao().getHistoryItem(played))
        assertNotNull(database.downloadedEpisodeDao().getDownload(played))
        assertNotNull(database.queueDao().getQueueItemByEpisodeId(played))
        assertEquals("aliases outlive the copy", aliasesBefore, database.episodeIdentityDao().aliasesForShow(SHOW).size)
    }

    @Test
    fun followAndUnfollowWriteTheFollowedColumn() = runBlocking {
        pages.read(request(Scope.NEWEST))
        catalog.setUnsubscribedTtl(SHOW, null)
        assertTrue(database.localEpisodeCatalogDao().getFeed(SHOW)!!.followed)
        catalog.setUnsubscribedTtl(SHOW, 5L)
        assertFalse(database.localEpisodeCatalogDao().getFeed(SHOW)!!.followed)
        database.podcastDao().upsert(PodcastEntity(podcastId = SHOW, title = "Show", author = "", imageUrl = "", description = null, isSubscribed = false))
        assertEquals("a copy under an unsubscribe TTL is left to the TTL sweep", 0, pages.cleanUp(Long.MAX_VALUE / 2))
    }

    private fun request(scope: Scope, show: String = SHOW) = ShowPageFeedPort.ReadRequest(show, feedUrl(show), scope, LocalEpisodeCatalogPort.PodcastMeta(title = "Show"))

    private companion object {
        const val ETAG = "\"whole-doc\""
        const val DAY = 24L * 60L * 60L * 1000L

        /** [count] items dated a day apart, newest first unless [oldestFirst]. */
        fun feedXml(count: Int, oldestFirst: Boolean = false): String {
            val order = if (oldestFirst) (count - 1 downTo 0) else (0 until count)
            val items =
                order.joinToString("") { index ->
                    val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(1_790_000_000L - index * 86_400L).atZone(ZoneOffset.UTC))
                    "<item><title>Episode $index</title><guid>g$index</guid><pubDate>$date</pubDate>" +
                        "<enclosure url=\"https://cdn.example/g$index.mp3\" type=\"audio/mpeg\" length=\"1\"/></item>"
                }
            return "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Show</title>$items</channel></rss>"
        }
    }
}
