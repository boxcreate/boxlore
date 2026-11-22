package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.prefs.ThemeSelection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h900dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeColorControlsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `hue slider follows the colour it is given and reports drags`() {
        val color = mutableStateOf(Color(0xFFE53935))
        val picked = mutableListOf<Color>()
        composeRule.setContent {
            MaterialTheme {
                ThemeColorControls("Primary", color.value, "E53935", expanded = false, onExpandedChange = {}, onChange = { picked += it }, onHexChange = {})
            }
        }
        val hue = composeRule.onNodeWithContentDescription("Primary hue")
        assertEquals(hueOf(color.value), hue.progress(), 0.5f)

        composeRule.runOnIdle { color.value = Color(0xFF1E88E5) }
        composeRule.waitForIdle()
        assertEquals(hueOf(Color(0xFF1E88E5)), hue.progress(), 0.5f)

        hue.performSemanticsAction(SemanticsActions.SetProgress) { it(140f) }
        assertEquals(140f, hueOf(picked.last()), 1f)
    }

    @Test fun `a picked colour survives switching between stacked and side-by-side layouts`() {
        val width = mutableStateOf(400)
        var saved: ThemeSelection? = null
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(width.value.dp).height(900.dp)) {
                    CustomThemeEditor(
                        AppearanceUiState("system", false, CustomThemeSeeds(Color(0xFF6255E8)).encode(), SurfaceStyles.STANDARD, artworkColorsEnabled = true),
                        onDismiss = {},
                        onSave = { saved = it },
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Primary hue").performSemanticsAction(SemanticsActions.SetProgress) { it(140f) }
        // Stacked → side by side → stacked.
        for (next in listOf(800, 400)) {
            composeRule.runOnIdle { width.value = next }
            composeRule.waitForIdle()
            assertEquals(140f, composeRule.onNodeWithContentDescription("Primary hue").progress(), 1f)
        }
        composeRule.onNodeWithTag("theme-editor-save").performClick()
        val seeds = requireNotNull(CustomThemeSeeds.decode(requireNotNull(saved).brand))
        assertEquals(140f, hueOf(seeds.primary), 1f)
    }

    private fun SemanticsNodeInteraction.progress(): Float = fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun hueOf(color: Color): Float = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }[0]
}
