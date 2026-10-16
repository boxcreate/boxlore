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
}
