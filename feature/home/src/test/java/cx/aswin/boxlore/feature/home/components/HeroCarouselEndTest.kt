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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
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

    @Test fun `card width fits two cards only where one would leave almost another`() {
        fun width(available: Float) = heroTabletItemWidth(available.dp, CARD_WIDTH.dp, SPACING.dp).value
        assertEquals(CARD_WIDTH, width(500f), 0.01f) // one card and a short peek
        assertEquals(CARD_WIDTH, width(600f), 0.01f) // the peek is still well short of a card
        assertEquals(615f / 2 - SPACING, width(615f), 0.01f) // almost a second card: two fill the row
        assertEquals(671f / 2 - SPACING, width(671f), 0.01f)
        assertEquals(CARD_WIDTH, width(679f), 0.01f) // two whole cards already fit
        assertEquals(CARD_WIDTH, width(1015f), 0.01f) // three or more keep the preferred width
        assertEquals(CARD_WIDTH, width(1315f), 0.01f)
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
                Box(Modifier.width(layout.widthDp.dp).height(400.dp)) {
                    ResponsiveHeroCarousel(
                        state = state,
                        preferredItemWidth = CARD_WIDTH.dp,
                        itemSpacing = SPACING.dp,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.testTag("hero").width(layout.widthDp.dp).height(400.dp),
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
        // the edge at the end of the row.
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
