package cx.aswin.boxlore.core.designsystem.layout

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
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

    @Test
    fun `side insets pad only start and end, follow the layout direction and are 0 in portrait`() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var pane by mutableStateOf<BoxloreLayoutBounds?>(null)
        var sides = 0.dp to 0.dp
        var content = PaddingValues()
        fixture.prepare()
        fixture.compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE_LAND,
                LocalBoxlorePaneBounds provides pane,
            ) {
                sides = boxloreHorizontalSafeInsetsDp()
                content = boxloreHorizontalSafeContentPadding(PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                Box(Modifier.boxloreHorizontalSafePadding()) { Text("Clear of the sides") }
            }
        }
        advance()
        // Portrait, or no side insets: nothing.
        assertEquals(0.dp to 0.dp, sides)
        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets.SIDEWAYS_3_BUTTON) }
        advance()
        assertEquals(32.dp to 48.dp, sides)
        assertEquals(48.dp, content.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(64.dp, content.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(8.dp, content.calculateTopPadding())
        val text = fixture.compose.onNodeWithText("Clear of the sides").fetchSemanticsNode().boundsInRoot
        // The top is untouched: only the sides are padded.
        assertEquals(32f, text.left, 0.5f)
        assertEquals(0f, text.top, 0.5f)

        direction = LayoutDirection.Rtl
        advance()
        // Right to left the start side is the right: the system buttons.
        assertEquals(48.dp to 32.dp, sides)

        // A pane that doesn't reach the left edge leaves the cutout to its neighbour.
        direction = LayoutDirection.Ltr
        pane = BoxloreLayoutBounds(80f, 0f, 915f, 412f)
        advance()
        assertEquals(0.dp to 48.dp, sides)
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.waitForIdle()
    }
}
