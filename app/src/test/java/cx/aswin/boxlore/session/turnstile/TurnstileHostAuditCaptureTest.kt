package cx.aswin.boxlore.session.turnstile

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
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
 * Review captures of the human check's sheet (run with `-PtabletCaptures=audit`; skipped otherwise)
 * while Cloudflare's widget is still loading: the page is a blank 72dp box, as on a slow network.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TurnstileHostAuditCaptureTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    @Test fun turnstileHostLoading() {
        golden.assumeAuditCapture()
        assumeTrue("Recorded in light", !golden.configuration.dark)
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Box(Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                    TurnstileStepUpSheet(onSkip = {}) { Box(Modifier.fillMaxWidth().height(72.dp)) }
                }
            }
        }
        golden.compose.onNodeWithTag(ROOT_TAG).assertExists()
        golden.capture("turnstile-host", allWindows = true)
    }

    companion object {
        private const val ROOT_TAG = "turnstile_audit_root"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.auditMatrix(PhoneGoldenConfiguration.PHONE_BARS_CONFIGURATIONS)
    }
}
