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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansFlexRoundness
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.buildBoxLoreTypography
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import cx.aswin.boxlore.core.prefs.ThemeSelection
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val restoration = StateRestorationTester(composeRule)
    private lateinit var view: View
    private var saved: ThemeSelection? = null
    private var dismissed = false
    private val recompose = mutableStateOf(false)
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val initial = CustomThemeSeeds(Color(0xFF6255E8))

    @Test fun `preview updates immediately when a color slider changes`() {
        render {
            val before = previewBackground()
            composeRule.onNodeWithContentDescription("Primary hue").performSemanticsAction(SemanticsActions.SetProgress) { it(140f) }
            composeRule.mainClock.advanceTimeBy(64)
            val after = previewBackground()
            writeScreenshot("changed")
            composeRule.onNodeWithTag("theme-editor-save").performClick()
            assertNotNull(saved)
            assertNotEquals(initial.encode(), saved?.brand)
            assertNotEquals(before, after)
            assertEquals(listOf("custom_theme_editor_opened"), events.map { it.first })
        }
    }

    @Test fun `incomplete hex disables save while keeping the last valid preview and back discards edits`() {
        render {
            val before = previewBackground()
            openHex()
            composeRule.onNodeWithTag("theme-editor-hex").performTextReplacement("62")
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.onNodeWithTag("theme-editor-save").assertIsNotEnabled()
            assertEquals(before, previewBackground())
            composeRule.onNodeWithTag("theme-editor-hex").performTextReplacement("22AACC")
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.onNodeWithTag("theme-editor-save").assertIsEnabled()
            composeRule.onNodeWithContentDescription("Back").performClick()
            assertEquals(true, dismissed)
            assertNull(saved)
            assertEquals(listOf("custom_theme_editor_opened"), events.map { it.first })
        }
    }

    @Test fun `changing preview mode does not save or emit another editor open`() {
        render {
            val before = previewBackground()
            composeRule.onNodeWithText("Dark").performClick()
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
    @Config(qualifiers = "w320dp-h360dp-mdpi")
    fun `very short RTL window keeps preview save and color entry usable`() {
        render(enlarged = true) {
            composeRule.onNodeWithTag("theme-editor-save").assertIsDisplayed()
            val preview = composeRule.onNodeWithTag("custom-theme-preview").fetchSemanticsNode().boundsInRoot
            composeRule.onNodeWithText("Primary").performScrollTo().performClick()
            composeRule.mainClock.advanceTimeBy(64)
            assertEquals(preview, composeRule.onNodeWithTag("custom-theme-preview").fetchSemanticsNode().boundsInRoot)
            openHex()
            composeRule.onNodeWithTag("theme-editor-hex").assertIsDisplayed()
            writeScreenshot("short-rtl")
            composeRule.onNodeWithTag("theme-editor-save").performClick()
            assertEquals(initial.encode(), saved?.brand)
        }
    }

    @Test fun `advanced controls stay collapsed until requested`() {
        render {
            composeRule.onNodeWithTag("theme-editor-hex").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Primary hue").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Primary saturation").assertIsDisplayed()
            composeRule.onNodeWithText("Primary").assertIsSelected()
            composeRule.onNodeWithText("Secondary").assertIsNotSelected()
            writeScreenshot("phone")
            openHex()
            composeRule.onNodeWithTag("theme-editor-hex").assertIsDisplayed()
        }
    }

    @Test fun `invalid hex survives recreation without enabling save or replacing the palette`() {
        render {
            openHex()
            composeRule.onNodeWithTag("theme-editor-hex").performTextReplacement("ZZ0000")
            composeRule.mainClock.advanceTimeByFrame()
            val before = previewBackground()
            restoration.emulateSavedInstanceStateRestore()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("theme-editor-save").assertIsNotEnabled()
            assertEquals(before, previewBackground())
        }
    }

    @Test fun `suggested secondary seed stays unchanged between light and dark previews`() {
        render {
            composeRule.onNodeWithText("Secondary").performClick()
            openHex()
            val before = composeRule.onNodeWithTag("theme-editor-hex").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            composeRule.onNodeWithText("Dark").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            val after = composeRule.onNodeWithTag("theme-editor-hex").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            assertEquals(before, after)
            composeRule.onNodeWithTag("theme-editor-save").performClick()
            assertEquals(initial.encode(), saved?.brand)
        }
    }

    @Test fun `background choice saves with the color without saving the preview mode`() {
        render {
            composeRule.onNodeWithText("Pure", substring = false).performScrollTo().performClick()
            composeRule.onNodeWithText("Dark").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("theme-editor-save").performClick()
            assertEquals(ThemeSelection(initial.encode(), SurfaceStyles.DYNAMIC_OLED_WHITE), saved)
        }
    }

    @Test fun `phone layout keeps main color controls visible below compact live preview`() {
        render(dark = true) {
            val preview = composeRule.onNodeWithTag("custom-theme-preview").fetchSemanticsNode().boundsInRoot
            val hue = composeRule.onNodeWithContentDescription("Primary hue").fetchSemanticsNode().boundsInRoot
            assertTrue(preview.height < 180f)
            assertTrue(hue.top >= preview.bottom)
            composeRule.onNodeWithContentDescription("Primary saturation").assertIsDisplayed()
            writeScreenshot("phone-dark")
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h480dp-mdpi")
    fun `landscape keeps preview beside independently scrollable controls`() {
        render {
            val preview = composeRule.onNodeWithTag("custom-theme-preview").fetchSemanticsNode().boundsInRoot
            val controls = composeRule.onNodeWithTag("theme-editor-controls").fetchSemanticsNode().boundsInRoot
            assertTrue(preview.right <= controls.left)
            openHex()
            composeRule.onNodeWithTag("theme-editor-hex").assertIsDisplayed()
            composeRule.onNodeWithTag("theme-editor-save").assertIsDisplayed()
            writeScreenshot("landscape")
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h540dp-mdpi")
    fun `large RTL controls scroll without clipping save or color entry`() {
        render(enlarged = true) {
            composeRule.onNodeWithTag("theme-editor-save").assertIsDisplayed()
            openHex()
            composeRule.onNodeWithTag("theme-editor-hex").assertIsDisplayed()
            composeRule.onNodeWithTag("theme-editor-hex").performTextReplacement("22AACC")
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("theme-editor-save").performClick()
            assertEquals(CustomThemeSeeds(Color(0xFF22AACC)).encode(), saved?.brand)
            writeScreenshot("large-rtl")
        }
    }

    private fun openHex() {
        composeRule.onNodeWithTag("theme-editor-fine-tune").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("theme-editor-hex").performScrollTo()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun writeScreenshot(name: String) {
        val directory = System.getenv("BOXLORE_UI_TEST_ARTIFACTS") ?: return
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "boxlore-theme-editor-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun render(enlarged: Boolean = false, dark: Boolean = false, test: () -> Unit) {
        val restore = AnalyticsHelper.installRecordingSink(events)
        try {
            composeRule.mainClock.autoAdvance = true
            restoration.setContent {
                view = LocalView.current
                val density = LocalDensity.current
                val configuration = android.content.res.Configuration(LocalConfiguration.current).apply { if (enlarged) fontScale = 2f }
                MaterialTheme(colorScheme = generatePersonalColorScheme(initial, dark), typography = buildBoxLoreTypography(LocalContext.current, GoogleSansFlexRoundness)) {
                    CompositionLocalProvider(LocalEffectiveDarkTheme provides dark, LocalDensity provides Density(density.density, if (enlarged) 2f else density.fontScale), LocalConfiguration provides configuration, LocalLayoutDirection provides if (enlarged) LayoutDirection.Rtl else LayoutDirection.Ltr) {
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
