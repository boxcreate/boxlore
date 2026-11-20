package cx.aswin.boxlore.core.designsystem.text

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cx.aswin.boxlore.core.model.text.UiText
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UiTextComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `asString resolves resources and raw text in composition`() {
        var ok = ""
        compose.setContent {
            ok = UiText.res(android.R.string.ok).asString()
            Text(UiText.raw("The Daily").asString())
        }
        compose.onNodeWithText("The Daily").assertExists()
        assertEquals("OK", ok)
    }

    @Test
    fun `currentLocale follows the composition's configuration`() {
        var seen: Locale? = null
        compose.setContent {
            val german = Configuration(LocalConfiguration.current).apply { setLocale(Locale.GERMANY) }
            CompositionLocalProvider(LocalConfiguration provides german) {
                seen = currentLocale()
            }
        }
        compose.waitForIdle()
        assertEquals(Locale.GERMANY, seen)
    }
}
