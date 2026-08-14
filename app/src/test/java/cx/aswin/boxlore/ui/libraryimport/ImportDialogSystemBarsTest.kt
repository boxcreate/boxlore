package cx.aswin.boxlore.ui.libraryimport

import android.app.Application
import android.graphics.Color
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImportDialogSystemBarsTest {
    private lateinit var activity: ComponentActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    }

    @Test
    @Suppress("DEPRECATION")
    fun apply_paintsThemeBackgroundAndUsesLightIconsInLightTheme() {
        val window = activity.window
        ImportDialogSystemBars.apply(
            window = window,
            backgroundArgb = 0xFFF0EBE0.toInt(),
            darkTheme = false,
        )

        assertEquals(Color.TRANSPARENT, window.statusBarColor)
        assertEquals(Color.TRANSPARENT, window.navigationBarColor)
        assertFalse(window.isStatusBarContrastEnforced)
        assertFalse(window.isNavigationBarContrastEnforced)
        assertEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
            window.attributes.layoutInDisplayCutoutMode,
        )
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }

    @Test
    fun apply_usesDarkIconsInDarkTheme() {
        val window = activity.window
        ImportDialogSystemBars.apply(
            window = window,
            backgroundArgb = 0xFF111316.toInt(),
            darkTheme = true,
        )
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertFalse(controller.isAppearanceLightStatusBars)
        assertFalse(controller.isAppearanceLightNavigationBars)
    }
}
