package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.components.HeroCarousel
import cx.aswin.boxlore.feature.home.components.LocalHeroCardStyle
import cx.aswin.boxlore.feature.home.components.LocalMarqueeAutoAdvance
import cx.aswin.boxlore.feature.home.components.MarqueeSheetOverlap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home over Marquee scrolls as one piece: the hero, its cover and the page's rounded edge move together, with no stop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarqueeHomeLayoutTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var grid: LazyStaggeredGridState

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val fontScale = mutableFloatStateOf(1f)

    private fun render(items: () -> List<SmartHeroItem> = { listOf(defaultItem()) }) {
        compose.setContent {
            val base = LocalDensity.current
            val hero = StableHeroList(items())
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(
                    LocalHeroCardStyle provides HeroCardStyle.MARQUEE,
                    LocalMarqueeAutoAdvance provides false,
                    LocalBoxloreWindowLayout provides window.value,
                    LocalDensity provides Density(base.density, fontScale.floatValue),
                ) {
                    grid = rememberLazyStaggeredGridState()
                    MarqueeHomeLayout(
                        gridState = grid,
                        barExpanded = { true },
                        feed = {
                            LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(1), state = grid, modifier = Modifier.fillMaxSize().testTag("feed")) {
                                item(key = "hero") {
                                    HeroCarousel(
                                        heroItems = hero,
                                        onPlayClick = { _, _ -> },
                                        onDetailsClick = {},
                                        onArrowClick = { _, _ -> },
                                        onToggleSubscription = {},
                                        onTogglePlayback = {},
                                        currentPlayingPodcastId = null,
                                        currentPlayingEpisodeId = null,
                                        isPlaying = false,
                                    )
                                }
                                items(40, key = { "row$it" }) { Box(Modifier.fillMaxWidth().height(120.dp)) }
                            }
                        },
                        topBar = { _, _, modifier -> Box(modifier.fillMaxWidth().height(BarHeight.dp)) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun defaultItem(): SmartHeroItem {
        val episode = TestFixtures.episode(id = "a", title = "Episode a", podcastId = "show-a").copy(duration = 3600)
        val show = TestFixtures.podcast(id = "show-a", title = "Show a", imageUrl = "")
            .copy(latestEpisode = episode, episodeStatus = EpisodeStatus.IN_PROGRESS, resumeProgress = 0.4f)
        return SmartHeroItem(HeroType.RESUME, show, "Resume", description = "Episode a")
    }

    private fun longShow(index: Int) = TestFixtures.podcast(id = "show-$index", title = "The Joe Rogan Experience", imageUrl = "").copy(
        latestEpisode = TestFixtures.episode(id = "e$index", title = "A Thanksgiving Dinner for \$100? Yes, It's Possible", podcastId = "show-$index").copy(duration = 3600),
        episodeStatus = EpisodeStatus.IN_PROGRESS,
        resumeProgress = 0.4f,
    )

    // Unclipped: a part scrolled above the screen keeps its real position.
    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    @Test fun `a short slow swipe moves the cover and the page's rounded edge up together`() {
        render()
        val cover = top("home_hero_artwork")
        val gap = top("home_hero_sheet_edge") - cover
        compose.onNodeWithTag("feed").performTouchInput { swipeUp(startY = 600f, endY = 450f, durationMillis = 1_500) }
        compose.waitForIdle()
        val scrolled = compose.runOnIdle { grid.firstVisibleItemScrollOffset }
        assertTrue("Home scrolled", scrolled > 0)
        assertEquals("the cover scrolls with Home instead of staying still", cover - scrolled, top("home_hero_artwork"), 1f)
        assertEquals("the edge and the cover keep their distance", gap, top("home_hero_sheet_edge") - top("home_hero_artwork"), 1f)
    }

    @Test fun `one long swipe carries the page's edge under the bar without stopping there`() {
        render()
        val heroHeight = compose.onNodeWithTag("home_hero_marquee_hero").fetchSemanticsNode().boundsInRoot.height
        // Where the old stop held the page: the body's edge (24dp above the hero's foot) meeting the bar's foot.
        val formerStop = heroHeight - MarqueeSheetOverlap.value - BarHeight
        compose.onNodeWithTag("feed").performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle {
            val scrolled = if (grid.firstVisibleItemIndex == 0) grid.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE
            assertTrue("one swipe goes past where the edge meets the bar ($scrolled > $formerStop)", scrolled > formerStop + 10f)
        }
    }

    @Test fun `on short windows the words stay below the floating top bar in both directions`() {
        val collection = mutableStateOf(false)
        render {
            if (collection.value) {
                listOf(SmartHeroItem(HeroType.RESUME_GRID, longShow(0), "Resume", gridItems = List(4) { longShow(it) }))
            } else {
                listOf(SmartHeroItem(HeroType.RESUME, longShow(0), "Resume"))
            }
        }
        val failures = mutableListOf<String>()
        val windows = listOf(BoxloreTestWindows.PHONE, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.COVER_LAND, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE)
        val cases = listOf(1f, 1.3f).flatMap { scale -> listOf(false, true).flatMap { grid -> windows.map { Triple(scale, grid, it) } } }
        for ((scale, grid, layout) in cases) {
            fontScale.floatValue = scale
            collection.value = grid
            window.value = layout
            compose.waitForIdle()
            failures += wordsUnderBar("${if (grid) "collection" else "episode"} at ${layout.widthDp}x${layout.heightDp}, text ${scale}x", grid)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun wordsUnderBar(label: String, collection: Boolean): List<String> {
        val words = listOf(if (collection) "home_hero_collection_position" else "home_hero_marquee_chip", "home_hero_title")
        val failures = words.mapNotNull { tag -> top(tag).takeIf { it < BarHeight - 0.5f }?.let { "$label: $tag starts at $it, under the ${BarHeight}dp top bar" } }
        val play = compose.onNodeWithTag("home_hero_play", useUnmergedTree = true).fetchSemanticsNode()
        return if (play.size.height < 48) failures + "$label: Play is ${play.size.height}dp tall" else failures
    }

    private companion object {
        const val BarHeight = 100
    }
}
