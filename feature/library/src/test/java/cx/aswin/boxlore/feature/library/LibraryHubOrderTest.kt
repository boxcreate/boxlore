package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
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
 * The hub's cards keep their order (Playlists last) and their phone geometry, and one grid keeps
 * the hub where it was when the window moves between phone and tablet sizes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryHubOrderTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 1500f))

    private fun showHub() {
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(window.value.widthDp.dp).height(window.value.heightDp.dp)) {
                        LibraryContent(
                            uiState = LibraryUiState.Success(),
                            onNavigateToLiked = {},
                            onNavigateToSubscriptions = {},
                            onNavigateToDownloads = {},
                            onNavigateToHistory = {},
                            playlists = LibraryPlaylistsCard(onClick = {}),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `playlists card comes after every other hub card in phone and tablet layouts`() {
        showHub()
        val layouts = listOf(
            BoxloreWindowLayout(360f, 1500f),
            BoxloreWindowLayout(700f, 1500f),
            BoxloreWindowLayout(1280f, 1500f),
            BoxloreWindowLayout(599f, 1500f),
        )
        for (layout in layouts) {
            compose.runOnIdle { window.value = layout }
            val playlists = card("Playlists").top
            for (other in listOf("Subscriptions", "Liked episodes", "Downloads", "Listening history")) {
                assertTrue("$other should sit above Playlists at ${layout.widthDp}dp", card(other).top < playlists)
            }
        }
    }

    @Test
    fun `phone cards keep their size and spacing`() {
        showHub()
        for (phone in listOf(BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_SMALL)) {
            compose.runOnIdle { window.value = phone }
            val subscriptions = card("Subscriptions")
            val liked = card("Liked episodes")
            // The large header (152dp) and the grid's 8dp top padding come first.
            assertEquals(160f, subscriptions.top)
            assertEquals(16f, subscriptions.left)
            assertEquals(phone.widthDp - 32f, subscriptions.width)
            assertEquals(120f, subscriptions.height)
            assertEquals(subscriptions.bottom + 16f, liked.top)
        }
    }

    @Test
    fun `the hub stays scrolled to Playlists while the window crosses the tablet size both ways`() {
        window.value = BoxloreTestWindows.COVER_443
        showHub()
        compose.onNode(hasScrollToKeyAction()).performScrollToKey(LibraryHubKeys.PLAYLISTS)
        compose.onNodeWithText("Playlists").assertIsDisplayed()
        with(BoxloreTestWindows) {
            compose.walkWindows(window, GATE_600x480, W900_H479, GATE_600x480, COVER_443) {
                compose.onNodeWithText("Playlists").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `a header collapsed on the cover opens again once the unfolded hub is at its top`() {
        window.value = BoxloreTestWindows.COVER_443
        showHub()
        // A short drag: enough to collapse the header while the first card stays on screen.
        compose.onNode(hasScrollToKeyAction()).performTouchInput { swipeUp(startY = centerY, endY = centerY - 150f, durationMillis = 2_000) }
        compose.waitForIdle()
        assertTrue("the cover's header collapsed", card("Subscriptions").top < EXPANDED_FIRST_CARD_TOP)
        with(BoxloreTestWindows) {
            compose.walkWindows(window, FOLD_FLAT, COVER_443) {
                // Wherever the window leaves the grid, the header is open whenever its first card is fully in view.
                compose.onNode(hasScrollToKeyAction()).performScrollToKey(LibraryHubKeys.SUBSCRIPTIONS)
                compose.waitForIdle()
                assertEquals(EXPANDED_FIRST_CARD_TOP, card("Subscriptions").top)
            }
        }
    }

    private fun card(title: String) = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot

    private companion object {
        /** The large header (152dp) and the grid's 8dp top padding. */
        const val EXPANDED_FIRST_CARD_TOP = 160f
    }
}
