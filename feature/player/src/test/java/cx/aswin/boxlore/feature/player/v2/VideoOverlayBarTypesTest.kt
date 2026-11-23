package cx.aswin.boxlore.feature.player.v2

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowInsetsCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hidesOnlyStatusAndNavigationBars() {
        val types = videoOverlayBarTypes()
        assertEquals(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars(), types)
        assertEquals(0, types and WindowInsetsCompat.Type.systemOverlays())
    }

    @Test
    fun `leaving full-screen video gives rotation back across a fold or unfold`() {
        val visible = mutableStateOf(false)
        val window = mutableStateOf(BoxloreWindowLayout(443f, 649f))
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                FullscreenVideoSystemUi(visible.value, isLandscape = true, activity = compose.activity, onExit = {})
            }
        }
        listOf(BoxloreWindowLayout(443f, 649f) to BoxloreWindowLayout(932f, 650f), BoxloreWindowLayout(932f, 650f) to BoxloreWindowLayout(443f, 649f))
            .forEach { (shownOn, hiddenOn) ->
                compose.runOnIdle {
                    window.value = shownOn
                    visible.value = true
                }
                compose.runOnIdle { window.value = hiddenOn }
                compose.waitForIdle()
                // While the video stays open, an inner screen is never held sideways; the cover is again.
                val expected = if (hiddenOn.usesTabletLayout) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                assertEquals(expected, compose.activity.requestedOrientation)
                compose.runOnIdle { visible.value = false }
                compose.waitForIdle()
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, compose.activity.requestedOrientation)
            }
    }

    @Test
    fun `a tablet layout opens full-screen video without forcing landscape`() {
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1_280f, 800f)) {
                FullscreenVideoSystemUi(true, isLandscape = true, activity = compose.activity, onExit = {})
            }
        }
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, compose.activity.requestedOrientation)
    }
}
