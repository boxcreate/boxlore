package cx.aswin.boxlore.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationRail
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletAuroraTest {
    private val scale = mutableFloatStateOf(0f)

    @get:Rule val compose = createComposeRule(
        effectContext = object : MotionDurationScale {
            override val scaleFactor: Float get() = scale.floatValue
        },
    )
    private lateinit var view: View

    @Test
    fun `Lore aurora holds still with motion off and responds to system setting changes`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            BoxLoreTheme(dynamicColor = false) { BoxLoreNavigationRail("learn", {}, Modifier.width(80.dp).height(600.dp)) }
        }
        advance(400)
        val stopped = pixels()
        advance(5_000)
        assertArrayEquals(stopped, pixels())
        compose.runOnUiThread { scale.floatValue = 1f }
        advance(5_000)
        assertFalse(stopped.contentEquals(pixels()))
        compose.runOnUiThread { scale.floatValue = 0f }
        advance(400)
        val stoppedAgain = pixels()
        advance(5_000)
        assertArrayEquals(stoppedAgain, pixels())
    }

    private fun advance(millis: Long) {
        compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun pixels(): IntArray {
        val bounds = compose.onNodeWithTag("navigation_rail_indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        var result = intArrayOf()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val width = bounds.width.toInt()
            val height = bounds.height.toInt()
            result = IntArray(width * height)
            bitmap.getPixels(result, 0, width, bounds.left.toInt(), bounds.top.toInt(), width, height)
            bitmap.recycle()
        }
        return result
    }
}
