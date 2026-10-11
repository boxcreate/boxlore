package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shared modifier regressions run in Home's existing native-graphics JVM host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpressivePressFeedbackTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var view: View
    private val enabled = mutableStateOf(true)
    private val events = mutableListOf<String>()

    @Test fun `surface stays still and clips Material state feedback inside rounded corners`() {
        val source = MutableInteractionSource()
        show(Scene(source = source))
        val resting = bounds("target")
        val centerBefore = pixel(resting.center)
        hold("target")
        assertEquals(resting, bounds("target"))
        assertEquals("Ripple cannot square off the surface corner", Color.Magenta.toArgb(), pixel(resting.topLeft + Offset(2f, 2f)))
        cancel("target")
        // Android's hardware press ripple is not drawn by View.draw's software canvas.
        // Its Compose focus state layer does render here, and shares the target's clip.
        composeRule.runOnIdle { assertTrue(source.tryEmit(FocusInteraction.Focus())) }
        advance(500)
        assertNotEquals("The Material indication remains visible", centerBefore, pixel(resting.center))
        assertEquals("State feedback retains rounded corners", Color.Magenta.toArgb(), pixel(resting.topLeft + Offset(2f, 2f)))
        assertEquals(emptyList<String>(), events)
    }

    @Test fun `control shrinks only three percent and cancel restores it without bouncing`() {
        show(Scene(feedback = ExpressivePressFeedback.Control))
        val resting = bounds("target")
        hold("target")
        assertEquals(resting.width * 0.97f, bounds("target").width, 0.4f)
        assertEquals(resting.center.x, bounds("target").center.x, 0.1f)
        composeRule.onNodeWithTag("target").performTouchInput { cancel() }
        repeat(12) {
            advance(32)
            assertTrue("Release must not overshoot", bounds("target").width <= resting.width + 0.1f)
        }
        assertEquals(resting.width, bounds("target").width, 0.1f)
        assertEquals(emptyList<String>(), events)
    }

    @Test fun `nested control owns its gesture without activating or shrinking the card`() {
        show(Scene(nested = true))
        val resting = bounds("target")
        hold("child")
        assertEquals(resting, bounds("target"))
        composeRule.onNodeWithTag("child").performTouchInput { up() }
        advance(500)
        assertEquals(listOf("child"), events)
        composeRule.onNodeWithTag("target").performTouchInput { click(Offset(150f, 50f)) }
        assertEquals(listOf("child", "target"), events)
    }

    @Test fun `long press and accessible long press keep selection separate from opening`() {
        show(Scene(longPress = true))
        composeRule.onNodeWithTag("target").performTouchInput { longClick() }
        assertEquals(listOf("long"), events)
        val action = composeRule.onNodeWithTag("target").fetchSemanticsNode().config[SemanticsActions.OnLongClick]
        assertEquals("Select", action.label)
        composeRule.onNodeWithTag("target").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.onNodeWithTag("target").performTouchInput { click() }
        assertEquals(listOf("long", "long", "target"), events)
    }

    @Test fun `disabling a held control restores its bounds and cancels the click`() {
        show(Scene(feedback = ExpressivePressFeedback.Control))
        val resting = bounds("target")
        hold("target")
        composeRule.runOnIdle { enabled.value = false }
        advance(500)
        assertEquals(resting, bounds("target"))
        composeRule.onNodeWithTag("target").assertIsNotEnabled()
        composeRule.onNodeWithTag("target").performTouchInput { up() }
        assertEquals(emptyList<String>(), events)
        composeRule.runOnIdle { enabled.value = true }
        advance(500)
        composeRule.onNodeWithTag("target").performTouchInput { click() }
        assertEquals(listOf("target"), events)
    }

    @Test fun `shared source stays pressed until all simultaneous presses end`() {
        val source = MutableInteractionSource()
        show(Scene(feedback = ExpressivePressFeedback.Control, source = source))
        val resting = bounds("target")
        val first = PressInteraction.Press(Offset.Zero)
        val second = PressInteraction.Press(Offset(10f, 10f))
        composeRule.runOnIdle {
            assertTrue(source.tryEmit(first))
            assertTrue(source.tryEmit(second))
        }
        advance(500)
        assertEquals(resting.width * 0.97f, bounds("target").width, 0.4f)
        composeRule.runOnIdle { assertTrue(source.tryEmit(PressInteraction.Cancel(first))) }
        advance(500)
        assertEquals(resting.width * 0.97f, bounds("target").width, 0.4f)
        composeRule.runOnIdle { assertTrue(source.tryEmit(PressInteraction.Release(second))) }
        advance(500)
        assertEquals(resting.width, bounds("target").width, 0.1f)
    }

    @Test fun `drag scale override and explicit no indication remain supported`() {
        show(Scene(feedback = ExpressivePressFeedback.Control, scaleEnabled = false, rippleEnabled = false))
        val resting = bounds("target")
        val centerBefore = pixel(resting.center)
        hold("target")
        assertEquals(resting, bounds("target"))
        assertEquals(centerBefore, pixel(resting.center))
        cancel("target")
    }

    @Test fun `dark RTL enlarged text keeps surface bounds stable during press`() {
        show(Scene(dark = true, rtl = true, fontScale = 2f))
        val resting = bounds("target")
        hold("target")
        assertEquals(resting, bounds("target"))
        cancel("target")
        composeRule.onNodeWithTag("target").performTouchInput { click() }
        assertEquals(listOf("target"), events)
    }

    @Test fun `native button nested in a steady card keeps one handler`() {
        show(Scene(nativeButton = true))
        composeRule.onNodeWithTag("native").performTouchInput { click() }
        assertEquals(listOf("native"), events)
        assertFalse(events.contains("target"))
    }

    private data class Scene(
        val feedback: ExpressivePressFeedback = ExpressivePressFeedback.Surface,
        val nested: Boolean = false,
        val longPress: Boolean = false,
        val source: MutableInteractionSource? = null,
        val scaleEnabled: Boolean = true,
        val rippleEnabled: Boolean = true,
        val dark: Boolean = false,
        val rtl: Boolean = false,
        val fontScale: Float = 1f,
        val nativeButton: Boolean = false,
    )

    private fun show(scene: Scene = Scene()) = with(scene) {
        composeRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(1f, fontScale),
            ) {
                BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                    val shape = RoundedCornerShape(24.dp)
                    val click = if (longPress) {
                        Modifier.expressiveClickable(enabled = enabled.value, shape = shape, feedback = feedback, onLongClickLabel = "Select", onLongClick = { events.add("long") }, onClick = { events.add("target") })
                    } else {
                        Modifier.expressiveClickable(interactionSource = source, enabled = enabled.value, shape = shape, feedback = feedback, pressScaleEnabled = scaleEnabled, indication = if (rippleEnabled) androidx.compose.material3.ripple() else null, onClick = { events.add("target") })
                    }
                    Box(Modifier.fillMaxSize().background(Color.Magenta).padding(24.dp)) {
                        Surface(modifier = Modifier.size(200.dp, 100.dp).then(click).testTag("target"), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Box(Modifier.fillMaxSize().padding(20.dp)) {
                                if (nested) {
                                    Surface(
                                        modifier = Modifier.size(48.dp).expressiveClickable(shape = RoundedCornerShape(16.dp), feedback = ExpressivePressFeedback.Control, onClick = { events.add("child") }).testTag("child"),
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.Blue,
                                    ) {}
                                } else if (nativeButton) {
                                    Button(onClick = { events.add("native") }, modifier = Modifier.testTag("native")) { Text("Play") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun hold(tag: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(tag).performTouchInput { down(center) }
        advance(500)
    }

    private fun advance(ms: Long) {
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    private fun cancel(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { cancel() }
        advance(500)
        composeRule.mainClock.autoAdvance = true
    }

    private fun pixel(point: Offset): Int = composeRule.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.getPixel(point.x.toInt(), point.y.toInt()).also { bitmap.recycle() }
    }
}
