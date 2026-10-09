package cx.aswin.boxlore.navigation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import cx.aswin.boxlore.feature.home.components.HomeChildSectionHeader
import cx.aswin.boxlore.feature.home.components.HomeTopLevelSectionHeader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeHeaderPaletteTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var view: View

    @Test fun `both home heading levels use the dark theme even if the shell content color is black`() {
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), true)) {
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    Column(Modifier.width(360.dp)) {
                        HomeTopLevelSectionHeader("Your shows", "Your shows", {})
                        HomeChildSectionHeader("Similar shows", Icons.Rounded.Subscriptions)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        listOf("Your shows", "Similar shows").forEach { title ->
            val bounds = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
            val brightPixels = composeRule.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                var count = 0
                for (x in bounds.left.toInt() until bounds.right.toInt()) {
                    for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                        val pixel = bitmap.getPixel(x, y)
                        if (android.graphics.Color.alpha(pixel) > 150 && android.graphics.Color.red(pixel) > 160) count++
                    }
                }
                bitmap.recycle()
                count
            }
            assertTrue("$title must use the theme's light foreground", brightPixels > 30)
        }
    }
}
