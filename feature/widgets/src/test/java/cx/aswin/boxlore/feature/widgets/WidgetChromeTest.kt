package cx.aswin.boxlore.feature.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.prefs.FontRoundnessAxis
import cx.aswin.boxlore.core.prefs.WidgetAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetChromeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultAppearanceFollowsAppTheme() {
        writeThemeCache(brand = "violet", appearance = null)
        val chrome = WidgetChrome.resolve(context)
        assertTrue(chrome.usesAppTheme)
        assertEquals(0xFF000000.toInt(), chrome.argb(WidgetPalette.primary) and 0xFF000000.toInt())
    }

    @Test
    fun appThemePrimaryDiffersByBrand() {
        writeThemeCache(brand = "violet", appearance = WidgetAppearance.APP)
        val violet = WidgetChrome.resolve(context).argb(WidgetPalette.primary)
        writeThemeCache(brand = "emerald", appearance = WidgetAppearance.APP)
        val emerald = WidgetChrome.resolve(context).argb(WidgetPalette.primary)
        assertNotEquals(violet, emerald)
    }

    @Test
    fun systemAppearanceStaysOpaqueWithoutFollowingAppTheme() {
        writeThemeCache(brand = "violet", appearance = WidgetAppearance.SYSTEM)
        val chrome = WidgetChrome.resolve(context)
        assertFalse(chrome.usesAppTheme)
        assertEquals(0xFF000000.toInt(), chrome.argb(WidgetPalette.primary) and 0xFF000000.toInt())
    }

    private fun writeThemeCache(
        brand: String,
        appearance: String?,
    ) {
        val editor =
            context
                .getSharedPreferences(FontRoundnessAxis.THEME_FAST_CACHE, Context.MODE_PRIVATE)
                .edit()
                .putString("theme_brand", brand)
                .putString("theme_config", "dark")
                .putString("surface_style", "classic_dynamic")
                .putBoolean("use_dynamic_color", false)
        if (appearance == null) {
            editor.remove(WidgetAppearance.PREF_KEY)
        } else {
            editor.putString(WidgetAppearance.PREF_KEY, appearance)
        }
        editor.commit()
    }
}
