package cx.aswin.boxlore.core.designsystem.audit

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shared capture matrix, its size filter, the named test windows and the fake system insets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w915dp-h412dp-land-mdpi")
class PhoneGoldenConfigurationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `audit size filter keeps only the named captures in the order given`() {
        val picked = PhoneGoldenConfiguration.auditConfigurations(sizes = "915x412-insets, 412x915,412x915-fs130-de")

        assertEquals(listOf("915x412-light-insets", "412x915-light", "412x915-light-fs130-de"), picked.map(PhoneGoldenConfiguration::toString))
        assertEquals(PhoneGoldenConfiguration(915, 412, false, "insets"), picked.first())
    }

    @Test
    fun `without a filter the matrix keeps every audit size and adds a suite's extra ones once`() {
        val extra = PhoneGoldenConfiguration(800, 1_280, false, locale = "fr")
        val all = PhoneGoldenConfiguration.auditConfigurations(sizes = null)
        val withExtra = PhoneGoldenConfiguration.auditConfigurations(listOf(extra, extra, all.first()), sizes = "")

        assertEquals(all + extra, withExtra)
        val names = all.map(PhoneGoldenConfiguration::toString)
        val expected = listOf("412x915-light", "412x450-light", "599x479-light", "600x480-light", "750x832-light-book") +
            listOf("915x412-light-insets", "695x475-light-insets", "932x650-light-tabletop-insets")
        expected.forEach { assertTrue("Audit matrix lacks $it", it in names) }
        assertEquals("Audit names are unique", names.size, names.toSet().size)
        assertTrue("Audit captures are light only", all.none(PhoneGoldenConfiguration::dark))
    }

    @Test
    fun `a named size outside the matrix is captured in light`() {
        val picked = PhoneGoldenConfiguration.auditConfigurations(sizes = "360x800,932x650-tabletop-insets-fs130-en-rXA,wide")

        assertEquals(
            listOf(
                PhoneGoldenConfiguration(360, 800, false),
                PhoneGoldenConfiguration(932, 650, false, "tabletop-insets", 1.3f, "en-rXA"),
            ),
            picked,
        )
        assertEquals("932x650-light-tabletop-insets-fs130-en-rXA", picked.last().toString())
    }

    @Test
    fun `capture names stay stable`() {
        assertEquals("915x412-light-insets", PhoneGoldenConfiguration(915, 412, false, "insets").toString())
        assertEquals("932x650-dark-tabletop", PhoneGoldenConfiguration(932, 650, true, "tabletop").toString())
        assertEquals("475x695-light-fs130", PhoneGoldenConfiguration(475, 695, false, fontScale = 1.3f).toString())
        assertEquals("412x915-light-fs130-de", PhoneGoldenConfiguration(412, 915, false, fontScale = 1.3f, locale = "de").toString())
        assertEquals("360x800-dark", PhoneGoldenConfiguration(360, 800, true).toString())
        assertEquals("915x412-insets", PhoneGoldenConfiguration(915, 412, true, "insets").auditKey)
    }

    @Test
    fun `inset postures send the shared presets`() {
        assertEquals(SystemInsets.SIDEWAYS_3_BUTTON, PhoneGoldenConfiguration(915, 412, false, "insets").systemInsets)
        assertEquals(SystemInsets.TABLETOP_STATUS, PhoneGoldenConfiguration(932, 650, false, "tabletop-insets").systemInsets)
        assertEquals(null, PhoneGoldenConfiguration(932, 650, false, "tabletop").systemInsets)
        assertEquals(
            listOf(listOf(0, 0, 915, 24), listOf(0, 0, 32, 412), listOf(867, 0, 915, 412)),
            SystemInsets.SIDEWAYS_3_BUTTON.bands(915, 412).map(IntArray::toList),
        )
    }

    @Test
    fun `named windows sit on the side of each gate their name says`() {
        with(BoxloreTestWindows) {
            assertFalse(W599_H900.usesTabletLayout)
            assertTrue(W600_H900.usesTabletLayout)
            assertFalse(W900_H479.usesTabletLayout)
            assertTrue(W900_H480.usesTabletLayout)
            assertEquals(BoxloreWindowWidth.Medium, W839.widthClass)
            assertEquals(BoxloreWindowWidth.Expanded, W841.widthClass)
            assertFalse(GATE_599x479.usesTabletLayout)
            assertTrue(GATE_600x480.usesTabletLayout)
            assertFalse(COVER_475.usesTabletLayout)
            assertFalse(SPLIT_HALF.usesTabletLayout)
            assertEquals(listOf(315f, 336f), FOLD_TABLETOP.tabletopFold?.bounds?.let { listOf(it.top, it.bottom) })
            assertEquals(BoxloreFoldOrientation.Vertical, FOLD_BOOK.folds.single().orientation)
            assertEquals(0f, UPRIGHT_BOOK.folds.single().bounds.width)
            assertEquals(UPRIGHT_BOOK, forCapture(750, 832, "book"))
            assertEquals(FOLD_TABLETOP, forCapture(932, 650, "tabletop-insets"))
            assertEquals(null, forCapture(915, 412, "insets"))
        }
    }

    @Test
    fun `walkWindows visits every step in order`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val composed = mutableListOf<BoxloreWindowLayout>()
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                val current = LocalBoxloreWindowLayout.current
                SideEffect { composed += current }
            }
        }
        val checked = mutableListOf<BoxloreWindowLayout>()
        val steps = arrayOf(BoxloreTestWindows.W599_H900, BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900, BoxloreTestWindows.FOLD_TABLETOP)

        compose.walkWindows(window, *steps) { step ->
            assertEquals("Composition settled before the check", step, composed.last())
            checked += step
        }

        assertEquals(steps.toList(), checked)
        assertEquals(listOf(BoxloreTestWindows.PHONE) + steps, composed)
    }

    @Test
    fun `fake system insets reach Compose`() {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        var safe = emptyList<Int>()
        compose.setContent {
            val density = LocalDensity.current
            val direction = LocalLayoutDirection.current
            val insets = WindowInsets.safeDrawing
            val values = listOf(insets.getLeft(density, direction), insets.getTop(density), insets.getRight(density, direction), insets.getBottom(density))
            SideEffect { safe = values }
        }
        compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets(statusTop = 24, navEnd = 48, cutoutStart = 32, imeBottom = 200)) }
        compose.waitForIdle()

        assertEquals(listOf(32, 24, 48, 200), safe)
    }

    @Test
    @Config(qualifiers = "en-rXA")
    fun `library resources include the en-XA pseudo-locale`() {
        val label = ApplicationProvider.getApplicationContext<Context>().getString(R.string.navigation_home)

        assertTrue("Pseudo-localized text is bracketed: $label", label.startsWith("[") && label.endsWith("]"))
        assertNotEquals("[Home]", label)
    }
}
