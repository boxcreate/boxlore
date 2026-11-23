package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Welcome beside its cover panel on sideways phones, landscape foldables and tablets: every way in
 * (Find shows for me, Search, Import and Skip) shows without scrolling, and the panel takes the same
 * share as on the language question. Portrait windows keep the stacked page. Turning the window
 * mid-reveal carries the entrance on from where it was, and each button calls its callback once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1700dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeTwoPaneTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val calls = mutableListOf<String>()
    private var clock: WelcomeMotionClock? = null

    private val ways = listOf("Find shows for me", "Search podcasts", "Import library", "Skip setup")

    /** Welcome as the app shows it, with its own clock, or with one the test reads ([sharedClock]). */
    private fun show(insets: SystemInsets? = null, sharedClock: Boolean = false, darkTheme: Boolean = false) {
        compose.mainClock.autoAdvance = false
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            val testClock = if (sharedClock) rememberWelcomeMotionClock().also { clock = it } else null
            BoxLoreTheme(darkTheme = darkTheme, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        val onHelpMeFind = { calls += "find" }
                        val onSearch = { calls += "search" }
                        val onSkip = { calls += "skip" }
                        val onImport = { calls += "import" }
                        if (testClock != null) {
                            WelcomeScreen(onHelpMeFind, onSearch, onSkip, onImport, clock = testClock)
                        } else {
                            WelcomeScreen(onHelpMeFind, onSearch, onSkip, onImport)
                        }
                    }
                }
            }
        }
        insets?.let { compose.runOnUiThread { applyFakeSystemInsets(compose.activity, it) } }
    }

    private fun way(label: String) = compose.onNode(hasText(label) and hasClickAction())

    private fun screen(): Rect = compose.onRoot().fetchSemanticsNode().boundsInRoot.let { root ->
        // The window sits centred in the larger test screen.
        val left = (root.width - window.value.widthDp) / 2f
        val top = (root.height - window.value.heightDp) / 2f
        Rect(left, top, left + window.value.widthDp, top + window.value.heightDp)
    }

    @Test
    fun `every way in shows without scrolling beside the panel`() {
        window.value = BoxloreTestWindows.PHONE_LAND
        show(SystemInsets.SIDEWAYS_3_BUTTON)
        compose.mainClock.advanceTimeBy(2_500)
        val wide = with(BoxloreTestWindows) { listOf(PHONE_LAND, SIDEWAYS_SMALL, FOLD_FLAT, PIXEL_FOLD, TABLET, DESKTOP) }

        compose.walkWindows(window, *wide.toTypedArray()) { step ->
            compose.mainClock.advanceTimeBy(100)
            val bounds = screen()
            ways.forEach { label ->
                val node = way(label).fetchSemanticsNode().boundsInRoot
                assertTrue("$label is below the fold on $step ($node in $bounds)", node.top >= bounds.top - 1f && node.bottom <= bounds.bottom + 1f)
            }
            assertEquals("one panel on $step", 1, compose.onAllNodesWithTag(OpeningPanelTag).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `the panel takes the language question's share of the width`() {
        show()
        compose.walkWindows(window, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.TABLET, SIDEWAYS_SMALL) { step ->
            val panel = compose.onAllNodesWithTag(OpeningPanelTag).fetchSemanticsNodes().single().boundsInRoot
            assertEquals("panel width on $step", step.widthDp * OpeningPanelPaneShare, panel.width, 1f)
            assertEquals("panel height on $step", step.heightDp, panel.height, 1f)
        }
    }

    @Test
    fun `portrait windows keep the stacked page`() {
        show()
        compose.walkWindows(window, BoxloreTestWindows.PHONE, BoxloreTestWindows.COVER_475, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.UPRIGHT_BOOK) { step ->
            val panel = compose.onAllNodesWithTag(OpeningPanelTag).fetchSemanticsNodes().single().boundsInRoot
            assertTrue("the panel spans the page on $step", panel.width >= minOf(step.widthDp, 720f) - 1f)
            assertTrue("the panel sits on top on $step", panel.height < step.heightDp * 0.6f)
        }
    }

    @Test
    fun `turning keeps Welcome's entrance where it was and each way in calls once`() {
        // Welcome's own clock, so a clock started inside one arrangement would show as Echo dropping back.
        show()
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        assertTrue("Echo has risen", echoRisen() == true)

        val turns = with(BoxloreTestWindows) { arrayOf(PHONE_LAND, PHONE, TABLET_PORTRAIT, TABLET, TABLET_PORTRAIT, PHONE) }
        compose.walkWindows(window, *turns) { step ->
            // The first frame after the turn: Echo, where it shows, is still up, not rising again.
            assertTrue("Welcome's entrance started again on $step", echoRisen() != false)
        }

        ways.forEach { way(it).performClick() }
        assertEquals(listOf("find", "search", "import", "skip"), calls)
    }

    @Test
    fun `turning mid-reveal carries the entrance on and resizing never jumps the covers`() {
        show(sharedClock = true)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val clock = requireNotNull(clock)
        var elapsed = clock.elapsedMillis.value
        assertTrue("mid-reveal", elapsed > 0 && !clock.revealComplete.value)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertTrue(clock.revealComplete.value)

        val walk = arrayOf(BoxloreTestWindows.COVER_475, BoxloreTestWindows.PIXEL_FOLD, BoxloreTestWindows.COVER_475, BoxloreTestWindows.PHONE)
        compose.walkWindows(window, *walk) { step ->
            // Settled on the first frame of each size: nothing moves on the next.
            val panel = compose.onAllNodesWithTag(OpeningPanelTag).fetchSemanticsNodes().single().boundsInRoot
            val reel = compose.onAllNodesWithTag("welcome_cover_reel").fetchSemanticsNodes().single().boundsInRoot
            val find = way("Find shows for me").fetchSemanticsNode().boundsInRoot
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("the panel jumps on $step", panel, compose.onAllNodesWithTag(OpeningPanelTag).fetchSemanticsNodes().single().boundsInRoot)
            assertEquals("the covers jump on $step", reel.height, compose.onAllNodesWithTag("welcome_cover_reel").fetchSemanticsNodes().single().boundsInRoot.height, 0.5f)
            assertEquals("the ways in jump on $step", find, way("Find shows for me").fetchSemanticsNode().boundsInRoot)
            val now = clock.elapsedMillis.value
            assertTrue("the entrance went back on $step ($now < $elapsed)", now >= elapsed)
            elapsed = now
            assertTrue("the reveal stays complete on $step", clock.revealComplete.value)
        }
    }

    @Test
    fun `beside the panel the clock icons are light, and the panel reaches the edge behind a cutout`() {
        window.value = BoxloreTestWindows.PHONE_LAND
        show(SystemInsets.SIDEWAYS_3_BUTTON)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        val panel = compose.onAllNodesWithTag(OpeningPanelTag).fetchSemanticsNodes().single().boundsInRoot
        assertEquals("the panel starts at the window's edge", screen().left, panel.left, 0.5f)
        val logo = compose.onAllNodesWithTag(OpeningLogoTag).fetchSemanticsNodes().single().boundsInRoot
        assertTrue("the logo keeps clear of the cutout", logo.left >= screen().left + SystemInsets.SIDEWAYS_3_BUTTON.cutoutStart - 0.5f)
        compose.walkWindows(window, BoxloreWindowLayout(840f, 600f), BoxloreTestWindows.PHONE_LAND) { step ->
            val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
            assertFalse("dark clock icons over the panel on $step", controller.isAppearanceLightStatusBars)
        }
    }

    @Test
    fun `the wordmark and the app mark never run together in a narrow panel`() {
        window.value = BoxloreWindowLayout(600f, 480f)
        show(SystemInsets.SIDEWAYS_3_BUTTON)
        compose.mainClock.advanceTimeBy(500)
        compose.walkWindows(window, BoxloreWindowLayout(600f, 480f), BoxloreTestWindows.COVER_LAND, BoxloreTestWindows.PHONE_LAND, BoxloreWindowLayout(600f, 480f)) { step ->
            compose.mainClock.advanceTimeBy(100)
            val wordmark = compose.onAllNodesWithTag(OpeningWordmarkTag).fetchSemanticsNodes().single().boundsInRoot
            val panel = compose.onAllNodesWithTag(OpeningPanelTag).fetchSemanticsNodes().single().boundsInRoot
            assertTrue("the wordmark runs out of the panel on $step", wordmark.right <= panel.right + 0.5f)
            compose.onAllNodesWithTag(OpeningMarkTag).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let { mark ->
                assertTrue("the wordmark is ${mark.left - wordmark.right}dp from the mark on $step", mark.left - wordmark.right >= OpeningLogoGap.value - 0.5f)
            }
        }
    }

    /** Whether Echo's peek stands above Find shows for me, or null where Echo doesn't show. */
    private fun echoRisen(): Boolean? {
        val echo = compose.onAllNodesWithTag("welcome_echo").fetchSemanticsNodes().singleOrNull()?.boundsInRoot ?: return null
        return way("Find shows for me").fetchSemanticsNode().boundsInRoot.top - echo.top > 30f
    }

    private companion object {
        val SIDEWAYS_SMALL = BoxloreWindowLayout(740f, 360f)
    }
}
