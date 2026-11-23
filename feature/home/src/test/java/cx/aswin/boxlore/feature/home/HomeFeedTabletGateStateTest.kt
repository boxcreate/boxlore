package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.CompletedDownloadItem
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.components.DailyBriefingCardState
import cx.aswin.boxlore.feature.home.components.LocalHeroCardStyle
import cx.aswin.boxlore.feature.home.components.homeHeroStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's grid swaps its items when a window crosses the tablet size (600dp wide and 480dp tall) and
 * when the Brief appears beside Your shows on a tablet. The Daily Mix rail used to jump back to its
 * start, a selected show's list to its top, the mix menu closed and the Brief's "hide for today?"
 * question vanished. They now stay where the listener left them, in both directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1400dp-h1400dp-mdpi")
class HomeFeedTabletGateStateTest {
    @get:Rule val composeRule = createComposeRule()

    private val podcasts = List(8) { index ->
        TestFixtures.podcast(id = "${index + 1}", title = "Show ${index + 1}", imageUrl = "")
            .copy(latestEpisode = TestFixtures.episode(id = "daily-$index", title = "Daily $index", podcastId = "${index + 1}"))
    }
    private val brief = Briefing("2026-11-20", "us", "The world this morning", "", "", "")
    private val window = mutableStateOf(BoxloreTestWindows.W599_H900)
    private val style = mutableStateOf(HeroCardStyle.COVER)
    private val uiState = mutableStateOf(state())
    private val sections = HomeSectionsUiState.initial()


    @Test
    fun `the Daily Mix rail and its open menu keep their place across the tablet gates`() {
        render()
        composeRule.onNodeWithTag(GRID).performScrollToKey("your_shows")
        composeRule.runOnIdle { runBlocking { sections.dailyRail.scrollToItem(3) } }
        click("home_mix_mode_trigger")
        assertMenuAndRail("start")
        val steps = listOf(
            BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900,
            BoxloreTestWindows.GATE_600x480, BoxloreTestWindows.GATE_599x479, BoxloreTestWindows.GATE_600x480,
            BoxloreTestWindows.W599_H900,
        )
        for (layout in steps) {
            window.value = layout
            composeRule.waitForIdle()
            assertMenuAndRail("${layout.widthDp}x${layout.heightDp}")
        }
    }

    @Test
    fun `the Brief keeps its hide question when it moves beside Your shows and back`() {
        uiState.value = state(briefing = brief)
        render()
        composeRule.onNodeWithTag(GRID).performScrollToKey("briefing")
        click("home_brief_hide")
        assertEquals(DailyBriefingCardState.CONFIRM_DISMISS, sections.briefStep(HomeSectionsUiState.briefKey(brief.region, brief.date)))
        for (layout in listOf(BoxloreTestWindows.W600_H900, BoxloreTestWindows.TABLET, BoxloreTestWindows.W599_H900)) {
            window.value = layout
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(GRID).performScrollToKey(if (layout.usesTabletLayout) "your_shows" else "briefing")
            assertTrue("${layout.widthDp}x${layout.heightDp}: the hide question is gone", exists("home_brief_hide_today"))
        }
        // On a tablet the Brief leaves and comes back (a new edition loading): the same edition keeps its step.
        window.value = BoxloreTestWindows.TABLET
        uiState.value = state(briefing = null)
        composeRule.waitForIdle()
        uiState.value = state(briefing = brief)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(GRID).performScrollToKey("your_shows")
        assertTrue("the hide question is gone after the Brief came back", exists("home_brief_hide_today"))
    }

