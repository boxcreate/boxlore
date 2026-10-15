package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
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
    private lateinit var view: View

    private fun episode(id: String) = TestFixtures.episode(id = id, title = "Episode $id", podcastId = "show-$id").copy(duration = 3600)
    private fun show(id: String) = TestFixtures.podcast(id = "show-$id", title = "Show $id", imageUrl = "")
        .copy(latestEpisode = episode(id), episodeStatus = EpisodeStatus.IN_PROGRESS, resumeProgress = 0.4f)
    private fun resumeItem() = SmartHeroItem(HeroType.RESUME, show("a"), "Resume", description = "Episode a")

    private fun render() {
        composeRule.setContent {
            view = LocalView.current
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

    @Test fun `a collection cover's play button is drawn whole over its ring`() {
        val shows = listOf("a", "b", "c", "d").map(::show)
        item.value = SmartHeroItem(HeroType.RESUME_GRID, shows.first(), "", gridItems = shows)
        render()
        val cover = composeRule.onNodeWithTag("home_hero_collection_1_play").fetchSemanticsNode().boundsInRoot
        // The button sits in the cover's bottom end corner: 30dp across with a 3dp ring in the card's colour.
        val corner = pixel(cover.right - 8f, cover.bottom - 8f)
        val fill = pixel(cover.right - 23f, cover.bottom - 15f)
        assertTrue(
            "the corner of the button, outside the ring, matches its fill (${Integer.toHexString(corner)} vs ${Integer.toHexString(fill)})",
            sameColor(corner, fill),
        )
    }

    @Test fun `right to left puts the cover in the top start corner`() {
        direction.value = LayoutDirection.Rtl
        render()
        val card = composeRule.onNodeWithTag("home_hero_halo").fetchSemanticsNode().boundsInRoot
        val cover = composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("cover centre ${cover.center.x} vs card centre ${card.center.x}", cover.center.x < card.center.x)
    }

    @Test fun `the ring ends in the card's upper half, leaves room for the context and runs edge to edge`() {
        // However far the lobes have turned, the progress's rounded ends (2dp past their centre) stay off the card.
        val turns = (0 until 8).map { it * HaloLobeTurn / 8 }
        for (width in 260..440 step 20) {
            for (height in listOf(416f, 476f, 616f)) {
                val g = haloGeometry(width.toFloat(), height)
                val label = "$width × $height"
                assertTrue("$label ring bottom", g.centerY + g.ringRadius <= height * 0.55f + 0.01f)
                assertTrue("$label cover runs past the top and end", g.coverTop < 0f && g.coverStart + g.cover > width)
                assertTrue("$label context ${g.contextWidth}", g.contextWidth >= 60f)
                assertTrue("$label sweep ${g.arcSweep}", g.arcSweep < -120f && g.arcSweep > -200f)
                assertTrue("$label drawing starts before progress begins", g.arcStart > g.arcFrom)
                turns.forEach { turn ->
                    val start = haloRingPoint(g, g.arcStart, turn)
                    val end = haloRingPoint(g, g.arcFrom + g.arcSweep, turn)
                    assertTrue("$label turn $turn: progress starts beyond the top edge (${start.y})", start.y <= -2f + 0.01f && start.x < g.centerX)
                    assertTrue("$label turn $turn: full progress ends beyond the end edge (${end.x})", end.x >= width + 2f - 0.01f)
                }
            }
        }
    }

    @Test fun `with a wide context the cover sits below it and progress starts at the ring's top`() {
        val g = haloGeometry(360f, 616f, top = 120f)
        assertEquals(120f, g.coverTop, 0.01f)
        assertTrue(g.centerY + g.ringRadius <= 616f * 0.55f + 0.01f)
        assertEquals(270f, g.arcFrom, 0.01f)
        assertEquals(270f, g.arcStart, 0.01f)
        assertTrue(abs(g.arcSweep) > 180f)
    }

    @Test fun `the cookie turns one lobe every 2·4 seconds, like the compact player`() {
        assertEquals(HaloLobeTurn / 2, advanceHaloRotation(0f, 1_200_000_000L), 0.0001f)
        assertEquals(0f, advanceHaloRotation(0f, 2_400_000_000L), 0.0001f)
        assertEquals(0f, advanceHaloRotation(Float.NaN, 0L), 0f)
        // The ring is the cookie's edge pushed out: a lobe tip reaches the ring radius at the top, half a
        // lobe's turn brings a valley there, and a whole lobe's turn looks the same as none.
        val g = haloGeometry(320f, 416f)
        assertEquals(g.centerY - g.ringRadius, haloRingPoint(g, 270f, 0f).y, 0.01f)
        assertEquals(g.centerY - (g.ringRadius - g.lobe * 2), haloRingPoint(g, 270f, HaloLobeTurn / 2).y, 0.01f)
        assertEquals(haloRingPoint(g, 250f, 0f).x, haloRingPoint(g, 250f, HaloLobeTurn).x, 0.01f)
    }

    private fun pixel(x: Float, y: Float): Int = composeRule.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.getPixel(x.toInt(), y.toInt()).also { bitmap.recycle() }
    }

    private fun sameColor(a: Int, b: Int): Boolean =
        listOf(16, 8, 0).all { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= 8 }
}
