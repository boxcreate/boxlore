package cx.aswin.boxlore.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialog
import cx.aswin.boxlore.core.designsystem.components.PrivacyConsentDialog
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletAlertHostingTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(673, 841, false))
    private lateinit var view: View
    private val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)

    @Test
    fun `phone alert paints every native Material pixel unchanged`() {
        val adapted = mutableStateOf(false)
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(412f, 915f)) {
                BoxLoreTheme(dynamicColor = false) {
                    val confirm = @Composable { TextButton({}) { Text("Continue") } }
                    val title = @Composable { Text("Confirm") }
                    val text = @Composable {
                        view = LocalView.current
                        Text("Dialog content")
                    }
                    if (adapted.value) {
                        BoxloreAlertDialog({}, confirmButton = confirm, title = title, text = text)
                    } else {
                        AlertDialog({}, confirmButton = confirm, title = title, text = text)
                    }
                }
            }
        }
        advance()
        val native = pixels()
        fixture.compose.runOnIdle { adapted.value = true }
        advance()
        assertArrayEquals(native, pixels())
    }

    @Test
    fun `folded alert retains owner buttons and blocked outside dismissal`() {
        var dismissed = 0
        var confirmed = 0
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(673f, 841f, listOf(fold))) {
                BoxLoreTheme {
                    BoxloreAlertDialog(
                        { dismissed++ },
                        confirmButton = { TextButton({ confirmed++ }) { Text("Continue") } },
                        dismissButton = { TextButton({}) { Text("Cancel") } },
                        title = { Text("Confirm") },
                        text = { Text("Dialog content") },
                        properties = DialogProperties(dismissOnClickOutside = false),
                    )
                }
            }
        }
        advance()
        assertSafePanel(560f)
        fixture.compose.onNodeWithText("Confirm").assertIsDisplayed()
        fixture.compose.onNodeWithText("Dialog content").assertIsDisplayed()
        fixture.compose.onNodeWithText("Cancel").assertIsDisplayed()
        fixture.compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        assertEquals(0, dismissed)
        fixture.compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `open alert keeps its slots and their state across phone, tablet and fold changes`() {
        val layouts = listOf(
            BoxloreWindowLayout(412f, 915f), // phone
            BoxloreWindowLayout(673f, 841f), // unfolded tablet
            BoxloreWindowLayout(673f, 841f, listOf(fold)), // half-folded
        )
        val layout = mutableStateOf(layouts[0])
        var confirmed = 0
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides layout.value) {
                BoxLoreTheme {
                    BoxloreAlertDialog(
                        {},
                        confirmButton = { TextButton({ confirmed++ }) { Text("Continue") } },
                        title = { Text("Confirm") },
                        text = {
                            var taps by remember { mutableIntStateOf(0) }
                            var saved by rememberSaveable { mutableIntStateOf(0) }
                            TextButton({
                                taps++
                                saved++
                            }) { Text("Taps $taps saved $saved") }
                        },
                    )
                }
            }
        }
        advance()
        assertNativePanel()
        fixture.compose.onNodeWithText("Taps 0 saved 0").performClick()
        // Phone → unfolded tablet → folded → unfolded tablet → phone: native, native, fold-safe, native, native.
        for ((step, index) in listOf(1, 2, 1, 0).withIndex()) {
            fixture.compose.runOnIdle { layout.value = layouts[index] }
            advance()
            if (index == 2) assertSafePanel(560f) else assertNativePanel()
            fixture.compose.onNodeWithText("Taps ${step + 1} saved ${step + 1}").assertIsDisplayed().performClick()
            fixture.compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        }
        fixture.compose.onNodeWithText("Taps 5 saved 5").assertIsDisplayed()
        assertEquals(4, confirmed)
    }

    @Test
    fun `custom tablet portal retains content inside the same bounded frame`() {
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(673f, 841f, listOf(fold))) {
                BoxLoreTheme {
                    BoxloreDialog({}) { Surface(Modifier.fillMaxWidth().height(200.dp)) { Text("Custom content") } }
                }
            }
        }
        advance()
        assertSafePanel(640f)
        fixture.compose.onNodeWithText("Custom content").assertIsDisplayed()
    }

    @Test
    fun `privacy portal keeps the consent footer reachable and retains its required acceptance`() {
        var accepted = false
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(673f, 841f, listOf(fold))) {
                BoxLoreTheme { PrivacyConsentDialog { _, _ -> accepted = true } }
            }
        }
        advance()
        assertSafePanel(640f)
        fixture.compose.onNodeWithText("Accept & Continue").assertIsDisplayed().assertIsNotEnabled()
        fixture.compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        assertEquals(false, accepted)
        fixture.compose.onNodeWithText("Accept & Continue").assertExists()
    }

    private fun assertNativePanel() {
        fixture.compose.onNodeWithText("Confirm").assertIsDisplayed()
        assertFalse(fixture.compose.onAllNodesWithTag("boxlore_dialog_panel").fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertSafePanel(maxWidth: Float) {
        val bounds = fixture.compose.onNodeWithTag("boxlore_dialog_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width <= maxWidth)
        assertTrue(bounds.top >= 431f && bounds.bottom <= 841f)
    }

    private fun advance() {
        fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }

    private fun pixels(): IntArray {
        var result = intArrayOf()
        fixture.compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            result = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(result, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
        return result
    }
}
