package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.navigation.TabletContextRoot
import cx.aswin.boxlore.navigation.rememberTabletDetailNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The late-night prompt (and the other playback notices) sit in one half of a half-open fold, just
 * below the top of that half, and keep the listener's pick while the fold moves. Tablet cases host
 * the notices in the real shell's browsing pane, which starts beside the rail and below a hinge. Phones keep the
 * prompt where it was: a status-bar height plus 8dp from the top, 16dp from each side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppPlaybackNoticesTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_000, 1_000, false))
    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val timers = mutableListOf<Int>()
    private var dismissed = 0

    @Test
    fun `on a phone the prompt sits where it always has`() {
        render()
        val prompt = prompt()
        assertEquals(24f + 8f, prompt.top, 1f)
        assertEquals(16f, prompt.left, 1f)
        assertEquals(412f - 32f, prompt.width, 1f)
    }

    @Test
    fun `in book posture the prompt stays in one half and keeps its pick through folding`() {
        window.value = BoxloreTestWindows.FOLD_FLAT
        render(inShell = true)
        fixture.compose.onNodeWithText("45m").performClick()
        assertEquals(listOf(45), timers)
        fixture.compose.walkWindows(window, BoxloreTestWindows.FOLD_BOOK, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.FOLD_BOOK) { step ->
            advance()
            assertInOneRegion(step)
        }
        fixture.compose.mainClock.advanceTimeBy(1_800L)
        fixture.compose.onNodeWithText("Pausing in 45 min").assertExists()
        // The pick set the timer once, whatever the fold did.
        assertEquals(listOf(45), timers)
        assertEquals(0, dismissed)
    }

    @Test
    fun `with the player docked in book posture the prompt stays clear of the hinge`() {
        window.value = BoxloreTestWindows.FOLD_FLAT
        render(inShell = true, playerDocked = true)
        fixture.compose.walkWindows(window, BoxloreTestWindows.FOLD_BOOK, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.FOLD_BOOK) { step ->
            advance()
            assertInOneRegion(step)
        }
    }

    @Test
    fun `in tabletop while playing the prompt shows at the top of the lower half`() {
        window.value = BoxloreTestWindows.FOLD_TABLETOP
        render(inShell = true)
        assertEquals(336f + 8f, prompt().top, 1f)
        assertInOneRegion(BoxloreTestWindows.FOLD_TABLETOP)
        // Flat again, it is back below the status bar, and folding brings it back to the lower half.
        fixture.compose.walkWindows(window, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.FOLD_TABLETOP, BoxloreTestWindows.FOLD_FLAT) { step ->
            advance()
            assertEquals(if (step.tabletopFold != null) 344f else 32f, prompt().top, 1f)
            assertInOneRegion(step)
        }
        fixture.compose.onNodeWithText("45m").performClick()
        assertEquals(listOf(45), timers)
    }

    /** The prompt is on screen (it has a height) and inside one fold-safe region. */
    private fun assertInOneRegion(step: BoxloreWindowLayout) {
        val prompt = prompt()
        assertTrue("$step $prompt", prompt.height > 40f)
        assertTrue(
            "$step $prompt",
            step.foldSafeRegions().any {
                it.left <= prompt.left + 0.5f && prompt.right <= it.right + 0.5f && it.top <= prompt.top + 0.5f && prompt.bottom <= it.bottom + 0.5f
            },
        )
    }

    /** Lets a window change settle, well inside the prompt's confirmation time. */
    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(100L)
        fixture.compose.waitForIdle()
    }

    @Composable
    private fun Notices() {
        AppPlaybackNotices(
            repairVisible = false,
            nudgeVisible = true,
            sleepActions = LateNightNudgeActions(setSleepTimer = { timers += it }, dismissNudge = { dismissed++ }),
        )
    }

    private fun prompt(): Rect = fixture.compose.onNodeWithTag(SLEEP_PROMPT_TAG).fetchSemanticsNode().boundsInRoot

    /**
     * The notices alone at the window's corner, as phones place them, or [inShell] in the tablet
     * shell's browsing pane while an episode plays, as `BoxLoreAppRoot` places them on tablets.
     */
    private fun render(inShell: Boolean = false, playerDocked: Boolean = false) {
        fixture.prepare()
        fixture.compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        if (inShell) {
                            val navigation = rememberTabletDetailNavigation(rememberNavController())
                            navigation.playerDocked = playerDocked
                            TabletAppShell(
                                navigation,
                                navigationVisible = true,
                                playerAllowed = true,
                                hasEpisode = true,
                                activeTab = "home",
                                onNavigateTab = { _, _ -> },
                                content = TabletShellContent(
                                    primary = { Box(Modifier.fillMaxSize()) { Notices() } },
                                    details = { NavHost(navigation.detail, TabletContextRoot) { composable(TabletContextRoot) {} } },
                                    player = { Text("Playback") },
                                ),
                            )
                        } else {
                            Notices()
                        }
                    }
                }
            }
        }
        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets.TABLETOP_STATUS) }
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeBy(1_200L)
        fixture.compose.waitForIdle()
    }
}
