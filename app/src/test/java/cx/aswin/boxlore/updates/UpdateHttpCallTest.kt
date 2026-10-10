package cx.aswin.boxlore.updates

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateHttpCallTest {
    @Test fun `pausing a blocked transfer cancels its socket without waiting for timeout`() = runBlocking {
        val call = BlockingCall()
        val transfer = launch(Dispatchers.IO) { call.withUpdateResponse { } }
        try {
            assertTrue(call.started.await(2, TimeUnit.SECONDS))
            withTimeout(2_000) { transfer.cancelAndJoin() }
            assertTrue(call.isCanceled())
        } finally {
            call.cancel()
            transfer.cancelAndJoin()
        }
    }

    private class BlockingCall : Call {
        val started = CountDownLatch(1)
        private val closed = CountDownLatch(1)
        override fun request(): Request = Request.Builder().url("https://example.test/update").build()
        override fun execute(): Response {
            started.countDown()
            check(closed.await(5, TimeUnit.SECONDS)) { "Socket was not cancelled" }
            throw IOException("Socket closed")
        }
        override fun cancel() {
            closed.countDown()
        }
        override fun isExecuted(): Boolean = started.count == 0L
        override fun isCanceled(): Boolean = closed.count == 0L
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = BlockingCall()
        override fun enqueue(responseCallback: Callback) = error("Synchronous fixture only")
    }
}
