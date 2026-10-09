package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import cx.aswin.boxlore.core.prefs.ThemeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h900dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomThemeEditorTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var view: View
    private var saved: ThemeSelection? = null
    private var dismissed = false
    private val recompose = mutableStateOf(false)
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val initial = CustomThemeSeeds(Color(0xFF6255E8))

    @Test fun `preview updates during a drag with the animation clock paused`() {
        render {
            val before = previewBackground()
            composeRule.onNodeWithContentDescription("Primary hue").performSemanticsAction(SemanticsActions.SetProgress) { it(140f) }
            composeRule.mainClock.advanceTimeBy(32)
            val after = previewBackground()
            assertNotEquals(before, after)
            // No debounce or animation-clock advancement is needed to see the new palette.
            composeRule.onNodeWithText("Save theme").performClick()
            assertNotEquals(initial.encode(), saved?.brand)
            assertEquals(listOf("custom_theme_editor_opened"), events.map { it.first })
        }
    }

    @Test fun `incomplete hex disables save while keeping the last valid preview and back discards edits`() {
        render {
            val before = previewBackground()
            composeRule.onNodeWithText("Hex color").performTextReplacement("62")
            composeRule.mainClock.advanceTimeBy(32)
            composeRule.onNodeWithText("Save theme").assertIsNotEnabled()
            assertEquals(before, previewBackground())
            composeRule.onNodeWithText("Hex color").performTextReplacement("22AACC")
            composeRule.mainClock.advanceTimeBy(32)
            composeRule.onNodeWithText("Save theme").assertIsEnabled()
            composeRule.onNodeWithContentDescription("Back").performClick()
            assertEquals(true, dismissed)
            assertNull(saved)
            assertEquals(listOf("custom_theme_editor_opened"), events.map { it.first })
        }
    }

    @Test fun `changing preview mode does not save or emit another editor open`() {
        render {
            val before = previewBackground()
            composeRule.onNodeWithText("Dark preview").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            assertNotEquals(before, previewBackground())
            recompose.value = true
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            assertNull(saved)
            assertEquals(1, events.size)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h540dp-mdpi")
    fun `short RTL editor with enlarged text keeps the preview and save action visible`() {
        render(enlarged = true) {
            composeRule.onNodeWithText("Save theme").assertIsDisplayed()
            val preview = composeRule.onNodeWithTag("custom-theme-preview").fetchSemanticsNode().boundsInRoot
            composeRule.onNodeWithText("Primary").performClick()
            composeRule.mainClock.advanceTimeBy(32)
            assertEquals(preview, composeRule.onNodeWithTag("custom-theme-preview").fetchSemanticsNode().boundsInRoot)
            composeRule.onNodeWithText("Save theme").performClick()
            assertEquals(initial.encode(), saved?.brand)
        }
    }

    private fun render(enlarged: Boolean = false, test: () -> Unit) {
        val restore = AnalyticsHelper.installRecordingSink(events)
        try {
            composeRule.mainClock.autoAdvance = false
            composeRule.setContent {
                view = LocalView.current
                val density = LocalDensity.current
                val configuration = android.content.res.Configuration(LocalConfiguration.current).apply { if (enlarged) fontScale = 2f }
                MaterialTheme(colorScheme = generatePersonalColorScheme(initial, false)) {
                    CompositionLocalProvider(LocalEffectiveDarkTheme provides false, LocalDensity provides Density(density.density, if (enlarged) 2f else density.fontScale), LocalConfiguration provides configuration, LocalLayoutDirection provides if (enlarged) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        CustomThemeEditor(
                            AppearanceUiState("system", false, initial.encode(), SurfaceStyles.STANDARD, artworkColorsEnabled = !recompose.value),
                            onDismiss = { dismissed = true },
                            onSave = { saved = it },
                        )
                    }
                }
            }
            composeRule.waitForIdle()
            test()
        } finally {
            restore()
        }
    }

    private fun previewBackground(): Int {
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithTag("custom-theme-preview").fetchSemanticsNode().boundsInRoot
        return composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(bounds.center.x.toInt(), bounds.top.toInt() + 4).also { bitmap.recycle() }
        }
    }
}
