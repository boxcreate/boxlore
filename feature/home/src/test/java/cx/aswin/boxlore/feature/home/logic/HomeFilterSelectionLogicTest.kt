package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeFilterSelectionLogicTest {
    @Test
    fun `empty subs clears existing selection`() {
        assertEquals(
            FilterSelectionAction.Clear,
            HomeFilterSelectionLogic.decide(
                currentSelectedId = "a",
                subs = emptyList(),
                filterSelectionIsAuto = false,
            ),
        )
        assertEquals(
            FilterSelectionAction.Keep,
            HomeFilterSelectionLogic.decide(
                currentSelectedId = null,
                subs = emptyList(),
                filterSelectionIsAuto = false,
            ),
        )
    }

    @Test
    fun `single sub auto selects when not already selected`() {
        val only = TestFixtures.podcast(id = "only")
        val action =
            HomeFilterSelectionLogic.decide(
                currentSelectedId = null,
                subs = listOf(only),
                filterSelectionIsAuto = false,
            )
        assertTrue(action is FilterSelectionAction.AutoSelect)
        assertEquals("only", (action as FilterSelectionAction.AutoSelect).podcast.id)
    }

    @Test
    fun `multi sub clears invalid or auto selection only`() {
        val subs = listOf(TestFixtures.podcast(id = "a"), TestFixtures.podcast(id = "b"))
        assertEquals(
            FilterSelectionAction.Clear,
            HomeFilterSelectionLogic.decide("gone", subs, filterSelectionIsAuto = false),
        )
        assertEquals(
            FilterSelectionAction.Clear,
            HomeFilterSelectionLogic.decide("a", subs, filterSelectionIsAuto = true),
        )
        assertEquals(
            FilterSelectionAction.Keep,
            HomeFilterSelectionLogic.decide("a", subs, filterSelectionIsAuto = false),
        )
    }
}
