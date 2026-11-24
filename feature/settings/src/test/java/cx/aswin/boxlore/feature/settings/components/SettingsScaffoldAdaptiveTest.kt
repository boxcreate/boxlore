package cx.aswin.boxlore.feature.settings.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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

    private fun render() {
        fixture.prepare()
        fixture.compose.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        SettingsScaffold(title = TITLE, onBack = {}, actions = { Text("Support") }) {
                            repeat(30) { Text("Row $it", modifier = Modifier.height(56.dp)) }
                        }
                    }
                }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.waitForIdle()
    }

    private fun rowTop(): Float = fixture.compose.onNodeWithText("Row 0").fetchSemanticsNode().boundsInRoot.top

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

    private companion object {
        const val TITLE = "Playback"
    }
}
