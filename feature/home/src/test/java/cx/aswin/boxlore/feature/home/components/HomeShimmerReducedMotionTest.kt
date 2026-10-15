package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression: a shimmer block outside any ShimmerScope must hold still when animations are off. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class HomeShimmerReducedMotionTest {
    private lateinit var view: View

    @get:Rule
    val composeRule = createComposeRule(
        effectContext = object : MotionDurationScale {
            override val scaleFactor = 0f
        },
    )

    @Test
    fun `a standalone block holds still when animations are off`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            Box(
                Modifier.size(120.dp, 40.dp).background(Color.White).testTag("shimmer")
                    .m3Shimmer(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.12f)),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        val initial = centerPixel()
        composeRule.mainClock.advanceTimeBy(400)
        assertEquals("With animations off the highlight must not move", initial, centerPixel())
    }

    private fun centerPixel(): Color {
        val bounds = composeRule.onNodeWithTag("shimmer").fetchSemanticsNode().boundsInRoot
        var pixel = 0
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            pixel = bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())
            bitmap.recycle()
        }
        return Color(pixel)
    }
}
