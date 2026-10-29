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
import kotlinx.coroutines.runBlocking
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
 * 1288–1344dp for 320dp cards and 16dp gaps).
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
                        preferredItemWidth = 320.dp,
                        itemSpacing = 16.dp,
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
        // The last card still meets the end edge: the tablet's 1dp end padding is not a visible gap.
        assertTrue("End gap at ${width}dp: $gap", gap in -0.5f..1.5f)
        composeRule.onNodeWithTag("hero").performTouchInput { if (rtl) swipeLeft() else swipeRight() }
        composeRule.runOnIdle { runBlocking { state.scrollToItem(0) } }
        composeRule.waitForIdle()
    }

    private companion object {
        const val CARDS = 8
    }
}
