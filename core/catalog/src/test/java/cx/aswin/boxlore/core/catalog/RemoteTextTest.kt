package cx.aswin.boxlore.core.catalog

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Many reads to one host all run at once, so a caller's time limit counts only its own read and
 * not time spent queued behind other reads; cancelling a read ends it straight away.
 */
class RemoteTextTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun manyReadsToOneHostAllRunAtOnce() = runBlocking {
        val reads = 15
        val allArrived = CountDownLatch(reads)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                allArrived.countDown()
                // Answer only once every read is in flight; reads queued behind a per-host limit never get here.
                return if (allArrived.await(ALL_ARRIVED_WAIT_S, TimeUnit.SECONDS)) {
                    MockResponse().setBody("chapters")
                } else {
                    MockResponse().setResponseCode(503)
                }
            }
        }
        val url = server.url("/chapters.json").toString()

        val results = withTimeout(TEST_LIMIT_MS) {
            List(reads) { async { runCatching { RemoteText.read(url) }.getOrNull() } }.awaitAll()
        }

        assertEquals(List(reads) { "chapters" }, results)
    }

    @Test
    fun aCancelledReadEndsWithoutWaitingForTheAnswer() = runBlocking {
        server.enqueue(MockResponse().setBody("late").setHeadersDelay(STALLED_ANSWER_MS, TimeUnit.MILLISECONDS))
        val url = server.url("/transcript.vtt").toString()

        val startedAt = System.nanoTime()
        val result = withTimeoutOrNull(CANCEL_AFTER_MS) { RemoteText.read(url) }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertNull(result)
        assertTrue("cancelled read took $tookMs ms", tookMs < STALLED_ANSWER_MS / 2)
    }

    @Test
    fun aFailedAnswerThrowsAnIOException() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val url = server.url("/missing.json").toString()

        val failure = runCatching { RemoteText.read(url) }.exceptionOrNull()

        assertTrue("expected IOException, got $failure", failure is IOException)
    }

    private companion object {
        const val ALL_ARRIVED_WAIT_S = 5L
        const val TEST_LIMIT_MS = 30_000L
        const val STALLED_ANSWER_MS = 3_000L
        const val CANCEL_AFTER_MS = 200L
    }
}
