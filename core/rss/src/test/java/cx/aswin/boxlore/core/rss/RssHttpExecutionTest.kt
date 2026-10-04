package cx.aswin.boxlore.core.rss

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RssHttpExecutionTest {
    @Test fun successConsumesAndClosesTheResponse() = runTest {
        val call = FakeCall()
        var closed = false
        val pending = async { executeRssCall(call) { it.body!!.string() } }
        runCurrent()
        call.callback.await().onResponse(call, response(call) { closed = true })
        assertEquals("feed", pending.await())
        assertTrue(closed)
    }

    @Test fun cancellationBeforeHeadersCancelsNetworkAndClosesAnyLateResponse() = runTest {
        val call = FakeCall()
        var read = false
        var closed = false
        val pending = launch { executeRssCall(call) { read = true } }
        runCurrent()
        pending.cancelAndJoin()
        assertTrue(call.cancelled)
        call.callback.await().onResponse(call, response(call) { closed = true })
        assertTrue(closed)
        assertEquals(false, read)
    }

    @Test fun cancellationStaysAttachedWhileBodyIsBeingRead() = runTest {
        val released = CountDownLatch(1)
        val reading = CompletableDeferred<Unit>()
        val call = FakeCall { released.countDown() }
        var closed = false
        val pending = launch {
            executeRssCall(call) {
                reading.complete(Unit)
                assertTrue(released.await(5, TimeUnit.SECONDS))
            }
        }
        runCurrent()
        val callback = call.callback.await()
        val network = launch(Dispatchers.IO) { callback.onResponse(call, response(call) { closed = true }) }
        reading.await()
        pending.cancelAndJoin()
        network.join()
        assertTrue(call.cancelled)
        assertTrue(closed)
    }

    private fun response(call: FakeCall, closed: () -> Unit): Response {
        val source = object : ForwardingSource(Buffer().writeUtf8("feed")) {
            override fun close() {
                closed()
                super.close()
            }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 4
            override fun source(): BufferedSource = source
        }
        return Response.Builder().request(call.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
    }

    private class FakeCall(private val onCancel: () -> Unit = {}) : Call {
        val callback = CompletableDeferred<Callback>()
        var cancelled = false
        override fun request(): Request = Request.Builder().url("https://feeds.example/show.xml").build()
        override fun execute(): Response = error("Use cancellable enqueue")
        override fun enqueue(responseCallback: Callback) {
            callback.complete(responseCallback)
        }
        override fun cancel() {
            cancelled = true
            onCancel()
        }
        override fun isExecuted(): Boolean = callback.isCompleted
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = FakeCall(onCancel)
    }
}
