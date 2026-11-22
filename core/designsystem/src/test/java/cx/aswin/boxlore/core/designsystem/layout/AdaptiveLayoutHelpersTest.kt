package cx.aswin.boxlore.core.designsystem.layout

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialog
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fold-safe placement helpers: the region that holds a pane or an anchor, menus that open in the
 * half they were opened from and never cross the hinge, the tabletop split, and the short-pane and
 * height helpers. With one region every helper gives the whole window, as on phones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w932dp-h650dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveLayoutHelpersTest {
    @get:Rule val compose = createComposeRule()

    private val mdpi = Density(1f)
    private val inner = IntSize(932, 650)
    private val leftHalf = BoxloreLayoutBounds(0f, 0f, 456f, 650f)
    private val rightHalf = BoxloreLayoutBounds(477f, 0f, 932f, 650f)
    private val upperHalf = BoxloreLayoutBounds(0f, 0f, 932f, 315f)
    private val lowerHalf = BoxloreLayoutBounds(0f, 336f, 932f, 650f)

    @Test
    fun `the region holding a pane or a point is the half it overlaps most`() {
        val book = BoxloreTestWindows.FOLD_BOOK
        assertEquals(rightHalf, book.foldSafeRegionContaining(BoxloreLayoutBounds(470f, 0f, 932f, 650f)))
        assertEquals(leftHalf, book.foldSafeRegionContaining(BoxloreLayoutBounds(80f, 0f, 470f, 650f)))
        // Inside the hinge: the region under the centre, else the first.
        assertEquals(leftHalf, book.foldSafeRegionContaining(BoxloreLayoutBounds(460f, 10f, 470f, 20f)))
        assertEquals(rightHalf, book.foldSafeRegionAt(700f, 300f))
        assertEquals(rightHalf, book.foldSafeRegionAt(470f, 300f))
        assertEquals(lowerHalf, BoxloreTestWindows.FOLD_TABLETOP.foldSafeRegionAt(10f, 340f))
        val flat = BoxloreTestWindows.FOLD_FLAT
        assertEquals(BoxloreLayoutBounds(0f, 0f, 932f, 650f), flat.foldSafeRegionContaining(BoxloreLayoutBounds(470f, 0f, 932f, 650f)))
    }

    @Test
    fun `a menu from a trailing anchor beside a vertical hinge stays in the left half`() {
        val anchor = IntRect(404, 8, 452, 56)
        val menu = IntSize(200, 300)
        for (endFirst in listOf(true, false)) {
            val provider = BoxloreFoldSafePopupPositionProvider(BoxloreTestWindows.FOLD_BOOK, mdpi, DpOffset.Zero, endFirst)
            val position = provider.calculatePosition(anchor, inner, LayoutDirection.Ltr, menu)
            assertEquals(IntOffset(252, 56), position)
            assertTrue(position.x + menu.width <= 456)
            assertEquals(650, provider.regionHeightPx)
        }
        // Right to left, the start side is the right: it still stays out of the hinge.
        val rtl = BoxloreFoldSafePopupPositionProvider(BoxloreTestWindows.FOLD_BOOK, mdpi, DpOffset.Zero, false)
            .calculatePosition(anchor, inner, LayoutDirection.Rtl, menu)
        assertTrue(rtl.x >= 0 && rtl.x + menu.width <= 456)
    }

    @Test
    fun `a menu below a horizontal hinge stays in the lower half and is capped to it`() {
        val provider = BoxloreFoldSafePopupPositionProvider(BoxloreTestWindows.FOLD_TABLETOP, mdpi, DpOffset.Zero, false)
        val anchor = IntRect(100, 343, 148, 391)
        // The cap: the lower half's 314dp less Material's 48dp margins.
        val capped = IntSize(200, 218)
        val position = provider.calculatePosition(anchor, inner, LayoutDirection.Ltr, capped)
        assertEquals(314, provider.regionHeightPx)
        assertTrue(position.y >= 336 && position.y + capped.height <= 650)
        // Even uncapped it starts below the hinge.
        assertTrue(provider.calculatePosition(anchor, inner, LayoutDirection.Ltr, IntSize(200, 600)).y >= 336)
        // An anchor above the hinge keeps its menu above it.
        val above = provider.calculatePosition(IntRect(100, 250, 148, 298), inner, LayoutDirection.Ltr, IntSize(200, 150))
        assertTrue(above.y + 150 <= 315)
    }

    @Test
    fun `with one region menus land where Material puts them`() {
        val phone = BoxloreFoldSafePopupPositionProvider(BoxloreTestWindows.PHONE, mdpi, DpOffset.Zero, false)
        val window = IntSize(412, 915)
        // Material: start-aligned would overflow the right edge, so end-aligned; below the anchor.
        assertEquals(IntOffset(148, 148), phone.calculatePosition(IntRect(300, 100, 348, 148), window, LayoutDirection.Ltr, IntSize(200, 300)))
        // Material: start-aligned; no room below (48dp margin), so above the anchor.
        assertEquals(IntOffset(20, 500), phone.calculatePosition(IntRect(20, 800, 68, 848), window, LayoutDirection.Ltr, IntSize(200, 300)))
        // Material: the offset moves the menu along the anchor's start and below it.
        val offset = BoxloreFoldSafePopupPositionProvider(BoxloreTestWindows.PHONE, mdpi, DpOffset(8.dp, 4.dp), false)
        assertEquals(IntOffset(28, 152), offset.calculatePosition(IntRect(20, 100, 68, 148), window, LayoutDirection.Ltr, IntSize(200, 300)))
        // No window model (an isolated preview): the whole window.
        val none = BoxloreFoldSafePopupPositionProvider(BoxloreWindowLayout.Unspecified, mdpi, DpOffset.Zero, false)
        assertEquals(IntOffset(148, 148), none.calculatePosition(IntRect(300, 100, 348, 148), window, LayoutDirection.Ltr, IntSize(200, 300)))
    }

    @Test
    fun `a popup's height is capped to its region only on a half-open fold`() {
        var window by mutableStateOf(BoxloreTestWindows.FOLD_TABLETOP)
        val provider = BoxloreFoldSafePopupPositionProvider(BoxloreTestWindows.FOLD_TABLETOP, mdpi, DpOffset.Zero, false)
        provider.calculatePosition(IntRect(100, 343, 148, 391), inner, LayoutDirection.Ltr, IntSize(200, 218))
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window) {
                Box(Modifier.boxloreMaxHeightInRegion(provider).testTag("capped")) { Box(Modifier.fillMaxWidth().height(600.dp)) }
                Box(Modifier.boxloreMaxHeightInRegion().testTag("shortest")) { Box(Modifier.fillMaxWidth().height(600.dp)) }
            }
        }
        compose.onNodeWithTag("capped").assertHeightIs(218)
        compose.onNodeWithTag("shortest").assertHeightIs(218)
        window = BoxloreTestWindows.FOLD_FLAT
        compose.waitForIdle()
        compose.onNodeWithTag("capped").assertHeightIs(600)
        compose.onNodeWithTag("shortest").assertHeightIs(600)
    }

    @Test
    fun `a panel from the right pane of a book fold opens in the right half, otherwise in the left`() {
        var pane by mutableStateOf<BoxloreLayoutBounds?>(rightHalf)
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.FOLD_BOOK, LocalBoxlorePaneBounds provides pane) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                    BoxloreDialog(onDismissRequest = {}) { Text("Panel", Modifier.fillMaxWidth().height(200.dp)) }
                }
            }
        }
        compose.waitForIdle()
        val right = compose.onNodeWithTag("boxlore_dialog_panel").fetchSemanticsNode().boundsInRoot
        assertTrue("$right", right.left >= 477f && right.right <= 932f)

        pane = null
        compose.waitForIdle()
        val left = compose.onNodeWithTag("boxlore_dialog_panel").fetchSemanticsNode().boundsInRoot
        assertTrue("$left", left.right <= 456f)

        // Browsing across the whole fold, with nothing open: the panel still keeps to one half.
        pane = BoxloreLayoutBounds(80f, 0f, 932f, 650f)
        compose.waitForIdle()
        val spanning = compose.onNodeWithTag("boxlore_dialog_panel").fetchSemanticsNode().boundsInRoot
        assertTrue("$spanning", spanning.right <= 456f)
    }

    @Test
    fun `the tabletop split keeps each slot on its side of the hinge and keeps what they hold`() {
        val window = mutableStateOf(BoxloreTestWindows.FOLD_FLAT)
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxloreTabletopSplit(
                    top = { Counter("top") },
                    bottom = { Counter("bottom") },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithText("top 0 · 0").performClick()
        compose.onNodeWithText("bottom 0 · 0").performClick()
        compose.onNodeWithText("bottom 1 · 1").performClick()

        compose.walkWindows(window, BoxloreTestWindows.FOLD_TABLETOP, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.FOLD_TABLETOP) { step ->
            compose.onNodeWithText("top 1 · 1").assertExists()
            compose.onNodeWithText("bottom 2 · 2").assertExists()
            val top = compose.onNodeWithTag("top").fetchSemanticsNode().boundsInWindow
            val bottom = compose.onNodeWithTag("bottom").fetchSemanticsNode().boundsInWindow
            if (step.tabletopFold != null) {
                assertTrue("$top", top.bottom <= 315f)
                assertTrue("$bottom", bottom.top >= 336f)
            } else {
                assertEquals(top.bottom, bottom.top)
            }
        }
    }

    @Composable
    private fun Counter(name: String) {
        var saved by rememberSaveable { mutableIntStateOf(0) }
        var held by remember { mutableIntStateOf(0) }
        Box(Modifier.fillMaxWidth().height(120.dp).testTag(name)) {
            Button(
                onClick = {
                    saved++
                    held++
                },
            ) { Text("$name $saved · $held") }
        }
    }

    @Test
    fun `short panes are sideways phones, split-screen halves and a tabletop half`() {
        val cases = listOf(
            Triple(BoxloreTestWindows.PHONE_LAND, null, true),
            Triple(BoxloreTestWindows.SPLIT_HALF, null, true),
            Triple(BoxloreTestWindows.PHONE, null, false),
            Triple(BoxloreTestWindows.COVER_443, null, false),
            Triple(BoxloreTestWindows.FOLD_FLAT, null, false),
            Triple(BoxloreTestWindows.FOLD_TABLETOP, lowerHalf, true),
        )
        var case by mutableStateOf(cases.first())
        var short: Boolean? = null
        var height = 0f
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides case.first, LocalBoxlorePaneBounds provides case.second) {
                short = boxloreIsShortPane()
                height = boxloreContentHeightDp()
            }
        }
        for (next in cases) {
            case = next
            compose.waitForIdle()
            assertEquals("${next.first}", next.third, short)
            assertEquals(next.second?.height ?: next.first.heightDp, height)
        }
    }

    @Test
    fun `a dialog can use its region's height on a half-open fold and the window's otherwise`() {
        var window by mutableStateOf(BoxloreTestWindows.FOLD_TABLETOP)
        var pane by mutableStateOf<BoxloreLayoutBounds?>(null)
        var available = 0f
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window, LocalBoxlorePaneBounds provides pane) {
                available = boxloreAvailableHeightDp()
            }
        }
        compose.waitForIdle()
        assertEquals(314f, available)
        pane = upperHalf
        compose.waitForIdle()
        assertEquals(315f, available)
        window = BoxloreTestWindows.FOLD_FLAT
        pane = null
        compose.waitForIdle()
        assertEquals(650f, available)
        window = BoxloreTestWindows.PHONE_LAND
        compose.waitForIdle()
        assertEquals(412f, available)
    }

    @Test
    fun `the reading column caps wide panes at 720dp, centred, and fills narrow ones`() {
        var paneWidth by mutableIntStateOf(915)
        compose.setContent {
            Box(Modifier.width(paneWidth.dp)) {
                Box(Modifier.boxloreReadingColumn().height(10.dp).testTag("column"))
            }
        }
        compose.waitForIdle()
        val wide = compose.onNodeWithTag("column").fetchSemanticsNode().boundsInRoot
        assertEquals(720f, wide.width, 0.01f)
        assertEquals(97.5f, wide.left, 0.5f)
        paneWidth = 412
        compose.waitForIdle()
        assertEquals(412f, compose.onNodeWithTag("column").fetchSemanticsNode().boundsInRoot.width, 0.01f)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertHeightIs(expected: Int) {
        assertEquals(expected, fetchSemanticsNode().size.height)
    }
}
