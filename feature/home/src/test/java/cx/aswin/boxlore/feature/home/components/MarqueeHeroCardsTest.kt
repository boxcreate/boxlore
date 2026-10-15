package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
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
import org.junit.Assert.assertTrue
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

    // Moving on by itself never lets the clock idle, so only the tests about it turn it on.
    private fun render(playingEpisode: String? = null, topInset: Dp = 0.dp, scroll: Int = 0, tone: MarqueeTopTone? = null, autoAdvance: Boolean = false) {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(
                    LocalHeroCardStyle provides HeroCardStyle.MARQUEE,
                    LocalHeroCoverShade provides shade.value,
                    LocalHeroTopInset provides topInset,
                    LocalHeroScrollOffset provides { scroll },
                    LocalMarqueeTopTone provides tone,
                    LocalMarqueeAutoAdvance provides autoAdvance,
                ) {
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

    @Test fun `the cover stays still while Home scrolls the hero away`() {
        render(topInset = 80.dp, scroll = 120)
        val hero = composeRule.onNodeWithTag("home_hero_marquee_hero").fetchSemanticsNode().boundsInRoot
        val cover = composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("the cover moves down as far as the hero scrolled up", 120f, cover.top - hero.top, 1f)
    }

    @Test fun `the frosted band shows only under the floating top bar and the card reports its words' colour`() {
        val tone = MarqueeTopTone().apply { content = Color.Red }
        render(topInset = 80.dp, tone = tone)
        composeRule.onNodeWithTag("home_hero_marquee_frost", useUnmergedTree = true).assertExists()
        assertEquals(MarqueeLightContent, tone.content)
        composeRule.onNodeWithTag("home_hero_sheet_handle", useUnmergedTree = true).assertExists()
    }

    @Test fun `without a top inset there is no frosted band`() {
        render()
        composeRule.onNodeWithTag("home_hero_marquee_frost", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `light covers get dark top bar words, and the bar fills only once the body passes under it`() {
        assertEquals(MarqueeDarkContent, marqueeTopContent(Color(0xFFF2EEE6).toArgb()))
        assertEquals(MarqueeLightContent, marqueeTopContent(Color(0xFF20304A).toArgb()))
        assertEquals(MarqueeLightContent, marqueeTopContent(null))
        assertTrue(marqueeVeilAlpha(toneKnown = false) > marqueeVeilAlpha(toneKnown = true))
        // A 100px bar; the body's edge starts 24px above the hero's foot. The bar stays clear while the edge
        // is at or below it (where the scroll stops), then fills over 24px as the edge passes under.
        assertEquals(1f, marqueeBarOverArtwork(heroBottom = 400, sheetOverlap = 24f, barHeight = 100, edgePx = 24f), 0.001f)
        assertEquals(1f, marqueeBarOverArtwork(heroBottom = 124, sheetOverlap = 24f, barHeight = 100, edgePx = 24f), 0.001f)
        assertEquals(0.5f, marqueeBarOverArtwork(heroBottom = 112, sheetOverlap = 24f, barHeight = 100, edgePx = 24f), 0.001f)
        assertEquals(0f, marqueeBarOverArtwork(heroBottom = 100, sheetOverlap = 24f, barHeight = 100, edgePx = 24f), 0.001f)
        assertEquals(0f, marqueeBarOverArtwork(heroBottom = null, sheetOverlap = 24f, barHeight = 100, edgePx = 24f), 0.001f)
    }

    @Test fun `the Settings preview shows Marquee as a slice of Home`() {
        val collection = mutableStateOf(false)
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                HeroStylePreview(HeroCardStyle.MARQUEE, HeroCoverShade.ARTWORK, collection.value, sessions = emptyList(), modifier = Modifier.size(320.dp, 480.dp))
            }
        }
        listOf(false, true).forEach { showCollection ->
            collection.value = showCollection
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_hero_marquee_preview").assertExists()
            composeRule.onNodeWithTag("home_hero_marquee_frost", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag("home_hero_page_dots", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithText("Your shows").assertExists()
        }
        composeRule.onNodeWithText("Jump back in · 1 of 4").assertExists()
    }

    @Test fun `the hero moves to its next card on its own`() {
        items.value = listOf(resumeItem(), SmartHeroItem(HeroType.SPOTLIGHT, show("b"), "#1 in US"), SmartHeroItem(HeroType.JUMP_BACK_IN, show("c"), "NEW EPISODE", description = "Episode c"))
        composeRule.mainClock.autoAdvance = false
        render(autoAdvance = true)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("1 of 3").assertExists()
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.onNodeWithContentDescription("1 of 3").assertExists()
        // Six seconds on a card, then the page turns.
        composeRule.mainClock.advanceTimeBy(2_500L)
        composeRule.onNodeWithContentDescription("2 of 3").assertExists()
    }

    @Test fun `the hero rests while touched, scrolled away or when motion does not suit the phone, and waits out a collection`() {
        assertTrue(marqueeShouldRotate(count = 3, allowed = true, touched = false, onScreen = true))
        assertEquals(false, marqueeShouldRotate(count = 1, allowed = true, touched = false, onScreen = true))
        assertEquals(false, marqueeShouldRotate(count = 3, allowed = true, touched = true, onScreen = true))
        assertEquals(false, marqueeShouldRotate(count = 3, allowed = true, touched = false, onScreen = false))
        assertEquals(false, marqueeShouldRotate(count = 3, allowed = false, touched = false, onScreen = true))
        assertEquals(6_000L, marqueeDwellMillis(resumeItem()))
        val four = listOf("a", "b", "c", "d").map(::show)
        assertEquals(14_000L, marqueeDwellMillis(SmartHeroItem(HeroType.RESUME_GRID, four.first(), "", gridItems = four)))
        assertEquals(7_000L, marqueeDwellMillis(SmartHeroItem(HeroType.NEW_EPISODES_GRID, four.first(), "", gridItems = four.take(2))))
    }

    @Test fun `up-next covers move the hero to their card`() {
        val spotlight = SmartHeroItem(HeroType.SPOTLIGHT, show("b"), "#1 in US")
        val fresh = SmartHeroItem(HeroType.JUMP_BACK_IN, show("c"), "NEW EPISODE", description = "Episode c")
        items.value = listOf(resumeItem(), spotlight, fresh)
        render()
        composeRule.onNodeWithContentDescription("Go to Episode c").assertExists()
        composeRule.onNodeWithContentDescription("1 of 3").assertExists()
        composeRule.onNodeWithContentDescription("Go to Show b").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("2 of 3").assertExists()
        composeRule.onNodeWithText("Show b").assertExists()
        composeRule.onNodeWithText("Episode a").assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_play").performClick()
        assertEquals(listOf("show-b"), plays)
    }

    @Test fun `collections advance on their own, pause after a cover is chosen, then carry on`() {
        val shows = listOf("a", "b", "c", "d").map(::show)
        items.value = listOf(SmartHeroItem(HeroType.RESUME_GRID, shows.first(), "", gridItems = shows))
        // The strip keeps advancing, so the clock only moves when the test moves it.
        composeRule.mainClock.autoAdvance = false
        render(autoAdvance = true)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Jump back in · 1 of 4").assertExists()
        composeRule.mainClock.advanceTimeBy(3_600L)
        composeRule.onNodeWithText("Jump back in · 2 of 4").assertExists()

        // Choosing a cover holds it for 8 seconds. With the clock held, the test presses through semantics.
        composeRule.onNodeWithTag("home_hero_collection_0").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("Jump back in · 1 of 4").assertExists()
        composeRule.mainClock.advanceTimeBy(4_000L)
        composeRule.onNodeWithText("Jump back in · 1 of 4").assertExists()

        // Play and details use the chosen episode.
        composeRule.onNodeWithTag("home_hero_collection_0_progress", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("home_hero_play").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Open episode details").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeBy(500L)
        assertEquals(listOf("show-a"), plays)
        assertEquals(listOf("show-a"), details)

        // Then the marquee carries on.
        composeRule.mainClock.advanceTimeBy(4_000L)
        composeRule.onNodeWithText("Jump back in · 2 of 4").assertExists()
    }
}
