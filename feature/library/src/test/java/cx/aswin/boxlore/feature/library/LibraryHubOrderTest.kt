package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Playlists card is the hub's last card on phone and in the tablet grid, through window changes both ways. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryHubOrderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `playlists card comes after every other hub card in phone and tablet layouts`() {
        val window = mutableStateOf(BoxloreWindowLayout(360f, 1500f))
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
        val layouts = listOf(
            BoxloreWindowLayout(360f, 1500f),
            BoxloreWindowLayout(700f, 1500f),
            BoxloreWindowLayout(1280f, 1500f),
            BoxloreWindowLayout(599f, 1500f),
        )
        for (layout in layouts) {
            compose.runOnIdle { window.value = layout }
            val playlists = top("Playlists")
            for (other in listOf("Subscriptions", "Liked Episodes", "Downloads", "Listening History")) {
                assertTrue("$other should sit above Playlists at ${layout.widthDp}dp", top(other) < playlists)
            }
        }
    }

    private fun top(title: String): Float =
        compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
}
