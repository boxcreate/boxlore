package cx.aswin.boxlore.navigation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.PredictiveBackWrapper
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkLoaderColors
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkPredictiveBackBackgroundTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var view: View

    @Test fun `back wrapper paints the changing page background instead of the saved app surface`() {
        val base = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), false)
        val parent = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), false)
        val child = generatePersonalColorScheme(CustomThemeSeeds(Color.Green), false)
        val background = mutableStateOf(parent.background)
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = base) {
                Box(Modifier.requiredSize(100.dp)) {
                    PredictiveBackWrapper(enabled = false, onBack = {}, modifier = Modifier.testTag("back"), backgroundColor = background.value) {}
                }
            }
        }
        assertBackground(parent.background)
        background.value = child.background
        assertBackground(child.background)
    }

    @Test fun `loader paints the inherited accent without changing the surrounding page theme`() {
        val base = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), false)
        val parent = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), false)
        var pagePrimary: Color? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = base) {
                CompositionLocalProvider(LocalArtworkLoaderColors provides parent) {
                    pagePrimary = MaterialTheme.colorScheme.primary
                    BoxLoreLoader.Custom(modifier = Modifier.testTag("loader"), shape = RectangleShape, size = 100.dp)
                }
            }
        }
        assertBackground(parent.primary, "loader")
        assertEquals(base.primary, pagePrimary)
    }

    private fun assertBackground(expected: Color, tag: String = "back") {
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val pixel = composeRule.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).let { bitmap ->
                view.draw(Canvas(bitmap))
                bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()).also { bitmap.recycle() }
            }
        }
        assertEquals(expected.toArgb(), pixel)
    }
}
