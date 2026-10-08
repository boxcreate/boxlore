package cx.aswin.boxlore.feature.home.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.discoveryGreetingFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w500dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDiscoveryHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    private data class Scenario(
        val chapter: HomeDiscoveryChapter = HomeDiscoveryChapter.PERSONAL,
        val width: Dp = 288.dp,
        val fontScale: Float = 1f,
        val direction: LayoutDirection = LayoutDirection.Ltr,
        val dark: Boolean = false,
        val dynamic: Boolean = false,
        val surface: String = SurfaceStyles.CLASSIC_DYNAMIC,
        val daypart: ContentDaypart = ContentDaypart.AFTERNOON,
    )

    private val scenario = mutableStateOf(Scenario())
    private var density = 1f
    private var clicks = 0
    private lateinit var view: View
    private val headerTop = mutableStateOf(0.dp)
    private var headerHeight = 0

    @Test fun `chapter headings wrap without clipping or competing with the shaped action at phone widths enlarged text and RTL`() {
        render()
        val variants = HomeDiscoveryChapter.entries.flatMap { chapter ->
            if (chapter == HomeDiscoveryChapter.MOMENT) {
                ContentDaypart.entries.map { Scenario(chapter = chapter, daypart = it) }
            } else {
                listOf(Scenario(chapter = chapter))
            }
        }
            .flatMap { value -> listOf(288.dp, 328.dp, 380.dp).map { value.copy(width = it) } }
            .flatMap { value -> listOf(1f, 2f).map { value.copy(fontScale = it) } }
            .flatMap { value -> LayoutDirection.entries.map { value.copy(direction = it) } }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            val header = composeRule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
            val title = composeRule.onNodeWithText(title()).fetchSemanticsNode().boundsInRoot
            val action = composeRule.onNodeWithContentDescription(description()).fetchSemanticsNode().boundsInRoot
            assertFalse(title.overlaps(action))
            assertTrue(title.left >= header.left && title.right <= header.right)
            assertTrue(title.top >= header.top && title.bottom <= header.bottom)
            assertEquals(48f * density, action.width, 1f)
            assertEquals(48f * density, action.height, 1f)
            if (value.direction == LayoutDirection.Ltr) assertTrue(title.right < action.left) else assertTrue(action.right < title.left)
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(title()).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("$value: heading must fully wrap within its allocated width", layouts.single().hasVisualOverflow)
            composeRule.onNodeWithText(title()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
    }

    @Test fun `native browse feedback keeps header geometry and dispatches once across chapters and appearance themes`() {
        render()
        val themes = listOf(Scenario(), Scenario(dark = true), Scenario(dynamic = true), Scenario(dark = true, surface = SurfaceStyles.AMOLED))
        for (theme in themes) {
            for (chapter in HomeDiscoveryChapter.entries) {
                scenario.value = theme.copy(chapter = chapter)
                composeRule.waitForIdle()
                val header = composeRule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
                val action = composeRule.onNodeWithContentDescription(description())
                val before = clicks
                action.performTouchInput { down(center) }
                composeRule.mainClock.advanceTimeBy(100)
                assertEquals(header, composeRule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot)
                assertEquals(before, clicks)
                action.performTouchInput { up() }
                composeRule.waitForIdle()
                assertEquals(before + 1, clicks)
            }
        }
    }

    @Test fun `daypart marks reveal then keep subtle motion without moving the heading or rotating the night crescent`() {
        composeRule.mainClock.autoAdvance = false
        scenario.value = Scenario(chapter = HomeDiscoveryChapter.MOMENT, daypart = ContentDaypart.MORNING)
        render()
        for (daypart in ContentDaypart.entries) {
            scenario.value = scenario.value.copy(daypart = daypart, width = 288.dp)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            val before = markPixels()
            val titleBounds = composeRule.onNodeWithText(title()).fetchSemanticsNode().boundsInRoot
            val actionBounds = composeRule.onNodeWithContentDescription(description()).fetchSemanticsNode().boundsInRoot
            val markBounds = composeRule.onNodeWithTag("home_daypart_mark").fetchSemanticsNode().boundsInRoot
            assertEquals(32f * density, markBounds.width, 1f)
            assertFalse(markBounds.overlaps(titleBounds))

            composeRule.mainClock.advanceTimeBy(1_200)
            composeRule.waitForIdle()
            val settled = markPixels()
            assertFalse("$daypart must visibly animate", before == settled)
            assertEquals(titleBounds, composeRule.onNodeWithText(title()).fetchSemanticsNode().boundsInRoot)
            assertEquals(actionBounds, composeRule.onNodeWithContentDescription(description()).fetchSemanticsNode().boundsInRoot)

            composeRule.mainClock.advanceTimeBy(900)
            composeRule.waitForIdle()
            val moving = markPixels()
            assertFalse("$daypart must retain ambient motion", settled == moving)
            val ambientChanges = settled.indices.count { settled[it] != moving[it] }
            assertTrue("$daypart motion must be noticeable within a second", ambientChanges >= if (daypart == ContentDaypart.LATE_NIGHT) 4 else 12)
            if (daypart == ContentDaypart.LATE_NIGHT) {
                val width = markBounds.width.toInt()
                val moonPixels: (Int, Int) -> Boolean = { index, _ ->
                    // Exclude both stars' full size ranges, including antialiased edges.
                    index % width < width * 0.68f || index / width > width * 0.63f
                }
                assertEquals("Only the stars change at night", settled.filterIndexed(moonPixels), moving.filterIndexed(moonPixels))
                val upperStarChanges = settled.indices.count { it % width >= width * 0.68f && it / width < width * 0.35f && settled[it] != moving[it] }
                val lowerStarChanges = settled.indices.count { it % width >= width * 0.75f && it / width > width * 0.4f && settled[it] != moving[it] }
                assertTrue("Both stars twinkle", upperStarChanges > 0 && lowerStarChanges > 0)
            }
            assertEquals(titleBounds, composeRule.onNodeWithText(title()).fetchSemanticsNode().boundsInRoot)
            assertEquals(actionBounds, composeRule.onNodeWithContentDescription(description()).fetchSemanticsNode().boundsInRoot)
            scenario.value = scenario.value.copy(width = 328.dp)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            val recomposed = markPixels()
            val changedPixels = moving.indices.count { moving[it] != recomposed[it] }
            assertTrue("$daypart may advance a frame but must not replay its reveal", changedPixels < moving.size / 12)
        }
    }

    @Test fun `reveal waits for the entire header inside a clipped viewport and ambient motion pauses when clipped`() {
        composeRule.mainClock.autoAdvance = false
        scenario.value = Scenario(chapter = HomeDiscoveryChapter.MOMENT, daypart = ContentDaypart.MORNING, fontScale = 2f)
        headerTop.value = (-8).dp
        render(viewportHeight = 192.dp)
        advanceFrames()
        val waiting = markPixels()
        composeRule.mainClock.advanceTimeBy(1_200)
        assertEquals("A visible icon with a clipped heading must not reveal", waiting, markPixels())

        headerTop.value = 192.dp - (headerHeight / density).dp + 8.dp
        advanceFrames()
        val bottomClipped = markPixels()
        composeRule.mainClock.advanceTimeBy(1_200)
        assertEquals("The last part of the heading must enter before revealing", bottomClipped, markPixels())

        headerTop.value = 0.dp
        advanceFrames()
        val entering = markPixels()
        composeRule.mainClock.advanceTimeBy(1_200)
        assertFalse("Fully visible headers reveal", entering == markPixels())
        composeRule.mainClock.advanceTimeBy(3_000)

        headerTop.value = (-8).dp
        advanceFrames()
        val paused = markPixels()
        composeRule.mainClock.advanceTimeBy(3_000)
        assertEquals("Clipped headers stop their ambient clock", paused, markPixels())
        headerTop.value = 0.dp
        advanceFrames()
        val reentered = markPixels()
        assertEquals("Completed reveals do not replay when scrolling back", paused, reentered)
        composeRule.mainClock.advanceTimeBy(3_000)
        assertFalse("Ambient motion resumes when fully visible", reentered == markPixels())
    }

    private fun advanceFrames() {
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun markPixels(): List<Int> {
        val bounds = composeRule.onNodeWithTag("home_daypart_mark").fetchSemanticsNode().boundsInRoot
        var pixels = emptyList<Int>()
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val width = bounds.width.toInt()
            pixels = List(width * bounds.height.toInt()) { index ->
                bitmap.getPixel(bounds.left.toInt() + index % width, bounds.top.toInt() + index / width)
            }
            bitmap.recycle()
        }
        return pixels
    }

    @Test fun `Explore keeps genre selection without showing secondary header text`() {
        val selected = mutableStateOf<String?>(null)
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                DiscoverSection(selected.value, { selected.value = it }, {}, Modifier.width(288.dp))
            }
        }
        composeRule.onNodeWithText(label(R.string.home_explore_shows)).assertExists()
        composeRule.onNodeWithText(label(R.string.home_genres_top)).assertIsSelected()
        composeRule.onNodeWithText("Browse by genre").assertDoesNotExist()
        selected.value = "News"
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label(R.string.home_explore_shows)).assertExists()
        composeRule.onNodeWithText("News").assertIsSelected()
        composeRule.onNodeWithText("Top in News").assertDoesNotExist()
        composeRule.onNodeWithText("Browse by genre").assertDoesNotExist()
    }

    @Test fun `subheader icons sit directly before wrapping titles in LTR and RTL`() {
        val tone = mutableStateOf(HomeChildHeaderTone.PRIMARY)
        val title = "Shows for curious minds"
        composeRule.setContent {
            val value = scenario.value
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, value.fontScale),
                LocalLayoutDirection provides value.direction,
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    HomeChildSectionHeader(title, Icons.Rounded.Subscriptions, Modifier.width(value.width).testTag("rail_header"), tone = tone.value)
                }
            }
        }
        val variants = listOf(288.dp, 380.dp).map { Scenario(width = it) }
            .flatMap { value -> listOf(1f, 2f).map { value.copy(fontScale = it) } }
            .flatMap { value -> LayoutDirection.entries.map { value.copy(direction = it) } }
        for (value in variants) {
            for (colorTone in HomeChildHeaderTone.entries) {
                scenario.value = value
                tone.value = colorTone
                composeRule.waitForIdle()
                val header = composeRule.onNodeWithTag("rail_header").fetchSemanticsNode().boundsInRoot
                val text = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
                if (value.direction == LayoutDirection.Ltr) {
                    assertEquals(header.left + 38f * density, text.left, 1f)
                } else {
                    assertEquals(header.right - 38f * density, text.right, 1f)
                }
                assertTrue(text.top >= header.top && text.bottom <= header.bottom)
                assertTrue(header.height >= 36f * density)
                val layouts = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse(layouts.single().hasVisualOverflow)
            }
        }
    }

    private fun render(viewportHeight: Dp? = null) {
        composeRule.setContent {
            val value = scenario.value
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, value.fontScale),
                LocalLayoutDirection provides value.direction,
            ) {
                BoxLoreTheme(darkTheme = value.dark, dynamicColor = value.dynamic, surfaceStyle = value.surface) {
                    val viewportModifier = if (viewportHeight == null) Modifier else Modifier.height(viewportHeight).clipToBounds()
                    Box(Modifier.width(value.width).then(viewportModifier)) {
                        HomeTopLevelSectionHeader(
                            title = title(),
                            seeAllContentDescription = description(),
                            onSeeAllClick = { clicks++ },
                            chapter = value.chapter,
                            daypart = if (value.chapter == HomeDiscoveryChapter.MOMENT) value.daypart else null,
                            modifier = Modifier.offset(y = headerTop.value).width(value.width)
                                .onSizeChanged { headerHeight = it.height }.testTag("header"),
                        )
                    }
                }
            }
        }
    }

    private fun title(): String = when (scenario.value.chapter) {
        HomeDiscoveryChapter.LIBRARY -> label(R.string.home_your_shows_heading)
        HomeDiscoveryChapter.PERSONAL -> label(R.string.home_popular_region)
        HomeDiscoveryChapter.MOMENT -> discoveryGreetingFor(scenario.value.daypart).title
        HomeDiscoveryChapter.EXPLORE -> label(R.string.home_explore_shows)
    }

    private fun description(): String = when (scenario.value.chapter) {
        HomeDiscoveryChapter.LIBRARY -> "View Library"
        HomeDiscoveryChapter.PERSONAL -> label(R.string.home_recommendations_see_all)
        HomeDiscoveryChapter.MOMENT -> label(R.string.home_discovery_see_all)
        HomeDiscoveryChapter.EXPLORE -> label(R.string.home_shows_see_all)
    }

    private fun label(id: Int, vararg args: Any): String = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)
}
