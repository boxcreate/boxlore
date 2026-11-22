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
    private lateinit var clock: WelcomeMotionClock

    private val ways = listOf("Find shows for me", "Search podcasts", "Import library", "Skip setup")

    private fun show(insets: SystemInsets? = null) {
        compose.mainClock.autoAdvance = false
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            clock = rememberWelcomeMotionClock()
            BoxLoreTheme {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        WelcomeScreen(
                            onHelpMeFind = { calls += "find" },
                            onSearch = { calls += "search" },
                            onSkip = { calls += "skip" },
                            onImportClick = { calls += "import" },
                            clock = clock,
                        )
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
    fun `turning mid-reveal carries the entrance on and each way in calls once`() {
        show()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        var elapsed = clock.elapsedMillis.value
        assertTrue("mid-reveal", elapsed > 0 && !clock.revealComplete.value)
        var completions = 0
        var wasComplete = false

        val turns = with(BoxloreTestWindows) { arrayOf(PHONE_LAND, PHONE, TABLET_PORTRAIT, TABLET, TABLET_PORTRAIT, PHONE) }
        compose.walkWindows(window, *turns) { step ->
            compose.mainClock.advanceTimeBy(300)
            val now = clock.elapsedMillis.value
            assertTrue("the entrance went back on $step ($now < $elapsed)", now > elapsed)
            elapsed = now
            if (clock.revealComplete.value && !wasComplete) completions++
            wasComplete = clock.revealComplete.value
        }
        assertEquals("the reveal completes once", 1, completions)

        ways.forEach { way(it).performClick() }
        assertEquals(listOf("find", "search", "import", "skip"), calls)
    }

    private companion object {
        val SIDEWAYS_SMALL = BoxloreWindowLayout(740f, 360f)
    }
}
