package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.ReadOutcome
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import cx.aswin.boxlore.core.model.FeedFetchCompleteness
import cx.aswin.boxlore.core.model.FeedFetchOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedFetchSource
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.network.model.V2FeedReportRequest
import cx.aswin.boxlore.core.network.model.V2FeedReportResponse
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The feed-first show page's failure ladder against a scripted stored copy: offline shows the copy
 * without reading or reporting, a failed read is tried once more (never after a 404 or 410), then
 * reported, then the reported address is read, and only then the page falls back to the server's
 * list. One `feed_fetch_result` per read, without addresses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedFirstShowPagesTest {
    private var online = true
    private var registry: V2PodcastResponse? = V2PodcastResponse(fetchUrl = PUBLISHER, fetchVia = "direct", epNs = SHOW.toLong())
    private val reports = mutableListOf<V2FeedReportRequest>()
    private var reportAnswer: V2FeedReportResponse? = null
    private val telemetry = RecordingTelemetry()
    private val copies = ScriptedCopies()
    private var now = 1_000_000L

    private val pages by lazy {
        FeedFirstShowPages(
            copies = copies,
            registry = { registry },
            transport = { ShowPageFeedPort.Document("<rss/>".encodeToByteArray(), "https://api.example/v2/feed/$it", null, complete = true) },
            reporter =
            FeedReporter(
                send = { request ->
                    reports += request
                    reportAnswer
                },
                limiter = FeedReportLimiter(MemoryFeedReportStamps()),
                isOnline = { online },
                enabled = { true },
            ),
            isOnline = { online },
            telemetry = telemetry,
            nowMillis = { now },
        )
    }

    @Test
    fun offlineShowsTheStoredCopyWithoutReadingOrReporting() = runBlocking {
        online = false

        assertEquals(ShowPageFeedResult.OFFLINE, pages.read(SHOW, META, Scope.NEWEST, SAVED).result)
        assertTrue(copies.reads.isEmpty())
        assertTrue(reports.isEmpty())
        assertFalse(pages.isInFallback(SHOW))
        assertTrue("no read, no event", telemetry.reports.isEmpty())
    }

    @Test
    fun theRegistryAddressIsReadAndOneEventSaysHowMuch() = runBlocking {
        copies.answers[PUBLISHER] = mutableListOf(ReadOutcome.Stored(100, complete = false))

        val read = pages.read(SHOW, META, Scope.NEWEST, SAVED)

        assertEquals(ShowPageFeedResult.UPDATED, read.result)
        assertEquals(listOf(PUBLISHER to Scope.NEWEST), copies.reads)
        assertEquals("the registry's namespace is passed on to be compared", SHOW.toLong(), copies.lastRequest?.serverEpNs)
        assertEquals(
            listOf(FeedFetchReport.ShowPage(SHOW, FeedFetchSource.FEED, FeedFetchOutcome.OK, FeedFetchCompleteness.PARTIAL)),
            telemetry.reports,
        )
    }

    @Test
    fun aFailedReadIsRetriedOnceThenReportedThenTheReportedAddressIsRead() = runBlocking {
        copies.answers[PUBLISHER] = mutableListOf(ReadOutcome.Failed("timeout"), ReadOutcome.Failed("timeout"))
        copies.answers[REPORTED] = mutableListOf(ReadOutcome.Stored(40, complete = true))
        reportAnswer = V2FeedReportResponse(queued = true, fetchUrl = REPORTED, fetchVia = "direct")

        val read = pages.read(SHOW, META, Scope.NEWEST, SAVED)

        assertEquals(ShowPageFeedResult.UPDATED, read.result)
        assertEquals(listOf(PUBLISHER, PUBLISHER, REPORTED), copies.reads.map { it.first })
        assertEquals(listOf(V2FeedReportRequest(SHOW.toLong(), "timeout")), reports)
        assertEquals(1, telemetry.reports.size)
    }

    @Test
    fun aGoneAddressIsNotRetriedAndWithoutAnotherAddressThePageFallsBack() = runBlocking {
        copies.answers[PUBLISHER] = mutableListOf(ReadOutcome.Failed("http_404"))
        reportAnswer = V2FeedReportResponse(queued = true, fetchUrl = PUBLISHER, fetchVia = "direct")

        val read = pages.read(SHOW, META, Scope.NEWEST, SAVED)

        assertEquals(ShowPageFeedResult.FALLBACK, read.result)
        assertEquals(listOf(PUBLISHER), copies.reads.map { it.first })
        assertEquals(1, reports.size)
        assertTrue(pages.isInFallback(SHOW))
        assertEquals(
            listOf(FeedFetchReport.ShowPage(SHOW, FeedFetchSource.FEED, FeedFetchOutcome.GONE, httpStatus = 404)),
            telemetry.reports,
        )

        // Opening the page again tries the feed again; a read that works keeps it there.
        pages.open(SHOW, META)
        assertFalse(pages.isInFallback(SHOW))
        copies.answers[PUBLISHER] = mutableListOf(ReadOutcome.NotModified)
        assertEquals(ShowPageFeedResult.UNCHANGED, pages.read(SHOW, META, Scope.NEWEST, SAVED).result)
        assertFalse(pages.isInFallback(SHOW))
    }

    @Test
    fun withoutTheRegistryTheSavedAddressIsReadAndAnHttpOnlyShowGoesThroughTheTransport() = runBlocking {
        registry = null
        copies.answers[SAVED] = mutableListOf(ReadOutcome.Stored(10, complete = true))
        assertEquals(ShowPageFeedResult.UPDATED, pages.read(SHOW, META, Scope.ARCHIVE, SAVED).result)
        assertEquals(listOf(SAVED to Scope.ARCHIVE), copies.reads)

        registry = V2PodcastResponse(fetchUrl = "http://plain.example/feed", fetchVia = "proxy", transportUrl = "https://api.example/v2/feed/$SHOW")
        assertEquals(ShowPageFeedResult.UPDATED, pages.read(SHOW, META, Scope.NEWEST, SAVED).result)
        assertEquals(1, copies.stores)
        assertEquals(FeedFetchSource.TRANSPORT, (telemetry.reports.last() as FeedFetchReport.ShowPage).source)

        registry = null
        assertEquals("no address at all", ShowPageFeedResult.FALLBACK, pages.read(SHOW, META, Scope.NEWEST, null).result)
        assertEquals("rss: shows never read through here", ShowPageFeedResult.FALLBACK, pages.read("rss:abc", META, Scope.NEWEST, SAVED).result)
    }

    @Test
    fun openingSaysWhatTheCopyHoldsAndWhenItIsDueAgain() = runBlocking {
        assertEquals(ShowPageFeedState(cached = false, archiveComplete = false, recheckDue = true, oldestFirst = false), pages.open(SHOW, META))

        copies.cachedShow = ShowPageFeedPort.CachedShow(SHOW, PUBLISHER, fetchedAt = now - 10 * 60_000L, itemCount = 100, archiveComplete = false, oldestFirst = false)
        assertEquals(ShowPageFeedState(cached = true, archiveComplete = false, recheckDue = false, oldestFirst = false), pages.open(SHOW, META))
        now += 60 * 60_000L
        assertTrue("an hour old: read again", pages.open(SHOW, META).recheckDue)
        assertEquals(META, pages.metaFor(SHOW))
        assertEquals("the daily cleanup ran once, keeping the show being opened", listOf(SHOW), copies.cleanups)
        assertEquals(2, copies.opened.size)
    }

    @Test
    fun readFailuresMapToTheEventOutcomes() {
        assertEquals(FeedFetchOutcome.GONE, FeedFirstShowPages.failureOutcome("http_410"))
        assertEquals(FeedFetchOutcome.HTTP_ERROR, FeedFirstShowPages.failureOutcome("http_503"))
        assertEquals(FeedFetchOutcome.TIMEOUT, FeedFirstShowPages.failureOutcome("timeout"))
        assertEquals(FeedFetchOutcome.PARSE_ERROR, FeedFirstShowPages.failureOutcome("parse"))
        assertEquals(FeedFetchOutcome.NETWORK_ERROR, FeedFirstShowPages.failureOutcome("dns"))
    }

    private class ScriptedCopies : ShowPageFeedPort {
        val answers = mutableMapOf<String, MutableList<ReadOutcome>>()
        val reads = mutableListOf<Pair<String, Scope>>()
        var lastRequest: ShowPageFeedPort.ReadRequest? = null
        var stores = 0
        var cachedShow: ShowPageFeedPort.CachedShow? = null
        val opened = mutableListOf<String>()
        val cleanups = mutableListOf<String?>()

        override suspend fun cached(podcastId: String): ShowPageFeedPort.CachedShow? = cachedShow

        override suspend fun read(request: ShowPageFeedPort.ReadRequest): ReadOutcome {
            reads += request.feedUrl to request.scope
            lastRequest = request
            val queue = answers[request.feedUrl] ?: return ReadOutcome.Failed("other")
            return if (queue.size > 1) queue.removeAt(0) else queue.first()
        }

        override suspend fun store(request: ShowPageFeedPort.ReadRequest, document: ShowPageFeedPort.Document): ReadOutcome {
            stores++
            return ReadOutcome.Stored(5, document.complete)
        }

        override suspend fun markOpened(podcastId: String, atMillis: Long) {
            opened += podcastId
        }

        override suspend fun cleanUp(nowMillis: Long, keepPodcastId: String?): Int {
            cleanups += keepPodcastId
            return 0
        }
    }

    private class RecordingTelemetry : FeedTelemetryPort {
        val reports = mutableListOf<FeedFetchReport>()

        override fun feedFetched(report: FeedFetchReport) {
            reports += report
        }

        override fun feedUrlCorrected(correction: FeedUrlCorrection) = Unit
    }

    private companion object {
        const val SHOW = "920666"
        const val PUBLISHER = "https://feeds.example/920666.xml"
        const val SAVED = "https://saved.example/920666.xml"
        const val REPORTED = "https://moved.example/920666.xml"
        val META = PodcastMeta(title = "Show", imageUrl = "https://img.example/a.jpg")
    }
}
