package cx.aswin.boxlore.wear.playback

import android.media.AudioDeviceInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioOutputRulesTest {
    private val minute = 60_000L

    @Test
    fun `headphones or the watch's own speaker can be heard, a watch with neither cannot`() {
        assertTrue(AudioOutputRules.canBeHeard(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP), hasAudioOutput = true))
        assertTrue(AudioOutputRules.canBeHeard(listOf(AudioDeviceInfo.TYPE_BLE_HEADSET), hasAudioOutput = true))
        assertTrue(AudioOutputRules.canBeHeard(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), hasAudioOutput = true))

        assertFalse(AudioOutputRules.canBeHeard(emptyList(), hasAudioOutput = true))
        // An earpiece or a call-only headset link is not something to play an episode through.
        assertFalse(AudioOutputRules.canBeHeard(listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BLUETOOTH_SCO), hasAudioOutput = true))
        assertFalse(AudioOutputRules.canBeHeard(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP), hasAudioOutput = false))
    }

    @Test
    fun `a start held for headphones runs once when they connect in time`() {
        var starts = 0
        val held = HeldStart(holdMs = 2 * minute)

        held.hold({ starts++ }, now = 0L)
        held.release(now = minute)
        held.release(now = minute + 1)

        assertEquals(1, starts)
    }

    @Test
    fun `a start that waited too long is dropped, and nothing held means nothing starts`() {
        var starts = 0
        val held = HeldStart(holdMs = 2 * minute)

        held.release(now = 0L)
        held.hold({ starts++ }, now = 0L)
        held.release(now = 2 * minute + 1)
        // Headphones connecting again later must not start it either.
        held.release(now = 2 * minute + 2)

        assertEquals(0, starts)
    }
}
