package cx.aswin.boxlore.core.rss

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Keep cancellation attached through response-body consumption, not only response headers. */
internal suspend fun <T> executeRssCall(call: Call, read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(RssCallCallback(continuation, read))
}

private class RssCallCallback<T>(
    private val continuation: CancellableContinuation<T>,
    private val read: (Response) -> T,
) : Callback {
    override fun onFailure(call: Call, error: IOException) {
        if (continuation.isActive) continuation.resumeWithException(error)
    }

    override fun onResponse(call: Call, response: Response) {
        if (!continuation.isActive) {
            response.close()
            return
        }
        try {
            val result = readRssResponse(response, read)
            if (continuation.isActive) continuation.resume(result)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}

private fun <T> readRssResponse(response: Response, read: (Response) -> T): T = response.use {
    require(it.request.url.isHttps) { "RSS feed redirects must stay on HTTPS" }
    read(it)
}
