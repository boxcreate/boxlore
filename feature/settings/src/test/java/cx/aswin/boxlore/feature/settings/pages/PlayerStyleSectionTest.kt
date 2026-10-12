package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerStyleSectionTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableStateOf(FullPlayerStyle.STANDARD)
    private val choices = mutableListOf<String>()
    private val links = mutableListOf<String>()

    private fun render(large: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) {
                        links.add(uri)
                    }
                },
                LocalDensity provides Density(density.density, if (large) 2f else 1f),
                LocalLayoutDirection provides if (large) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                BoxLoreTheme(darkTheme = large) {
                    PlayerStyleSection(selected.value) {
                        choices.add(it)
                        selected.value = it
                    }
                }
            }
        }
    }

    @Test
    fun `selection changes only the player style and credit opens the design source`() {
        render()
        compose.onNodeWithText("Standard").assertIsOn()
        compose.onNodeWithText("bunpod").assertIsOff().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(64)
        assertEquals("Selection callback", listOf(FullPlayerStyle.BUNPOD), choices)
        assertEquals("Selected model", FullPlayerStyle.BUNPOD, selected.value)
        compose.onNodeWithText("bunpod").assertIsOn()
        compose.onNodeWithText("Standard").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Standard").assertIsOn()
        compose.onNodeWithText("Design inspired by BunPod", substring = true).performClick()
        assertEquals(listOf(FullPlayerStyle.BUNPOD, FullPlayerStyle.STANDARD), choices)
        assertEquals(listOf("https://github.com/kamranbekirovyz/bunpod"), links)
    }

    @Test
    fun `large RTL text keeps both choices and design credit reachable`() {
        render(large = true)
        compose.onNodeWithText("Standard").assertIsDisplayed()
        compose.onNodeWithText("bunpod").assertIsDisplayed().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(64)
        assertEquals("Selection callback", listOf(FullPlayerStyle.BUNPOD), choices)
        compose.onNodeWithText("bunpod").assertIsOn()
        compose.onNodeWithText("Design inspired by BunPod", substring = true).assertIsDisplayed().performClick()
        assertEquals(1, links.size)
    }
}
