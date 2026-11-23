package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import kotlin.math.floor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tablet hero carousel crashed in material3's keyline math when it reached the end of the row
 * at widths that leave almost a whole card after the last full one (616–672dp, 952–1008dp,
 * 1288–1344dp for 320dp cards and 16dp gaps). In the first band, stable material3 also left the last
 * card past the end edge, so those windows fit two whole cards instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1400dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeroCarouselEndTest {
    @get:Rule val composeRule = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(1300f, 800f))
    private val direction = mutableStateOf(LayoutDirection.Ltr)

    /** The hero row's own width when it sits in a pane narrower than the window, or null for the window's. */
    private val pane = mutableStateOf<Float?>(null)
    private lateinit var state: CarouselState

    @Test fun `tablet carousel reaches the end at crashing widths`() {
        render()
        for (width in listOf(616f, 640f, 672f, 952f, 1000f, 1300f, 1344f, 1100f, 1400f)) {
            window.value = BoxloreWindowLayout(width, 800f)
            scrollToEndAndBack(width)
        }
    }

    @Test fun `end of the row survives live phone, tablet and foldable resizes in both directions`() {
        render()
        val windows = listOf(
            BoxloreWindowLayout(411f, 891f), // phone
            BoxloreTestWindows.COVER_475, // Fold 8 cover: one large card and a peek
            BoxloreTestWindows.PHONE_LAND, // sideways phone: a row of whole cards
            BoxloreWindowLayout(1300f, 800f), // tablet landscape, crashing band
            BoxloreWindowLayout(673f, 841f), // unfolded foldable, outside the band
            BoxloreWindowLayout(640f, 800f), // foldable or split window, crashing band
            BoxloreWindowLayout(800f, 1280f), // tablet portrait
            BoxloreWindowLayout(1300f, 800f),
            BoxloreWindowLayout(411f, 891f),
        )
        for (layout in windows + windows.reversed()) {
            window.value = layout
            scrollToEndAndBack(layout.widthDp)
        }
    }

    @Test fun `windows with almost a second card fit two whole cards`() {
        render()
        for (width in listOf(606f, 616f, 624f, 640f, 656f, 672f)) {
            window.value = BoxloreWindowLayout(width, 800f)
            scrollToEndAndBack(width)
        }
    }

    @Test fun `the row keeps its position when a resize changes the card width`() {
        render()
        window.value = BoxloreWindowLayout(640f, 800f) // two whole cards fill the row
        composeRule.waitForIdle()
        composeRule.runOnIdle { runBlocking { state.scrollToItem(3) } }
        composeRule.waitForIdle()
        // 640dp fits two narrower cards; 680dp keeps the 320dp cards. Cross that boundary both ways.
        for (width in listOf(680f, 640f, 673f, 640f)) {
            window.value = BoxloreWindowLayout(width, 800f)
            composeRule.waitForIdle()
            assertEquals("Item at ${width}dp", 3, state.currentItem)
            composeRule.onNodeWithTag("card_3").assertIsDisplayed()
        }
    }

    @Test fun `the current card survives cover, tablet, gate and sideways resizes in both directions`() {
        render()
        window.value = BoxloreTestWindows.COVER_475
        composeRule.waitForIdle()
        composeRule.runOnIdle { runBlocking { state.scrollToItem(3) } }
        composeRule.waitForIdle()
        val steps = listOf(
            BoxloreTestWindows.TABLET,
            BoxloreTestWindows.COVER_475,
            BoxloreTestWindows.W599_H900,
            BoxloreTestWindows.W600_H900,
            BoxloreTestWindows.W599_H900,
            BoxloreTestWindows.PHONE,
            BoxloreTestWindows.PHONE_LAND,
            BoxloreTestWindows.PHONE,
            BoxloreTestWindows.COVER_475,
        )
        for (layout in steps) {
            window.value = layout
            composeRule.waitForIdle()
            assertEquals("Item at ${layout.widthDp}x${layout.heightDp}", 3, state.currentItem)
            composeRule.onNodeWithTag("card_3").assertIsDisplayed()
        }
    }

    @Test fun `card width fits two cards only where one would leave almost another`() {
        fun width(available: Float) = heroTabletItemWidth(available.dp, CARD_WIDTH.dp, SPACING.dp).value
        assertEquals(CARD_WIDTH, width(500f), 0.01f) // one card and a short peek
        assertEquals(CARD_WIDTH, width(600f), 0.01f) // the peek is still well short of a card
        assertEquals(615f / 2 - SPACING, width(615f), 0.01f) // almost a second card: two fill the row
        assertEquals(671f / 2 - SPACING, width(671f), 0.01f)
        assertEquals(CARD_WIDTH, width(679f), 0.01f) // two whole cards already fit
        assertEquals(CARD_WIDTH, width(1015f), 0.01f) // three or more keep the preferred width
        assertEquals(CARD_WIDTH, width(1315f), 0.01f)
        // Panes narrower than a card and its gap: one card fills the pane.
        listOf(280f, 297f, 307f, 335f, 360f, 375f).forEach { available -> assertEquals("$available", available, width(available), 0.01f) }
        // material3 draws each card one gap wider than asked: the next card's drawn edge shows nothing or
        // at least 40dp, never a sliver (a card and 24dp at 376dp; two cards and 13dp on a book's 717dp row).
        fun nextShows(available: Float): Float {
            val stride = width(available) + SPACING * 2
            val whole = floor((available + SPACING) / stride).coerceAtLeast(1f)
            return available - stride * whole
        }
        (376..1400).map { it.toFloat() }.forEach { available ->
            val next = nextShows(available)
            assertTrue("Next card at ${available}dp shows ${next}dp", next <= 0.5f || next >= 40f - 0.5f)
        }
        assertEquals(CARD_WIDTH, width(400f), 0.01f) // a card and a 48dp peek of the next
        assertEquals(CARD_WIDTH, width(1015f), 0.01f)
    }

    /**
     * A half-open book foldable or a split view beside the player gives the hero a pane narrower than a
     * card and a gap. The card used to stay 320dp and run past the pane's edge, or leave a hairline
     * sliver of the next card. Now the first card fits whole and the next one shows 0 or at least 40dp.
     */
    @Test fun `narrow tablet panes and book windows fit whole cards and never a sliver of the next`() {
        window.value = BoxloreTestWindows.TABLET
        render()
        // Narrow panes, and the two-card band of a half-open book's window (about 700-730dp of row).
        for (width in listOf(280f, 297f, 307f, 340f, 352f, 360f, 375f, 385f, 400f, 700f, 710f, 718f, 730f, 734f)) {
            pane.value = width
            composeRule.waitForIdle()
            composeRule.runOnIdle { runBlocking { state.scrollToItem(0) } }
            composeRule.waitForIdle()
            val hero = composeRule.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
            val first = composeRule.onNodeWithTag("card_0").fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }
            assertTrue("Card at ${width}dp: $first in $hero", first.left >= hero.left - 0.5f && first.right <= hero.right + 0.5f)
            // The first card that isn't whole shows nothing or at least 40dp.
            val cut = (1 until CARDS).asSequence().mapNotNull { index -> composeRule.onAllNodesWithTag("card_$index").fetchSemanticsNodes().firstOrNull() }
                .firstOrNull { it.positionInRoot.x + it.size.width > hero.right + 0.5f }
            val nextVisible = cut?.let { (minOf(it.positionInRoot.x + it.size.width, hero.right) - maxOf(it.positionInRoot.x, hero.left)).coerceAtLeast(0f) } ?: 0f
            assertTrue("Next card at ${width}dp shows ${nextVisible}dp", nextVisible < 0.5f || nextVisible >= 40f - 0.5f)
            scrollToEndAndBack(width)
        }
    }

    @Test fun `the current card survives book-pane, flat and split-pane resizes in both directions`() {
        window.value = BoxloreTestWindows.PIXEL_FOLD
        pane.value = 340f
        render()
        composeRule.runOnIdle { runBlocking { state.scrollToItem(3) } }
        composeRule.waitForIdle()
        for (width in listOf(null, 385f, null, 340f)) {
            pane.value = width
            composeRule.waitForIdle()
            assertEquals("Item in a ${width ?: "full"} pane", 3, state.currentItem)
            composeRule.onNodeWithTag("card_3").assertIsDisplayed()
        }
    }

    @Test fun `right-to-left tablet carousel reaches the end`() {
        direction.value = LayoutDirection.Rtl
        render()
        for (width in listOf(640f, 1300f)) {
            window.value = BoxloreWindowLayout(width, 800f)
            scrollToEndAndBack(width)
        }
    }

    private fun render() {
        composeRule.setContent {
            val layout = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides layout, LocalLayoutDirection provides direction.value) {
                state = androidx.compose.material3.carousel.rememberCarouselState { CARDS }
                val rowWidth = (pane.value ?: layout.widthDp).dp
                Box(Modifier.width(rowWidth).height(400.dp)) {
                    ResponsiveHeroCarousel(
                        state = state,
                        preferredItemWidth = CARD_WIDTH.dp,
                        itemSpacing = SPACING.dp,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.testTag("hero").width(rowWidth).height(400.dp),
                    ) { i -> Box(Modifier.fillMaxSize().testTag("card_$i")) }
                }
            }
        }
    }

    private fun scrollToEndAndBack(width: Float) {
        composeRule.runOnIdle { runBlocking { state.scrollToItem(CARDS - 1) } }
        composeRule.waitForIdle()
        val rtl = direction.value == LayoutDirection.Rtl
        repeat(3) { composeRule.onNodeWithTag("hero").performTouchInput { if (rtl) swipeRight() else swipeLeft() } }
        composeRule.waitForIdle()
        val hero = composeRule.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        val last = composeRule.onNodeWithTag("card_${CARDS - 1}").assertIsDisplayed().fetchSemanticsNode()
        val left = last.positionInRoot.x
        val gap = if (rtl) left - hero.left else hero.right - (left + last.size.width)
        // The last card is never cut off. With one whole card it meets the end edge (the 1dp end padding
        // is not a visible gap); with two or more, material3 can stop it up to one card spacing short of
        // the edge at the end of the row (a known material3 bug, see ResponsiveHeroCarousel).
        val wholeCards = floor((width - 1f) / (CARD_WIDTH + SPACING)).toInt()
        val allowedGap = if (wholeCards >= 2) SPACING + 1.5f else 1.5f
        assertTrue("End gap at ${width}dp: $gap", gap in -0.5f..allowedGap)
        composeRule.onNodeWithTag("hero").performTouchInput { if (rtl) swipeLeft() else swipeRight() }
        composeRule.runOnIdle { runBlocking { state.scrollToItem(0) } }
        composeRule.waitForIdle()
    }

    private companion object {
        const val CARDS = 8
        const val CARD_WIDTH = 320f
        const val SPACING = 16f
    }
}
