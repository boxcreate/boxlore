package cx.aswin.boxlore.feature.home.logic

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
}
