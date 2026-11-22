package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreBottomChromeReserved
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreDockedPlayerBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreNavigationRailVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreSnackbarBottomPadding
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
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
 * [BoxloreSnackbarHost] puts messages just above the bottom bar and mini player, in a card at most
 * 480dp wide, and moves the action to its own line when the message wraps. [BoxloreScrollableCenteredColumn]
 * stays centred when it fits and scrolls when it doesn't.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoxloreSnackbarHostTest {
    @get:Rule val compose = createComposeRule()

    private data class Chrome(
        val window: BoxloreWindowLayout,
        val style: NavigationStyle = NavigationStyle.Floating,
        val miniPlayer: Boolean = false,
        val rail: Boolean = false,
        val reserved: Boolean = false,
        val pane: BoxloreLayoutBounds? = null,
        val dock: BoxloreLayoutBounds? = null,
    )

    @Test
    fun `the snackbar clears the bar and the mini player wherever they cover the pane`() {
        val dock = BoxloreLayoutBounds(80f, 0f, 760f, 800f)
        val cases = listOf(
            // Floating bar 56 + 12 under it, then the 8dp gap.
            Chrome(BoxloreTestWindows.PHONE) to 76f,
            // Plus the mini player 64 and its 8dp gap.
            Chrome(BoxloreTestWindows.PHONE, miniPlayer = true) to 148f,
            // Classic: an 80dp bar and a 72dp mini player.
            Chrome(BoxloreTestWindows.PHONE, NavigationStyle.Classic, miniPlayer = true) to 168f,
            // Tablet with a rail: the docked mini player over this pane, then the 24dp tablet gap.
            Chrome(BoxloreTestWindows.TABLET, miniPlayer = true, rail = true, pane = dock, dock = dock) to 96f,
            // A detail pane beside the dock clears nothing but the gap.
            Chrome(BoxloreTestWindows.TABLET, miniPlayer = true, rail = true, pane = BoxloreLayoutBounds(776f, 0f, 1_280f, 800f), dock = dock) to 24f,
            // Above chrome the shell already reserves, only the mini player counts.
            Chrome(BoxloreTestWindows.PHONE, miniPlayer = true, reserved = true) to 80f,
        )
        var case by mutableStateOf(cases.first().first)
        var padding = -1f
        var extra = -1f
        compose.setContent {
            Provide(case) {
                padding = boxloreSnackbarBottomPadding().value
                extra = boxloreSnackbarBottomPadding(extra = 56.dp).value
            }
        }
        cases.forEach { (chrome, expected) ->
            compose.runOnIdle { case = chrome }
            compose.waitForIdle()
            assertEquals("$chrome", expected, padding, 0.5f)
            assertEquals("$chrome", expected + 56f, extra, 0.5f)
        }
    }

    @Test
    fun `on a phone the message sits above the mini player`() {
        val host = SnackbarHostState()
        compose.setContent {
            Window(Chrome(BoxloreTestWindows.PHONE, miniPlayer = true)) {
                // Where the shell draws the mini player: above the floating bar.
                val miniBottom = appBottomChromeContentPadding(NavigationStyle.Floating, isMiniPlayerVisible = false)
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = miniBottom).fillMaxWidth().height(64.dp).testTag("mini_player"))
                BoxloreSnackbarHost(host, Modifier.align(Alignment.BottomCenter))
                LaunchedEffect(Unit) { host.showSnackbar("Removed from history", "Undo", duration = SnackbarDuration.Indefinite) }
            }
        }
        compose.waitForIdle()
        val snackbar = compose.onNodeWithTag(BOXLORE_SNACKBAR_TAG).fetchSemanticsNode().boundsInRoot
        val mini = compose.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue("snackbar ${snackbar.bottom} above mini player ${mini.top}", snackbar.bottom <= mini.top)
        assertEquals(412f - 32f, snackbar.width, 0.5f)
    }

    @Test
    fun `on a tablet the message is 480dp wide and centred`() {
        val host = SnackbarHostState()
        compose.setContent {
            Window(Chrome(BoxloreTestWindows.TABLET, rail = true)) {
                BoxloreSnackbarHost(host, Modifier.align(Alignment.BottomCenter))
                LaunchedEffect(Unit) { host.showSnackbar("Removed from history", "Undo", duration = SnackbarDuration.Indefinite) }
            }
        }
        compose.waitForIdle()
        val snackbar = compose.onNodeWithTag(BOXLORE_SNACKBAR_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(480f, snackbar.width, 0.5f)
        assertEquals(640f, snackbar.center.x, 0.5f)
    }

    @Test
    fun `a long message moves its action to its own line and stays inside the fold cover`() {
        val host = SnackbarHostState()
        val window = BoxloreTestWindows.COVER_475
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Window(Chrome(window)) {
                    BoxloreSnackbarHost(host, Modifier.align(Alignment.BottomCenter))
                    LaunchedEffect(Unit) {
                        host.showSnackbar(
                            "Die Folge wurde aus deinem Hörverlauf entfernt und erscheint nicht mehr in deinen Listen",
                            "Rückgängig",
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val snackbar = compose.onNodeWithTag(BOXLORE_SNACKBAR_TAG).fetchSemanticsNode().boundsInRoot
        val message = compose.onNodeWithText("Die Folge", substring = true).fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithText("Rückgängig").fetchSemanticsNode().boundsInRoot
        assertTrue("action ${action.top} below message ${message.bottom}", action.top >= message.bottom)
        assertTrue(snackbar.left >= 0f && snackbar.right <= window.widthDp)
        assertTrue(snackbar.top >= 0f && snackbar.bottom <= window.heightDp)
    }

    @Test
    fun `a centred panel scrolls to its button when the window is short and stays centred when it fits`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        compose.setContent {
            Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                BoxloreScrollableCenteredColumn(Modifier.testTag("panel")) {
                    Box(Modifier.fillMaxWidth().height(500.dp).testTag("panel_body"))
                    Button(onClick = {}, Modifier.height(48.dp)) { Text("Try again") }
                }
            }
        }
        compose.walkWindows(window, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE) { step ->
            val body = compose.onNodeWithTag("panel_body").fetchSemanticsNode().boundsInRoot
            if (step == BoxloreTestWindows.PHONE_LAND) {
                compose.onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
                val button = compose.onNodeWithText("Try again").fetchSemanticsNode().boundsInRoot
                assertTrue("button ${button.bottom} inside ${step.heightDp}", button.bottom <= step.heightDp + 0.5f)
            } else {
                // 548dp of content centred in 915dp.
                val panel = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
                assertEquals("panel $panel body $body", (915f - 548f) / 2f, body.top, 0.5f)
            }
        }
    }

    @Composable
    private fun Provide(chrome: Chrome, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalBoxloreWindowLayout provides chrome.window,
            LocalNavigationStyle provides chrome.style,
            LocalBoxloreMiniPlayerVisible provides chrome.miniPlayer,
            LocalBoxloreNavigationRailVisible provides chrome.rail,
            LocalBoxloreBottomChromeReserved provides chrome.reserved,
            LocalBoxlorePaneBounds provides chrome.pane,
            LocalBoxloreDockedPlayerBounds provides chrome.dock,
            content = content,
        )
    }

    @Composable
    private fun Window(chrome: Chrome, content: @Composable BoxScope.() -> Unit) {
        Provide(chrome) {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.size(chrome.window.widthDp.dp, chrome.window.heightDp.dp), content = content)
            }
        }
    }
}
