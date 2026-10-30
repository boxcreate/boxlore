package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.feature.info.logic.HideCompletedFill.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideCompletedFillTest {
    @Test
    fun `every loaded episode finished with more to load keeps loading instead of a blank page`() {
        // The 0.0.29 report: oldest first loads 50, all finished, hide completed shows nothing.
        assertTrue(HideCompletedFill.loadsMore(hideCompleted = true, searching = false, visibleCount = 0, hasMore = true, isLoadingMore = false))
        assertEquals(State.FINDING, HideCompletedFill.state(true, false, visibleCount = 0, loadedCount = 50, hasMore = true, isLoadingMore = false))
    }

    @Test
    fun `a few unfinished ones still load more, enough of them stop`() {
        assertTrue(HideCompletedFill.loadsMore(true, false, visibleCount = 3, hasMore = true, isLoadingMore = false))
        assertFalse(HideCompletedFill.loadsMore(true, false, visibleCount = HideCompletedFill.MIN_VISIBLE, hasMore = true, isLoadingMore = false))
        assertEquals(State.NONE, HideCompletedFill.state(true, false, HideCompletedFill.MIN_VISIBLE, 50, hasMore = true, isLoadingMore = false))
    }

    @Test
    fun `one page at a time, and never when hiding is off or a search shows`() {
        assertFalse(HideCompletedFill.loadsMore(true, false, 0, hasMore = true, isLoadingMore = true))
        assertEquals(State.FINDING, HideCompletedFill.state(true, false, 0, 50, hasMore = false, isLoadingMore = true))
        assertFalse(HideCompletedFill.loadsMore(hideCompleted = false, searching = false, visibleCount = 0, hasMore = true, isLoadingMore = false))
        assertFalse(HideCompletedFill.loadsMore(hideCompleted = true, searching = true, visibleCount = 0, hasMore = true, isLoadingMore = false))
        assertEquals(State.NONE, HideCompletedFill.state(false, false, 0, 50, hasMore = true, isLoadingMore = false))
        assertEquals(State.NONE, HideCompletedFill.state(true, true, 0, 50, hasMore = true, isLoadingMore = false))
    }

    @Test
    fun `the show running out says caught up, but an empty show or a few left does not`() {
        assertEquals(State.CAUGHT_UP, HideCompletedFill.state(true, false, 0, loadedCount = 66, hasMore = false, isLoadingMore = false))
        assertEquals(State.NONE, HideCompletedFill.state(true, false, 0, loadedCount = 0, hasMore = false, isLoadingMore = false))
        assertEquals(State.NONE, HideCompletedFill.state(true, false, 3, loadedCount = 66, hasMore = false, isLoadingMore = false))
        assertFalse(HideCompletedFill.loadsMore(true, false, 0, hasMore = false, isLoadingMore = false))
    }
}
