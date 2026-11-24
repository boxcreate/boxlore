package cx.aswin.boxlore.feature.player.v2.logic

import android.content.pm.ActivityInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FullscreenVideoOrientationTest {
    @Test
    fun `phones turn for the video and give rotation back when it closes`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, fullscreenVideoOrientation(FullscreenVideoPhase.Enter, true, false, null))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, fullscreenVideoOrientation(FullscreenVideoPhase.Enter, false, false, null))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, fullscreenVideoOrientation(FullscreenVideoPhase.Exit, true, false, null))
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            fullscreenVideoOrientation(FullscreenVideoPhase.Exit, true, false, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
        )
    }

    @Test
    fun `tablets and inner screens are never forced and get their own orientation back`() {
        assertNull(fullscreenVideoOrientation(FullscreenVideoPhase.Enter, true, true, null))
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER,
            fullscreenVideoOrientation(FullscreenVideoPhase.Exit, true, true, ActivityInfo.SCREEN_ORIENTATION_USER),
        )
    }
}
