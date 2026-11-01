package cx.aswin.boxlore.core.playback.service.auto

import cx.aswin.boxlore.core.analytics.PendingEntryPoint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarPlayLabelTest {
    @AfterEach
    fun tearDown() {
        PendingEntryPoint.consume()
    }

    @Test
    fun `a play from the app keeps the app's entry point`() {
        // Regression: phone plays pass through the session callback and were relabelled android_auto_*.
        PendingEntryPoint.set(mapOf("entry_point" to "home_hero_resume"))

        labelCarPlay(fromCar = false, entryPoint = "android_auto_discover")

        assertEquals("home_hero_resume", PendingEntryPoint.consume()?.get("entry_point"))
    }

    @Test
    fun `a play Android Auto asked for is labelled as one`() {
        labelCarPlay(fromCar = true, entryPoint = "android_auto_liked")

        assertEquals("android_auto_liked", PendingEntryPoint.consume()?.get("entry_point"))
    }
}
