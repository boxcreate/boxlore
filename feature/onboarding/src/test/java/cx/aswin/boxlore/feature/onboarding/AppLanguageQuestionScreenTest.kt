package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
 * Onboarding's language question: an English screen whose choices are each written in their own
 * language, the suggestion first and every other language one tap away. The choices stay reachable
 * and working through the 600dp and 840dp width gates and the 480dp height gate in both directions,
 * and through recreation. The phone here is Spanish, so a translated screen would show. The motion
 * itself is covered by LanguageGreetingMotion's tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "es-w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLanguageQuestionScreenTest {
    private object NoMotion : MotionDurationScale {
        override val scaleFactor = 0f
    }

    // Reduced motion: the greeting and Echo rest, so a dozen resizes don't spin the endless motion clock.
    @OptIn(ExperimentalTestApi::class)
    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val picks = mutableListOf<AppLanguage>()

    @Composable
    private fun screen(question: AppLanguageQuestion = AppLanguageQuestion(AppLanguage.SPANISH)) {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                    AppLanguageQuestionContent(question = question, onChoose = { picks += it })
                }
            }
        }
    }

    /** The motion clock runs forever, so the test clock is stepped by hand past the entrance. */
    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        settle()
    }

    private fun settle(millis: Long = 1_600) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun button(name: String) = compose.onNode(hasText(name, substring = true) and hasClickAction())

    @Test
    fun `the screen is English while each language is named in itself`() {
        show { screen() }
        compose.onNodeWithText("Pick your app language").assertIsDisplayed()
        compose.onNodeWithText("You can change it anytime in Settings.").assertIsDisplayed()
        button("Español").assertIsDisplayed()
        compose.onNodeWithText("Suggested for you").assertExists()
        listOf("English", "Português (Brasil)", "Deutsch", "Français").forEach { button(it).performScrollTo().assertIsDisplayed() }
        // The decorative greeting and its line stay out of the accessibility tree.
        compose.onNodeWithTag("language_greeting").assertExists()
        listOf("Hola", "Elige tu idioma").forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun `the suggestion comes first and every language is one tap away, without System default`() {
        show { screen() }
        val spanishTop = button("Español").fetchSemanticsNode().boundsInRoot.top
        val englishTop = button("English").performScrollTo().fetchSemanticsNode().boundsInRoot.top
        assert(spanishTop < englishTop) { "The suggested language comes first" }
        compose.onAllNodesWithText("System default").assertCountEquals(0)

        listOf("Español", "English", "Português (Brasil)", "Deutsch", "Français").forEach { button(it).performScrollTo().performClick() }
        assertEquals(
            listOf(AppLanguage.SPANISH, AppLanguage.ENGLISH, AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.GERMAN, AppLanguage.FRENCH),
            picks,
        )
    }

    @Test
    fun `a German phone leads with German and offers Spanish among the rest`() {
        show { screen(AppLanguageQuestion(AppLanguage.GERMAN)) }
        val germanTop = button("Deutsch").fetchSemanticsNode().boundsInRoot.top
        val spanishTop = button("Español").performScrollTo().fetchSemanticsNode().boundsInRoot.top
        assert(germanTop < spanishTop) { "The suggested language comes first" }
        compose.onAllNodesWithText("Deutsch").assertCountEquals(1)
        compose.onAllNodesWithText("Wähle deine Sprache").assertCountEquals(0)
    }

    @Test
    fun `every choice stays reachable across phone, tablet, split and short windows both ways`() {
        show { screen() }
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(839f, 1180f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(1280f, 480f),
            BoxloreWindowLayout(800f, 360f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            settle(millis = 100)
            button("Español").performScrollTo().assertIsDisplayed()
            button("Français").performScrollTo().assertIsDisplayed()
        }
        button("Français").performScrollTo().performClick()
        assertEquals(listOf(AppLanguage.FRENCH), picks)
    }

    @Test
    fun `the question survives recreation`() {
        compose.mainClock.autoAdvance = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent { screen() }
        settle()
        restoration.emulateSavedInstanceStateRestore()
        settle()
        button("Português (Brasil)").performScrollTo().assertIsDisplayed()
        button("Español").performScrollTo().performClick()
        assertEquals(listOf(AppLanguage.SPANISH), picks)
    }
}
