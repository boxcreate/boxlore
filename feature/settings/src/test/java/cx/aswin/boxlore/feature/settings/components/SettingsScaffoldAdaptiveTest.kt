package cx.aswin.boxlore.feature.settings.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings page headers on short windows: they start slim, follow the window as it turns or folds
 * (keeping the listener's own scroll), line up with the page column and stay clear of the sides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScaffoldAdaptiveTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_000, 1_000, false))

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)

    /** Read when the page is (re)composed but not watched, like a window that changed while it was saved. */
    private var restoredWindow: BoxloreWindowLayout? = null

    private val fontScale = mutableStateOf(1f)

    private fun render(title: String = TITLE, action: String = "Support"): StateRestorationTester {
        fixture.prepare()
        val restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = restoredWindow ?: window.value
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
                LocalDensity provides Density(density.density, fontScale.value),
            ) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        SettingsScaffold(title = title, onBack = {}, actions = { Text(action) }) {
                            repeat(30) { Text("Row $it", modifier = Modifier.height(56.dp).fillMaxWidth()) }
                        }
                    }
                }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.waitForIdle()
        return restoration
    }

    private fun rowTop(row: Int = 0): Float = fixture.compose.onNodeWithText("Row $row").fetchSemanticsNode().boundsInRoot.top

    private fun resize(size: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = size }
        fixture.compose.waitForIdle()
    }

    @Test fun `short windows open with a slim header, tall phones with today's large one`() {
        render()
        val tall = rowTop()
        assertTrue("phones open with the large header ($tall)", tall > 140f)
        for (short in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.COVER_443)) {
            resize(short)
            assertTrue("$short opens slim (${rowTop()})", rowTop() < 90f)
        }
    }

    @Test fun `turning sideways and back slims the header and opens it again at the top of the page`() {
        render()
        val tall = rowTop()
        resize(BoxloreTestWindows.PHONE_LAND)
        val slim = rowTop()
        assertTrue(slim < tall - 40f)
        resize(BoxloreTestWindows.PHONE)
        assertEquals(tall, rowTop(), 1f)
    }

    // Regression: the header followed the window only while the page stayed composed, so a page restored
    // after the phone turned (another screen was on top) kept the header of the old height.
    @Test fun `a page restored after the window changed height still opens slim, or full, as the new window needs`() {
        val restoration = render()
        val tall = rowTop()
        restoredWindow = BoxloreTestWindows.PHONE_LAND
        restoration.emulateSavedInstanceStateRestore()
        fixture.compose.waitForIdle()
        assertTrue("restored sideways opens slim (${rowTop()})", rowTop() < 90f)
        restoredWindow = BoxloreTestWindows.PHONE
        restoration.emulateSavedInstanceStateRestore()
        fixture.compose.waitForIdle()
        assertEquals("restored upright at the top opens full again", tall, rowTop(), 1f)
    }

    @Test fun `a page scrolled down keeps its place when the phone turns sideways and back`() {
        render()
        fixture.compose.onNodeWithText("Row 0").performTouchInput { swipeUp() }
        fixture.compose.waitForIdle()
        val before = rowTop(12)
        resize(BoxloreTestWindows.PHONE_LAND)
        resize(BoxloreTestWindows.PHONE)
        assertEquals(before, rowTop(12), 1f)
    }

    // Regression: on the Fold 8 cover at large text the slim title broke "Settings" into "Setting" / "s".
    @Test fun `on the cover screen at large text the slim title stays on one line beside its action`() {
        window.value = BoxloreTestWindows.COVER_443
        render(title = "Einstellungen", action = "boxlore unterstützen")
        for (scale in listOf(1.3f, 2f)) {
            fixture.compose.runOnIdle { fontScale.value = scale }
            fixture.compose.waitForIdle()
            val layouts = mutableListOf<TextLayoutResult>()
            fixture.compose.onAllNodesWithText("Einstellungen", useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.height > 0f && it.boundsInRoot.top < rowTop() }
                .forEach { it.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) }
            assertTrue("a visible title at $scale", layouts.isNotEmpty())
            layouts.forEach { assertEquals("one line at $scale", 1, it.lineCount) }
        }
    }

    @Test fun `on a sideways phone the title lines up with the page column`() {
        window.value = BoxloreTestWindows.PHONE_LAND
        render()
        val column = (BoxloreTestWindows.PHONE_LAND.widthDp - SETTINGS_PAGE_MAX_WIDTH.value) / 2f
        val row = fixture.compose.onNodeWithText("Row 0").fetchSemanticsNode().boundsInRoot.left
        assertEquals(column + 16f, row, 1f)
        val back = fixture.compose.onNodeWithContentDescription(text(R.string.settings_back)).fetchSemanticsNode().boundsInRoot.left
        assertTrue("Back sits inside the page column ($back)", back >= column - 1f)
    }

    @Test fun `with side system buttons and a camera cutout, Back and the header actions stay clear and work`() {
        window.value = BoxloreTestWindows.PHONE_LAND
        render()
        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets.SIDEWAYS_3_BUTTON) }
        fixture.compose.waitForIdle()
        val back = fixture.compose.onNodeWithContentDescription(text(R.string.settings_back))
        back.assertHasClickAction()
        assertTrue(back.fetchSemanticsNode().boundsInRoot.left >= SystemInsets.SIDEWAYS_3_BUTTON.cutoutStart - 1f)
        val action = fixture.compose.onNodeWithText("Support").fetchSemanticsNode().boundsInRoot
        assertTrue(action.right <= BoxloreTestWindows.PHONE_LAND.widthDp - SystemInsets.SIDEWAYS_3_BUTTON.navEnd + 1f)
    }

    // Regression: the camera cutout was padded twice, pushing the page off the header's column.
    @Test fun `with a camera cutout the page is centred in the safe area and the header lines up with it`() {
        window.value = BoxloreTestWindows.PHONE_LAND
        render()
        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets.SIDEWAYS_3_BUTTON) }
        fixture.compose.waitForIdle()
        val insets = SystemInsets.SIDEWAYS_3_BUTTON
        val row = fixture.compose.onNodeWithText("Row 0").fetchSemanticsNode().boundsInRoot
        val cutoutGap = row.left - insets.cutoutStart
        val navGap = BoxloreTestWindows.PHONE_LAND.widthDp - insets.navEnd - row.right
        assertEquals("gaps beside the cutout ($cutoutGap) and the buttons ($navGap)", cutoutGap, navGap, 1f)
        val column = row.left - 16f
        val back = fixture.compose.onNodeWithContentDescription(text(R.string.settings_back)).fetchSemanticsNode().boundsInRoot.left
        assertTrue("Back ($back) starts on the page column ($column)", back >= column - 1f && back <= column + 8f)
    }

    private companion object {
        const val TITLE = "Playback"
    }
}
