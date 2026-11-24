package cx.aswin.boxlore.feature.settings.feedback

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Feedback's small controls: the typing header's type pill and its menu, and the type pills. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "de-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackComposePartsTest {
    @get:Rule val compose = createComposeRule()

    private val longChoice = FeedbackChoice(id = "server", label = UiText.raw("Wiedergabe und Audio-Streams"))
    private val shortChoice = FeedbackChoice(id = "bug", label = UiText.raw("Fehler"))

    // Regression: a long type name made the typing header wrap; the pill was a 40dp target.
    @Test fun `the type pill stays narrow with a long name and has a full touch target`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                BoxLoreTheme(dynamicColor = false) {
                    FeedbackTypeMenuPill(choices = listOf(shortChoice, longChoice), selected = longChoice, enabled = true, onSelect = {})
                }
            }
        }
        val pill = compose.onNodeWithTag(FEEDBACK_TYPE_PILL_TAG).fetchSemanticsNode()
        assertTrue("pill width ${pill.boundsInRoot.width}", pill.boundsInRoot.width <= FEEDBACK_TYPE_PILL_MAX_WIDTH.value + 0.5f)
        compose.onNodeWithTag(FEEDBACK_TYPE_PILL_TAG).assertTouchHeightIsEqualTo(48.dp)
    }

    @Test fun `the type menu shows the current type as picked and stays open through recreation`() {
        val picks = mutableListOf<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            BoxLoreTheme(dynamicColor = false) {
                FeedbackTypeMenuPill(choices = listOf(shortChoice, longChoice), selected = shortChoice, enabled = true, onSelect = { picks += it })
            }
        }
        compose.onNodeWithTag(FEEDBACK_TYPE_PILL_TAG).performClick()
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        val selected = compose.onAllNodes(hasText("Fehler") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes()
        assertEquals(1, selected.size)
        compose.onNode(hasText("Wiedergabe und Audio-Streams") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("server"), picks)
        assertEquals(0, compose.onAllNodes(isPopup()).fetchSemanticsNodes().size)
    }

    @Test fun `type pills are at least 48dp tall and keep the pick`() {
        var picked by mutableStateOf("bug")
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                FeedbackTypePills(listOf(shortChoice, longChoice), selected = if (picked == "bug") shortChoice else longChoice, enabled = true, onSelect = { picked = it }, modifier = Modifier.width(360.dp).testTag("pills"))
            }
        }
        compose.onNode(hasText("Wiedergabe und Audio-Streams")).performClick()
        assertEquals("server", picked)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes().forEach {
            assertTrue(it.boundsInRoot.height >= 48f)
        }
    }

    // Regression: in German at 130% the typing header's title wrapped to three lines above the keyboard.
    @Test fun `the typing header stays one row in German at large text on a narrow phone`() {
        val actions = FeedbackComposeActions({}, {}, {}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                BoxLoreTheme(dynamicColor = false) {
                    androidx.compose.foundation.layout.Box(Modifier.width(360.dp)) { TypingHeader(FeedbackUiState(message = "Hallo"), actions) }
                }
            }
        }
        val header = compose.onNodeWithTag(FEEDBACK_TYPING_HEADER_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("header height ${header.height}", header.height <= 64f)
        compose.onNodeWithTag(FEEDBACK_TYPE_PILL_TAG).assertExists()
    }
}
