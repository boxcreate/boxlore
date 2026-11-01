package cx.aswin.boxlore.core.playback.service

import cx.aswin.boxlore.core.analytics.PlaybackListenValues
import cx.aswin.boxlore.core.database.ListeningSessionValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A listen's labels: what controlled it, whether a car is really connected, and where the sound went. */
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
    fun `the output is where the sound goes, never the car`() {
        assertEquals(PlaybackListenValues.OUTPUT_WIRED, ListeningOutputRoute.analyticsOutput(ListeningSessionValues.OUTPUT_HEADPHONES))
        assertEquals(PlaybackListenValues.OUTPUT_BLUETOOTH, ListeningOutputRoute.analyticsOutput(ListeningSessionValues.OUTPUT_BLUETOOTH))
        assertEquals(PlaybackListenValues.OUTPUT_SPEAKER, ListeningOutputRoute.analyticsOutput(ListeningSessionValues.OUTPUT_SPEAKER))
        assertEquals(PlaybackListenValues.OUTPUT_CAST, ListeningOutputRoute.analyticsOutput(ListeningSessionValues.OUTPUT_CAST))
        assertEquals(PlaybackListenValues.OUTPUT_OTHER, ListeningOutputRoute.analyticsOutput(ListeningSessionValues.OUTPUT_CAR))
        assertEquals(PlaybackListenValues.OUTPUT_OTHER, ListeningOutputRoute.analyticsOutput(null))
    }
}
