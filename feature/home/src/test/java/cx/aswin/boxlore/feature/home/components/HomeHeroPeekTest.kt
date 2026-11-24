package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
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

    @Test fun `every style's peeking card shows only its artwork until it slides into place`() {
        val style = mutableStateOf(HeroCardStyle.BLOCK)
        val collection = mutableStateOf(false)
        var played = 0
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                val first = Podcast("show", "Radiolab", "Creator", "", latestEpisode = Episode("episode", "Focal episode", "", "", duration = 3600))
                val second = first.copy(id = "show_2", latestEpisode = first.latestEpisode!!.copy(id = "episode_2", title = "Peeking episode"))
                val grid = (0 until 4).map { index -> first.copy(id = "grid_$index", latestEpisode = first.latestEpisode!!.copy(id = "grid_episode_$index", title = "Grid episode $index")) }
                val next = if (collection.value) SmartHeroItem(HeroType.RESUME_GRID, grid.first(), "", gridItems = grid) else SmartHeroItem(HeroType.RESUME, second, "")
                CompositionLocalProvider(LocalHeroCardStyle provides style.value) {
                    key(style.value, collection.value) {
                        HeroCarousel(
                            heroItems = StableHeroList(listOf(SmartHeroItem(HeroType.RESUME, first, ""), next)),
                            onPlayClick = { _, _ -> played++ },
                            onDetailsClick = {},
                            onArrowClick = { _, _ -> },
                            onToggleSubscription = {},
                            onTogglePlayback = {},
                            modifier = Modifier.width(380.dp).testTag("carousel"),
                        )
                    }
                }
            }
        }
        val styles = listOf(HeroCardStyle.BLOCK, HeroCardStyle.COVER, HeroCardStyle.DUOTONE, HeroCardStyle.HALO, HeroCardStyle.TIDE)
        for ((name, grid) in styles.flatMap { listOf(it to false, it to true) }) {
            style.value = name
            collection.value = grid
            composeRule.waitForIdle()
            val words = if (grid) "Grid episode 0" else "Peeking episode"
            assertEquals("$name focal card keeps its words", 1, composeRule.onAllNodesWithText("Focal episode", substring = true).fetchSemanticsNodes().size)
            assertEquals("$name peek hides its words ($grid)", 0, wordNodes(words))
            if (!grid) {
                val plays = composeRule.onAllNodesWithTag("home_hero_play").fetchSemanticsNodes()
                assertEquals("$name peek has no Play target: ${plays.map { it.boundsInRoot }}", 1, plays.size)
            }
            composeRule.onNodeWithTag("carousel").performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            assertTrue("$name card shows its words once in place ($grid)", wordNodes(words) > 0)
        }
        assertEquals(0, played)
    }

    /**
     * Tablets, the Pixel Fold's inner screen and sideways phones use material3's uncontained row, where
     * every card keeps its full size and the row's edge cuts the last one. That card used to show chopped
     * words and half a Play button; it now shows only its artwork until it slides fully into view.
     */
    @Test
    @Config(qualifiers = "w1300dp-h1500dp-mdpi")
    fun `a card cut by the tablet row's edge shows no words or Play until it slides into view`() {
        val window = mutableStateOf(BoxloreTestWindows.TABLET)
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalHeroCardStyle provides HeroCardStyle.COVER) {
                BoxLoreTheme(dynamicColor = false) {
                    key(window.value) {
                        val items = List(6) { index ->
                            val podcast = Podcast("show_$index", "Show $index", "Creator", "", latestEpisode = Episode("episode_$index", "Row episode $index", "", "", duration = 3600))
                            SmartHeroItem(HeroType.RESUME, podcast, "")
                        }
                        HeroCarousel(
                            heroItems = StableHeroList(items),
                            onPlayClick = { _, _ -> },
                            onDetailsClick = {},
                            onArrowClick = { _, _ -> },
                            onToggleSubscription = {},
                            onTogglePlayback = {},
                            modifier = Modifier.width(window.value.widthDp.dp).testTag("carousel"),
                        )
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        for (layout in listOf(BoxloreTestWindows.TABLET, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.PIXEL_FOLD, BoxloreTestWindows.PHONE_LAND)) {
            window.value = layout
            composeRule.waitForIdle()
            val label = "${layout.widthDp.toInt()}x${layout.heightDp.toInt()}"
            val atRest = rowCards()
            failures += rowFailures("$label at rest", atRest)
            if (atRest.none { it.cut }) failures += "$label: no card is cut at rest, so the case isn't covered"
            // Bring the cut card to the row's start, where it shows whole.
            val cutIndex = atRest.first { it.cut }.title.substringAfterLast(' ').toInt()
            composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(cutIndex)
            composeRule.waitForIdle()
            val scrolled = rowCards()
            failures += rowFailures("$label scrolled", scrolled)
            val revealed = atRest.filter { it.cut }.map { it.title }.toSet().intersect(scrolled.filter { it.words }.map { it.title }.toSet())
            if (revealed.isEmpty()) failures += "$label: no cut card showed its words once it slid into view"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private data class RowCard(val title: String, val cut: Boolean, val words: Boolean, val bounds: Rect)

    /** Each Cover card in the row: its name (words or, while hidden, its label), whether the row's edge cuts it, and whether its words show. */
    private fun rowCards(): List<RowCard> {
        val row = composeRule.onNodeWithTag("carousel").fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }
        return composeRule.onAllNodesWithTag("home_hero_cover").fetchSemanticsNodes().mapNotNull { node ->
            val bounds = Rect(node.positionInRoot, node.size.toSize())
            if (bounds.right <= row.left || bounds.left >= row.right) return@mapNotNull null
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
            val title = Regex("Row episode \\d").find("$text $description")?.value ?: return@mapNotNull null
            RowCard(title, cut = bounds.left < row.left - 0.5f || bounds.right > row.right + 0.5f, words = "Row episode" in text, bounds = bounds)
        }
    }

    /** A cut card has no words and no Play inside it; every tappable node has a name for TalkBack. */
    private fun rowFailures(label: String, cards: List<RowCard>): List<String> {
        val failures = mutableListOf<String>()
        val plays = composeRule.onAllNodesWithTag("home_hero_play").fetchSemanticsNodes().map { Rect(it.positionInRoot, it.size.toSize()) }
        cards.filter { it.cut }.forEach { card ->
            if (card.words) failures += "$label: ${card.title} shows its words while cut at ${card.bounds}"
            if (plays.any { it.center.x in card.bounds.left..card.bounds.right }) failures += "$label: ${card.title} keeps a Play button while cut"
        }
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes().forEach { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
            if (text.isBlank() && description.isBlank()) failures += "$label: a tappable node at ${node.boundsInRoot} has no name"
        }
        return failures
    }

    @Test fun `words fade in across the swipe and stay put outside the carousel`() {
        val reveal = HeroPeekReveal(info, minimumPeekWidth = 40f)
        size.floatValue = 40f
        assertEquals(0f, reveal.alpha, 0f)
        // Mid-fade: the words come in over the first half of the slide (HeroPeekMotion).
        size.floatValue = 80f
        assertTrue("Mid-fade ${reveal.alpha}", reveal.alpha > 0f && reveal.alpha < 1f)
        size.floatValue = 320f
        assertEquals(1f, reveal.alpha, 0f)
        size.floatValue = 80f
        assertTrue(reveal.alpha > 0f && reveal.alpha < 1f)
        size.floatValue = 40f
        assertEquals(0f, reveal.alpha, 0f)
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

    /**
     * Nodes that show or read out [words]: Tide's square collection names its covers instead of printing
     * titles. A peeking card's own name for its tap (it opens the episode) isn't words on the card.
     */
    private fun wordNodes(words: String): Int =
        composeRule.onAllNodesWithText(words, substring = true).fetchSemanticsNodes().size +
            composeRule.onAllNodes(hasContentDescription(words, substring = true) and !hasText(words, substring = true) and !isHeroCard).fetchSemanticsNodes().size

    /** The hero cards themselves, whose tap keeps a name while they peek. */
    private val isHeroCard = HERO_CARD_TAGS.map { hasTestTag(it) }.reduce { a, b -> a or b }

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

    private companion object {
        val HERO_CARD_TAGS = listOf("home_hero_block", "home_hero_cover", "home_hero_duotone", "home_hero_halo", "home_hero_tide")
    }
}
