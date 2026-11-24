package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
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

    @Test
    fun `the list keeps its place and saved state while the window crosses the tablet size both ways`() {
        val window = mutableStateOf(BoxloreTestWindows.COVER_475)
        val listStates = mutableSetOf<LazyListState>()
        var flag by mutableStateOf<Boolean?>(null)
        var plays = 0
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(window.value.widthDp.dp).height(window.value.heightDp.dp)) {
                        LibraryPlaybackLayout(Modifier.fillMaxSize(), content = {
                            val listState = rememberLazyListState().also { listStates += it }
                            var open by rememberSaveable { mutableStateOf(false) }
                            flag = open
                            LazyColumn(Modifier.fillMaxSize().testTag("episodes"), state = listState) {
                                items(60) { index ->
                                    Button({ open = true }, Modifier.height(80.dp).testTag("row_$index")) { Text("Row $index") }
                                }
                            }
                        }, playbackAction = { PlayAllFab({ plays++ }, false, Modifier.testTag("play_all")) })
                    }
                }
            }
        }
        compose.onNodeWithTag("episodes").performScrollToIndex(40)
        compose.onNodeWithTag("row_40").performClick()
        val there = with(BoxloreTestWindows) { listOf(FOLD_FLAT, W599_H900, W600_H900, W900_H479, W900_H480) }
        val back = there.reversed().drop(1) + BoxloreTestWindows.COVER_475
        compose.walkWindows(window, *(there + back).toTypedArray()) { step ->
            assertEquals("${step.widthDp}x${step.heightDp}", 40, listStates.single().firstVisibleItemIndex)
            assertEquals(true, flag)
            if (step.usesTabletLayout) {
                val action = compose.onNodeWithTag("play_all").fetchSemanticsNode().boundsInRoot
                val list = compose.onNodeWithTag("episodes").fetchSemanticsNode().boundsInRoot
                assertTrue("Play All sits under the list at ${step.widthDp}x${step.heightDp}", action.top >= list.bottom)
            }
            compose.onAllNodesWithText("Play All")[0].performClick()
        }
        assertEquals(there.size + back.size, plays)
    }

    @Test
    fun `phones keep Play All exactly where the plain overlay put it`() {
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    Row {
                        Box(Modifier.width(412.dp).height(915.dp).testTag("new")) {
                            LibraryPlaybackLayout(Modifier.fillMaxSize(), content = {
                                LazyColumn(Modifier.fillMaxSize()) { items(20) { Text("Row $it", Modifier.height(80.dp)) } }
                            }, playbackAction = { PlayAllFab({}, true, Modifier.testTag("new_play")) })
                        }
                        // Today's phone layout: the list with the button drawn over it in one Box.
                        Box(Modifier.width(412.dp).height(915.dp).testTag("old")) {
                            LazyColumn(Modifier.fillMaxSize()) { items(20) { Text("Row $it", Modifier.height(80.dp)) } }
                            PlayAllFab({}, true, Modifier.testTag("old_play"))
                        }
                    }
                }
            }
        }
        val newBox = compose.onNodeWithTag("new").fetchSemanticsNode().boundsInRoot
        val oldBox = compose.onNodeWithTag("old").fetchSemanticsNode().boundsInRoot
        val newPlay = compose.onNodeWithTag("new_play").fetchSemanticsNode().boundsInRoot
        val oldPlay = compose.onNodeWithTag("old_play").fetchSemanticsNode().boundsInRoot
        assertEquals(oldPlay.translate(-oldBox.left, -oldBox.top), newPlay.translate(-newBox.left, -newBox.top))
    }
}
