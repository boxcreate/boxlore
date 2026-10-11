package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableHeroList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeHeroPeekTest {
    @get:Rule val composeRule = createComposeRule()

    private val size = mutableFloatStateOf(40f)
    private val maskWidth = mutableFloatStateOf(-1f)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val textScale = mutableFloatStateOf(1f)
    private val mode = mutableStateOf(HeroGridMode.Resume)
    private var details = 0
    private var play = 0
    private val info = mock(CarouselItemDrawInfo::class.java).apply {
        `when`(size).thenAnswer { this@HomeHeroPeekTest.size.floatValue }
        `when`(minSize).thenReturn(40f)
        `when`(maxSize).thenReturn(320f)
        `when`(maskRect).thenAnswer {
            val width = maskWidth.floatValue.takeIf { it >= 0f } ?: this@HomeHeroPeekTest.size.floatValue
            Rect((320f - width) / 2f, 0f, (320f + width) / 2f, 416f)
        }
    }

    @Test fun `peek has one vertical heading and no exposed episode semantics or actions`() {
        render()
        for (value in HeroGridMode.entries) {
            mode.value = value
            val heading = peekBounds()
            assertEquals(160f, heading.center.x, 1f)
            assertEquals(208f, heading.center.y, 1f)
            assertTrue("Vertical heading $heading", heading.height > heading.width)
            assertTrue("Heading fits the narrow mask $heading", heading.width <= 40f)
            val count = textBounds("home_hero_collection_peek_count")
            assertEquals(160f, count.center.x, 1f)
            assertTrue("Count stays below the rounded top and above the label: $count / $heading", count.top >= 27f && count.bottom < heading.top)
            assertEquals(1, composeRule.onAllNodesWithContentDescription("4 episodes").fetchSemanticsNodes().size)
            assertEquals(0, composeRule.onAllNodesWithText("Episode 0").fetchSemanticsNodes().size)
            composeRule.onNodeWithTag("hero").performTouchInput { click() }
        }
        assertEquals(0, details)
        assertEquals(0, play)
    }

    @Test fun `swipe fades stationary headings and reverses without moving or duplicating them`() {
        render()
        val peek = peekBounds()
        size.floatValue = 70f
        assertEquals(peek, peekBounds())
        assertEquals(0, composeRule.onAllNodesWithText("Episode 0").fetchSemanticsNodes().size)
        size.floatValue = 180f
        val halfway = headingBounds()
        assertEquals(1, composeRule.onAllNodesWithText("Jump back in").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithContentDescription("4 episodes").fetchSemanticsNodes().size)
        size.floatValue = 320f
        val expanded = headingBounds()
        assertEquals(halfway, expanded)
        assertTrue(expanded.width > expanded.height)
        assertTrue(expanded.top <= 19f && expanded.bottom < 70f)
        composeRule.onNodeWithTag("home_hero_collection_0_title", useUnmergedTree = true).performTouchInput { click() }
        composeRule.onNodeWithTag("home_hero_collection_0_artwork", useUnmergedTree = true).performTouchInput { click() }
        assertEquals(1, details)
        assertEquals(1, play)
        size.floatValue = 180f
        assertEquals(halfway, headingBounds())
        size.floatValue = 40f
        assertEquals(peek, peekBounds())
        assertEquals(1, composeRule.onAllNodesWithText("Jump back in").fetchSemanticsNodes().size)
    }

    @Test fun `startup masks narrower than heading inset do not crash and recover on expansion`() {
        maskWidth.floatValue = 0f
        render()
        for (width in listOf(0f, 1f, 8f, 11f, 12f, 40f)) {
            maskWidth.floatValue = width
            val heading = peekBounds()
            assertTrue("Finite heading bounds with $width px mask: $heading", listOf(heading.left, heading.top, heading.right, heading.bottom).all { it.isFinite() })
            assertEquals(160f, heading.center.x, 1f)
        }
        maskWidth.floatValue = -1f
        size.floatValue = 320f
        assertTrue(headingBounds().width > headingBounds().height)
        composeRule.onNodeWithTag("home_hero_collection_0_artwork", useUnmergedTree = true).performTouchInput { click() }
        assertEquals(1, play)
    }

    @Test fun `vertical and expanded headings fit enlarged text in both layout directions`() {
        render()
        val cases = LayoutDirection.entries.flatMap { layout ->
            listOf(1f, 2f).flatMap { scale -> HeroGridMode.entries.map { Triple(layout, scale, it) } }
        }
        for ((layout, scale, value) in cases) {
            direction.value = layout
            textScale.floatValue = scale
            mode.value = value
            size.floatValue = 40f
            val peek = peekBounds()
            assertEquals(160f, peek.center.x, 1f)
            assertTrue("Peek width $peek at $layout $scale", peek.width <= 40f)
            val count = textBounds("home_hero_collection_peek_count")
            assertTrue("Count fits enlarged text and stays clear of label: $count / $peek", count.width <= 40f && count.bottom < peek.top)
            for (width in listOf(80f, 140f, 220f)) {
                size.floatValue = width
                assertEquals("Peek stays stationary at $width px", peek, peekBounds())
            }
            size.floatValue = 320f
            val heading = headingBounds()
            assertTrue("Header $heading at $layout $scale", heading.left >= 17f && heading.right <= 303f && heading.top >= 17f)
        }
    }

    @Test fun `real Material carousel presents only the vertical grid heading in the trailing peek`() {
        renderCarousel()
        val peek = peekBounds()
        assertTrue("Trailing peek is vertical: $peek", peek.height > peek.width)
        assertTrue("Heading remains in the carousel: $peek", peek.left >= 0f && peek.right <= 380f)
        assertEquals(0, composeRule.onAllNodesWithText("Grid episode 0").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("carousel").performTouchInput { swipeLeft() }
        val full = headingBounds()
        assertTrue("Expanded heading is horizontal: $full", full.width > full.height)
        assertEquals(1, composeRule.onAllNodesWithText("Grid episode 0").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("carousel").performTouchInput { swipeRight() }
        assertTrue(peekBounds().height > peekBounds().width)
    }

    @Test
    @Config(qualifiers = "w400dp-h850dp-520dpi")
    fun `phone density peek retains readable typography instead of scaling to the offscreen anchor`() {
        renderCarousel(width = 337)
        val density = 520f / 160f
        val peek = peekBounds()
        assertTrue("Readable vertical label: $peek", peek.height >= 100f * density && peek.width >= 18f * density)
        val count = textBounds("home_hero_collection_peek_count")
        assertTrue("Count fits phone peek and stays clear of label: $count / $peek", count.width <= 40f * density && count.bottom < peek.top)
        assertEquals(0, composeRule.onAllNodesWithText("Grid episode 0").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("carousel").performTouchInput { swipeLeft() }
        assertTrue(headingBounds().width > headingBounds().height)
        composeRule.onNodeWithTag("carousel").performTouchInput { swipeRight() }
        val returned = peekBounds()
        assertTrue("Readable after reversed swipe: $returned", returned.height >= 100f * density && returned.width >= 18f * density)
    }

    private fun renderCarousel(width: Int = 380) {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                val podcast = Podcast("show", "Radiolab", "Creator", "", latestEpisode = Episode("episode", "A solo episode", "", "", duration = 3600))
                val episodes = (0 until 4).map { index -> podcast.copy(id = "show_$index", latestEpisode = podcast.latestEpisode!!.copy(id = "episode_$index", title = "Grid episode $index")) }
                HeroCarousel(
                    heroItems = StableHeroList(listOf(SmartHeroItem(HeroType.RESUME, podcast, ""), SmartHeroItem(HeroType.RESUME_GRID, episodes.first(), "", gridItems = episodes))),
                    onPlayClick = { _, _ -> },
                    onDetailsClick = {},
                    onArrowClick = { _, _ -> },
                    onToggleSubscription = {},
                    onTogglePlayback = {},
                    modifier = Modifier.width(width.dp).testTag("carousel"),
                )
            }
        }
    }

    private fun headingBounds(): Rect = textBounds("home_hero_collection_heading")

    private fun peekBounds(): Rect = textBounds("home_hero_collection_peek")

    private fun textBounds(tag: String): Rect {
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates.boundsInRoot()
    }

    private fun render() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, textScale.floatValue),
                LocalLayoutDirection provides direction.value,
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(320.dp).height(homeHeroHeight()).testTag("hero")) {
                        HeroGridCard(
                            items = (0 until 4).map { index -> Podcast("show_$index", "Show $index", "Creator", "", latestEpisode = Episode("episode_$index", "Episode $index", "", "", duration = 3600), resumeProgress = 0.5f) },
                            title = if (mode.value == HeroGridMode.Resume) "Jump back in" else "New episodes",
                            mode = mode.value,
                            onCellClick = { details++ },
                            onPlaybackClick = { play++ },
                            carouselItemDrawInfo = info,
                        )
                    }
                }
            }
        }
    }
}
