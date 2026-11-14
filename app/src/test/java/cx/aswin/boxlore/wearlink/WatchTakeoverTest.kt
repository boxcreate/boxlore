package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.ui.WatchTakeover
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTakeoverTest {
    @Test
    fun `the watch offer has its own card only when the sleep timer prompt is not up`() {
        assertTrue(WatchTakeover.showsAlone(offered = true, sleepPromptVisible = false))
        // Late at night the sleep timer prompt carries the offer, so there is one card, not two.
        assertFalse(WatchTakeover.showsAlone(offered = true, sleepPromptVisible = true))
        assertFalse(WatchTakeover.showsAlone(offered = false, sleepPromptVisible = false))
        assertFalse(WatchTakeover.showsAlone(offered = false, sleepPromptVisible = true))
    }
}
