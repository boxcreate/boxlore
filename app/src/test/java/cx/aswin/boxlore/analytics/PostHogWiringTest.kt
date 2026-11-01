package cx.aswin.boxlore.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PostHogWiringTest {
    @Test
    fun releaseBuildsAreTaggedAsProduction() {
        assertEquals(
            mapOf("is_internal" to false, "app_environment" to "production"),
            PostHogWiring.environment(debug = false, isolatedTests = false),
        )
    }

    @Test
    fun debugBuildsAreInternal() {
        assertEquals(
            mapOf("is_internal" to true, "app_environment" to "debug"),
            PostHogWiring.environment(debug = true, isolatedTests = false),
        )
    }

    @Test
    fun isolatedTestBuildsAreInternalUpdateTests() {
        val expected = mapOf("is_internal" to true, "app_environment" to "update_test")
        assertEquals(expected, PostHogWiring.environment(debug = false, isolatedTests = true))
        assertEquals(expected, PostHogWiring.environment(debug = true, isolatedTests = true))
    }

    @Test
    fun flagsLoadedRunsEveryStepInOrder() {
        val calls = mutableListOf<String>()
        val callback = PostHogWiring.onFlagsLoaded({ calls += "rollout" }, { calls += "analytics" })

        callback.loaded()
        callback.loaded()

        assertEquals(listOf("rollout", "analytics", "rollout", "analytics"), calls)
    }

    @Test
    fun aFailingStepDoesNotSkipTheNextAndIsRethrownAfter() {
        val calls = mutableListOf<String>()
        val first = IllegalStateException("rollout")
        val second = IllegalArgumentException("analytics")
        val callback =
            PostHogWiring.onFlagsLoaded(
                {
                    calls += "rollout"
                    throw first
                },
                {
                    calls += "analytics"
                    throw second
                },
                { calls += "last" },
            )

        val thrown = assertThrows(IllegalStateException::class.java) { callback.loaded() }

        assertEquals(listOf("rollout", "analytics", "last"), calls)
        assertSame(first, thrown)
        assertEquals(listOf<Throwable>(second), thrown.suppressed.toList())
    }
}
