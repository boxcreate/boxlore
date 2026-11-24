package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Privacy's panels are solid and the same at every size; the page keeps its place through resizes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w1400dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacySettingsPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `promise pills look the same on phones and tablets, and the page keeps its scroll both ways`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        var surface = 0
        var destructive: AnalyticsCardColors? = null
        var errorContainer = 0
        compose.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        surface = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
                        errorContainer = MaterialTheme.colorScheme.errorContainer.toArgb()
                        destructive = analyticsCardColors(destructive = true)
                        PrivacySettingsPage("test-deletion-id", false, PrivacySettingsActions({}, {}, {}, {}, {}), {})
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(errorContainer, destructive?.container?.toArgb())
        val pill = { compose.onAllNodesWithTag(PRIVACY_PROMISE_PILL_TAG).onFirst().captureToImage().asAndroidBitmap().getPixel(2, 10) }
        val phonePill = pill()
        assertEquals(surface, phonePill)
        val scrollable = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        compose.onNodeWithText("Reset recommendations", substring = true).performScrollTo()
        val offset = { compose.onNode(scrollable).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() }
        val scrolled = offset()
        assertTrue(scrolled > 0f)
        for (size in listOf(BoxloreTestWindows.TABLET, BoxloreTestWindows.PHONE)) {
            compose.runOnIdle { window.value = size }
            compose.waitForIdle()
            assertTrue("$size keeps the page's place", offset() > 0f)
        }
        compose.runOnIdle { window.value = BoxloreTestWindows.TABLET }
        compose.onAllNodesWithTag(PRIVACY_PROMISE_PILL_TAG).onFirst().performScrollTo()
        assertEquals(phonePill, pill())
    }
}
