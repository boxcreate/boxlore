package cx.aswin.boxlore.core.catalog

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads small text files (chapters and transcripts from publishers' hosts, and the Brief's chapters
 * links on the boxlore API) with a time limit, so a stalled connection gives up instead of hanging.
 * A plain client: none of these links need the boxlore API client's headers or session, so none are sent.
 *
 * Each read runs as a blocking call on [Dispatchers.IO], not through OkHttp's async queue, so many
 * reads to one host (a bulk download) all start at once instead of waiting behind its per-host limit,
 * and a caller's time limit counts only the read itself.
 */
object RemoteText {
    /** Connect, read and whole-call limit for one read. */
    const val TIMEOUT_MS = 15_000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /** The body at [url]. Throws [IOException] on a failed or non-2xx answer; cancelling the caller cancels the call. */
    suspend fun read(url: String): String = coroutineScope {
        val call = client.newCall(Request.Builder().url(url).build())
        // The read hands back its failure rather than throwing it, so a call ended by cancelling the
        // caller cannot replace that cancellation with its own "Canceled" error.
        val answer = async(Dispatchers.IO) {
            try {
                Result.success(runInterruptible { execute(call, url) })
            } catch (failed: IOException) {
                Result.failure(failed)
            }
        }
        val result = try {
            answer.await()
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        }
        result.getOrThrow()
    }

    private fun execute(call: Call, url: String): String = call.execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        response.body?.string() ?: throw IOException("Empty body for $url")
    }
}
