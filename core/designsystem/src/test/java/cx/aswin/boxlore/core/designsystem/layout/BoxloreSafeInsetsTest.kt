package cx.aswin.boxlore.core.designsystem.layout

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Inset helpers measure from the pane a screen is in: a pane that starts below the top of the
 * window (the lower half in tabletop) reserves no status bar, and one that ends above the bottom
 * reserves no system navigation. Without shell pane bounds they are the window's insets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoxloreSafeInsetsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(932, 650, false))

    @Test
    fun `pane top and bottom insets follow the pane's own edges`() {
        var pane by mutableStateOf<BoxloreLayoutBounds?>(null)
        var top = -1f
        var bottom = -1f
        fixture.prepare()
        fixture.compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.FOLD_TABLETOP, LocalBoxlorePaneBounds provides pane) {
                top = boxlorePaneTopInset().value
                bottom = boxlorePaneBottomInset().value
                Box(Modifier.boxlorePaneStatusBarsPadding()) { Text("Below the status bar") }
            }
        }
        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets(statusTop = 24, navBottom = 48)) }
        advance()
        val cases = listOf(
            null to (24f to 48f),
            BoxloreLayoutBounds(0f, 0f, 932f, 315f) to (24f to 0f),
            BoxloreLayoutBounds(0f, 335f, 932f, 650f) to (0f to 48f),
            BoxloreLayoutBounds(80f, 0f, 932f, 650f) to (24f to 48f),
        )
        cases.forEach { (bounds, expected) ->
            pane = bounds
            advance()
            assertEquals("$bounds", expected.first, top, 0.5f)
            assertEquals("$bounds", expected.second, bottom, 0.5f)
            assertEquals("$bounds", expected.first, fixture.compose.onNodeWithText("Below the status bar").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        }
    }

    @Test
    fun `bottom padding counts the mini player only over its pane and the bar only once`() {
        val dock = BoxloreLayoutBounds(80f, 0f, 760f, 800f)
        var pane by mutableStateOf<BoxloreLayoutBounds?>(dock)
        var reserved by mutableStateOf(false)
        var padding = -1f
        fixture.compose.setContent {
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides BoxloreTestWindows.TABLET,
                LocalBoxlorePaneBounds provides pane,
                LocalBoxloreDockedPlayerBounds provides dock,
                LocalBoxloreNavigationRailVisible provides !reserved,
                LocalBoxloreBottomChromeReserved provides reserved,
            ) {
                padding = cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding(isMiniPlayerVisible = true, systemNavigationInset = 48.dp).value
            }
        }
        advance()
        // Under the dock: system navigation plus the mini player and its gap.
        assertEquals(48f + 64f + 8f, padding, 0.5f)
        // A detail beside the dock reserves only system navigation.
        pane = BoxloreLayoutBounds(776f, 0f, 1_280f, 800f)
        advance()
        assertEquals(48f, padding, 0.5f)
        // Above chrome the shell already reserves, only the mini player over it counts.
        pane = dock
        reserved = true
        advance()
        assertEquals(64f + 8f, padding, 0.5f)
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.waitForIdle()
    }
}
