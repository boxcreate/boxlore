package cx.aswin.boxlore.core.designsystem.components

import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkLoadPolicyTest {
    private val optimize: (String, Int) -> String = { url, width -> "proxy:$width:$url" }

    @Test
    fun `failed episode routes fall through to show without duplicate requests`() {
        val policy = ArtworkLoadPolicy()
        val queue = ArtworkAttempts(listOf("episode", "show", "show"), 200, policy, optimize)
        val requests = mutableListOf<String>()
        repeat(3) {
            val attempt = requireNotNull(queue.next())
            requests += attempt.url
            if (it < 2) queue.failed(attempt, IllegalArgumentException("not an image"))
        }
        assertEquals(listOf("proxy:200:episode", "episode", "proxy:200:show"), requests)
    }

    @Test
    fun `only one recovery attempt is allowed after transient failures`() {
        val queue = ArtworkAttempts(listOf("episode", "show"), 200, ArtworkLoadPolicy(), optimize)
        val attempts = mutableListOf<ArtworkAttempt>()
        while (true) {
            val attempt = queue.next() ?: break
            attempts += attempt
            queue.failed(attempt, IOException("offline"))
        }
        assertEquals(5, attempts.size)
        assertEquals(1, attempts.count { it.recovery })
        assertEquals(attempts.first().url, attempts.last().url)
        assertNull(queue.next())
    }

    @Test
    fun `deterministic errors and identical optimized URLs are never retried`() {
        val queue = ArtworkAttempts(listOf("local", "local"), 100, ArtworkLoadPolicy()) { url, _ -> url }
        val attempt = requireNotNull(queue.next())
        queue.failed(attempt, IllegalArgumentException("decode failed"))
        assertNull(queue.next())
    }

    @Test
    fun `cooldown expires and size specific optimized failure does not poison another size`() {
        var now = 0L
        val policy = ArtworkLoadPolicy(clock = { now })
        val first = requireNotNull(ArtworkAttempts(listOf("episode"), 200, policy, optimize).next())
        policy.recordFailure(first.url, transient = true)
        assertEquals("episode", ArtworkAttempts(listOf("episode"), 200, policy, optimize).next()?.url)
        assertEquals("proxy:400:episode", ArtworkAttempts(listOf("episode"), 400, policy, optimize).next()?.url)
        now = ArtworkLoadPolicy.TRANSIENT_COOLDOWN
        assertEquals(first.url, ArtworkAttempts(listOf("episode"), 200, policy, optimize).next()?.url)
    }

    @Test
    fun `successful raw transport is shared across pages and success clears failure`() {
        val policy = ArtworkLoadPolicy()
        policy.recordFailure("episode", transient = true)
        policy.recordSuccess("episode")
        assertTrue(policy.available("episode"))
        assertEquals("episode", ArtworkAttempts(listOf("episode"), 200, policy, optimize).next()?.url)
        assertEquals(1L, policy.revision.value)
        policy.recordSuccess("episode")
        assertEquals(1L, policy.revision.value)
    }

    @Test
    fun `cancellation is not cached and reconnect clears only transient errors`() {
        val policy = ArtworkLoadPolicy()
        val queue = ArtworkAttempts(listOf("episode"), 200, policy, optimize)
        val attempt = requireNotNull(queue.next())
        queue.failed(attempt, CancellationException())
        assertTrue(policy.available(attempt.url))
        policy.recordFailure("transient", true)
        policy.recordFailure("missing", false)
        policy.networkRestored()
        assertTrue(policy.available("transient"))
        assertFalse(policy.available("missing"))
    }

    @Test
    fun `hints are bounded and a later load retries an evicted failure`() {
        val policy = ArtworkLoadPolicy(capacity = 2)
        repeat(3) { policy.recordFailure("failed:$it", true) }
        assertTrue(policy.available("failed:0"))
        assertFalse(policy.available("failed:2"))
        repeat(3) { policy.recordSuccess("loaded:$it") }
        assertFalse(policy.succeeded("loaded:0"))
        assertTrue(policy.succeeded("loaded:2"))
    }

    @Test
    fun `a cover chip thumbnail never becomes a larger card's first choice`() {
        val policy = ArtworkLoadPolicy()
        policy.recordSuccess("proxy:320:show", "show", width = 320)
        assertEquals("proxy:1080:show", ArtworkAttempts(listOf("show"), 1080, policy, optimize).next()?.url)
        assertEquals("proxy:320:show", ArtworkAttempts(listOf("show"), 320, policy, optimize).next()?.url)
        assertEquals("proxy:320:show", ArtworkAttempts(listOf("show"), 200, policy, optimize).next()?.url)

        // A larger copy still serves smaller tiles, and the original serves every size.
        policy.recordSuccess("proxy:1080:show", "show", width = 1080)
        assertEquals("proxy:1080:show", ArtworkAttempts(listOf("show"), 320, policy, optimize).next()?.url)
        policy.recordSuccess("show", "show", width = 320)
        assertEquals("show", ArtworkAttempts(listOf("show"), 1080, policy, optimize).next()?.url)
    }

    @Test
    fun `a smaller copy is only tried after the larger card's own routes fail`() {
        val policy = ArtworkLoadPolicy()
        policy.recordSuccess("proxy:320:show", "show", width = 320)
        val queue = ArtworkAttempts(listOf("show"), 1080, policy, optimize)
        val requests = mutableListOf<String>()
        while (true) {
            val attempt = queue.next() ?: break
            requests += attempt.url
            queue.failed(attempt, IllegalArgumentException("not an image"))
        }
        assertEquals(listOf("proxy:1080:show", "show", "proxy:320:show"), requests)
    }

    @Test
    fun `successful detail size recovers a failed home size through the cached route`() {
        val policy = ArtworkLoadPolicy()
        policy.recordFailure("proxy:200:episode", true)
        policy.recordFailure("episode", true)
        assertNull(ArtworkAttempts(listOf("episode"), 200, policy, optimize).next())
        policy.recordSuccess("proxy:800:episode", "episode")
        assertEquals("proxy:800:episode", ArtworkAttempts(listOf("episode"), 200, policy, optimize).next()?.url)
        assertEquals("proxy:800:episode", policy.preferredRoute("episode"))
    }

    @Test
    fun `missing forbidden and rate limited responses are not immediately retried`() {
        for (code in listOf(403, 404, 429)) assertFalse(isTransientArtworkFailure(httpError(code)))
        for (code in listOf(408, 500, 503)) assertTrue(isTransientArtworkFailure(httpError(code)))
    }

    private fun httpError(code: Int): coil.network.HttpException = coil.network.HttpException(
        okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://art/episode").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("Response").build(),
    )
}
