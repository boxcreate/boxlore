package cx.aswin.boxlore.updates

import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response

/** Cancellation closes the socket even when a response body is blocked waiting for bytes. */
internal suspend fun <T> Call.withUpdateResponse(body: suspend (Response) -> T): T = coroutineScope {
    val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            this@withUpdateResponse.cancel()
        }
    }
    try {
        execute().use { body(it) }
    } catch (error: IOException) {
        // OkHttp reports a cancelled socket as IO failure. Keep Pause a cancellation,
        // rather than reporting an error or cancelling the application scope.
        currentCoroutineContext().ensureActive()
        throw error
    } finally {
        cancellation.cancel()
    }
}
