package cx.aswin.boxlore.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DefaultBasicAlertDialogOverride
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.LocalBasicAlertDialogOverride
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ComponentOverrideApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletAlertHostingTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(673, 841, false))
    private lateinit var view: View
    private val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)

    @Test
    fun `phone alert override paints every native Material pixel unchanged`() {
        val adapted = mutableStateOf(false)
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(412f, 915f)) {
                BoxLoreTheme(dynamicColor = false) {
                    val themed = LocalBasicAlertDialogOverride.current
                    CompositionLocalProvider(LocalBasicAlertDialogOverride provides if (adapted.value) themed else DefaultBasicAlertDialogOverride) {
                        AlertDialog({}, confirmButton = { TextButton({}) { Text("Continue") } }, title = { Text("Confirm") }, text = {
                            view = LocalView.current
                            Text("Dialog content")
                        })
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
    fun `folded native alert retains owner buttons and blocked outside dismissal`() {
        var dismissed = 0
        var confirmed = 0
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(673f, 841f, listOf(fold))) {
                BoxLoreTheme {
                    AlertDialog(
                        { dismissed++ },
                        confirmButton = { TextButton({ confirmed++ }) { Text("Continue") } },
                        title = { Text("Confirm") },
                        text = { Text("Dialog content") },
                        properties = DialogProperties(dismissOnClickOutside = false),
                    )
                }
            }
        }
        advance()
        assertSafePanel(560f)
        fixture.compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        assertEquals(0, dismissed)
        fixture.compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `folded date picker keeps its confirmation reachable inside the safe region`() {
        var confirmed = false
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(673f, 841f, listOf(fold))) {
                BoxLoreTheme {
                    DatePickerDialog({}, confirmButton = { TextButton({ confirmed = true }) { Text("Select") } }) {
                        DatePicker(rememberDatePickerState())
                    }
                }
            }
        }
        advance()
        assertSafePanel(560f)
        fixture.compose.onNodeWithText("Select").assertIsDisplayed().performClick()
        assertTrue(confirmed)
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
