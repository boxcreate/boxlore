package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.AppLanguage
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * From the language question to Welcome: nothing slides, the panel and the logo stay where they were,
 * and Welcome's panel settles from the question's height to its own while the greeting gives way to
 * the cover reel. Beside the panel (tablets) the panel keeps its width and only its content changes.
 * With animations off Welcome shows as usual at once. The hand-off outlives the recreation a language
 * change causes, Welcome takes it over once, and the pick moves on to Welcome as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1400dp-h1400dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningHandoffTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var window by mutableStateOf(BoxloreTestWindows.PHONE)

    /** Stands in for the view model's state, which outlives the activity. */
    private var state by mutableStateOf(OnboardingAppLanguage.initialState(onboardingCompleted = false) { AppLanguage.GERMAN })
    private val picks = mutableListOf<AppLanguage>()
    private var handoffsDone = 0

    @After
    fun animationsOn() = setAnimationsOff(false)

    @Composable
    private fun Host() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window) {
                Box(Modifier.requiredSize(window.widthDp.dp, window.heightDp.dp)) {
                    OnboardingStepTransition(state.currentStep) { step ->
                        if (step == OnboardingStep.APP_LANGUAGE) {
                            state.appLanguageQuestion?.let { question ->
                                AppLanguageQuestionContent(question, onChoose = { language, handoff ->
                                    picks += language
                                    state = OnboardingAppLanguage.answered(state, handoff)
                                })
                            }
                        } else {
                            WelcomeScreen(
                                onHelpMeFind = {},
                                onSearch = {},
                                onSkip = {},
                                onImportClick = {},
                                handoff = WelcomeHandoff(state.openingHandoff) {
                                    handoffsDone++
                                    state = OnboardingAppLanguage.handoffDone(state)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Host() }
        compose.mainClock.advanceTimeBy(1_600)
        compose.waitForIdle()
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun single(tag: String): Rect = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().single().boundsInRoot

    private fun pickGerman() = compose.onNode(hasText("Deutsch") and hasClickAction()).performClick()

    @Test
    fun `the pick moves on to Welcome with the panel's place, once`() {
        val open = OnboardingAppLanguage.initialState(onboardingCompleted = false) { AppLanguage.SPANISH }
        val handoff = OpeningHandoff(0.42f, twoPane = false, AppLanguage.SPANISH)

        val answered = OnboardingAppLanguage.answered(open, handoff)

        assertEquals(OnboardingStep.WELCOME, answered.currentStep)
        assertEquals(handoff, answered.openingHandoff)
        assertNull(answered.appLanguageQuestion)
        assertNull(OnboardingAppLanguage.handoffDone(answered).openingHandoff)
        assertNull(OnboardingAppLanguage.answered(open).openingHandoff)
    }

    @Test
    fun `on a phone the panel and logo stay put, then the panel settles at Welcome's height`() = handsOver(BoxloreTestWindows.PHONE)

    @Test
    fun `on the Fold 8 cover the panel and logo stay put, then the panel settles`() = handsOver(BoxloreTestWindows.COVER_475)

    @Test
    fun `beside the panel only its content changes`() {
        window = BoxloreTestWindows.TABLET
        show()
        val panel = single(OpeningPanelTag)
        val logo = single(OpeningLogoTag)

        pickGerman()
        frame()

        assertEquals(listOf(AppLanguage.GERMAN), picks)
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertEquals(panel, single(OpeningPanelTag))
        assertEquals(logo, single(OpeningLogoTag))
        assertEquals("the greeting stays for the hand-off", 1, compose.onAllNodesWithTag("handoff_greeting").fetchSemanticsNodes().size)
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertEquals(panel, single(OpeningPanelTag))
        assertEquals(1, handoffsDone)
        assertEquals(0, compose.onAllNodesWithTag("handoff_greeting").fetchSemanticsNodes().size)
    }

    @Test
    fun `with animations off Welcome shows as usual at once`() {
        setAnimationsOff(true)
        show()
        pickGerman()
        frame()

        assertEquals(0, compose.onAllNodesWithTag("handoff_greeting").fetchSemanticsNodes().size)
        val first = single(OpeningPanelTag)
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertEquals("nothing moves", first, single(OpeningPanelTag))
        assertEquals(1, handoffsDone)
    }

    @Test
    fun `the hand-off outlives recreation and Welcome takes it over from the question's height`() {
        compose.mainClock.autoAdvance = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host() }
        compose.mainClock.advanceTimeBy(1_600)
        compose.waitForIdle()
        val question = single(OpeningPanelTag)
        pickGerman()
        val handoff = state.openingHandoff
        assertNotNull(handoff)

        restoration.emulateSavedInstanceStateRestore()
        frame()

        assertEquals(handoff, state.openingHandoff)
        assertEquals("Welcome starts at the question's height", question.height, single(OpeningPanelTag).height, 1f)
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertEquals(1, handoffsDone)
        assertNull(state.openingHandoff)
    }

    private fun handsOver(size: BoxloreWindowLayout) {
        window = size
        show()
        val panel = single(OpeningPanelTag)
        val logo = single(OpeningLogoTag)

        pickGerman()
        frame()

        assertEquals(listOf(AppLanguage.GERMAN), picks)
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        val first = single(OpeningPanelTag)
        assertEquals("Welcome's panel starts at the question's height", panel.height, first.height, 1f)
        assertEquals("nothing slides", panel.left, first.left, 0.5f)
        assertEquals("the logo stays put", logo, single(OpeningLogoTag))
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertEquals(1, handoffsDone)
        assertNull(state.openingHandoff)
        // Welcome's own height: the morph ended where the panel now stays.
        val settled = single(OpeningPanelTag)
        frame()
        assertEquals(settled, single(OpeningPanelTag))
        assertTrue("the panel sits at the top", settled.top <= panel.top + 0.5f)
    }

    private fun setAnimationsOff(off: Boolean) {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (off) 0f else 1f)
    }
}
