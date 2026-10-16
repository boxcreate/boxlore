package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Echo's Welcome peek across phone, foldable and tablet sizes and text sizes: it shows wherever there is room above the
 * button, sized to that room, and never overlaps the invitation text. It hides only when even the
 * smallest Echo can't fit (short windows, very large text).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi")
class WelcomeEchoFitTest(private val widthDp: Int, private val heightDp: Int, private val fontScale: Float, private val mustShow: Boolean) {
    @get:Rule val compose = createComposeRule()

    @Test fun `Echo fits above the button without overlapping the text`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                        WelcomeScreen(onHelpMeFind = {}, onSearch = {}, onSkip = {}, onImportClick = {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()

        val echo = compose.onAllNodesWithTag("welcome_echo").fetchSemanticsNodes()
        val label = "${widthDp}x$heightDp dp at ${fontScale}x text"
        if (mustShow) assertEquals("Echo should show on $label", 1, echo.size)
        if (echo.isNotEmpty()) {
            val textBottom = compose.onNodeWithContentDescription("We’ve got the lore.").fetchSemanticsNode().boundsInRoot.bottom
            val echoTop = echo.single().boundsInRoot.top
            assertTrue("Echo overlaps the invitation on $label ($echoTop < $textBottom)", echoTop >= textBottom - 1f)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}x{1} font {2}")
        fun phones() = listOf(
            // Common phone sizes at default text must show Echo.
            arrayOf<Any>(360, 740, 1.0f, true),
            arrayOf<Any>(369, 821, 1.0f, true),
            arrayOf<Any>(393, 852, 1.0f, true),
            arrayOf<Any>(412, 915, 1.0f, true),
            arrayOf<Any>(427, 952, 1.0f, true),
            // Short windows and larger text: Echo may hide, but must never overlap.
            arrayOf<Any>(320, 568, 1.0f, false),
            arrayOf<Any>(360, 640, 1.0f, false),
            arrayOf<Any>(360, 740, 1.3f, false),
            arrayOf<Any>(369, 821, 1.3f, false),
            arrayOf<Any>(412, 915, 2.0f, false),
            // Foldables and tablets, both orientations, and a split-screen half.
            arrayOf<Any>(673, 841, 1.0f, true),
            arrayOf<Any>(841, 673, 1.0f, false),
            arrayOf<Any>(800, 1280, 1.0f, true),
            arrayOf<Any>(1280, 800, 1.0f, false),
            arrayOf<Any>(640, 800, 1.3f, false),
        )
    }
}
