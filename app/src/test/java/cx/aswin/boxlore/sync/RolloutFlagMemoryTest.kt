package cx.aswin.boxlore.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolloutFlagMemoryTest {
    private class MemoryFlag(var value: Boolean? = null) : LastKnownFlag {
        override fun read(): Boolean? = value

        override fun write(value: Boolean) {
            this.value = value
        }
    }

    private class Fixture(feedFirstLive: Any? = null) {
        val feedFirstSaved = MemoryFlag()
        val repairSaved = MemoryFlag()
        val checkSaved = MemoryFlag()
        var exposures = 0

        // Gated readers as in production: they see no answer while PostHog is still starting.
        val feedFirst =
            FeedFirstSwitch(
                readFlag = { sendExposure ->
                    if (sendExposure) exposures++
                    feedFirstLive
                },
                lastKnown = feedFirstSaved,
            )
        val repair = SavedKillSwitch(FeedFixSwitches.UPGRADE_REPAIR_OFF, { null }, repairSaved)
        val check = SavedKillSwitch(FeedFixSwitches.ADDRESS_CHECK_OFF, { null }, checkSaved)
        val memory = RolloutFlagMemory(feedFirst, listOf(repair, check))
    }

    @Test
    fun loadedAnswersAreSavedEvenBeforePostHogCountsAsReady() {
        // Regression: the callback can run while setup is still returning (PostHogStartup.isReady
        // false); answers read there must still be saved instead of dropped.
        val fixture = Fixture()
        val answers = mapOf(FeedFirstSwitch.FLAG to true, FeedFixSwitches.UPGRADE_REPAIR_OFF to true)

        fixture.memory.onFlagsLoaded { answers[it] }

        assertEquals(true, fixture.feedFirstSaved.value)
        assertEquals(true, fixture.repairSaved.value)
        assertEquals(false, fixture.checkSaved.value)
        assertFalse(fixture.repair.isOn())
        assertTrue(fixture.check.isOn())
        assertEquals(0, fixture.exposures)
    }

    @Test
    fun loadedAnswersNeverChangeThisProcessFeedFirstValue() {
        val fixture = Fixture(feedFirstLive = true)
        assertTrue(fixture.feedFirst.isOn())

        fixture.memory.onFlagsLoaded { flag -> if (flag == FeedFirstSwitch.FLAG) false else null }

        assertTrue(fixture.feedFirst.isOn())
        assertEquals(false, fixture.feedFirstSaved.value)
        assertEquals(1, fixture.exposures)
    }

    @Test
    fun oneFailingReadKeepsTheOtherAnswers() {
        val fixture = Fixture()
        fixture.repairSaved.value = true

        fixture.memory.onFlagsLoaded { flag ->
            when (flag) {
                FeedFixSwitches.UPGRADE_REPAIR_OFF -> error("read failed")
                FeedFixSwitches.ADDRESS_CHECK_OFF -> true
                else -> false
            }
        }

        assertEquals(true, fixture.repairSaved.value)
        assertEquals(true, fixture.checkSaved.value)
        assertEquals(false, fixture.feedFirstSaved.value)
    }
}
