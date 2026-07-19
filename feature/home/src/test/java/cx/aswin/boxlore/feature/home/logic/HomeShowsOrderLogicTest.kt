package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeShowsOrderLogicTest {
    @Test
    fun `first pass sorts by score then title`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "b", title = "B"),
                TestFixtures.podcast(id = "a", title = "A"),
                TestFixtures.podcast(id = "c", title = "C"),
            )
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = null,
                subs = subs,
                scores = mapOf("a" to 1.0, "b" to 5.0, "c" to 5.0),
            )

        assertEquals(listOf("b", "c", "a"), order)
    }

    @Test
    fun `later pass keeps order drops removed and prepends new`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "new", title = "New"),
                TestFixtures.podcast(id = "keep", title = "Keep"),
            )
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = listOf("gone", "keep"),
                subs = subs,
                scores = emptyMap(),
            )

        assertEquals(listOf("new", "keep"), order)
    }

    @Test
    fun `ordered subs maps ids to podcasts`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "a"),
                TestFixtures.podcast(id = "b"),
            )
        assertEquals(listOf("b", "a"), HomeShowsOrderLogic.orderedSubs(listOf("b", "a", "missing"), subs).map { it.id })
    }

    @Test
    fun `mixtape cache invalidates only when signature changes after first build`() {
        assertFalse(HomeShowsOrderLogic.shouldInvalidateMixtapeCache(null, setOf("a")))
        assertFalse(HomeShowsOrderLogic.shouldInvalidateMixtapeCache(setOf("a"), setOf("a")))
        assertTrue(HomeShowsOrderLogic.shouldInvalidateMixtapeCache(setOf("a"), setOf("a", "b")))
    }
}
