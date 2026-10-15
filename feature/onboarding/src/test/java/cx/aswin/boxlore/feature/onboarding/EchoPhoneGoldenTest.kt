package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EchoPhoneGoldenTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    @Test fun openingQuestion() {
        golden.prepare()
        golden.compose.setContent {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                EchoOnboardingScreen(
                    OnboardingUiState(currentStep = OnboardingStep.AI_ONBOARDING),
                    EchoCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        golden.capture("echo-onboarding")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.matrix()
    }
}
