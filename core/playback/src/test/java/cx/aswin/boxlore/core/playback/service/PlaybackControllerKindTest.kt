package cx.aswin.boxlore.core.playback.service

import android.media.AudioDeviceInfo
import androidx.media3.common.Player
import cx.aswin.boxlore.core.analytics.PlaybackListenValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A listen's labels: what controlled it, whether a car is really connected, where the sound went, and when it ends. */
class PlaybackControllerKindTest {
    private fun kind(isCar: Boolean = false, isNotification: Boolean = false, packageName: String,) =
        PlaybackControllerKind.classify(isCar, isNotification, packageName, ownPackage = "cx.aswin.boxlore")

    @Test
    fun `controllers are the app, the car, the notification or other`() {
        assertEquals(PlaybackListenValues.CONTROL_APP, kind(packageName = "cx.aswin.boxlore"))
        assertEquals(PlaybackListenValues.CONTROL_CAR, kind(isCar = true, packageName = "com.google.android.projection.gearhead"))
        assertEquals(
            PlaybackListenValues.CONTROL_NOTIFICATION,
            kind(isNotification = true, packageName = "cx.aswin.boxlore"),
            "the media notification runs in boxlore's own package",
        )
        assertEquals(PlaybackListenValues.CONTROL_NOTIFICATION, kind(packageName = "com.android.systemui"))
        assertEquals(PlaybackListenValues.CONTROL_OTHER, kind(packageName = "com.android.bluetooth"))
        assertEquals(PlaybackListenValues.CONTROL_OTHER, kind(packageName = "com.google.android.wearable.app"))
    }

    @Test
    fun `a car counts only in car mode or on Android Automotive`() {
        assertFalse(CarConnectionState.isInCar(isCarMode = false, isAutomotive = false))
        assertTrue(CarConnectionState.isInCar(isCarMode = true, isAutomotive = false))
        assertTrue(CarConnectionState.isInCar(isCarMode = false, isAutomotive = true))
    }

    @Test
    fun `the output is where the sound goes, with hearing aids as Bluetooth`() {
        val output = ListeningOutputRoute::analyticsOutputOf
        assertEquals(PlaybackListenValues.OUTPUT_WIRED, output(listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertEquals(PlaybackListenValues.OUTPUT_WIRED, output(listOf(AudioDeviceInfo.TYPE_USB_HEADSET)))
        assertEquals(PlaybackListenValues.OUTPUT_BLUETOOTH, output(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_WIRED_HEADSET)))
        assertEquals(PlaybackListenValues.OUTPUT_BLUETOOTH, output(listOf(AudioDeviceInfo.TYPE_HEARING_AID)))
        assertEquals(PlaybackListenValues.OUTPUT_SPEAKER, output(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertEquals(PlaybackListenValues.OUTPUT_OTHER, output(emptyList()))
    }

    @Test
    fun `sound stopping ends the listen unless it's only rebuffering`() {
        assertNull(ListenEndRule.reason(playWhenReady = true, playbackState = Player.STATE_BUFFERING, suppressed = false))
        assertEquals(PlaybackListenValues.END_PAUSED, ListenEndRule.reason(playWhenReady = false, playbackState = Player.STATE_BUFFERING, suppressed = false))
        assertEquals(PlaybackListenValues.END_PAUSED, ListenEndRule.reason(playWhenReady = true, playbackState = Player.STATE_READY, suppressed = true))
        assertEquals(PlaybackListenValues.END_STOPPED, ListenEndRule.reason(playWhenReady = false, playbackState = Player.STATE_IDLE, suppressed = false))
        assertEquals(PlaybackListenValues.END_FINISHED, ListenEndRule.reason(playWhenReady = true, playbackState = Player.STATE_ENDED, suppressed = false))
    }
}
