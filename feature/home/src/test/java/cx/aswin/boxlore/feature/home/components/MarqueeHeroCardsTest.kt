package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Marquee hero keeps the carousel's playback and details routing and moves between cards from its up-next covers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarqueeHeroCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val shade = mutableStateOf(HeroCoverShade.ARTWORK)
    private val items = mutableStateOf(listOf(resumeItem()))
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
                CompositionLocalProvider(LocalHeroCardStyle provides HeroCardStyle.MARQUEE, LocalHeroCoverShade provides shade.value) {
                    HeroCarousel(
                        heroItems = StableHeroList(items.value),
                        onPlayClick = { podcast, _ -> plays += podcast.id },
                        onDetailsClick = { details += it.id },
                        onArrowClick = { _, _ -> arrows++ },
                        onToggleSubscription = {},
                        onTogglePlayback = { toggles++ },
                        currentPlayingPodcastId = playingEpisode?.let { "show-$it" },
                        currentPlayingEpisodeId = playingEpisode,
                        isPlaying = playingEpisode != null,
                        modifier = Modifier.width(400.dp),
                    )
                }
            }
        }
    }

    @Test fun `marquee card plays, shows progress and opens details in every colour choice`() {
        render()
        listOf(HeroCoverShade.ARTWORK, HeroCoverShade.DARK, HeroCoverShade.THEME).forEach { option ->
            shade.value = option
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_hero_marquee").assertExists()
            composeRule.onNodeWithText("Episode a").assertExists()
            composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag("home_hero_play").performClick()
        }
        assertEquals(listOf("show-a", "show-a", "show-a"), plays)
        composeRule.onNodeWithContentDescription("Open episode details").performClick()
        assertEquals(1, arrows)
    }

    @Test fun `marquee card pauses the episode that is playing`() {
        render(playingEpisode = "a")
        composeRule.onNodeWithText("Pause").assertExists()
        composeRule.onNodeWithTag("home_hero_play").performClick()
        assertEquals(1, toggles)
    }

    @Test fun `full color keeps covers as they are and the other choices redraw them`() {
        var fullColor: MarqueeLook? = null
        var artwork: MarqueeLook? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalHeroCoverShade provides HeroCoverShade.DARK) { fullColor = rememberMarqueeLook(null) }
            CompositionLocalProvider(LocalHeroCoverShade provides HeroCoverShade.ARTWORK) { artwork = rememberMarqueeLook(null) }
        }
        composeRule.waitForIdle()
        assertNull(fullColor?.filter)
        assertNotNull(artwork?.filter)
    }

    @Test fun `up-next covers move the hero to their card`() {
        val spotlight = SmartHeroItem(HeroType.SPOTLIGHT, show("b"), "#1 in US")
        val fresh = SmartHeroItem(HeroType.JUMP_BACK_IN, show("c"), "NEW EPISODE", description = "Episode c")
        items.value = listOf(resumeItem(), spotlight, fresh)
        render()
        composeRule.onNodeWithContentDescription("Go to Episode c").assertExists()
        composeRule.onNodeWithContentDescription("Go to Show b").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Show b").assertExists()
        composeRule.onNodeWithText("Episode a").assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_play").performClick()
        assertEquals(listOf("show-b"), plays)
    }

    @Test fun `collections advance on their own until a cover is chosen, then play and open the chosen episode`() {
        val shows = listOf("a", "b", "c", "d").map(::show)
        items.value = listOf(SmartHeroItem(HeroType.RESUME_GRID, shows.first(), "", gridItems = shows))
        // The strip keeps advancing while nothing is chosen, so the clock only moves when the test moves it.
        composeRule.mainClock.autoAdvance = false
        render()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Jump back in · 1 of 4").assertExists()
        composeRule.mainClock.advanceTimeBy(3_600L)
        composeRule.onNodeWithText("Jump back in · 2 of 4").assertExists()

        // Choosing a cover stops the marquee. With the clock held, the test presses through semantics.
        composeRule.onNodeWithTag("home_hero_collection_0").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("Jump back in · 1 of 4").assertExists()
        composeRule.mainClock.advanceTimeBy(8_000L)
        composeRule.onNodeWithText("Jump back in · 1 of 4").assertExists()

        composeRule.onNodeWithTag("home_hero_collection_0_progress", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("home_hero_play").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Open episode details").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeBy(500L)
        assertEquals(listOf("show-a"), plays)
        assertEquals(listOf("show-a"), details)
    }
}
