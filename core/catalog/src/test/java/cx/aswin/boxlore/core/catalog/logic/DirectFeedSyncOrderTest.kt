package cx.aswin.boxlore.core.catalog.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DirectFeedSyncOrderTest {
    @Test
    fun `prioritize leaves order when preferred is missing`() {
        val ids = listOf("a", "b", "c")
        assertEquals(ids, DirectFeedSyncOrder.prioritize(ids, preferredPodcastId = null))
        assertEquals(ids, DirectFeedSyncOrder.prioritize(ids, preferredPodcastId = "z"))
    }

    @Test
    fun `prioritize moves preferred id first`() {
        assertEquals(
            listOf("b", "a", "c"),
            DirectFeedSyncOrder.prioritize(listOf("a", "b", "c"), preferredPodcastId = "b"),
        )
    }
}
