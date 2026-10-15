package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Cover hero option keeps the carousel's playback and details routing in every shade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverHeroCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val cover = mutableStateOf(true)
    private val shade = mutableStateOf(HeroCoverShade.ARTWORK)
    private val item = mutableStateOf(resumeItem())
    private val plays = mutableListOf<String>()
    private val details = mutableListOf<String>()
    private var arrows = 0
    private var toggles = 0

    private fun episode(id: String) = TestFixtures.episode(id = id, title = "Episode $id", podcastId = "show-$id")
    private fun show(id: String) = TestFixtures.podcast(id = "show-$id", title = "Show $id", imageUrl = "")
        .copy(latestEpisode = episode(id), episodeStatus = EpisodeStatus.IN_PROGRESS, resumeProgress = 0.4f)
    private fun resumeItem() = SmartHeroItem(HeroType.RESUME, show("a"), "Resume", description = "Episode a")

    private fun render(playingEpisode: String? = null) {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHeroCardStyle provides (if (cover.value) HeroCardStyle.COVER else HeroCardStyle.BLOCK), LocalHeroCoverShade provides shade.value) {
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

    @Test fun `cover card plays and opens details through the carousel callbacks`() {
        render()
        composeRule.onNodeWithTag("home_hero_cover").assertExists()
        composeRule.onNodeWithText("Episode a").assertExists()
        composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("home_hero_play").performClick()
        assertEquals(listOf("show-a"), plays)
        composeRule.onNodeWithContentDescription("Open episode details").performClick()
        assertEquals(1, arrows)
    }

    @Test fun `cover card pauses the episode that is playing`() {
        render(playingEpisode = "a")
        composeRule.onNodeWithText("Pause").assertExists()
        composeRule.onNodeWithTag("home_hero_play").performClick()
        assertEquals(1, toggles)
        assertEquals(emptyList<String>(), plays)
    }

    @Test fun `dark and app theme shades keep the same card and actions`() {
        render()
        listOf(HeroCoverShade.DARK, HeroCoverShade.THEME).forEach { option ->
            shade.value = option
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_hero_cover").assertExists()
            composeRule.onNodeWithTag("home_hero_play").performClick()
        }
        assertEquals(listOf("show-a", "show-a"), plays)
    }

    @Test fun `collection tiles open their episode and their cover button plays it`() {
        item.value = SmartHeroItem(HeroType.RESUME_GRID, show("a"), "", gridItems = listOf(show("a"), show("b")))
        render()
        composeRule.onNodeWithText("Jump back in").assertExists()
        composeRule.onNodeWithTag("home_hero_collection_1").performClick()
        assertEquals(listOf("show-b"), details)
        composeRule.onNodeWithTag("home_hero_collection_1_play").performClick()
        assertEquals(listOf("show-b"), plays)

        item.value = SmartHeroItem(HeroType.NEW_EPISODES_GRID, show("a"), "", gridItems = listOf(show("a"), show("b"), show("c")))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_hero_collection_2").performClick()
        assertEquals(listOf("show-b", "show-c"), details)
        composeRule.onNodeWithTag("home_hero_collection_2_play").performClick()
        assertEquals(listOf("show-b", "show-c"), plays)
    }

    @Test fun `collection tile progress and time left share one row`() {
        val withDuration = { id: String -> show(id).let { it.copy(latestEpisode = it.latestEpisode?.copy(duration = 3600)) } }
        item.value = SmartHeroItem(HeroType.RESUME_GRID, withDuration("a"), "", gridItems = listOf(withDuration("a"), withDuration("b"), withDuration("c"), withDuration("d")))
        render()
        listOf(0, 2).forEach { index ->
            val progress = composeRule.onNodeWithTag("home_hero_collection_${index}_progress", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val time = composeRule.onNodeWithTag("home_hero_collection_${index}_time", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val progressCentre = (progress.top + progress.bottom) / 2
            val timeCentre = (time.top + time.bottom) / 2
            assertTrue("tile $index: progress $progressCentre vs time $timeCentre", abs(progressCentre.value - timeCentre.value) <= 2f)
            assertTrue("tile $index: time follows the bar", time.left >= progress.right)
        }
    }

    @Test fun `new episode tiles show their length on the cover instead of a New tag`() {
        val withDuration = { id: String -> show(id).let { it.copy(latestEpisode = it.latestEpisode?.copy(duration = 2640), rssHasNewEpisodes = true) } }
        item.value = SmartHeroItem(HeroType.NEW_EPISODES_GRID, withDuration("a"), "", gridItems = listOf(withDuration("a"), withDuration("b"), withDuration("c")))
        render()
        composeRule.onNodeWithText("New").assertDoesNotExist()
        val time = composeRule.onNodeWithTag("home_hero_collection_1_time", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithTag("home_hero_collection_1_title", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the length sits at the top of the cover", time.bottom <= title.top)
        composeRule.onNodeWithTag("home_hero_collection_1_progress", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `block style is unchanged when cover is off`() {
        cover.value = false
        render()
        composeRule.onNodeWithTag("home_hero_cover").assertDoesNotExist()
    }
}
