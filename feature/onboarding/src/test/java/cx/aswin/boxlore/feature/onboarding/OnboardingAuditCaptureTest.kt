package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.prefs.AppLanguage
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review captures of onboarding's opening screens (run with `-PtabletCaptures=audit`; skipped
 * otherwise): Echo's opening turn and a later turn with a long question, Welcome, the language
 * question and the hand-off from the language question to Welcome as a frame sequence. Besides the
 * shared audit sizes they cover phones and the Fold 8 cover with their system bars, a small phone on
 * its side, the cover in split-screen and the lower halves the shell gives onboarding in tabletop.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingAuditCaptureTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    private val callbacks = EchoCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val longChoices = listOf("Unsolved mysteries", "Big ideas in science", "Stories from history", "Comedians riffing")

    @Test fun echoOpening() = render("echo-opening") {
        EchoOnboardingScreen(
            OnboardingUiState(currentStep = OnboardingStep.AI_ONBOARDING, aiSelectedOptions = setOf(EchoInitialTopic.SCIENCE.answer, EchoInitialTopic.TRUE_CRIME.answer)),
            callbacks,
        )
    }

    @Test fun echoLaterTurn() = render("echo-later-turn") {
        EchoOnboardingScreen(
            OnboardingUiState(
                currentStep = OnboardingStep.AI_ONBOARDING,
                aiCurrentTurn = 2,
                aiHistory = listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart("True crime")))),
                aiOptions = longChoices,
                aiSelectedOptions = setOf(longChoices[1]),
                aiGuidedTurn = GuidedOnboardingTurn(
                    1,
                    "question",
                    "Cold cases and curious minds. Good mix.",
                    "Which of these would you happily hear about on a long drive?",
                    longChoices,
                    intent = "preferences",
                ),
            ),
            callbacks,
        )
    }

    @Test fun welcome() = render("welcome") {
        WelcomeScreen(onHelpMeFind = {}, onSearch = {}, onSkip = {}, onImportClick = {})
    }

    @Test fun appLanguage() = render("app-language") {
        AppLanguageQuestionContent(AppLanguageQuestion(AppLanguage.GERMAN), onChoose = {})
    }

    /** Picking a language, then Welcome, as the step host plays it; with animations on and off. */
    @Test fun languageToWelcome() = languageToWelcomeSequence(reducedMotion = false)

    @Test fun languageToWelcomeReducedMotion() = languageToWelcomeSequence(reducedMotion = true)

    private fun languageToWelcomeSequence(reducedMotion: Boolean) {
        golden.assumeAuditCapture()
        assumeTrue("The hand-off is recorded on phones, the cover, a foldable and a tablet", golden.configuration.auditKey in SEQUENCE_SIZES)
        val step = mutableStateOf(OnboardingStep.APP_LANGUAGE)
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Box(Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                    OnboardingStepTransition(step.value, Modifier.fillMaxSize()) { current ->
                        when (current) {
                            OnboardingStep.APP_LANGUAGE -> AppLanguageQuestionContent(AppLanguageQuestion(AppLanguage.GERMAN), onChoose = {})
                            else -> WelcomeScreen(onHelpMeFind = {}, onSearch = {}, onSkip = {}, onImportClick = {})
                        }
                    }
                }
            }
        }
        golden.compose.mainClock.advanceTimeBy(1_600)
        golden.compose.waitForIdle()
        golden.compose.onNodeWithTag(ROOT_TAG).assertExists()
        golden.captureSequence("language-to-welcome", frames = 16, stepMs = 40, reducedMotion = reducedMotion) {
            step.value = OnboardingStep.WELCOME
        }
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        golden.assumeAuditCapture()
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Box(Modifier.fillMaxSize().testTag(ROOT_TAG)) { content() }
            }
        }
        golden.compose.onNodeWithTag(ROOT_TAG).assertExists()
        golden.capture(name)
    }

    companion object {
        private const val ROOT_TAG = "onboarding_audit_root"
        private val SEQUENCE_SIZES = setOf("412x915", "475x695", "932x650", "1280x800")

        /**
         * Phones and the cover with their bars, a small phone on its side, the cover in split-screen
         * and the lower fold-safe halves (Fold 8, Pixel Fold, a 9 Pro Fold) in tabletop.
         */
        val ONBOARDING_SIZES = PhoneGoldenConfiguration.PHONE_BARS_CONFIGURATIONS + listOf(
            PhoneGoldenConfiguration(740, 360, false),
            PhoneGoldenConfiguration(475, 330, false),
            PhoneGoldenConfiguration(932, 314, false),
            PhoneGoldenConfiguration(841, 340, false),
            PhoneGoldenConfiguration(673, 410, false),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.auditMatrix(ONBOARDING_SIZES)
    }
}
