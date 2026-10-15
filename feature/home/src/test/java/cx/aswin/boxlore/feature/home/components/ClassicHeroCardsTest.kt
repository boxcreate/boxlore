package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableHeroList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Classic hero option keeps the carousel's playback and details routing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicHeroCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val classic = mutableStateOf(true)
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
                CompositionLocalProvider(LocalClassicHeroCards provides classic.value) {
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

    @Test fun `classic card plays, pauses and opens details through the carousel callbacks`() {
        render()
        composeRule.onNodeWithText("RESUME").assertExists()
        composeRule.onNodeWithText("Resume").performClick()
        assertEquals(listOf("show-a"), plays)
        composeRule.onNodeWithContentDescription("Open episode details").performClick()
        assertEquals(1, arrows)
    }

    @Test fun `classic card pauses the episode that is playing`() {
        render(playingEpisode = "a")
        composeRule.onNodeWithText("Pause").performClick()
        assertEquals(1, toggles)
        assertEquals(emptyList<String>(), plays)
    }

    @Test fun `classic Jump back in tiles play and New episodes tiles open details`() {
        item.value = SmartHeroItem(HeroType.RESUME_GRID, show("a"), "", gridItems = listOf(show("a"), show("b")))
        render()
        composeRule.onNodeWithText("Jump back in").assertExists()
        composeRule.onNodeWithContentDescription("Show b").performClick()
        assertEquals(listOf("show-b"), plays)

        item.value = SmartHeroItem(HeroType.NEW_EPISODES_GRID, show("a"), "", gridItems = listOf(show("a"), show("b"), show("c")))
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Show c").performClick()
        assertEquals(listOf("show-c"), details)
    }

    @Test fun `standard style is unchanged when classic is off`() {
        classic.value = false
        render()
        composeRule.onNodeWithText("RESUME").assertDoesNotExist()
    }
}
