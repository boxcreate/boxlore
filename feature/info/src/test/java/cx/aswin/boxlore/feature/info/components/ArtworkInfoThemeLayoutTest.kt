package cx.aswin.boxlore.feature.info.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkColorsEnabled
import org.junit.Assert.assertEquals
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
class ArtworkInfoThemeLayoutTest {
    @get:Rule val composeRule = createComposeRule()
    private val headerOpaque = mutableStateOf(false)
    private lateinit var view: View

    @Test fun `full page surface never forces sibling header overlays to viewport height`() {
        render()
        val page = composeRule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val header = composeRule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val back = composeRule.onNodeWithTag("back").fetchSemanticsNode().boundsInRoot
        assertEquals(760f, page.height, 1f)
        assertEquals(88f, header.height, 1f)
        assertEquals(page.top, header.top, 1f)
        assertEquals(header.center.y, back.center.y, 1f)
        assertTrue(back.bottom < page.top + 88f)
    }

    @Test fun `scrolling and painting the header background leaves content below it visible`() {
        render()
        composeRule.onNodeWithTag("episodes").performScrollToIndex(5)
        headerOpaque.value = true
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val bitmap = composeRule.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        try {
            val x = (bounds.left + 10f).toInt()
            assertEquals(Color.Red.toArgb(), bitmap.getPixel(x, (bounds.top + 10f).toInt()))
            assertEquals(Color.Green.toArgb(), bitmap.getPixel(x, (bounds.top + 400f).toInt()))
        } finally {
            bitmap.recycle()
        }
    }

    private fun render() {
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme {
                CompositionLocalProvider(LocalArtworkColorsEnabled provides false) {
                    Box(Modifier.requiredSize(360.dp, 760.dp).testTag("page")) {
                        ArtworkInfoTheme(emptyList()) {
                            // Episode Info has separate root siblings for content and sticky chrome.
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().background(Color.Green).testTag("episodes"),
                                contentPadding = PaddingValues(top = 88.dp),
                            ) {
                                items(30) { index ->
                                    Text("Episode $index", modifier = Modifier.fillMaxWidth().height(64.dp))
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().height(88.dp)
                                    .background(if (headerOpaque.value) Color.Red else Color.Transparent)
                                    .testTag("header"),
                            ) {
                                Text("Back", modifier = Modifier.align(Alignment.CenterStart).testTag("back"))
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
