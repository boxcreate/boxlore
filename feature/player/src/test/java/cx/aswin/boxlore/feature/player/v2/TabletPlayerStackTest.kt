package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tablet and inner-screen players whose controls and supporting panel share one column, in a window
 * exactly their size: the panel takes the height left under the controls (at least 200dp, 16dp above
 * the bottom edge), the page scrolls only when even that doesn't fit, and in book posture the panel
 * starts under the quick actions instead of over them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletPlayerStackTest(private val name: String, private val window: BoxloreWindowLayout, private val insets: SystemInsets?) {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(window.widthDp.toInt(), window.heightDp.toInt(), false))

    @Test
    fun `the supporting panel fills the rest of the window and shows Up next without scrolling`() {
        assumeTrue(name in FITS)
        fixture.renderPlayerInWindow(mutableStateOf(window), queueSize = 3, insets = insets)
        val support = fixture.compose.onNodeWithTag("tablet_player_support_region").fetchSemanticsNode().boundsInRoot
        val bottomEdge = window.heightDp - (insets?.navBottom ?: 0)
        assertEquals("Panel ends 16dp above the window's bottom edge", bottomEdge - 16f, support.bottom, 1f)
        assertTrue("Panel keeps at least 200dp: $support", support.height >= 199f)
        fixture.compose.onNodeWithText("Queued 1").assertIsDisplayed()
        val scroll = fixture.compose.onNodeWithTag("tablet_player_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("Nothing scrolls", 0f, scroll.maxValue(), 1f)
    }

    @Test
    fun `with system bars a cramped cover becomes the small cover beside the title and Up next still shows`() {
        assumeTrue(name == INSETS)
        fixture.renderPlayerInWindow(mutableStateOf(window), queueSize = 3, insets = insets)
        fixture.compose.onNodeWithTag(COMPACT_PLAYER_ARTWORK_TAG).assertExists()
        val support = fixture.compose.onNodeWithTag("tablet_player_support_region").fetchSemanticsNode().boundsInRoot
        assertTrue("Panel keeps at least 200dp: $support", support.height >= 199f)
        fixture.compose.onNodeWithText("Queued 1").assertIsDisplayed()
    }

    @Test
    fun `a short tablet window scrolls with every control before the panel`() {
        assumeTrue(name == SHORT)
        val shown = mutableStateOf(window)
        fixture.renderPlayerInWindow(shown, queueSize = 3)
        val scroll = fixture.compose.onNodeWithTag("tablet_player_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("The page scrolls", scroll.maxValue() > 0f)
        val lastControl = fixture.compose.onNodeWithContentDescription("Complete", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val support = fixture.compose.onNodeWithTag("tablet_player_support_region").getUnclippedBoundsInRoot()
        assertTrue("Controls $lastControl come before the panel $support", lastControl.bottom <= support.top)
        // Scrolled, then stretched into two columns and back: the column keeps its place.
        fixture.compose.onNodeWithTag("tablet_player_scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 80f) }
        settle()
        fun place() = fixture.compose.onNodeWithTag("tablet_player_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val scrolled = place()
        assertTrue("the column scrolled", scrolled > 0f)
        listOf(BoxloreWindowLayout(932f, 650f), window).forEach { size ->
            fixture.compose.runOnIdle { shown.value = size }
            settle()
        }
        assertEquals("the column's place after two columns", scrolled, place(), 1f)
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }

    @Test
    fun `in book posture the panel starts under the quick actions`() {
        assumeTrue(name == BOOK)
        fixture.renderPlayerInWindow(mutableStateOf(window), queueSize = 3)
        val lastControl = fixture.compose.onNodeWithContentDescription("Complete", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val support = fixture.compose.onNodeWithTag("tablet_player_support_region").fetchSemanticsNode().boundsInRoot
        assertTrue("Quick actions end at ${lastControl.bottom}, panel starts at ${support.top}", lastControl.bottom <= support.top - 12f + 0.5f)
        assertTrue("Panel stays on the right-hand screen", support.left >= 375f)
    }

    companion object {
        private const val SHORT = "600x500"
        private const val BOOK = "750x832-book"
        private const val INSETS = "750x832-insets"
        private val FITS = setOf("750x832", "800x1280")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun windows(): List<Array<Any?>> = listOf(
            arrayOf("750x832", BoxloreWindowLayout(750f, 832f), null),
            arrayOf(INSETS, BoxloreWindowLayout(750f, 832f), SystemInsets(statusTop = 24, navBottom = 48)),
            arrayOf("800x1280", BoxloreTestWindows.TABLET_PORTRAIT, null),
            arrayOf(SHORT, BoxloreWindowLayout(600f, 500f), null),
            arrayOf(BOOK, BoxloreTestWindows.UPRIGHT_BOOK, null),
        )
    }
}
