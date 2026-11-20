package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Onboarding's language question: the suggestion first, then English, then the rest behind
 * "Other languages". The open list and the callbacks stay through the 600dp and 840dp width gates
 * and the 480dp height gate in both directions, and through recreation (the state lives in the
 * view model, hoisted here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLanguageQuestionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val question = mutableStateOf(AppLanguageQuestion(AppLanguage.SPANISH))
    private val picks = mutableListOf<AppLanguage>()

    @Composable
    private fun screen() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                    AppLanguageQuestionContent(
                        question = question.value,
                        onChoose = { picks += it },
                        onShowAll = { question.value = question.value.copy(showsAll = true) },
                    )
                }
            }
        }
    }

    private fun button(name: String) = compose.onNode(hasText(name) and hasClickAction())

    @Test
    fun `offers the suggestion first, then English, and says where to change it later`() {
        compose.setContent { screen() }
        compose.onNodeWithText("Choose your language").assertIsDisplayed()
        compose.onNodeWithText("Settings › Appearance", substring = true).assertIsDisplayed()
        val spanishTop = button("Español").fetchSemanticsNode().boundsInRoot.top
        val englishTop = button("English").fetchSemanticsNode().boundsInRoot.top
        assert(spanishTop < englishTop) { "The suggested language comes first" }
        compose.onAllNodesWithText("Deutsch").assertCountEquals(0)

        button("Español").performClick()
        assertEquals(listOf(AppLanguage.SPANISH), picks)
    }

    @Test
    fun `other languages lists the rest, the phone's language first`() {
        compose.setContent { screen() }
        button("Other languages").performScrollTo().performClick()
        listOf("System default", "Português (Brasil)", "Deutsch", "Français").forEach {
            button(it).performScrollTo().assertIsDisplayed()
        }
        compose.onAllNodesWithText("Español").assertCountEquals(1)
        button("Deutsch").performScrollTo().performClick()
        button("System default").performScrollTo().performClick()
        assertEquals(listOf(AppLanguage.GERMAN, AppLanguage.SYSTEM), picks)
    }

    @Test
    fun `the open list stays across phone, tablet, split and short windows both ways`() {
        compose.setContent { screen() }
        button("Other languages").performScrollTo().performClick()
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(839f, 1180f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(1280f, 480f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            button("Français").performScrollTo().assertIsDisplayed()
            button("Español").performScrollTo().assertIsDisplayed()
        }
        button("Français").performScrollTo().performClick()
        assertEquals(listOf(AppLanguage.FRENCH), picks)
    }

    @Test
    fun `the open list survives recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { screen() }
        button("Other languages").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        button("Português (Brasil)").performScrollTo().assertIsDisplayed()
    }
}
