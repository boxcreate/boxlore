package cx.aswin.boxlore.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabletQueueIndexTest {
    @Test
    fun `continuation header never changes the queue index delivered by reordering`() {
        assertEquals(0, queueReorderIndex(1, 1, 4))
        assertEquals(3, queueReorderIndex(4, 1, 4))
        assertEquals(0, queueReorderIndex(0, 1, 4))
        assertEquals(2, queueReorderIndex(2, 0, 4))
        assertEquals(0, queueReorderIndex(0, 1, 0))
    }

    @Test
    fun `tablet save row above the queue keeps reorder indices on the dragged episode`() {
        val headerItems = queueHeaderItemCount(
            scrollContinuation = true,
            showContinuation = false,
            showHeader = false,
            canSaveAsPlaylist = true,
        )
        assertEquals(1, headerItems)
        // Queue [A,B,C,D] at lazy indices 1..4: dragging B (2) onto C (3) moves queue 1 -> 2.
        assertEquals(1, queueReorderIndex(2, headerItems, 4))
        assertEquals(2, queueReorderIndex(3, headerItems, 4))
    }

    @Test
    fun `save row and continuation banner both shift the tablet queue`() {
        val headerItems = queueHeaderItemCount(
            scrollContinuation = true,
            showContinuation = true,
            showHeader = false,
            canSaveAsPlaylist = true,
        )
        assertEquals(2, headerItems)
        assertEquals(0, queueReorderIndex(2, headerItems, 4))
        assertEquals(3, queueReorderIndex(5, headerItems, 4))
    }

    @Test
    fun `phone sheet keeps save in the header so the list has no extra row`() {
        assertEquals(0, queueHeaderItemCount(scrollContinuation = false, showContinuation = true, showHeader = true, canSaveAsPlaylist = true))
        assertEquals(0, queueHeaderItemCount(scrollContinuation = true, showContinuation = false, showHeader = false, canSaveAsPlaylist = false))
    }
}
