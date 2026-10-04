package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class HomeLoadingRevealTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `loading cover never leaves a blank frame when ready animation time stops`() {
        composeRule.mainClock.autoAdvance = false
        val ready = mutableStateOf(false)
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            Box(Modifier.background(Color.Black)) {
                HomeLoadingReveal(
                    ready = ready.value,
                    modifier = Modifier.testTag("handoff"),
                    placeholder = { Box(Modifier.size(120.dp, 40.dp).background(Color.Blue)) },
                ) {
                    Box(Modifier.size(120.dp, 40.dp).background(Color.Red))
                }
            }
        }
        composeRule.onNodeWithTag("handoff").assertExists()
        assertEquals(Color.Blue.toArgb(), centerPixel(view))
        composeRule.runOnIdle {
            ready.value = true
            Snapshot.sendApplyNotifications()
        }
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        assertNotEquals(Color.Black.toArgb(), centerPixel(view))
        composeRule.onNodeWithTag("handoff").assertExists()
        composeRule.mainClock.advanceTimeBy(240)
        composeRule.waitForIdle()
        assertEquals(Color.Red.toArgb(), centerPixel(view))
    }

    @Test
    fun `cover fades over a single opaque content tree then disposes skeleton`() {
        composeRule.mainClock.autoAdvance = false
        val ready = mutableStateOf(false)
        var compositions = 0
        composeRule.setContent {
            HomeLoadingReveal(
                ready = ready.value,
                placeholder = { Box(Modifier.size(120.dp, 40.dp).testTag("skeleton")) },
            ) {
                SideEffect { compositions++ }
                Box(Modifier.size(120.dp, 40.dp).testTag("content"))
            }
        }
        composeRule.onNodeWithTag("skeleton").assertExists()
        composeRule.runOnIdle {
            ready.value = true
            Snapshot.sendApplyNotifications()
        }
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("content").assertExists()
        composeRule.onNodeWithTag("skeleton").assertExists()
        val initialCompositions = compositions
        repeat(5) {
            composeRule.mainClock.advanceTimeBy(16)
            composeRule.waitForIdle()
        }
        assertEquals(initialCompositions, compositions)
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("skeleton").assertDoesNotExist()
        assertEquals(initialCompositions, compositions)
    }

    @Test
    fun `cached content is present immediately without a skeleton`() {
        composeRule.setContent {
            HomeLoadingReveal(
                ready = true,
                placeholder = { Box(Modifier.testTag("skeleton")) },
            ) { Box(Modifier.testTag("content")) }
        }
        composeRule.onNodeWithTag("content").assertExists()
        composeRule.onNodeWithTag("skeleton").assertDoesNotExist()
    }

    private fun centerPixel(view: View): Int {
        val bounds = composeRule.onNodeWithTag("handoff").fetchSemanticsNode().boundsInRoot
        return composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()).also { bitmap.recycle() }
        }
    }
}
