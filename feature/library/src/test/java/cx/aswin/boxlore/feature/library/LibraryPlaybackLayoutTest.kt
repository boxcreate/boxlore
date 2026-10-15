package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryPlaybackLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tablet Play All reserves space outside list controls in portrait and landscape`() {
        val window = mutableStateOf(BoxloreWindowLayout(1280f, 800f))
        var plays = 0
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalBoxloreMiniPlayerVisible provides true) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(window.value.widthDp.dp).height(window.value.heightDp.dp)) {
                        LibraryPlaybackLayout(Modifier.fillMaxSize(), content = {
                            LazyColumn(Modifier.fillMaxSize().testTag("episodes")) {
                                items(20) { index -> Button({}, Modifier.height(80.dp)) { Text("Episode $index") } }
                            }
                        }, playbackAction = { PlayAllFab({ plays++ }, true, Modifier.testTag("play_all")) })
                    }
                }
            }
        }
        for (layout in listOf(BoxloreWindowLayout(1280f, 800f), BoxloreWindowLayout(800f, 1280f))) {
            compose.runOnIdle { window.value = layout }
            val list = compose.onNodeWithTag("episodes").fetchSemanticsNode().boundsInRoot
            val action = compose.onNodeWithTag("play_all").fetchSemanticsNode().boundsInRoot
            assertTrue(list.height > 400f)
            assertTrue(list.bottom <= action.top)
            compose.onNodeWithText("Play All").performClick()
        }
        assertEquals(2, plays)
    }
}
