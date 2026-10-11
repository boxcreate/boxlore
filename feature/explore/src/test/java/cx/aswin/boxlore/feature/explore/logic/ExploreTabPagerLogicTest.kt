package cx.aswin.boxlore.feature.explore.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExploreTabPagerLogicTest {
    @Test fun `visual page order keeps existing For You and Top IDs`() {
        assertEquals(0, ExploreTabPagerLogic.pageForTab(1))
        assertEquals(1, ExploreTabPagerLogic.pageForTab(0))
        assertEquals(1, ExploreTabPagerLogic.tabForPage(0))
        assertEquals(0, ExploreTabPagerLogic.tabForPage(1))
    }

    @Test fun `settled gesture changes selection without repeating the current callback`() {
        assertEquals(0, ExploreTabPagerLogic.selectionAfterSettle(1, 1, true))
        assertEquals(1, ExploreTabPagerLogic.selectionAfterSettle(0, 0, true))
        assertNull(ExploreTabPagerLogic.selectionAfterSettle(0, 1, true))
        assertNull(ExploreTabPagerLogic.selectionAfterSettle(1, 0, true))
    }

    @Test fun `search and mood results cannot change browse tab on a settled gesture`() {
        assertNull(ExploreTabPagerLogic.selectionAfterSettle(1, 1, false))
        assertNull(ExploreTabPagerLogic.selectionAfterSettle(0, 0, false))
    }
}
