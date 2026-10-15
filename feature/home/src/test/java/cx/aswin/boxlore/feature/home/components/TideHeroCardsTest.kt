package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import cx.aswin.boxlore.core.testing.TestFixtures
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

/** The Tide hero option keeps the carousel's playback and details routing, and its panel makes room for the words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TideHeroCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val shade = mutableStateOf(HeroCoverShade.ARTWORK)
    private val item = mutableStateOf(resumeItem())
    private val plays = mutableListOf<String>()
    private val details = mutableListOf<String>()
    private var arrows = 0
    private var toggles = 0

    private fun episode(id: String) = TestFixtures.episode(id = id, title = "Episode $id", podcastId = "show-$id").copy(duration = 3600)
    private fun show(id: String) = TestFixtures.podcast(id = "show-$id", title = "Show $id", imageUrl = "")
        .copy(latestEpisode = episode(id), episodeStatus = EpisodeStatus.IN_PROGRESS, resumeProgress = 0.4f)
    private fun resumeItem() = SmartHeroItem(HeroType.RESUME, show("a"), "Resume", description = "Episode a")

    private fun render(playingEpisode: String? = null) {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHeroCardStyle provides HeroCardStyle.TIDE, LocalHeroCoverShade provides shade.value) {
                    HeroCarousel(
                        heroItems = StableHeroList(listOf(item.value)),
                        onPlayClick = { podcast, _ -> plays += podcast.id },
                        onDetailsClick = { details += it.id },
                        onArrowClick = { _, _ -> arrows++ },
                        onToggleSubscription = {},
                        onTogglePlayback = { toggles++ },
                        currentPlayingPodcastId = playingEpisode?.let { "show-$it" },
                        currentPlayingEpisodeId = playingEpisode,
                        isPlaying = playingEpisode != null,
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }
    }

    @Test fun `tide card plays from its disc, shows progress and opens details in every colour choice`() {
        render()
        listOf(HeroCoverShade.ARTWORK, HeroCoverShade.DARK, HeroCoverShade.THEME).forEach { option ->
            shade.value = option
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_hero_tide").assertExists()
            composeRule.onNodeWithText("Episode a").assertExists()
            composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag("home_hero_play").performClick()
        }
        assertEquals(listOf("show-a", "show-a", "show-a"), plays)
        composeRule.onNodeWithText("Episode a").performClick()
        assertEquals(1, arrows)
    }

    @Test fun `the disc pauses the episode that is playing`() {
        render(playingEpisode = "a")
        composeRule.onNodeWithContentDescription("Pause").performClick()
        assertEquals(1, toggles)
        assertEquals(emptyList<String>(), plays)
    }

    @Test fun `the disc sits on the panel's edge, clear of the words`() {
        render()
        val play = bounds("home_hero_play")
        val context = bounds("home_hero_context")
        val title = bounds("home_hero_title")
        assertTrue("context ends before the disc (${context.right} vs ${play.left})", context.right <= play.left)
        assertTrue("title starts below the disc (${title.top} vs ${play.bottom})", title.top >= play.bottom)
    }

    @Test fun `tide collections open the episode from its cover or row and play from the cover's button`() {
        render()
        listOf(2, 3, 4).forEach { count ->
            val shows = listOf("a", "b", "c", "d").take(count).map(::show)
            item.value = SmartHeroItem(HeroType.RESUME_GRID, shows.first(), "", gridItems = shows)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_hero_tide_collection").assertExists()
            // The summary adds up each episode's time left, as the cards count it.
            val minutes = shows.sumOf { HomeHeroStyle.minutes(it, resume = true)!! }
            composeRule.onNodeWithText("$count episodes · ${minutes / 60}h ${minutes % 60}m left").assertExists()
            composeRule.onNodeWithTag("home_hero_collection_1").performClick()
            composeRule.onNodeWithTag("home_hero_collection_1_play").performClick()
            composeRule.onNodeWithTag("home_hero_collection_1_progress", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag("home_hero_collection_1_time", useUnmergedTree = true).assertExists()
            if (count < 4) composeRule.onNodeWithTag("home_hero_collection_1_row").performClick()
        }
        assertEquals(listOf("show-b", "show-b", "show-b", "show-b", "show-b"), details)
        assertEquals(listOf("show-b", "show-b", "show-b"), plays)
    }

    @Test fun `the panel rises only as far as the words need, over the cover's lower part`() {
        // Short words: the edge stays low, 16dp above the cover's foot.
        assertEquals(286f, tidePanelTop(302f, 416f, copy = 60f), 0.01f)
        // Longer words: the edge sits 40dp above them.
        assertEquals(416f - 24f - 130f - 40f, tidePanelTop(302f, 416f, copy = 130f), 0.01f)
        // Very long words never push it above 65% of the width.
        assertEquals(302f * 0.65f, tidePanelTop(302f, 416f, copy = 300f), 0.01f)
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
}
