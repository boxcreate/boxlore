package cx.aswin.boxlore.core.wearlink

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearLinkStatusTest {
    // Regression: Send to watch stayed on the episode page with no watch in reach, where a tap could only fail.
    @Test
    fun `the watch is in reach only when one has linked and can be reached now`() {
        assertTrue(WearLinkStatus(watchKnown = true, reachable = true).inReach)
        // Linked once, away now: nothing to offer.
        assertFalse(WearLinkStatus(watchKnown = true, reachable = false).inReach)
        assertFalse(WearLinkStatus(watchKnown = false, reachable = true).inReach)
        assertFalse(WearLinkStatus().inReach)
    }
}
