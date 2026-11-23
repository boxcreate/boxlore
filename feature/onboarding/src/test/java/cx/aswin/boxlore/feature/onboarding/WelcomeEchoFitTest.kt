package cx.aswin.boxlore.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Echo's Welcome peek across phone, foldable and tablet sizes and text sizes: it shows wherever there is room above the
 * button, sized to that room, and never overlaps the invitation text. It hides only when even the
 * smallest Echo can't fit (short windows, very large text). On the Galaxy Z Fold 8 cover, with its
 * system bars, the covers shrink so the main ways in show on the first screen; common phones keep
 * their covers' size.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi")
class WelcomeEchoFitTest(
    private val widthDp: Int,
    private val heightDp: Int,
    private val fontScale: Float,
    private val mustShow: Boolean,
    private val bars: Boolean,
) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `Echo fits above the button without overlapping the text`() {
        show()
        val echo = compose.onAllNodesWithTag("welcome_echo").fetchSemanticsNodes()
        val label = "${widthDp}x$heightDp dp at ${fontScale}x text"
        if (mustShow) assertEquals("Echo should show on $label", 1, echo.size)
        if (echo.isNotEmpty()) {
            val textBottom = compose.onNodeWithContentDescription("We’ve got the lore.").fetchSemanticsNode().boundsInRoot.bottom
            val echoTop = echo.single().boundsInRoot.top
            assertTrue("Echo overlaps the invitation on $label ($echoTop < $textBottom)", echoTop >= textBottom - 1f)
        }
    }

    @Test fun `on the cover the main ways in show on the first screen`() {
        assumeTrue("Checked on cover-sized windows at default text", widthDp in 440..480 && fontScale == 1f)
        show()
        val window = window()
        listOf("Find shows for me", "Search podcasts", "Import library").forEach { label ->
            val node = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue("$label is below the fold on ${widthDp}x$heightDp ($node in $window)", node.bottom <= window.bottom - if (bars) 24f else 0f)
        }
        compose.onNode(hasText("Skip setup") and hasClickAction()).assertExists()
    }

    @Test fun `on the cover the ways in are in place from the first frame`() {
        assumeTrue("Checked on the cover with its bars", widthDp == 443 && bars && fontScale == 1f)
        show(settle = false)
        val find = { compose.onNode(hasText("Find shows for me") and hasClickAction()).fetchSemanticsNode().boundsInRoot }
        val first = find()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("Find shows for me moves after the first frame", first, find())
    }

    @Test fun `common phones keep their covers' size`() {
        assumeTrue("Checked on common phones", "${widthDp}x$heightDp" in COMMON_PHONES && fontScale == 1f && !bars)
        show()
        val reel = compose.onNodeWithTag("welcome_cover_reel").fetchSemanticsNode().boundsInRoot
        val byWidth = (widthDp * 0.315f).coerceIn(90f, 132f)
        assertEquals("two rows of covers by width", byWidth * 2 + 12f, reel.height, 1f)
    }

    private fun window(): Rect {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val left = (root.width - widthDp) / 2f
        val top = (root.height - heightDp) / 2f
        return Rect(left, top, left + widthDp, top + heightDp)
    }

    private fun show(settle: Boolean = true) {
        compose.mainClock.autoAdvance = false
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            BoxLoreTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                        WelcomeScreen(onHelpMeFind = {}, onSearch = {}, onSkip = {}, onImportClick = {})
                    }
                }
            }
        }
        if (bars) compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets.PHONE_BARS) }
        // The first frame with the bars in place.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        if (!settle) return
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
    }

    companion object {
        private val COMMON_PHONES = setOf("360x780", "393x852", "411x914", "430x932")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}x{1} font {2} bars {4}")
        fun phones() = listOf(
            // Common phone sizes at default text must show Echo.
            arrayOf<Any>(360, 740, 1.0f, true, false),
            arrayOf<Any>(369, 821, 1.0f, true, false),
            arrayOf<Any>(393, 852, 1.0f, true, false),
            arrayOf<Any>(412, 915, 1.0f, true, false),
            arrayOf<Any>(427, 952, 1.0f, true, false),
            arrayOf<Any>(360, 780, 1.0f, true, false),
            arrayOf<Any>(411, 914, 1.0f, true, false),
            arrayOf<Any>(430, 932, 1.0f, true, false),
            // Short windows and larger text: Echo may hide, but must never overlap.
            arrayOf<Any>(320, 568, 1.0f, false, false),
            arrayOf<Any>(360, 640, 1.0f, false, false),
            arrayOf<Any>(360, 740, 1.3f, false, false),
            arrayOf<Any>(369, 821, 1.3f, false, false),
            arrayOf<Any>(412, 915, 2.0f, false, false),
            // The Galaxy Z Fold 8 cover with its bars, and a book foldable's half.
            arrayOf<Any>(443, 649, 1.0f, false, true),
            arrayOf<Any>(475, 695, 1.0f, false, true),
            arrayOf<Any>(475, 695, 1.3f, false, true),
            arrayOf<Any>(456, 650, 1.0f, false, false),
            // Foldables and tablets, both orientations, and a split-screen half.
            arrayOf<Any>(673, 841, 1.0f, true, false),
            arrayOf<Any>(841, 673, 1.0f, false, false),
            arrayOf<Any>(800, 1280, 1.0f, true, false),
            arrayOf<Any>(1280, 800, 1.0f, false, false),
            arrayOf<Any>(640, 800, 1.3f, false, false),
        )
    }
}
