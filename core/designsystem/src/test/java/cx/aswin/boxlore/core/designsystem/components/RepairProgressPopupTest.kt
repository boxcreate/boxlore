package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The repair pill is at most 420dp wide, uses the theme's own surface in light and dark, and is announced politely. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h900dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepairProgressPopupTest {
    @get:Rule val compose = createComposeRule()

    private var window by mutableStateOf(1_280.dp)
    private var dark by mutableStateOf(false)
    private var surface = Color.Unspecified

    @Test
    fun `the pill is card-sized on a tablet and fills a phone, both ways`() {
        show()
        listOf(1_280.dp to 420.dp, 412.dp to 380.dp, 1_280.dp to 420.dp).forEach { (width, expected) ->
            window = width
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertWidth(expected)
        }
        compose.onNodeWithTag(REPAIR_PROGRESS_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `the pill uses the theme's surface in light and dark`() {
        show()
        listOf(false, true).forEach { isDark ->
            dark = isDark
            compose.mainClock.advanceTimeBy(1_000L)
            compose.waitForIdle()
            val image = compose.onNodeWithTag(REPAIR_PROGRESS_TAG).captureToImage().toPixelMap()
            // Inside the rounded edge, left of the loader.
            assertEquals("dark=$isDark", surface.toArgb(), image[6, image.height / 2].toArgb())
        }
    }

    private fun show() {
        // The loader never stops, so time moves only when the test moves it.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                surface = MaterialTheme.colorScheme.surfaceContainerHigh
                Box(Modifier.width(window)) {
                    RepairProgressPopup(visible = true, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000L)
    }

    private fun assertWidth(expected: Dp) {
        val bounds = compose.onNodeWithTag(REPAIR_PROGRESS_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(expected.value, bounds.width, 1f)
    }
}
