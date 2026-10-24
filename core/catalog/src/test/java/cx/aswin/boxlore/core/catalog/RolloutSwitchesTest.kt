package cx.aswin.boxlore.core.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolloutSwitchesTest {
    @Test
    fun withoutTheAppsWiringFeedFirstIsOffAndEveryJobRuns() {
        val switches = RolloutSwitches()

        assertFalse(switches.feedFirst())
        assertTrue(switches.upgradeRepair())
        assertTrue(switches.addressCheck())
        assertTrue(switches.v2Discovery())
    }

    @Test
    fun eachSwitchIsReadThroughItsOwnLambda() {
        var feedFirst = false
        var discovery = true
        val switches = RolloutSwitches(feedFirst = { feedFirst }, v2Discovery = { discovery })

        feedFirst = true
        discovery = false

        assertTrue(switches.feedFirst())
        assertFalse(switches.v2Discovery())
        assertTrue(switches.upgradeRepair())
        assertTrue(switches.addressCheck())
    }
}
