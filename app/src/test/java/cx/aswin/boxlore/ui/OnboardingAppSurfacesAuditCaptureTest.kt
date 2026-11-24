package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.ui.libraryimport.OpmlImportDialog
import cx.aswin.boxlore.ui.libraryimport.OpmlImportDialogActions
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review captures of the onboarding screens the app owns (run with `-PtabletCaptures=audit`;
 * skipped otherwise): the library import's three choices, opened from Welcome.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingAppSurfacesAuditCaptureTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    @Test fun importSelector() {
        golden.assumeAuditCapture()
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Box(Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                    OpmlImportDialog(OpmlImportState.ShowSelector, OpmlImportDialogActions(onDismissRequest = {}))
                }
            }
        }
        golden.compose.onNodeWithTag(ROOT_TAG).assertExists()
        golden.capture("import-selector", allWindows = true)
    }

    companion object {
        private const val ROOT_TAG = "onboarding_app_audit_root"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.auditMatrix(PhoneGoldenConfiguration.PHONE_BARS_CONFIGURATIONS)
    }
}
