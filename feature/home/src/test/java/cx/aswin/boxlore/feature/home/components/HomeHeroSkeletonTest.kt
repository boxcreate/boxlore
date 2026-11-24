package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The skeleton must occupy the same card and peek as the real hero carousel, so the reveal never jumps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1400dp-h1400dp-mdpi")
class HomeHeroSkeletonTest {
    @get:Rule val composeRule = createComposeRule()

    private val width = mutableStateOf(328)
    private val windowHeight = mutableStateOf(900f)
    private val direction = mutableStateOf(LayoutDirection.Ltr)

    /** A tablet window whose pane is narrower than the window, or null for a window 32dp wider than the row. */
    private val paneWindow = mutableStateOf<BoxloreWindowLayout?>(null)
    private var carouselCard = 0f
    private var carouselPeek = 0f
    private var carouselCardLeft = 0f

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `skeleton card and peek match the hero carousel across phone and Fold 8 cover widths and RTL`() {
        renderBoth()
        // Rows 288-398dp are phones; 399-567dp are the Fold 8 cover and other compact windows up to 599dp.
        for (screen in listOf(288, 328, 348, 360, 380, 412, 443, 453, 468, 508, 567)) {
            for (dir in LayoutDirection.entries) {
                width.value = screen
                direction.value = dir
                composeRule.waitForIdle()
                // mdpi: one pixel per dp.
                val geometry = heroSkeletonGeometry(screen, density = 1f)
                assertEquals("card at $screen $dir", carouselCard, geometry.cardWidth.toFloat(), 1f)
                assertEquals("peek at $screen $dir", carouselPeek, geometry.peekWidth.toFloat(), 1f)
                val skeleton = composeRule.onNodeWithTag("home_hero_skeleton").fetchSemanticsNode().boundsInRoot
                assertEquals(screen.toFloat(), skeleton.width, 1f)
            }
        }
    }

    @Test
    fun `on sideways phones, the Fold 8 cover held sideways and tablets the skeleton cards match the loaded row`() {
        renderBoth()
        // Windows: a 640x360 phone, the Fold 8 cover sideways, a 915x412 phone and a tablet, then back.
        val windows = listOf(640 to 360f, 695 to 475f, 915 to 412f, 1280 to 800f, 695 to 475f, 412 to 915f, 640 to 360f)
        for ((window, height, dir) in windows.flatMap { (w, h) -> LayoutDirection.entries.map { Triple(w, h, it) } }) {
            width.value = window - 32
            windowHeight.value = height
            direction.value = dir
            composeRule.waitForIdle()
            val cards = composeRule.onAllNodesWithTag(skeletonTag(HeroCardStyle.COVER), useUnmergedTree = true).fetchSemanticsNodes()
            val first = cards.minBy { if (dir == LayoutDirection.Ltr) it.boundsInRoot.left else -it.boundsInRoot.right }.boundsInRoot
            val expected = if (window <= 430) heroSkeletonGeometry(window - 32, density = 1f).cardWidth.toFloat() else carouselCard
            assertEquals("card width at ${window}x$height $dir", expected, first.width, 1f)
            if (window > 430 && dir == LayoutDirection.Ltr) assertEquals("card start at ${window}x$height", carouselCardLeft, first.left, 1f)
        }
    }

    @Test
    fun `in narrow tablet panes the skeleton card fills the pane as the loaded card does`() {
        renderBoth()
        // A half-open book pane, a split pane beside the player, then a full tablet row and back.
        val panes = listOf(BoxloreTestWindows.UPRIGHT_BOOK to 343, BoxloreTestWindows.TABLET to 385, BoxloreTestWindows.TABLET to 1216, BoxloreTestWindows.UPRIGHT_BOOK to 280, BoxloreTestWindows.UPRIGHT_BOOK to 343)
        for ((window, row) in panes) {
            paneWindow.value = window
            width.value = row
            composeRule.waitForIdle()
            val cards = composeRule.onAllNodesWithTag(skeletonTag(HeroCardStyle.COVER), useUnmergedTree = true).fetchSemanticsNodes()
            val first = cards.minBy { it.boundsInRoot.left }.boundsInRoot
            assertEquals("card width in a ${row}dp pane", carouselCard, first.width, 1f)
            assertTrue("card in a ${row}dp pane ends past the pane", first.right <= row + 0.5f)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun renderBoth() {
        composeRule.setContent {
            // Home's grid keeps 16dp each side of the hero row, so the window is 32dp wider than the row.
            val window = paneWindow.value ?: BoxloreWindowLayout(width.value + 32f, windowHeight.value)
            CompositionLocalProvider(LocalLayoutDirection provides direction.value, LocalBoxloreWindowLayout provides window) {
                BoxLoreTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalHeroCardStyle provides HeroCardStyle.COVER) {
                        Column {
                            ResponsiveHeroCarousel(
                                state = rememberCarouselState { 8 },
                                preferredItemWidth = 320.dp,
                                itemSpacing = 16.dp,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.width(width.value.dp).height(120.dp).testTag("carousel"),
                            ) { index ->
                                // Read the carousel's own mask sizes after layout, never during composition.
                                Box(
                                    Modifier.fillMaxSize().onGloballyPositioned {
                                        if (index == 0) {
                                            carouselCard = carouselItemDrawInfo.size
                                            carouselCardLeft = it.boundsInRoot().left + carouselItemDrawInfo.maskRect.left
                                        }
                                        if (index == 1) carouselPeek = carouselItemDrawInfo.size
                                    },
                                )
                            }
                            HeroSkeleton(Modifier.width(width.value.dp))
                        }
                    }
                }
            }
        }
    }
}
