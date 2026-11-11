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

    @Test
    fun fixedDisplayModeSendsOneColourAndSystemModeSendsLightAndDark() {
        writeThemeCache(brand = "violet", appearance = WidgetAppearance.APP, themeConfig = "dark")
        val fixed = WidgetChrome.resolve(context)
        assertFalse(fixed.followsSystemDayNight)

        // Display mode "System": the launcher gets both colours, so widgets switch with the phone even
        // when the app is not running.
        writeThemeCache(brand = "violet", appearance = WidgetAppearance.APP, themeConfig = "system")
        val following = WidgetChrome.resolve(context)
        assertTrue(following.usesAppTheme)
        assertTrue(following.followsSystemDayNight)

        // System appearance leaves light and dark to the launcher's own resources.
        writeThemeCache(brand = "violet", appearance = WidgetAppearance.SYSTEM, themeConfig = "system")
        assertFalse(WidgetChrome.resolve(context).followsSystemDayNight)
    }

    @Test
    fun lightAndDarkAppColoursDifferSoTheLauncherHasARealChoice() {
        writeThemeCache(brand = "violet", appearance = WidgetAppearance.APP, themeConfig = "light")
        val light = WidgetChrome.resolve(context)
        writeThemeCache(brand = "violet", appearance = WidgetAppearance.APP, themeConfig = "dark")
        val dark = WidgetChrome.resolve(context)

        for (role in listOf(WidgetPalette.surface, WidgetPalette.primaryContainer, WidgetPalette.tertiaryContainer, WidgetPalette.onSurface)) {
            assertNotEquals(light.argb(role), dark.argb(role))
        }
    }

    @Test
    fun everyRoleTheWidgetsPaintWithComesFromTheAppTheme() {
        writeThemeCache(brand = "violet", appearance = WidgetAppearance.APP)
        val violet = WidgetChrome.resolve(context)
        writeThemeCache(brand = "emerald", appearance = WidgetAppearance.APP)
        val emerald = WidgetChrome.resolve(context)

        // Tertiary paints New Episodes; if it fell back to the system resource it would not follow the brand.
        assertNotEquals(violet.argb(WidgetPalette.tertiaryContainer), emerald.argb(WidgetPalette.tertiaryContainer))
        assertNotEquals(violet.argb(WidgetPalette.tertiary), emerald.argb(WidgetPalette.tertiary))
        assertNotEquals(violet.argb(WidgetPalette.primaryContainer), emerald.argb(WidgetPalette.primaryContainer))
    }

    private fun writeThemeCache(
        brand: String,
        appearance: String?,
        themeConfig: String = "dark",
    ) {
        val editor =
            context
                .getSharedPreferences(FontRoundnessAxis.THEME_FAST_CACHE, Context.MODE_PRIVATE)
                .edit()
                .putString("theme_brand", brand)
                .putString("theme_config", themeConfig)
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
