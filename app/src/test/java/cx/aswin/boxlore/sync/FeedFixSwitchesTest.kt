package cx.aswin.boxlore.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFixSwitchesTest {
    @Test
    fun aFixRunsUnlessItsOffFlagIsOn() {
        val asked = mutableListOf<String>()
        val recordAsked: (String) -> Boolean = { flag ->
            asked += flag
            false
        }
        assertTrue(FeedFixSwitches.isOn(FeedFixSwitches.MATCH_BASELINE_OFF, recordAsked))
        assertFalse(FeedFixSwitches.isOn(FeedFixSwitches.MATCH_BASELINE_OFF) { true })
        assertEquals(listOf("feed-fix-match-baseline-off"), asked)
    }

    @Test
    fun noAnswerFromPostHogKeepsTheFixOn() {
        assertTrue(FeedFixSwitches.isOn(FeedFixSwitches.MATCH_BASELINE_OFF) { error("PostHog not started") })
    }

    @Test
    fun v2BuildKillSwitchesFailOpenAndUseTheirOwnFlags() {
        val flags =
            listOf(
                FeedFixSwitches.UPGRADE_REPAIR_OFF to "upgrade-repair-off",
                FeedFixSwitches.ADDRESS_CHECK_OFF to "address-check-off",
                FeedFixSwitches.V2_DISCOVERY_OFF to "v2-discovery-off",
            )
        flags.forEach { (flag, key) ->
            val asked = mutableListOf<String>()
            val recordAsked: (String) -> Boolean = { asking ->
                asked += asking
                false
            }
            assertTrue(FeedFixSwitches.isOn(flag, recordAsked))
            assertTrue(FeedFixSwitches.isOn(flag) { error("PostHog not started") })
            assertFalse(FeedFixSwitches.isOn(flag) { it == key })
            assertEquals(listOf(key), asked)
        }
    }
}
