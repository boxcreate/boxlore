package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w500dp-h2400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DailyBriefingCardTest {
    @get:Rule val compose = createComposeRule()
    private val dark = mutableStateOf(false)
    private val large = mutableStateOf(false)
    private var play = 0
    private var open = 0
    private var hide = 0
    private var hideAll = 0
        private val briefing = Briefing("2026-10-11", "global", "Energy, technology and the week ahead", "", "https://example.invalid/audio", "")
    private val chapters = listOf(Chapter(0.0, "Energy prices and the week ahead"), Chapter(60.0, "New rules for digital markets"), Chapter(120.0, "The third story"), Chapter(180.0, "The fourth story"))

    private fun render() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large.value) 2f else 1f), LocalLayoutDirection provides if (large.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                BoxLoreTheme(darkTheme = dark.value) {
                    Box(Modifier.width(320.dp)) {
                        DailyBriefingCard(briefing, chapters, false, { play++ }, { open++ }, { hide++ }, onDismissForever = { hideAll++ })
                    }
                }
            }
        }
    }

    @Test fun `play open and inline story expansion keep independent actions`() {
        render()
        compose.onNodeWithText("Listen").performClick()
        assertEquals(1, play)
        assertEquals(0, open)
        compose.onNodeWithText("Open brief").performClick()
        assertEquals(1, open)
        compose.onNodeWithText("The third story").assertIsDisplayed()
        compose.onNodeWithText("The fourth story").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show all 4 stories").performClick()
        compose.onNodeWithText("The fourth story").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show fewer stories").performClick()
        compose.onNodeWithText("The fourth story").assertDoesNotExist()
        compose.onNodeWithText("The third story").assertIsDisplayed()
        compose.onNodeWithText("Global", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(1, open)
    }

    @Test fun `editorial card stays usable in dark RTL enlarged text and hiding is explicit`() {
        dark.value = true
        large.value = true
        render()
        compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Listen").assertIsDisplayed().performClick()
        assertEquals(1, play)
        compose.onNodeWithText("Open brief").assertIsDisplayed()
        val number = compose.onNodeWithText("01", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val headline = compose.onNodeWithText(chapters.first().title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(number.center.y, headline.center.y, 1f)
        compose.onNodeWithContentDescription("Hide briefing").performClick()
        compose.onNodeWithText("Hide today’s brief?").assertIsDisplayed()
        assertEquals(0, hide)
        compose.onNodeWithText("Always hide the Brief").performClick()
        compose.onNodeWithText("Always hide the Brief?").assertIsDisplayed()
        compose.onNodeWithText("Always hide").performClick()
        assertEquals(1, hideAll)
        assertEquals(0, hide)
        assertEquals(0, open)
    }
}
