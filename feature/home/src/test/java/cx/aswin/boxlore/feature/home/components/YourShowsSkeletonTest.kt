package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w420dp-h1000dp-mdpi")
class YourShowsSkeletonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `unknown count reserves both rows with visible cover placeholders`() {
        verifyPlaceholderRows(subscribedCount = 0, secondCount = 5)
    }

    @Test fun `known count matches artwork-only selectors in two rows`() {
        verifyPlaceholderRows(subscribedCount = 8, secondCount = 4)
    }

    @Test fun `landscape skeleton grows with text and leaves the next card visible`() {
        val scale = mutableStateOf(1f)
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale.value)) {
                MaterialTheme {
                    ShimmerScope(active = false) { YourShowsSkeleton(3) }
                }
            }
        }
        for (value in listOf(1f, 1.6f, 2f)) {
            scale.value = value
            composeRule.waitForIdle()
            val first = bounds("home_mix_skeleton_0")
            val second = bounds("home_mix_skeleton_1")
            assertEquals(HomeMixLayout.CardHeight.value * value * density, first.height, 1f)
            assertTrue(second.left > first.right && second.width > 0f)
        }
    }

    private fun verifyPlaceholderRows(subscribedCount: Int, secondCount: Int) {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = darkColorScheme(onSurface = Color.White)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ShimmerScope(active = false) { YourShowsSkeleton(subscribedCount, Modifier.testTag("skeleton")) }
                }
            }
        }
        val covers = (0..1).flatMap { row ->
            (0 until if (row == 0) 5 else secondCount).map { column -> bounds("home_show_skeleton_${row}_$column") }
        }
        val section = bounds("skeleton")
        assertTrue(covers.all { it.left >= section.left && it.right <= section.right })
        assertTrue(covers[5].top > covers[0].bottom)
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                covers.forEach { cover ->
                    assertNotEquals("Visible cover backing", Color.Black.toArgb(), bitmap.getPixel(cover.center.x.toInt(), (cover.top + cover.width / 2).toInt()))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
}
