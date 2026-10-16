package cx.aswin.boxlore.feature.player.v2

import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression (0.0.25): `systemBars()` includes system overlays, which androidx.core maps through
 * an API 34 method that some devices reporting API 34 lack (NoSuchMethodError).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoOverlayBarTypesTest {
    @Test
    fun hidesOnlyStatusAndNavigationBars() {
        val types = videoOverlayBarTypes()
        assertEquals(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars(), types)
        assertEquals(0, types and WindowInsetsCompat.Type.systemOverlays())
    }
}
