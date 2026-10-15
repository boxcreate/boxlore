package cx.aswin.boxlore.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.BoxloreClickInteraction
import cx.aswin.boxlore.core.designsystem.theme.boxloreCombinedClickable
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletInputTest {
    private lateinit var inputMode: InputModeManager

    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))

    @Test
    fun `transport keys override button Space but leave text editing and modifiers native`() {
        var toggles = 0
        var backward = 0
        var forward = 0
        var clicks = 0
        val enabled = mutableStateOf(true)
        var text by mutableStateOf("")
        fixture.setContent {
            inputMode = LocalInputModeManager.current
            BoxLoreTheme {
                TabletPlaybackKeyboard(enabled.value, PlaybackKeyboardActions({ toggles++ }, { backward++ }, { forward++ })) { modifier ->
                    Column(modifier.testTag("keyboard")) {
                        Button({ clicks++ }, Modifier.testTag("button")) { Text("Action") }
                        BasicTextField(text, { text = it }, Modifier.testTag("editor"))
                    }
                }
            }
        }
        fixture.compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        fixture.compose.onNodeWithTag("button").requestFocus()
        fixture.compose.onNodeWithTag("keyboard").performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
            keyDown(Key.CtrlLeft)
            pressKey(Key.DirectionLeft)
            keyUp(Key.CtrlLeft)
        }
        assertEquals(1, toggles)
        assertEquals(1, backward)
        assertEquals(1, forward)
        assertEquals(0, clicks)
        fixture.compose.onNodeWithTag("keyboard").performKeyInput {
            keyDown(Key.Spacebar)
            keyDown(Key.CtrlLeft)
            keyUp(Key.Spacebar)
            keyUp(Key.CtrlLeft)
            pressKey(Key.Spacebar)
        }
        assertEquals("A modified key-up must not latch the next Space press", 3, toggles)
        fixture.compose.onNodeWithTag("editor").performClick()
        advance()
        fixture.compose.runOnIdle { enabled.value = false }
        advance()
        fixture.compose.runOnIdle { enabled.value = true }
        advance()
        fixture.compose.onNodeWithTag("editor").assertIsFocused()
        fixture.compose.onNodeWithTag("editor").performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.DirectionLeft)
        }
        advance()
        assertEquals(" ", text)
        assertEquals(3, toggles)
        assertEquals(1, backward)
    }

    @Test
    fun `phone key handling remains with the focused native button`() {
        var toggles = 0
        var clicks = 0
        fixture.setContent {
            inputMode = LocalInputModeManager.current
            BoxLoreTheme {
                TabletPlaybackKeyboard(false, PlaybackKeyboardActions({ toggles++ }, {}, {})) { modifier ->
                    Column(modifier) { Button({ clicks++ }, Modifier.testTag("button")) { Text("Action") } }
                }
            }
        }
        fixture.compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        fixture.compose.onNodeWithTag("button").requestFocus()
        fixture.compose.onNodeWithTag("button").performKeyInput { pressKey(Key.Spacebar) }
        advance()
        assertEquals(0, toggles)
        assertEquals(1, clicks)
    }

    @Test
    fun `secondary click invokes the existing menu once and respects disabled actions`() {
        var clicks = 0
        var menus = 0
        val enabled = mutableStateOf(true)
        fixture.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1_280f, 800f)) {
                BoxLoreTheme {
                    val focus = remember { FocusRequester() }
                    Text(
                        "Card",
                        Modifier.size(160.dp, 80.dp).focusRequester(focus).testTag("card")
                            .expressiveClickable(enabled = enabled.value, onLongClick = { menus++ }, onClick = { clicks++ }),
                    )
                }
            }
        }
        fixture.compose.onNodeWithTag("card").performMouseInput { click(button = MouseButton.Secondary) }
        assertEquals(1, menus)
        assertEquals(0, clicks)
        fixture.compose.onNodeWithTag("card").performMouseInput { click() }
        assertEquals(1, clicks)
        fixture.compose.runOnIdle { enabled.value = false }
        advance()
        fixture.compose.onNodeWithTag("card").performMouseInput { click(button = MouseButton.Secondary) }
        assertEquals(1, menus)
        assertEquals(1, clicks)
    }

    @Test
    fun `nested secondary click belongs to the deepest eligible menu`() {
        var outer = 0
        var inner = 0
        var clicks = 0
        fixture.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1_280f, 800f)) {
                BoxLoreTheme {
                    Box(Modifier.size(200.dp).expressiveClickable(onLongClick = { outer++ }, onClick = { clicks++ })) {
                        Text("Inner", Modifier.size(100.dp).testTag("inner").expressiveClickable(onLongClick = { inner++ }, onClick = { clicks++ }))
                    }
                }
            }
        }
        fixture.compose.onNodeWithTag("inner", useUnmergedTree = true).performMouseInput { click(button = MouseButton.Secondary) }
        assertEquals(1, inner)
        assertEquals(0, outer)
        assertEquals(0, clicks)
    }

    @Test
    fun `secondary click follows live width height callback and enablement changes for both source modes`() {
        val window = mutableStateOf(BoxloreWindowLayout(600f, 480f))
        val enabled = mutableStateOf(true)
        val hasAction = mutableStateOf(true)
        val updatedAction = mutableStateOf(false)
        val originalMenus = IntArray(3)
        val currentMenus = IntArray(3)
        val clicks = IntArray(3)
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme {
                    Column {
                        repeat(3) { index ->
                            val source = remember { MutableInteractionSource() }
                            val action: (() -> Unit)? = if (hasAction.value) {
                                if (updatedAction.value) ({ currentMenus[index]++ }) else ({ originalMenus[index]++ })
                            } else {
                                null
                            }
                            Text(
                                "Card",
                                Modifier.size(160.dp, 80.dp).testTag("source_$index").boxloreCombinedClickable(
                                    enabled = enabled.value,
                                    onLongClick = action,
                                    interaction = when (index) {
                                        0 -> null
                                        1 -> BoxloreClickInteraction(source, null)
                                        else -> BoxloreClickInteraction(null, null)
                                    },
                                    onClick = { clicks[index]++ },
                                ),
                            )
                        }
                    }
                }
            }
        }
        fun secondaryClick() = repeat(3) { index ->
            fixture.compose.onNodeWithTag("source_$index").performMouseInput { click(button = MouseButton.Secondary) }
        }
        secondaryClick()
        assertEquals(listOf(1, 1, 1), originalMenus.toList())
        fixture.compose.runOnIdle { updatedAction.value = true }
        advance()
        secondaryClick()
        assertEquals(listOf(1, 1, 1), currentMenus.toList())
        listOf(BoxloreWindowLayout(599f, 480f), BoxloreWindowLayout(600f, 479f)).forEachIndexed { transition, compact ->
            fixture.compose.runOnIdle { window.value = compact }
            advance()
            secondaryClick()
            assertEquals(List(3) { transition + 1 }, currentMenus.toList())
            repeat(3) { index -> fixture.compose.onNodeWithTag("source_$index").performMouseInput { click() } }
            fixture.compose.runOnIdle { window.value = BoxloreWindowLayout(600f, 480f) }
            advance()
            secondaryClick()
            assertEquals(List(3) { transition + 2 }, currentMenus.toList())
        }
        val nativeClicks = clicks.toList()
        fixture.compose.runOnIdle { enabled.value = false }
        advance()
        secondaryClick()
        repeat(3) { index -> fixture.compose.onNodeWithTag("source_$index").performMouseInput { click() } }
        assertEquals(nativeClicks, clicks.toList())
        assertEquals(listOf(3, 3, 3), currentMenus.toList())
        fixture.compose.runOnIdle {
            enabled.value = true
            hasAction.value = false
        }
        advance()
        secondaryClick()
        assertEquals(listOf(3, 3, 3), currentMenus.toList())
        fixture.compose.runOnIdle { hasAction.value = true }
        advance()
        secondaryClick()
        assertEquals(listOf(4, 4, 4), currentMenus.toList())
        assertEquals(listOf(1, 1, 1), originalMenus.toList())
    }

    @Test
    fun `disabling during a held secondary gesture cancels its owner and reenabling starts cleanly`() {
        val enabled = mutableStateOf(true)
        var menus = 0
        var clicks = 0
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(600f, 480f)) {
                BoxLoreTheme {
                    Text(
                        "Card",
                        Modifier.size(160.dp, 80.dp).testTag("card")
                            .expressiveClickable(enabled = enabled.value, onLongClick = { menus++ }, onClick = { clicks++ }),
                    )
                }
            }
        }
        val target = fixture.compose.onNodeWithTag("card")
        target.performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
        }
        assertEquals(1, menus)
        fixture.compose.runOnIdle { enabled.value = false }
        advance()
        target.performMouseInput { release(MouseButton.Secondary) }
        assertEquals(1, menus)
        assertEquals(0, clicks)
        fixture.compose.runOnIdle { enabled.value = true }
        advance()
        target.performMouseInput { click(button = MouseButton.Secondary) }
        assertEquals(2, menus)
        assertEquals(0, clicks)
        target.performMouseInput { click() }
        assertEquals(1, clicks)
    }

    @Test
    fun `tablet custom surface paints hover and keyboard focus without changing geometry`() {
        lateinit var view: View
        fixture.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1_280f, 800f)) {
                BoxLoreTheme {
                    view = LocalView.current
                    Box(Modifier.size(160.dp, 80.dp).background(Color.White).testTag("feedback").expressiveClickable {})
                }
            }
        }
        val target = fixture.compose.onNodeWithTag("feedback")
        val bounds = target.fetchSemanticsNode().boundsInRoot
        fun pixels(): IntArray {
            var result = intArrayOf()
            fixture.compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                result = IntArray(160 * 80)
                bitmap.getPixels(result, 0, 160, bounds.left.toInt(), bounds.top.toInt(), 160, 80)
                bitmap.recycle()
            }
            return result
        }
        val plain = pixels()
        target.performMouseInput { enter(center) }
        advance()
        assertFalse(plain.contentEquals(pixels()))
        target.performMouseInput { exit() }
        advance()
        val unhovered = pixels()
        fixture.compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        target.requestFocus()
        target.assertIsFocused()
        advance()
        assertFalse(unhovered.contentEquals(pixels()))
        assertEquals(bounds, target.fetchSemanticsNode().boundsInRoot)
    }

    private fun advance() {
        fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
