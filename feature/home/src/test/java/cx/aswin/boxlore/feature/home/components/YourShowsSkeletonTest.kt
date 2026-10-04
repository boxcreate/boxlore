package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
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
@Config(sdk = [33], qualifiers = "w420dp-h800dp")
class YourShowsSkeletonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `unknown subscription count shows five covers in both rows`() {
        assertFiveCoversInBothRows(subscribedCount = 0)
    }

    @Test
    fun `known two row subscription count shows five covers in both rows`() {
        assertFiveCoversInBothRows(subscribedCount = 8)
    }

    private fun assertFiveCoversInBothRows(subscribedCount: Int) {
        lateinit var view: View
        var density = 1f
        composeRule.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            MaterialTheme(colorScheme = darkColorScheme(onSurface = Color.White)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ShimmerScope(active = false) {
                        YourShowsSkeleton(subscribedCount, Modifier.testTag("your_shows_skeleton"))
                    }
                }
            }
        }
        val bounds = composeRule.onNodeWithTag("your_shows_skeleton").fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                for (row in 0..1) {
                    val y = (bounds.top + (78 + row * 72) * density).toInt()
                    for (column in 0..4) {
                        val x = (bounds.left + (30 + column * 72) * density).toInt()
                        assertNotEquals("Missing cover in row ${row + 1}, column ${column + 1}", Color.Black.toArgb(), bitmap.getPixel(x, y))
                    }
                    val gapX = (bounds.left + 66 * density).toInt()
                    assertEquals("Covers remain separated by a gap", Color.Black.toArgb(), bitmap.getPixel(gapX, y))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
