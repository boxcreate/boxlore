package cx.aswin.boxlore.feature.home.logic

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeForegroundSyncLogicTest {
    @Test
    fun `preferFeedPodcast follows selection and clear`() {
        assertEquals("pod-1", HomeForegroundSyncLogic.preferredFeedPodcastId("pod-1"))
        assertNull(HomeForegroundSyncLogic.preferredFeedPodcastId(null))
    }

    @Test
    fun `matching refresh reloads the open chip`() {
        assertTrue(HomeForegroundSyncLogic.shouldReloadSelectedChip("pod-1", "pod-1"))
        assertFalse(HomeForegroundSyncLogic.shouldReloadSelectedChip("pod-2", "pod-1"))
        assertFalse(HomeForegroundSyncLogic.shouldReloadSelectedChip("pod-1", null))
    }

    @Test
    fun `a chip read that finishes after a move to another chip doesn't reload the open one`() {
        // Before, every finished chip read restarted the open chip's episode load, even for another show.
        val selected = MutableStateFlow<String?>("serial")
        val version = MutableStateFlow(4L)

        HomeForegroundSyncLogic.reloadIfStillOpen("daily", selected, version)

        assertEquals(4L, version.value)
    }

    @Test
    fun `a chip read reloads the chip once when its show is still open`() {
        val selected = MutableStateFlow<String?>("daily")
        val version = MutableStateFlow(4L)

        HomeForegroundSyncLogic.reloadIfStillOpen("daily", selected, version)

        assertEquals(5L, version.value)
    }

    @Test
    fun `a chip read that finishes after the chip was cleared reloads nothing`() {
        val selected = MutableStateFlow<String?>(null)
        val version = MutableStateFlow(4L)

        HomeForegroundSyncLogic.reloadIfStillOpen("daily", selected, version)

        assertEquals(4L, version.value)
    }
}
