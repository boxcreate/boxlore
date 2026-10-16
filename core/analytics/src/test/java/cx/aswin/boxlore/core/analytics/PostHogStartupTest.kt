package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * PostHog setup moved off the main thread (startup ANR). Calls made before it finishes must be
 * kept and replayed in order, not dropped.
 */
class PostHogStartupTest {
    @BeforeEach
    fun setUp() = PostHogStartup.resetForTest()

    @AfterEach
    fun tearDown() = PostHogStartup.resetForTest()

    @Test
    fun `calls before setup replay in order once ready`() {
        val calls = mutableListOf<String>()
        PostHogStartup.runWhenReady { calls += "register" }
        PostHogStartup.runWhenReady { calls += "capture" }
        assertTrue(calls.isEmpty())
        assertFalse(PostHogStartup.isReady)

        PostHogStartup.markReady()

        assertEquals(listOf("register", "capture"), calls)
        assertTrue(PostHogStartup.isReady)
    }

    @Test
    fun `calls after setup run immediately`() {
        PostHogStartup.markReady()
        val calls = mutableListOf<String>()
        PostHogStartup.runWhenReady { calls += "capture" }
        assertEquals(listOf("capture"), calls)
    }

    @Test
    fun `queue is bounded if setup never finishes`() {
        var replayed = 0
        repeat(PostHogStartup.MAX_PENDING + 25) { PostHogStartup.runWhenReady { replayed++ } }
        PostHogStartup.markReady()
        assertEquals(PostHogStartup.MAX_PENDING, replayed)
    }

    @Test
    fun `distinct id is blank before setup so callers use their fallback`() {
        assertEquals("", AnalyticsHelper.getDistinctId())
    }

    @Test
    fun `successful setup replays queued calls and marks ready`() {
        val calls = mutableListOf<String>()
        PostHogStartup.runWhenReady { calls += "register" }
        PostHogStartup.finishSetup(setup = { calls += "setup" }, onFailure = {})
        assertEquals(listOf("setup", "register"), calls)
        assertTrue(PostHogStartup.isReady)
        assertFalse(PostHogStartup.hasFailed)
    }

    @Test
    fun `failed setup never counts as ready, drops the queue and ignores later calls`() {
        val calls = mutableListOf<String>()
        var failure: Throwable? = null
        PostHogStartup.runWhenReady { calls += "queued" }
        // An Error, not just an Exception: a missing class at setup must not leave calls waiting forever.
        PostHogStartup.finishSetup(setup = { throw LinkageError("no PostHog") }, onFailure = { failure = it })
        assertFalse(PostHogStartup.isReady)
        assertTrue(PostHogStartup.hasFailed)
        assertTrue(failure is LinkageError)
        PostHogStartup.runWhenReady { calls += "later" }
        assertTrue(calls.isEmpty(), "Got $calls")
    }
}
