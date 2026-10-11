package cx.aswin.boxlore.feature.settings.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeEditorPressFeedbackTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var view: View
    private val labels = listOf("Primary", "Secondary", "Tertiary")

    @Test fun `color role buttons have no press highlight in light mode`() = checkPressFeedback(false, false)

    @Test fun `color role buttons have no press highlight in dark mode`() = checkPressFeedback(true, false)

    @Test fun `wrapped selectors retain corners and selection with large text and RTL`() = checkPressFeedback(true, true)

    private fun checkPressFeedback(dark: Boolean, enlarged: Boolean) {
        composeRule.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides if (enlarged) LayoutDirection.Rtl else LayoutDirection.Ltr, LocalDensity provides Density(density.density, if (enlarged) 2f else 1f)) {
                BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                    CustomThemeEditor(AppearanceUiState("system", false, "violet", "classic_dynamic"), onDismiss = {}, onSave = {})
                }
            }
        }
        labels.forEachIndexed { index, label ->
            val node = composeRule.onNodeWithText(label)
            val before = backgroundPixel(node)
            composeRule.mainClock.autoAdvance = false
            node.performTouchInput { down(center) }
            composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
            repeat(2) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
            composeRule.mainClock.advanceTimeBy(180)
            composeRule.waitForIdle()
            assertEquals("$label has no pressed color overlay", before, backgroundPixel(node))
            node.performTouchInput { cancel() }
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            if (index == 0) node.assertIsSelected() else node.assertIsNotSelected()
            node.performClick().assertIsSelected()
        }
    }

    private fun backgroundPixel(node: SemanticsNodeInteraction): Int {
        val bounds = node.getUnclippedBoundsInRoot()
        return composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(((bounds.left.value + bounds.right.value) / 2).toInt(), bounds.bottom.value.toInt() - 7).also { bitmap.recycle() }
        }
    }
}
