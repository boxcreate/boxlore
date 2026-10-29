package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ArtworkDuotone
import cx.aswin.boxlore.core.designsystem.theme.ArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
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

/** The Duotone hero option keeps the carousel's playback and details routing in every colour choice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DuotoneHeroCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val shade = mutableStateOf(HeroCoverShade.ARTWORK)
    private val item = mutableStateOf(resumeItem())
    private val plays = mutableListOf<String>()
    private val details = mutableListOf<String>()
    private var arrows = 0

    private fun episode(id: String) = TestFixtures.episode(id = id, title = "Episode $id", podcastId = "show-$id")
    private fun show(id: String) = TestFixtures.podcast(id = "show-$id", title = "Show $id", imageUrl = "")
        .copy(latestEpisode = episode(id), episodeStatus = EpisodeStatus.IN_PROGRESS, resumeProgress = 0.4f)
    private fun resumeItem() = SmartHeroItem(HeroType.RESUME, show("a"), "Resume", description = "Episode a")

    private fun render() {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHeroCardStyle provides HeroCardStyle.DUOTONE, LocalHeroCoverShade provides shade.value) {
                    HeroCarousel(
                        heroItems = StableHeroList(listOf(item.value)),
                        onPlayClick = { podcast, _ -> plays += podcast.id },
                        onDetailsClick = { details += it.id },
                        onArrowClick = { _, _ -> arrows++ },
                        onToggleSubscription = {},
                        onTogglePlayback = {},
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }
    }

    @Test fun `duotone card plays and opens details in every colour choice`() {
        render()
        listOf(HeroCoverShade.ARTWORK, HeroCoverShade.DARK, HeroCoverShade.THEME).forEach { option ->
            shade.value = option
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_hero_duotone").assertExists()
            composeRule.onNodeWithText("Episode a").assertExists()
            composeRule.onNodeWithTag("home_hero_play").performClick()
        }
        assertEquals(listOf("show-a", "show-a", "show-a"), plays)
        composeRule.onNodeWithContentDescription("Open episode details").performClick()
        assertEquals(1, arrows)
    }

    @Test fun `duotone collection keeps the solid cards' row routing`() {
        item.value = SmartHeroItem(HeroType.RESUME_GRID, show("a"), "", gridItems = listOf(show("a"), show("b")))
        render()
        composeRule.onNodeWithTag("home_hero_duotone_collection").assertExists()
        composeRule.onNodeWithTag("home_hero_collection_1_title", useUnmergedTree = true).performClick()
        assertEquals(listOf("show-b"), details)
        composeRule.onNodeWithTag("home_hero_collection_1_artwork").performClick()
        assertEquals(listOf("show-b"), plays)
    }

    @Test fun `each colour choice picks its tones`() {
        val primary = Color(0xFF5145D8)
        val palette = ArtworkPalette(seed = Color(0xFF2855BB).toArgb(), average = 0)
        assertEquals(ArtworkDuotone.Neutral, duotoneTones(HeroCoverShade.DARK, palette, primary))
        assertEquals(ArtworkDuotone.fromColor(primary), duotoneTones(HeroCoverShade.THEME, palette, primary))
        assertEquals(ArtworkDuotone.fromPalette(palette), duotoneTones(HeroCoverShade.ARTWORK, palette, primary))
        assertEquals(ArtworkDuotone.Neutral, duotoneTones(HeroCoverShade.ARTWORK, null, primary))
        // Marquee's vivid pairs follow the same choice.
        assertEquals(ArtworkDuotone.NeutralVivid, duotoneTones(HeroCoverShade.DARK, palette, primary, vivid = true))
        assertEquals(ArtworkDuotone.fromColor(primary, vivid = true), duotoneTones(HeroCoverShade.THEME, palette, primary, vivid = true))
        assertEquals(ArtworkDuotone.fromPalette(palette, vivid = true), duotoneTones(HeroCoverShade.ARTWORK, palette, primary, vivid = true))
        assertEquals(ArtworkDuotone.NeutralVivid, duotoneTones(HeroCoverShade.ARTWORK, null, primary, vivid = true))
    }
}
