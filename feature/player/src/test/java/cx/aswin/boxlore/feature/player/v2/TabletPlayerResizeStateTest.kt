package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rotating a tablet moves the supporting panel between the stacked and side-by-side layouts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletPlayerResizeStateTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 1_280, false))

    @Test
    fun `rotating a tablet keeps the Up next tab and how far its list is scrolled`() {
        val window = mutableStateOf(BoxloreTestWindows.TABLET_PORTRAIT)
        fixture.renderPlayerInWindow(window, queueSize = 30)
        fixture.compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(20)
        fixture.settlePlayer()
        fixture.compose.onNodeWithText("Queued 21").assertExists()
        listOf(BoxloreTestWindows.TABLET, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.TABLET).forEach { layout ->
            fixture.compose.runOnIdle { window.value = layout }
            fixture.settlePlayer()
            fixture.compose.onNodeWithText("Up next").assertIsSelected()
            fixture.compose.onNodeWithText("Queued 21").assertExists()
        }
    }
}
