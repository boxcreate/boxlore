package cx.aswin.boxlore.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFixSwitchesTest {
    @Test
    fun aFixRunsUnlessItsOffFlagIsOn() {
        val asked = mutableListOf<String>()
        assertTrue(FeedFixSwitches.isOn(FeedFixSwitches.MATCH_BASELINE_OFF) { asked += it; false })
        assertFalse(FeedFixSwitches.isOn(FeedFixSwitches.MATCH_BASELINE_OFF) { true })
        assertEquals(listOf("feed-fix-match-baseline-off"), asked)
    }

    @Test
    fun noAnswerFromPostHogKeepsTheFixOn() {
        assertTrue(FeedFixSwitches.isOn(FeedFixSwitches.MATCH_BASELINE_OFF) { error("PostHog not started") })
    }
}
