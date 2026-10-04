package cx.aswin.boxlore.feature.home.logic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeShimmerVisibilityTest {
    @Test
    fun `initial empty layout can animate its first loading frame`() {
        assertTrue(active(emptySet(), initial = true))
        assertFalse(active(emptySet()))
    }

    @Test
    fun `loaded viewport pauses clock even when remote sections are loading`() {
        assertFalse(active(setOf("hero", "your_shows"), discovery = true, editorial = true, recommendations = true))
    }

    @Test
    fun `visible loading sections restart the shared clock`() {
        assertTrue(active(setOf("hero"), initial = true))
        assertTrue(active(setOf("your_shows"), selectedShow = true))
        assertTrue(active(setOf("discover_grid_2"), discovery = true))
        assertTrue(active(setOf("editorial_evening"), editorial = true))
        assertTrue(active(setOf("for_you_hero"), recommendations = true))
        assertTrue(active(setOf("for_you_body_1"), recommendations = true))
    }

    @Test
    fun `headers and unloaded offscreen sections do not animate`() {
        assertFalse(active(setOf("discover_header", "curated_header"), discovery = true, recommendations = true))
        assertFalse(active(setOf("editorial_evening"), discovery = true))
        assertFalse(active(setOf("discover_grid_1")))
    }

    private fun active(
        keys: Set<String>,
        initial: Boolean = false,
        discovery: Boolean = false,
        editorial: Boolean = false,
        recommendations: Boolean = false,
        selectedShow: Boolean = false,
    ) = shouldAnimateHomeShimmer(keys, initial, discovery, editorial, recommendations, selectedShow)
}
