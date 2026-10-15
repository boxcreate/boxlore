package cx.aswin.boxlore.feature.home.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePodcastList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletHomeRefinementTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1280, 1400, false))
    private val width = mutableStateOf(900)
    private val podcast = TestFixtures.podcast(title = "Seed show", imageUrl = PhoneGoldenRule.ARTWORK_URL)

    @Test fun `single recommendation rail matches featured artwork and poster heights at fractional density and large text`() {
        val episodes = List(9) { TestFixtures.episode(id = "$it", title = "Episode $it with a long title across several lines", imageUrl = PhoneGoldenRule.ARTWORK_URL) }
        val clicks = mutableListOf<Int>()
        render(density = 1.3f, fontScale = 2f) {
            TabletRecommendationRail(episodes) { episode, index, presentation ->
                CuratedEpisodeCard(podcast, episode, { clicks += index }, showSubtitle = false, presentation = presentation)
            }
        }
        val hero = bounds("tablet_recommendation_0")
        val poster = bounds("tablet_recommendation_1")
        assertEquals(hero.top, poster.top, 1f)
        assertEquals(hero.bottom, poster.bottom, 1f)
        assertEquals(hero.height, bounds("feed_featured_artwork").height, 1f)
        assertTrue(hero.width <= 480f * 1.3f)
        assertTrue(poster.width <= 208f * 1.3f)
        captureReview("home-tablet-rail-large-text")
        fixture.compose.onNodeWithTag("tablet_recommendation_rail").performScrollToIndex(8)
        fixture.compose.onNodeWithText(episodes.last().title).performClick()
        assertEquals(listOf(8), clicks)
    }

    @Test fun `tablet seed shares the similar shows heading and preserves independent actions`() {
        var showClicks = 0
        var swapClicks = 0
        render {
            BecauseYouLikeSection(podcast, StableEpisodeList(emptyList()), StablePodcastList(listOf(podcast.copy(id = "other", title = "Similar podcast"))), null, false, { _, _ -> }, { _, _ -> }, { showClicks++ }, { swapClicks++ })
        }
        val seed = bounds("tablet_recommendation_seed")
        val heading = fixture.compose.onNodeWithText("Similar shows").fetchSemanticsNode().boundsInRoot
        assertTrue(seed.left > heading.right)
        assertTrue(heading.center.y in seed.top..seed.bottom)
        captureReview("home-tablet-similar-seed")
        fixture.compose.onNodeWithText(podcast.title).performClick()
        fixture.compose.onNodeWithContentDescription("Change show for recommendations").performClick()
        assertEquals(1, showClicks)
        assertEquals(1, swapClicks)
    }

    @Test fun `genre row reveals additional chips on wider panes and retains a late selection`() {
        val selections = mutableListOf<String?>()
        render { GenreSelector("Government", { selections += it }) }
        fixture.compose.onNodeWithText("Govt").assertIsSelected()
        fixture.compose.runOnIdle { width.value = 1200 }
        settle()
        fixture.compose.onNodeWithText("Sports").assertIsDisplayed().performClick()
        assertEquals(listOf("Sports"), selections)
        captureReview("home-tablet-inline-genres")
        fixture.compose.onNodeWithText("More").performClick()
        fixture.compose.onNodeWithText("Browse genres").assertIsDisplayed()
        fixture.compose.runOnIdle { width.value = 700 }
        settle()
        fixture.compose.onNodeWithText("Browse genres").assertDoesNotExist()
        fixture.compose.onNodeWithText("Govt").assertIsSelected()
    }

    @Test fun `tablet loading Your shows uses the same full width split frame`() {
        render { YourShowsSkeleton(subscribedCount = 8) }
        val picker = bounds("home_shows_picker_pane")
        val content = bounds("home_shows_content_pane")
        assertTrue(content.left > picker.right)
        assertEquals(picker.top, content.top, 1f)
        assertEquals(picker.bottom, content.bottom, 1f)
        assertEquals(900f, content.right - picker.left, 1f)
    }

    @Test fun `tabletop resume action fits above the retained mini player`() {
        var clicks = 0
        val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)
        render {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(673f, 841f, listOf(fold)), LocalBoxlorePaneBounds provides BoxloreLayoutBounds(80f, 431f, 673f, 841f), LocalBoxloreMiniPlayerVisible provides true) {
                Box(Modifier.width(320.dp).height(homeHeroHeight()).testTag("short_hero")) {
                    HeroCard(SmartHeroItem(HeroType.RESUME, podcast.copy(latestEpisode = TestFixtures.episode()), "Continue listening"), { clicks++ }, {}, {})
                }
            }
        }
        val hero = bounds("short_hero")
        val play = bounds("home_hero_play")
        assertTrue(hero.height <= 258f)
        assertTrue(play.height >= 48f && play.bottom <= hero.bottom)
        fixture.compose.onNodeWithTag("home_hero_play", useUnmergedTree = true).performClick()
        assertEquals(1, clicks)
        captureReview("home-tabletop-compact-hero")
    }

    private fun captureReview(name: String) {
        if (System.getProperty("boxlore.tabletCaptures") == "after") fixture.capture(name)
    }

    private fun bounds(tag: String) = fixture.compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun settle() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.waitForIdle()
    }

    private fun render(density: Float = 1f, fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        fixture.prepare()
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1280f, 1400f), LocalDensity provides Density(density, fontScale)) {
                BoxLoreTheme(dynamicColor = false) { Box(Modifier.fillMaxSize()) { Box(Modifier.requiredSize(width.value.dp, 800.dp)) { content() } } }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        settle()
    }
}
