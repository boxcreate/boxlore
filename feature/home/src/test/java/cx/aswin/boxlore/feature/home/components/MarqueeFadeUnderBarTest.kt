package cx.aswin.boxlore.feature.home.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Words that move without Home scrolling (a resize, a card of another height) redraw their fade under
 * the top bar: the move alone must update it, not only the next scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w300dp-h500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarqueeFadeUnderBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `words moved under the bar at a fixed scroll fade at once`() {
        // A 100dp bar and a fixed 80dp scroll: the bar's foot is 180dp down the hero.
        val top = mutableIntStateOf(220)
        compose.setContent {
            CompositionLocalProvider(LocalHeroTopInset provides 100.dp, LocalHeroScrollOffset provides { 80 }) {
                // The words' block is moved in placement only, so nothing recomposes: only its position changes.
                Layout(
                    content = { Box(Modifier.size(200.dp, 100.dp).marqueeFadesUnderBar().background(Color.Red)) },
                    modifier = Modifier.size(300.dp, 500.dp).background(Color.White),
                ) { measurables, constraints ->
                    val words = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) { words.place(0, top.intValue) }
                }
            }
        }
        compose.waitForIdle()
        assertTrue("below the bar, the words are whole", isRed(x = 10, y = 221))

        // Moved up 100dp at the same scroll: the bar's foot is now 60dp into the words, so their top is clear.
        compose.runOnIdle { top.intValue = 120 }
        compose.waitForIdle()
        assertTrue("the top of the words has faded under the bar", !isRed(x = 10, y = 121))
        assertTrue("the words below the bar's foot stay whole", isRed(x = 10, y = 210))
    }

    private fun isRed(x: Int, y: Int): Boolean {
        val file = File.createTempFile("marquee-fade", ".png")
        try {
            compose.onRoot().captureRoboImage(file.absolutePath, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            val pixel = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)).getPixel(x, y)
            return android.graphics.Color.red(pixel) > 230 && android.graphics.Color.green(pixel) < 25 && android.graphics.Color.blue(pixel) < 25
        } finally {
            file.delete()
        }
    }
}
