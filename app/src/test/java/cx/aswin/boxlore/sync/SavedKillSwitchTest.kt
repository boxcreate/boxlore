package cx.aswin.boxlore.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedKillSwitchTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class MemoryFlag(var value: Boolean? = null) : LastKnownFlag {
        override fun read(): Boolean? = value

        override fun write(value: Boolean) {
            this.value = value
        }
    }

    private fun switch(saved: LastKnownFlag, read: () -> Boolean?) =
        SavedKillSwitch(FeedFixSwitches.UPGRADE_REPAIR_OFF, read, saved)

    @Test
    fun savedOffKeepsTheJobPausedWhilePostHogIsStarting() {
        // Regression: a repair worker starting before PostHog setup finished ran despite the switch.
        assertFalse(switch(MemoryFlag(value = true)) { null }.isOn())
    }

    @Test
    fun nothingSavedAndPostHogStartingRunsTheJob() {
        val saved = MemoryFlag()

        assertTrue(switch(saved) { null }.isOn())
        assertNull(saved.value)
    }

    @Test
    fun liveAnswerDecidesAndIsSaved() {
        val saved = MemoryFlag()

        assertFalse(switch(saved) { true }.isOn())
        assertEquals(true, saved.value)
        // The flag turned off (or was disabled: a missing flag reads as off once PostHog is ready).
        assertTrue(switch(saved) { false }.isOn())
        assertEquals(false, saved.value)
        assertTrue(switch(saved) { null }.isOn())
    }

    @Test
    fun throwingReadUsesTheSavedAnswer() {
        assertFalse(switch(MemoryFlag(value = true)) { error("PostHog not started") }.isOn())
        assertTrue(switch(MemoryFlag()) { error("PostHog not started") }.isOn())
    }

    @Test
    fun rememberSavesLoadedAnswersLikeIsFeatureEnabled() {
        val saved = MemoryFlag()
        val killSwitch = switch(saved) { null }

        killSwitch.remember(true)
        assertFalse(killSwitch.isOn())
        killSwitch.remember(null)
        assertTrue(killSwitch.isOn())
        killSwitch.remember("variant")
        assertFalse(killSwitch.isOn())
        killSwitch.remember(false)
        assertTrue(killSwitch.isOn())
    }

    @Test
    fun eachFlagHasItsOwnSavedFile() {
        val names =
            listOf(FeedFixSwitches.UPGRADE_REPAIR_OFF, FeedFixSwitches.ADDRESS_CHECK_OFF, FeedFixSwitches.V2_DISCOVERY_OFF)
                .map(SavedKillSwitch::lastKnownFile)

        assertEquals(listOf("kill_switch_upgrade_repair_off", "kill_switch_address_check_off", "kill_switch_v2_discovery_off"), names)
        assertFalse(FeedFirstSwitch.LAST_KNOWN_FILE in names)
    }

    @Test
    fun savedFileSurvivesIntoTheNextProcess() {
        val file = File(folder.root, SavedKillSwitch.lastKnownFile(FeedFixSwitches.ADDRESS_CHECK_OFF))
        SavedKillSwitch(FeedFixSwitches.ADDRESS_CHECK_OFF, { null }, FileLastKnownFlag(file)).remember(true)

        val nextProcess = SavedKillSwitch(FeedFixSwitches.ADDRESS_CHECK_OFF, { null }, FileLastKnownFlag(file))
        assertFalse(nextProcess.isOn())
    }
}
