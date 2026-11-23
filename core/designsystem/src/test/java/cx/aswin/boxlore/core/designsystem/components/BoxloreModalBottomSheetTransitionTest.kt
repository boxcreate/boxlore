package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.activity.ComponentDialog
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.ScreenViews
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * [BoxloreModalBottomSheet] keeps what the listener did in it, its switches and its scroll, when it
 * changes between a bottom sheet and a centred panel, in both directions: across 840dp, a tablet
 * turning, and a foldable half-opening as a book or a laptop. The flip never dismisses the sheet and
 * never counts a second page view. Values held in plain `remember` stay too, because the content
 * itself moves between the two hosts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoxloreModalBottomSheetTransitionTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private var dismissals = 0
    private var scroll: ScrollState? = null
    private val views = mutableListOf<String?>()

    @Before fun resetTrail() = ScreenViews.resetForTest()

    @After fun clearTrail() = ScreenViews.resetForTest()

    private fun show(start: BoxloreWindowLayout) {
        window.value = start
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    BoxloreModalBottomSheet(onDismissRequest = { dismissals++ }) {
                        TrackScreenView("test_sheet") { previous -> views += previous }
                        var on by rememberSaveable { mutableStateOf(false) }
                        Switch(checked = on, onCheckedChange = { on = it }, modifier = Modifier.testTag("toggle"))
                        // Not saveable: only moving the content itself keeps it.
                        var taps by remember { mutableIntStateOf(0) }
                        Button(onClick = { taps++ }, modifier = Modifier.testTag("tap")) { Text("Tapped $taps") }
                        val state = rememberScrollState()
                        scroll = state
                        Column(Modifier.fillMaxWidth().height(160.dp).verticalScroll(state)) {
                            repeat(40) { Text("Row $it", Modifier.height(40.dp)) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun switchOnAndScroll() {
        compose.onNodeWithTag("toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("toggle").assertIsOn()
        compose.onNodeWithTag("tap").performClick()
        compose.waitForIdle()
        runBlocking { compose.runOnIdle { scroll }!!.scrollTo(600) }
        compose.waitForIdle()
        assertEquals(600, scroll!!.value)
    }

    private fun assertKept() {
        compose.onNodeWithTag("toggle").assertIsOn()
        compose.onNodeWithText("Tapped 1").assertExists()
        assertEquals(600, scroll!!.value)
        assertEquals(0, dismissals)
    }

    private fun flip(vararg steps: BoxloreWindowLayout) {
        show(steps.first())
        switchOnAndScroll()
        compose.walkWindows(window, *steps.drop(1).toTypedArray()) { assertKept() }
        assertEquals(1, views.size)
    }

    @Test
    fun `switches and scroll survive crossing 840dp both ways`() {
        val narrow = BoxloreWindowLayout(839f, 600f)
        val wide = BoxloreWindowLayout(841f, 600f)
        flip(narrow, wide, narrow, wide)
    }

    @Test
    fun `switches and scroll survive a tablet turning both ways`() {
        flip(BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.TABLET, BoxloreTestWindows.TABLET_PORTRAIT)
    }

    @Test
    fun `switches and scroll survive a foldable half-opening as a book and back`() {
        flip(BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.FOLD_BOOK, BoxloreTestWindows.FOLD_FLAT)
    }

    @Test
    fun `switches and scroll survive a foldable half-opening like a laptop and back`() {
        flip(BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.FOLD_TABLETOP, BoxloreTestWindows.FOLD_FLAT)
    }

    @Test
    fun `a phone sheet kept through unfolding and folding again`() {
        flip(BoxloreTestWindows.PHONE, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.PHONE)
        assertNull(views.single())
    }

    @Test
    fun `a sheet back from the centred panel settles as it opened, and Back still closes it once`() {
        val restoration = StateRestorationTester(compose)
        var sheetState: SheetState? = null
        window.value = BoxloreTestWindows.PHONE
        restoration.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    val state = rememberModalBottomSheetState().also { sheetState = it }
                    BoxloreModalBottomSheet(onDismissRequest = { dismissals++ }, sheetState = state) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            repeat(30) { Text("Row $it", Modifier.height(40.dp)) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(SheetValue.PartiallyExpanded, sheetState!!.currentValue)

        compose.walkWindows(window, BoxloreTestWindows.TABLET, BoxloreTestWindows.PHONE) {}
        assertEquals(SheetValue.PartiallyExpanded, sheetState!!.currentValue)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(1, dismissals)
        assertFalse(sheetState!!.isVisible)
    }

    @Test
    fun `a centred panel gives content the drag handle's room, and no extra room when the sheet has no handle`() {
        val withHandle = mutableStateOf(true)
        window.value = BoxloreTestWindows.PHONE
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    BoxloreModalBottomSheet(
                        onDismissRequest = {},
                        dragHandle = if (withHandle.value) ({ BottomSheetDefaults.DragHandle() }) else null,
                    ) { Text("Title", Modifier.testTag("first")) }
                }
            }
        }
        compose.walkWindows(window, BoxloreTestWindows.FOLD_FLAT) {}
        fun gap() =
            compose.onNodeWithTag("first").getUnclippedBoundsInRoot().top -
                compose.onNodeWithTag("expanded_sheet_panel").getUnclippedBoundsInRoot().top
        val withHandleGap = gap()
        withHandle.value = false
        compose.waitForIdle()
        assertEquals(CenteredSheetTopSpace.value, (withHandleGap - gap()).value, 0.5f)
    }
}
