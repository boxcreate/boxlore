package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The skeleton must occupy the same card and peek as the real hero carousel, so the reveal never jumps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1400dp-mdpi")
class HomeHeroSkeletonTest {
    @get:Rule val composeRule = createComposeRule()

    private val width = mutableStateOf(328)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private var carouselCard = 0f
    private var carouselPeek = 0f

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `skeleton card and peek match the hero carousel across phone widths and RTL`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Column {
                        HorizontalMultiBrowseCarousel(
                            state = rememberCarouselState { 4 },
                            preferredItemWidth = 320.dp,
                            itemSpacing = 16.dp,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.width(width.value.dp).height(120.dp).testTag("carousel"),
                        ) { index ->
                            // Read the carousel's own mask sizes after layout, never during composition.
                            Box(
                                Modifier.fillMaxSize().onGloballyPositioned {
                                    if (index == 0) carouselCard = carouselItemDrawInfo.size
                                    if (index == 1) carouselPeek = carouselItemDrawInfo.size
                                },
                            )
                        }
                        HeroSkeleton(Modifier.width(width.value.dp))
                    }
                }
            }
        }
        for (screen in listOf(288, 328, 348, 360, 380, 412)) {
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
}
