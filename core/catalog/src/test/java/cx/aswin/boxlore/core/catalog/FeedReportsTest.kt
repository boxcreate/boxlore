package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.network.model.V2FeedReadTarget
import cx.aswin.boxlore.core.network.model.V2FeedReportRequest
import cx.aswin.boxlore.core.network.model.V2FeedReportResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Broken-feed reports of followed shows (own-episode-IDs P4 PR 14, plan section 11): limited per
 * show and per day, never sent offline, never for `rss:` shows, and only a readable new address is
 * offered back.
 */
class FeedReportsTest {
    private var now = 100 * DAY
    private var online = true
    private var enabled = true
    private val sent = mutableListOf<V2FeedReportRequest>()
    private var answer: V2FeedReportResponse? = V2FeedReportResponse(queued = true, fetchUrl = NEW, fetchVia = "direct", feedSource = "registry:apple")
    private val stamps = MemoryFeedReportStamps()
    private val reporter =
        FeedReporter(
            send = { request ->
                sent += request
                answer
            },
            limiter = FeedReportLimiter(stamps),
            isOnline = { online },
            enabled = { enabled },
            nowMillis = { now },
        )

    @Test
    fun `a show is reported once a day, and the API's new address comes back`() = runTest {
        assertEquals(NEW, reporter.report("920666", "http_410", OLD))
        assertNull(reporter.report("920666", "http_410", OLD), "the same day")
        now += DAY
        assertEquals(NEW, reporter.report("920666", "timeout", OLD))

        assertEquals(listOf(V2FeedReportRequest(920666, "http_410"), V2FeedReportRequest(920666, "timeout")), sent)
    }

    @Test
    fun `at most twenty reports a day in all`() = runTest {
        repeat(25) { reporter.report((it + 1).toString(), "dns", OLD) }

        assertEquals(FeedReportLimiter.DAILY_CAP, sent.size)
        now += DAY
        reporter.report("25", "dns", OLD)
        assertEquals(FeedReportLimiter.DAILY_CAP + 1, sent.size, "a new day")
    }

    @Test
    fun `nothing is sent offline, for rss shows, without a read, or with the switch off`() = runTest {
        online = false
        assertNull(reporter.report("1", "network", OLD))
        online = true
        assertNull(reporter.report("rss:abc", "http_404", OLD))
        assertNull(reporter.report("2", null, OLD))
        assertNull(reporter.report("3", "not-a-status", OLD))
        enabled = false
        assertNull(reporter.report("4", "http_404", OLD))

        assertTrue(sent.isEmpty())
        assertTrue(stamps.all().isEmpty(), "an unsent report doesn't use up the show's day")
    }

    @Test
    fun `the same address, an http-only one, no answer or a failure offers nothing`() = runTest {
        answer = V2FeedReportResponse(queued = true, fetchUrl = OLD, fetchVia = "direct")
        assertNull(reporter.report("1", "http_404", OLD))
        answer = V2FeedReportResponse(queued = true, fetchUrl = "http://new.example/feed.xml", fetchVia = "proxy", transportUrl = "https://api.example/v2/feed/2")
        assertNull(reporter.report("2", "http_404", OLD))
        answer = null
        assertNull(reporter.report("3", "http_404", OLD))
        val failing = FeedReporter(send = { error("offline after all") }, limiter = FeedReportLimiter(stamps), isOnline = { true }, enabled = { true }, nowMillis = { now })
        assertNull(failing.report("4", "http_404", OLD))
    }

    @Test
    fun `the show page reads an http-only answer through the transport`() = runTest {
        answer = V2FeedReportResponse(queued = true, fetchUrl = "http://new.example/feed.xml", fetchVia = "proxy", transportUrl = "https://api.example/v2/feed/2")
        assertEquals(V2FeedReadTarget.ViaApi(2), reporter.reportTarget("2", "http_404", OLD))
        answer = V2FeedReportResponse(queued = true, fetchUrl = NEW, fetchVia = "direct")
        assertEquals(V2FeedReadTarget.Direct(NEW), reporter.reportTarget("3", "http_404", OLD))
    }

    @Test
    fun `limiters sharing one store never pass the caps together`() {
        val first = FeedReportLimiter(stamps)
        val second = FeedReportLimiter(stamps)
        val claims = java.util.concurrent.atomic.AtomicInteger()
        val threads =
            (0 until 8).map { worker ->
                Thread {
                    repeat(10) { index -> if ((if (worker % 2 == 0) first else second).tryClaim("${worker * 10 + index}", now)) claims.incrementAndGet() }
                }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(FeedReportLimiter.DAILY_CAP, claims.get())
    }

    @Test
    fun `old report times are dropped as new ones are written`() {
        val limiter = FeedReportLimiter(stamps)
        assertTrue(limiter.tryClaim("1", now))
        now += 2 * DAY
        assertTrue(limiter.tryClaim("2", now))

        assertEquals(setOf("2"), stamps.all().keys)
        assertFalse(limiter.tryClaim("2", now + 1))
    }

    private companion object {
        const val DAY = FeedReportLimiter.PER_SHOW_INTERVAL_MS
        const val OLD = "https://old.example/feed.xml"
        const val NEW = "https://new.example/feed.xml"
    }
}
