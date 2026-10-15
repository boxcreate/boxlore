package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
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
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Halo hero option keeps the carousel's playback and details routing, and its ring stays clear of the words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HaloHeroCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val shade = mutableStateOf(HeroCoverShade.ARTWORK)
    private val item = mutableStateOf(resumeItem())
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val plays = mutableListOf<String>()
    private val details = mutableListOf<String>()
    private var arrows = 0

    private fun episode(id: String) = TestFixtures.episode(id = id, title = "Episode $id", podcastId = "show-$id").copy(duration = 3600)
    private fun show(id: String) = TestFixtures.podcast(id = "show-$id", title = "Show $id", imageUrl = "")
        .copy(latestEpisode = episode(id), episodeStatus = EpisodeStatus.IN_PROGRESS, resumeProgress = 0.4f)
    private fun resumeItem() = SmartHeroItem(HeroType.RESUME, show("a"), "Resume", description = "Episode a")

    private fun render() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                BoxLoreTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalHeroCardStyle provides HeroCardStyle.HALO, LocalHeroCoverShade provides shade.value) {
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
    }

    @Test fun `halo card plays, shows progress on its ring and opens details in every colour choice`() {
        render()
        listOf(HeroCoverShade.ARTWORK, HeroCoverShade.DARK, HeroCoverShade.THEME).forEach { option ->
            shade.value = option
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_hero_halo").assertExists()
            composeRule.onNodeWithText("Episode a").assertExists()
            composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag("home_hero_play").performClick()
        }
        assertEquals(listOf("show-a", "show-a", "show-a"), plays)
        composeRule.onNodeWithContentDescription("Open episode details").performClick()
        assertEquals(1, arrows)
    }

    @Test fun `halo collections play from the cover and open the episode from the rest of the tile`() {
        render()
        listOf(2, 3, 4).forEach { count ->
            val shows = listOf("a", "b", "c", "d").take(count).map(::show)
            item.value = SmartHeroItem(HeroType.RESUME_GRID, shows.first(), "", gridItems = shows)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_hero_halo_collection").assertExists()
            composeRule.onNodeWithTag("home_hero_collection_1_title", useUnmergedTree = true).performClick()
            composeRule.onNodeWithTag("home_hero_collection_1_play").performClick()
            composeRule.onNodeWithTag("home_hero_collection_1_progress", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag("home_hero_collection_1_time", useUnmergedTree = true).assertExists()
        }
        assertEquals(listOf("show-b", "show-b", "show-b"), details)
        assertEquals(listOf("show-b", "show-b", "show-b"), plays)
    }

    @Test fun `right to left puts the cover in the top start corner`() {
        direction.value = LayoutDirection.Rtl
        render()
        val card = composeRule.onNodeWithTag("home_hero_halo").fetchSemanticsNode().boundsInRoot
        val cover = composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("cover centre ${cover.center.x} vs card centre ${card.center.x}", cover.center.x < card.center.x)
    }

    @Test fun `the ring ends in the card's upper half, leaves room for the context and runs edge to edge`() {
        for (width in 260..440 step 20) {
            for (height in listOf(416f, 476f, 616f)) {
                val g = haloGeometry(width.toFloat(), height)
                val label = "$width × $height"
                assertTrue("$label ring bottom", g.centerY + g.ringRadius <= height * 0.55f + 0.01f)
                assertTrue("$label cover runs past the top and end", g.coverTop < 0f && g.coverStart + g.cover > width)
                assertTrue("$label context ${g.contextWidth}", g.contextWidth >= 60f)
                assertTrue("$label sweep ${g.arcSweep}", g.arcSweep < -120f && g.arcSweep > -200f)
                val (startX, startY) = point(g, g.arcStart)
                val (endX, endY) = point(g, g.arcStart + g.arcSweep)
                assertTrue("$label progress starts just inside the top edge ($startX, $startY)", startY in 0f..12f && startX < g.centerX)
                assertTrue("$label progress ends just inside the end edge ($endX, $endY)", endX in (width - 12f)..width.toFloat() && endY in 0f..height)
            }
        }
    }

    @Test fun `with a wide context the cover sits below it and progress starts at the ring's top`() {
        val g = haloGeometry(360f, 616f, top = 120f)
        assertEquals(120f, g.coverTop, 0.01f)
        assertTrue(g.centerY + g.ringRadius <= 616f * 0.55f + 0.01f)
        assertEquals(267f, g.arcStart, 0.01f)
        assertTrue(abs(g.arcSweep) > 180f)
    }

    private fun point(g: HaloGeometry, degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        return (g.centerX + g.ringRadius * cos(radians).toFloat()) to (g.centerY + g.ringRadius * sin(radians).toFloat())
    }
}
