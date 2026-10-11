package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.LocalUpdateAvailableAction
import cx.aswin.boxlore.core.designsystem.components.TopBarUtilityActions
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopBarUtilityActionsTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var view: View
    private val actions = mutableListOf<String>()

    @Test fun `press retains the outer connected corners without a rectangular highlight`() {
        show(update = false)
        checkCornerWhilePressed("Send Feedback", trailing = false)
        checkCornerWhilePressed("Settings", trailing = true)
        assertEquals(emptyList<String>(), actions)
    }

    @Test fun `update segment also retains its rounded corner while shimmering and pressed`() {
        show(update = true)
        checkCornerWhilePressed("Update available", trailing = false)
        checkCornerWhilePressed("Settings", trailing = true)
        assertEquals(emptyList<String>(), actions)
    }

    @Test fun `native click and long press keep the existing action callbacks`() {
        show(update = true)
        composeRule.onNodeWithContentDescription("Update available").performClick()
        composeRule.onNodeWithContentDescription("Send Feedback").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithContentDescription("Send Feedback").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Settings").performTouchInput { longClick() }
        assertEquals(listOf("update", "feedback", "settings", "feedback_long", "settings_long"), actions)
    }

    private fun checkCornerWhilePressed(label: String, trailing: Boolean) {
        val node = composeRule.onNodeWithContentDescription(label)
        composeRule.mainClock.autoAdvance = false
        node.performTouchInput { down(center) }
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.mainClock.advanceTimeBy(120)
        composeRule.waitForIdle()
        val bounds = node.getUnclippedBoundsInRoot()
        val x = if (trailing) bounds.right.value.toInt() - 6 else bounds.left.value.toInt() + 5
        val y = bounds.top.value.toInt() + 3
        val pixel = composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(x, y).also { bitmap.recycle() }
        }
        assertEquals("$label keeps its outer corner clipped during press", Color.Magenta.toArgb(), pixel)
        node.performTouchInput { cancel() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    private fun show(update: Boolean) {
        val updateAction: (() -> Unit)? = if (update) ({ actions.add("update") }) else null
        composeRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalUpdateAvailableAction provides updateAction) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(Color.Magenta).padding(24.dp)) {
                        TopBarUtilityActions(
                            onFeedbackClick = { actions.add("feedback") },
                            onSettingsClick = { actions.add("settings") },
                            onFeedbackLongClick = { actions.add("feedback_long") },
                            onSettingsLongClick = { actions.add("settings_long") },
                        )
                    }
                }
            }
        }
    }
}
