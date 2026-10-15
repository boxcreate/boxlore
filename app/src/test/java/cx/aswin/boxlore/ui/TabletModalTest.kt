package cx.aswin.boxlore.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletModalTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))
    private lateinit var view: View

    @Test
    fun `phone adapter paints exactly the native sheet`() {
        val adapted = mutableStateOf(false)
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(412f, 915f)) {
                BoxLoreTheme {
                    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    if (adapted.value) {
                        BoxloreModalBottomSheet({}, sheetState = state) {
                            view = LocalView.current
                            Text("Sheet content", Modifier.fillMaxWidth().height(200.dp))
                        }
                    } else {
                        ModalBottomSheet({}, sheetState = state) {
                            view = LocalView.current
                            Text("Sheet content", Modifier.fillMaxWidth().height(200.dp))
                        }
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
    fun `expanded sheet is centered bounded and dismisses only outside its panel`() {
        var dismissed = 0
        render(BoxloreWindowLayout(1_280f, 800f)) { dismissed++ }
        val bounds = fixture.compose.onNodeWithTag("expanded_sheet_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width <= 640f)
        assertEquals(640f, bounds.center.x, 1f)
        assertTrue(bounds.top >= 24f && bounds.bottom <= 776f)
        fixture.compose.onNodeWithText("Sheet content").performTouchInput { click() }
        assertEquals(0, dismissed)
        fixture.compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        assertEquals(1, dismissed)
    }

    @Test
    fun `owner controlled sheet blocks outside dismissal and retains explicit close`() {
        var dismissed = 0
        render(BoxloreWindowLayout(1_280f, 800f), dismissAllowed = false) { dismissed++ }
        fixture.compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        assertEquals(0, dismissed)
        fixture.compose.onNodeWithText("Close").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `expanded dialog stays inside one physical fold region`() {
        render(
            BoxloreWindowLayout(1_280f, 800f, listOf(BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Vertical, BoxloreLayoutBounds(630f, 0f, 650f, 800f), true, true))),
        ) {}
        val bounds = fixture.compose.onNodeWithTag("expanded_sheet_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.right <= 630f || bounds.left >= 650f)
    }

    @Test
    fun `tabletop dialog keeps a long finite form reachable without crossing the hinge`() {
        var finished = false
        fixture.setContent {
            val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(673f, 841f, listOf(fold))) {
                BoxLoreTheme {
                    BoxloreModalBottomSheet({}) {
                        Column(Modifier.fillMaxWidth().tabletSheetScroll()) {
                            repeat(12) { Text("Option $it", Modifier.height(48.dp)) }
                            Button({ finished = true }) { Text("Finish") }
                        }
                    }
                }
            }
        }
        advance()
        val bounds = fixture.compose.onNodeWithTag("expanded_sheet_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.bottom <= 410f || bounds.top >= 431f)
        fixture.compose.onNodeWithText("Finish").performScrollTo().performClick()
        assertTrue(finished)
    }

    @Test
    fun `dialog retains draft state and current owner across the tablet width boundary in both directions`() {
        assertDialogRetainsDraftAcrossResize(
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
        )
    }

    @Test
    fun `dialog retains draft state and current owner across the phone width boundary in both directions`() {
        assertDialogRetainsDraftAcrossResize(
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(600f, 800f),
        )
    }

    @Test
    fun `dialog retains draft state and current owner across the tablet height boundary in both directions`() {
        assertDialogRetainsDraftAcrossResize(
            BoxloreWindowLayout(1_280f, 480f),
            BoxloreWindowLayout(1_280f, 479f),
        )
    }

    @Test
    fun `dialog retains draft state and current owner across the phone height boundary in both directions`() {
        assertDialogRetainsDraftAcrossResize(
            BoxloreWindowLayout(1_280f, 479f),
            BoxloreWindowLayout(1_280f, 480f),
        )
    }

    @Test
    fun `dialog retains draft state and current owner while folding and unfolding`() {
        val flat = BoxloreWindowLayout(1_280f, 800f)
        assertDialogRetainsDraftAcrossResize(
            flat,
            flat.copy(
                folds = listOf(
                    BoxloreFold(
                        BoxloreFoldState.HalfOpened,
                        BoxloreFoldOrientation.Horizontal,
                        BoxloreLayoutBounds(0f, 390f, 1_280f, 410f),
                        true,
                        true,
                    ),
                ),
            ),
        )
    }

    private fun assertDialogRetainsDraftAcrossResize(initial: BoxloreWindowLayout, resized: BoxloreWindowLayout) {
        val window = mutableStateOf(initial)
        val owner = mutableIntStateOf(0)
        val savedDrafts = mutableListOf<Pair<Int, Int>>()
        lateinit var dialogScroll: ScrollState
        fixture.compose.mainClock.autoAdvance = false
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme {
                    val currentOwner = owner.intValue
                    BoxloreDialog({}) {
                        val draft = remember { mutableIntStateOf(0) }
                        val scroll = rememberScrollState()
                        val dialogView = LocalView.current
                        SideEffect {
                            view = dialogView
                            dialogScroll = scroll
                        }
                        Column(Modifier.fillMaxWidth().height(200.dp).verticalScroll(scroll)) {
                            Button({ draft.intValue++ }) { Text("Draft ${draft.intValue}") }
                            repeat(8) { Text("Form option $it", Modifier.height(48.dp)) }
                            Button({ savedDrafts += currentOwner to draft.intValue }) {
                                Text("Save with owner $currentOwner")
                            }
                        }
                    }
                }
            }
        }
        advance()
        val originalHost = view
        fixture.compose.onNodeWithText("Draft 0").performClick()
        advance()
        fixture.compose.onNodeWithText("Draft 1").assertExists()
        fixture.compose.runOnIdle { runBlocking { dialogScroll.scrollTo(dialogScroll.maxValue) } }
        advance()
        val originalScroll = dialogScroll.value
        assertTrue("The fixture must have an unsaved nonzero scroll position", originalScroll > 0)

        fixture.compose.runOnIdle {
            owner.intValue = 1
            window.value = resized
        }
        advance()
        fixture.compose.onNodeWithText("Draft 1").assertExists()
        assertSame("Resizing must retain the native dialog host", originalHost, view)
        assertEquals("Resizing must retain the form scroll position", originalScroll, dialogScroll.value)
        fixture.compose.runOnIdle { runBlocking { dialogScroll.scrollTo(0) } }
        advance()
        fixture.compose.onNodeWithText("Draft 1").performClick()
        advance()
        fixture.compose.onNodeWithText("Draft 2").assertExists()
        fixture.compose.runOnIdle { runBlocking { dialogScroll.scrollTo(dialogScroll.maxValue) } }
        advance()
        fixture.compose.onNodeWithText("Save with owner 1").performClick()
        assertEquals(listOf(1 to 2), savedDrafts)
        val editedScroll = dialogScroll.value
        assertTrue(editedScroll > 0)

        fixture.compose.runOnIdle {
            owner.intValue = 2
            window.value = initial
        }
        advance()
        fixture.compose.onNodeWithText("Draft 2").assertExists()
        assertSame("Returning across the threshold must retain the native dialog host", originalHost, view)
        assertEquals("Returning must retain the edited form scroll position", editedScroll, dialogScroll.value)
        fixture.compose.onNodeWithText("Save with owner 2").performClick()
        assertEquals(listOf(1 to 2, 2 to 2), savedDrafts)
    }

    private fun render(window: BoxloreWindowLayout, dismissAllowed: Boolean = true, dismiss: () -> Unit) {
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window) {
                BoxLoreTheme {
                    BoxloreModalBottomSheet(dismiss, centeredDismissAllowed = dismissAllowed, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
                        Column(Modifier.fillMaxWidth().height(200.dp)) {
                            Text("Sheet content")
                            Button(dismiss) { Text("Close") }
                        }
                    }
                }
            }
        }
        advance()
    }

    private fun advance() {
        fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }

    private fun pixels(): IntArray {
        var pixels = intArrayOf()
        fixture.compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
        return pixels
    }
}
