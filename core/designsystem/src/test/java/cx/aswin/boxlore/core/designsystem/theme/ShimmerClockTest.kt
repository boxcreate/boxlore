package cx.aswin.boxlore.core.designsystem.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShimmerClockTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var view: View

    @Test
    fun aBlockFollowsASharedClockThatArrivesAfterItWasAttached() {
        // Regression: the block read the shared clock once, when attached, and kept its own clock after.
        val shared = mutableStateOf(0.5f)
        var scoped by mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            Row {
                CompositionLocalProvider(LocalShimmerPhase provides if (scoped) shared else null) {
                    Box(Modifier.size(BLOCK.dp).m3Shimmer(Color.Black, Color.White))
                }
                CompositionLocalProvider(LocalShimmerPhase provides shared) {
                    Box(Modifier.size(BLOCK.dp).m3Shimmer(Color.Black, Color.White))
                }
            }
        }
        // Its own clock has only just started, far from the shared clock's halfway point.
        assertFalse("its own clock starts elsewhere", blocksMatch())

        scoped = true
        compose.waitForIdle()

        assertTrue("it now draws on the shared clock", blocksMatch())
    }

    /** Whether the first block's middle row of pixels matches the second's: the same band in the same place. */
    private fun blocksMatch(): Boolean = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        try {
            (0 until BLOCK).all { x -> bitmap.getPixel(x, BLOCK / 2) == bitmap.getPixel(BLOCK + x, BLOCK / 2) }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val BLOCK = 40
    }
}