    @Test
    fun `a selected show's list keeps its scroll across the tablet gates`() {
        uiState.value = state(selected = podcasts[1].id)
        render()
        composeRule.onNodeWithTag(GRID).performScrollToKey("your_shows")
        composeRule.onNodeWithTag("home_filtered_episodes").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 120f) }
        composeRule.waitForIdle()
        val scrolled = sections.showListScroll(podcasts[1].id).value
        assertTrue("the list scrolled", scrolled > 0)
        for (layout in listOf(BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900, BoxloreTestWindows.GATE_600x480, BoxloreTestWindows.GATE_599x479)) {
            window.value = layout
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(GRID).performScrollToKey("your_shows")
            assertEquals("${layout.widthDp}x${layout.heightDp}", scrolled, sections.showListScroll(podcasts[1].id).value)
            val shown = composeRule.onNodeWithTag("home_filtered_episodes").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals("${layout.widthDp}x${layout.heightDp}: the list on screen", scrolled.toFloat(), shown, 0.5f)
        }
    }

    /**
     * With Marquee (the style the issue-1254 reporter uses), unfolding switches Home to the tablet
     * look. That used to rebuild all of Home: the hero went back to its first card and Your shows reset.
     */
    @Test
    fun `Marquee users keep their hero card and Your shows when folding and unfolding`() {
        style.value = HeroCardStyle.MARQUEE
        window.value = BoxloreWindowLayout(412f, 900f)
        uiState.value = state().copy(heroItems = podcasts.take(4).map { SmartHeroItem(HeroType.RESUME, it, "Continue listening") })
        render()
        composeRule.onNodeWithTag("home_hero_marquee").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_hero_marquee").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals("Marquee's card after two swipes", 2, sections.heroIndex.intValue)
        composeRule.onNodeWithTag(GRID).performScrollToKey("your_shows")
        composeRule.runOnIdle { runBlocking { sections.dailyRail.scrollToItem(3) } }
        click("home_mix_mode_trigger")
        for (layout in listOf(BoxloreTestWindows.PIXEL_FOLD, BoxloreWindowLayout(412f, 900f), BoxloreTestWindows.PIXEL_FOLD, BoxloreWindowLayout(412f, 900f))) {
            window.value = layout
            composeRule.waitForIdle()
            val label = "${layout.widthDp}x${layout.heightDp}"
            assertEquals("$label: hero card", 2, sections.heroIndex.intValue)
            assertMenuAndRail(label)
            composeRule.onNodeWithTag(GRID).performScrollToKey("hero")
            composeRule.waitForIdle()
            val hero = composeRule.onNodeWithTag(GRID).fetchSemanticsNode().boundsInRoot
            val shown = composeRule.onAllNodesWithText("Daily 2", useUnmergedTree = true).fetchSemanticsNodes().any { it.boundsInRoot.left >= hero.left - 0.5f && it.boundsInRoot.right <= hero.right + 0.5f }
            assertTrue("$label: the third card isn't on show", shown)
            composeRule.onNodeWithTag(GRID).performScrollToKey("your_shows")
            composeRule.waitForIdle()
        }
    }

    private fun assertMenuAndRail(label: String) {
        assertTrue("$label: the mix menu closed", exists("home_mix_menu_offline"))
        assertEquals("$label: the Daily Mix rail moved", 3, sections.dailyRail.firstVisibleItemIndex)
        assertTrue("$label: the rail's fourth card isn't shown", exists("home_mix_episode_daily-3"))
    }

    private fun exists(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun click(tag: String) {
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun render() {
        composeRule.setContent {
            val layout: BoxloreWindowLayout = window.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides layout,
                LocalHomeSectionsUiState provides sections,
                LocalHeroCardStyle provides homeHeroStyle(style.value, layout.usesTabletLayout),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(layout.widthDp.dp, layout.heightDp.dp)) {
                        HomeScreen(
                            uiState = uiState.value,
                            playback = HomePlaybackUi(
                                null,
                                null,
                                false,
                                completedDownloads = StableCompletedDownloadList(listOf(CompletedDownloadItem(podcasts[0].latestEpisode!!, podcasts[0], 1L))),
                            ),
                            sheets = HomeSheetUi(),
                            callbacks = HomeScreenCallbacks(feed = feedCallbacks()),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun state(briefing: Briefing? = null, selected: String? = null) = HomeUiState(
        heroItems = emptyList(),
        discoverPodcasts = emptyList(),
        subscribedPodcasts = podcasts,
        latestEpisodes = podcasts,
        selectedPodcastId = selected,
        selectedPodcastEpisodes = if (selected == null) emptyList() else List(8) { TestFixtures.episode(id = "s$it", title = "Episode $it", podcastId = selected) },
        briefing = briefing,
        briefingChapters = List(5) { Chapter(startTime = it * 60.0, title = "Story $it") },
        isRecommendationsLoading = false,
        isEditorialRowsLoading = false,
    )

    private fun feedCallbacks() = HomeFeedCallbacks(
        onPodcastClick = { _, _, _, _ -> }, onHeroArrowClick = { _, _ -> }, onEpisodeClick = { _: Episode, _: Podcast, _: String? -> },
        onPlayClick = null, onNavigateToLibrary = null, onNavigateToDownloads = null, onNavigateToExplore = null,
        onToggleSubscription = {}, onTogglePlayback = {}, onSelectCategory = {}, onPodcastSelected = {},
        onPlayMix = {}, onMixModeChanged = {}, onPlayEpisode = { _, _, _ -> }, onImportClick = {},
        onAiOnboardingClick = {}, onDismissImportBanner = {}, onBriefingClick = {}, onDismissBriefing = {},
        onDismissBriefingForever = {}, onFeedbackClick = {},
    )

    private companion object {
        const val GRID = "home_feed_grid"
    }
}
