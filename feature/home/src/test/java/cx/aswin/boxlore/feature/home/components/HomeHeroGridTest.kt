package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeHeroGridTest {
    @get:Rule val composeRule = createComposeRule()

    private data class Scenario(
        val type: HeroType = HeroType.NEW_EPISODES_GRID,
        val count: Int = 4,
        val width: Int = 288,
        val scale: Float = 1f,
        val direction: LayoutDirection = LayoutDirection.Ltr,
        val title: String = "Episode",
        val duration: Int = 10560,
    )

    private val scenario = mutableStateOf(Scenario())
    private val collectionPlayClicks = mutableListOf<String>()
    private val collectionClicks = mutableListOf<String>()
    private var collectionPauseClicks = 0

    @Test fun `new collection duration aligns with title without a new tag at large text and RTL`() {
        render()
        val variants = listOf(2, 3, 4).flatMap { count ->
            LayoutDirection.entries.flatMap { direction ->
                listOf(1f, 2f).map { scale -> Scenario(type = HeroType.NEW_EPISODES_GRID, count = count, width = 288, scale = scale, direction = direction, title = "Long titles", duration = 10560) }
            }
        }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            val row = bounds("home_hero_collection_0")
            val title = bounds("home_hero_collection_0_title")
            val time = bounds("home_hero_collection_0_time")
            assertTrue(title.top >= row.top && title.bottom <= time.top && time.bottom <= row.bottom)
            assertEquals(if (value.direction == LayoutDirection.Ltr) title.left else title.right, if (value.direction == LayoutDirection.Ltr) time.left else time.right, 1f)
            assertEquals("2h 56m", textLayout("home_hero_collection_0_time").layoutInput.text.text)
            assertEquals(1, textLayout("home_hero_collection_0_time").lineCount)
            assertTrue(!textLayout("home_hero_collection_0_time").hasVisualOverflow)
            composeRule.onNodeWithText("New", useUnmergedTree = true).assertDoesNotExist()
            composeRule.onNodeWithTag("home_hero_collection_0_progress", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test fun `carousel routes both grid titles to details and artwork to exact playback or pause`() {
        composeRule.setContent {
            val value = scenario.value
            val items = (0 until value.count).map { podcast(it, collectionTitle(it)) }
            BoxLoreTheme(dynamicColor = false) {
                HeroCarousel(
                    heroItems = StableHeroList(listOf(SmartHeroItem(value.type, items.first(), "", gridItems = items))),
                    onPlayClick = { podcast, _ -> collectionPlayClicks += podcast.id },
                    onDetailsClick = { collectionClicks += it.id },
                    onArrowClick = { _, _ -> },
                    onToggleSubscription = {},
                    onTogglePlayback = { collectionPauseClicks++ },
                    currentPlayingPodcastId = "show_0",
                    currentPlayingEpisodeId = "episode_0",
                    isPlaying = true,
                    modifier = Modifier.width(320.dp),
                )
            }
        }
        for (type in listOf(HeroType.NEW_EPISODES_GRID, HeroType.RESUME_GRID)) {
            scenario.value = Scenario(type = type)
            composeRule.onNodeWithTag("home_hero_collection_0_artwork", useUnmergedTree = true).performTouchInput { click(Offset(6f, 6f)) }
            composeRule.onNodeWithTag("home_hero_collection_1_artwork", useUnmergedTree = true).performTouchInput { click(Offset(6f, 6f)) }
            composeRule.onNodeWithTag("home_hero_collection_0_title", useUnmergedTree = true).performTouchInput { click() }
        }
        assertEquals(2, collectionPauseClicks)
        assertEquals(listOf("show_1", "show_1"), collectionPlayClicks)
        assertEquals(listOf("show_0", "show_0"), collectionClicks)
    }

    private fun render() {
        composeRule.setContent {
            val value = scenario.value
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, value.scale),
                LocalLayoutDirection provides value.direction,
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(value.width.dp).height(homeHeroHeight()).testTag("hero")) {
                        HeroGridCard(
                            items = (0 until value.count).map { podcast(it, collectionTitle(it)) },
                            title = "New episodes",
                            mode = HeroGridMode.NewEpisodes,
                            onCellClick = { collectionClicks += it.id },
                            onPlaybackClick = { collectionPlayClicks += it.id },
                        )
                    }
                }
            }
        }
    }

    private fun podcast(index: Int, title: String) = Podcast(
        "show_$index",
        "Show $index",
        "Creator",
        "",
        latestEpisode = Episode("episode_$index", title, "", "", duration = scenario.value.duration),
        resumeProgress = 0.5f,
    )

    private fun collectionTitle(index: Int) = if (scenario.value.title == "Long titles") {
        "Episode $index — a detailed conversation exploring interesting ideas and stories ".repeat(8)
    } else {
        "Episode $index"
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates.boundsInRoot()

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
