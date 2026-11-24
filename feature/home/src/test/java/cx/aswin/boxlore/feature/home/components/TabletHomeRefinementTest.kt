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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
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
    private val window = mutableStateOf(BoxloreWindowLayout(1280f, 1400f))
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

    @Test fun `genre row reveals additional chips on wider panes, retains a late selection and keeps More open`() {
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
        val wide = sheetChips()
        assertTrue("The sheet lists the genres behind More", wide > 0)
        // A narrower pane puts more genres behind More; the open sheet follows and stays open, both ways.
        for (pane in listOf(700, 1200, 700)) {
            fixture.compose.runOnIdle { width.value = pane }
            settle()
            fixture.compose.onNodeWithText("Browse genres").assertIsDisplayed()
            assertTrue("At ${pane}dp the sheet lists ${sheetChips()} genres", sheetChips() > 0)
        }
        assertTrue("A narrower row has more behind More", sheetChips() >= wide)
        fixture.compose.onNodeWithText("Govt").assertIsSelected()
    }

    @Test fun `More genres stays open between phone and tablet windows and lists every genre on phones`() {
        val phone = BoxloreWindowLayout(412f, 915f)
        val fold = BoxloreWindowLayout(841f, 701f)
        window.value = phone
        width.value = 412
        render { GenreSelector(null, {}) }
        // The phone row scrolls, so More can sit past the edge: tap it as TalkBack would.
        fixture.compose.onNodeWithTag("home_genres_more").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals("Every genre and Top on a phone", 20, sheetChips())
        for ((layout, pane) in listOf(fold to 809, phone to 412, fold to 809, BoxloreWindowLayout(1000f, 800f) to 968, fold to 809)) {
            fixture.compose.runOnIdle {
                window.value = layout
                width.value = pane
            }
            settle()
            fixture.compose.onNodeWithText("Browse genres").assertIsDisplayed()
            assertTrue("${layout.widthDp}dp: the sheet is empty", sheetChips() > 0)
        }
    }

    @Test fun `genre chips keep a 48dp target on the row and pick a genre once`() {
        val selections = mutableListOf<String?>()
        window.value = BoxloreWindowLayout(412f, 915f)
        width.value = 412
        render { GenreSelector(null, { selections += it }) }
        fixture.compose.onAllNodes(hasTestTag("home_genre_chip"), useUnmergedTree = true).fetchSemanticsNodes().forEach { chip ->
            assertTrue("A genre chip is ${chip.size.height}dp tall", chip.size.height >= 48 - 1)
        }
        fixture.compose.onNodeWithText("News").performClick()
        assertEquals(listOf<String?>("News"), selections)
    }

    private fun sheetChips() = fixture.compose.onAllNodes(hasTestTag("home_genre_chip") and hasAnyAncestor(hasTestTag("home_genres_sheet")), useUnmergedTree = true)
        .fetchSemanticsNodes().size

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
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalDensity provides Density(density, fontScale)) {
                BoxLoreTheme(dynamicColor = false) { Box(Modifier.fillMaxSize()) { Box(Modifier.requiredSize(width.value.dp, 800.dp)) { content() } } }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        settle()
    }
}
