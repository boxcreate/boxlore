package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.CompletedDownloadItem
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.StableCompletedDownloadList
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeConnectedRailTest {
    @get:Rule val composeRule = createComposeRule()
    private data class Scenario(val count: Int = 3, val selected: String? = null, val width: Int = 353, val scale: Float = 1f, val rtl: Boolean = false, val empty: Boolean = false, val mode: HomeMixMode = HomeMixMode.DAILY, val progressed: Boolean = true, val distinctEpisodes: Boolean = false, val episodeCount: Int = 1, val parentScrollHost: Boolean = false)
    private val state = mutableStateOf(Scenario())
    private val parentScroll = ScrollState(0)
    private var infoClicks = 0
    private var plays = 0
    private var episodeClicks = 0
    private val episode = TestFixtures.episode(id = "episode", title = "An exceptionally long episode name with enough words to fill three lines and still need truncation", duration = 7500)
    private val podcasts = List(14) { index ->
        TestFixtures.podcast(id = "show-$index", title = "Show $index with a longer name", imageUrl = "")
            .copy(latestEpisode = episode.copy(id = "ep-$index"), episodeStatus = EpisodeStatus.IN_PROGRESS, resumeProgress = 0.4f)
    }

    @Test fun `selection and clear keep episodes connected while preserving callbacks`() {
        render()
        composeRule.onNodeWithTag("home_show_selector_show-0").performClick()
        composeRule.onNodeWithTag("home_show_selector_show-0").assertIsSelected()
        val surface = bounds("home_your_shows_surface")
        val selectors = bounds("home_show_selectors")
        val header = bounds("home_selected_show_header")
        val episodes = bounds("home_filtered_episodes")
        assertTrue(surface.contains(selectors.topLeft) && surface.contains(episodes.bottomRight))
        val divider = bounds("home_your_shows_divider")
        assertEquals(selectors.bottom, divider.top, 1f)
        assertEquals(divider.bottom + HomeMixLayout.TopPadding.value, header.top, 1f)
        composeRule.onNodeWithTag("home_selected_show_info").performClick()
        assertEquals(1, infoClicks)
        composeRule.onNodeWithText("Latest episodes").assertDoesNotExist()
        composeRule.onNodeWithTag("home_selected_show_title").performClick()
        assertEquals("The show name also opens the show", 2, infoClicks)
        composeRule.onNodeWithTag("home_mix_play_episode", useUnmergedTree = true).performTouchInput { click() }
        assertEquals(1, plays)
        assertEquals(0, episodeClicks)
        composeRule.onNodeWithText(episode.title, useUnmergedTree = true).performClick()
        assertEquals(1, episodeClicks)
        composeRule.onNodeWithTag("home_selected_show_clear").performClick()
        composeRule.onNodeWithTag("home_show_selector_mix").assertIsSelected()
        composeRule.onNodeWithTag("home_selected_show_header").assertDoesNotExist()
        composeRule.onNodeWithTag("home_mix_episode_ep-0").assertExists()
    }

    @Test fun `one subscription is selected automatically without a redundant Mix or clear button`() {
        state.value = Scenario(count = 1)
        render()
        composeRule.onNodeWithTag("home_show_selector_show-0").assertIsSelected()
        composeRule.onNodeWithTag("home_show_selector_mix").assertDoesNotExist()
        composeRule.onNodeWithTag("home_selected_show_clear").assertDoesNotExist()
        composeRule.onNodeWithTag("home_filtered_episodes").assertExists()
    }

    @Test fun `Mix and show artwork share square geometry across selector layouts`() {
        render()
        for (count in listOf(3, 8, 14)) {
            for (rtl in listOf(false, true)) {
                state.value = Scenario(count = count, width = 288, scale = 2f, rtl = rtl)
                composeRule.waitForIdle()
                val mix = bounds("home_mix_selector_visual")
                val cover = bounds("home_show_artwork_show-0")
                assertEquals(mix.width, mix.height, 1f)
                assertEquals(cover.width, cover.height, 1f)
                assertEquals("Cover size matches Mix at count=$count RTL=$rtl", mix.width, cover.width, 1f)
                assertEquals(mix.center.y, cover.center.y, 1f)
                assertTrue(mix.width <= YourShowsLayout.CoverSize.value)
            }
        }
    }

    @Test fun `switching shows gives incoming list its own scroll state during crossfade`() {
        state.value = Scenario(selected = "show-0", episodeCount = 8, distinctEpisodes = true, parentScrollHost = true)
        render()
        composeRule.onNodeWithTag("home_filtered_episodes").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 180f) }
        composeRule.waitForIdle()
        val outgoingOffset = composeRule.onNodeWithTag("home_filtered_episodes").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Outgoing show must be scrolled for this regression", outgoingOffset > 0f)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("home_show_selector_show-1").performClick()
        applySelectionFrame()
        composeRule.mainClock.advanceTimeBy(64)
        val offsets = composeRule.onAllNodesWithTag("home_filtered_episodes").fetchSemanticsNodes().map {
            it.config[SemanticsProperties.VerticalScrollAxisRange].value()
        }.sorted()
        assertEquals("Each concurrently visible show owns its scroll offset", listOf(0f, outgoingOffset), offsets)
        assertEquals("Switching shows must not scroll Home", 0, parentScroll.value)
        composeRule.mainClock.advanceTimeBy(1200)
        assertEquals(0f, composeRule.onNodeWithTag("home_filtered_episodes").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.1f)
    }

    @Test fun `outgoing show retains its episode during a crossfade into another show`() {
        state.value = Scenario(selected = "show-0", distinctEpisodes = true)
        render()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("home_show_selector_show-1").performClick()
        applySelectionFrame()
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.onNodeWithText("show-0 episode", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("show-1 episode", useUnmergedTree = true).assertExists()
        composeRule.mainClock.advanceTimeBy(1200)
        composeRule.onNodeWithText("show-0 episode", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("show-1 episode", useUnmergedTree = true).assertExists()
        assertEquals(0, plays)
        assertEquals(0, episodeClicks)
    }

    @Test fun `reversing selection keeps the surface continuous and selectors anchored`() {
        state.value = Scenario(episodeCount = 8)
        render()
        composeRule.waitForIdle()
        val selectors = bounds("home_show_selectors")
        val initial = bounds("home_your_shows_surface")
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("home_show_selector_show-0").performClick()
        applySelectionFrame()
        composeRule.mainClock.advanceTimeBy(96)
        val during = bounds("home_your_shows_surface")
        assertTrue("Surface should grow continuously into the episode list", during.height > initial.height)
        composeRule.onNodeWithTag("home_show_selector_mix").performClick()
        applySelectionFrame()
        composeRule.mainClock.advanceTimeBy(16)
        val reversed = bounds("home_your_shows_surface")
        assertTrue("Height must not snap when a switch reverses", kotlin.math.abs(during.height - reversed.height) < 64f)
        assertEquals(initial.top, reversed.top, 1f)
        assertEquals(initial.width, reversed.width, 1f)
        assertEquals(selectors, bounds("home_show_selectors"))
        composeRule.mainClock.advanceTimeBy(1600)
        assertEquals(initial.height, bounds("home_your_shows_surface").height, 1f)
        composeRule.onNodeWithTag("home_show_selector_mix").assertIsSelected()
        composeRule.onNodeWithTag("home_filtered_episodes").assertDoesNotExist()
    }

    @Test fun `five to nine shows fit two rows and preserve pinned first position in scrolling grid`() {
        render()
        for (count in listOf(5, 8, 9, 14)) {
            for (rtl in listOf(false, true)) {
                state.value = Scenario(count = count, rtl = rtl, width = 288, scale = 2f)
                composeRule.waitForIdle()
                val mix = bounds("home_show_selector_mix")
                val first = bounds("home_show_selector_show-0")
                assertEquals("First show belongs to the top row", mix.top, first.top, 1f)
                assertTrue(if (rtl) first.right <= mix.left else first.left >= mix.right)
                val second = bounds("home_show_selector_show-1")
                if (count > 9) assertTrue(second.top > first.bottom)
                val container = bounds("home_show_selectors")
                assertTrue(first.top >= container.top && first.bottom <= container.bottom)
                if (count <= 9) {
                    val last = bounds("home_show_selector_show-${count - 1}")
                    assertTrue(last.left >= container.left && last.right <= container.right)
                }
            }
        }
    }

    @Test fun `long titles and time stay within compact cards at narrow widths large text and RTL`() {
        render()
        for (scale in listOf(1f, 1.6f, 2f)) {
            for (rtl in listOf(false, true)) {
                state.value = Scenario(width = 288, scale = scale, rtl = rtl)
                composeRule.waitForIdle()
                val card = bounds("home_mix_episode_ep-0")
                val title = bounds("home_mix_title_ep-0")
                val time = bounds("home_mix_time_ep-0")
                val bar = bounds("home_mix_progress_ep-0")
                assertTrue(title.top >= card.top && time.bottom <= card.bottom)
                assertTrue(title.bottom <= time.top)
                assertTrue(title.left >= card.left && title.right <= card.right)
                assertTrue(bar.width > 10 && bar.center.y >= time.top && bar.center.y <= time.bottom)
                assertEquals("Long mix titles stop at two lines", 2, textLayout("home_mix_title_ep-0").lineCount)
                assertEquals(1, textLayout("home_mix_time_label_ep-0").lineCount)
                assertTrue("Time clipped at scale $scale RTL=$rtl: ${textLayout("home_mix_time_label_ep-0")}", !textLayout("home_mix_time_label_ep-0").hasVisualOverflow && !textLayout("home_mix_time_label_ep-0").isLineEllipsized(0))
            }
        }
    }

    @Test fun `caught up mode keeps selector and heading without empty rail or play button`() {
        state.value = Scenario(empty = true)
        render()
        composeRule.onNodeWithTag("home_show_selector_mix").assertExists()
        composeRule.onNodeWithTag("home_mix_episode_ep-0").assertDoesNotExist()
        composeRule.onNodeWithText("Play").assertDoesNotExist()
        composeRule.onNodeWithText("You’re all caught up").assertExists()
    }

    @Test fun `caught up collapse animates the same surface without moving selectors`() {
        render()
        composeRule.waitForIdle()
        val initial = bounds("home_your_shows_surface")
        val selectors = bounds("home_show_selectors")
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { state.value = state.value.copy(empty = true) }
        applySelectionFrame()
        composeRule.mainClock.advanceTimeBy(64)
        val during = bounds("home_your_shows_surface")
        composeRule.mainClock.advanceTimeBy(1200)
        val collapsed = bounds("home_your_shows_surface")
        assertTrue("Collapse must pass through an intermediate height", during.height < initial.height && during.height > collapsed.height)
        assertEquals(selectors, bounds("home_show_selectors"))
        composeRule.onNodeWithText("You’re all caught up").assertExists()
        composeRule.onNodeWithTag("home_mix_episode_ep-0").assertDoesNotExist()
    }

    @Test fun `offline mix keeps download access and independent playback actions`() {
        state.value = Scenario(mode = HomeMixMode.OFFLINE)
        render()
        composeRule.onNodeWithTag("home_mix_episode_episode").assertExists()
        composeRule.onNodeWithTag("home_mix_episode_ep-0").assertDoesNotExist()
        composeRule.onNodeWithTag("home_mix_play_episode", useUnmergedTree = true).performTouchInput { click() }
        assertEquals(1, plays)
        assertEquals(0, episodeClicks)
        composeRule.onNodeWithText("All downloads").assertExists()
    }

    @Test fun `unplayed episodes do not reserve a progress row`() {
        state.value = Scenario(progressed = false)
        render()
        composeRule.onNodeWithTag("home_mix_progress_ep-0", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("2h 5m", textLayout("home_mix_time_label_ep-0").layoutInput.text.text)
        val card = bounds("home_mix_episode_ep-0")
        val title = bounds("home_mix_title_ep-0")
        val time = bounds("home_mix_time_ep-0")
        assertTrue(title.top >= card.top && time.bottom <= card.bottom)
    }

    @Test fun `episode list drag and fling stay inside the list then a fresh edge swipe scrolls Home`() {
        state.value = Scenario(selected = "show-0", episodeCount = 8, parentScrollHost = true)
        render()
        composeRule.onNodeWithTag("home_filtered_episodes").performTouchInput { swipeUp(durationMillis = 300) }
        composeRule.waitForIdle()
        assertEquals("Home must stay still while the episode list owns the gesture", 0, parentScroll.value)
        composeRule.onNodeWithTag("home_filtered_episodes").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_filtered_episodes").performTouchInput { swipeUp(durationMillis = 300) }
        composeRule.waitForIdle()
        assertTrue("A new swipe from the list's end should scroll Home", parentScroll.value > 0)
    }

    @Test fun `slightly smaller artwork keeps the full playback hit area`() {
        render()
        val mixArt = bounds("home_mix_artwork_ep-0")
        assertEquals(86f, mixArt.width, 1f)
        assertEquals(mixArt.width, mixArt.height, 1f)
        assertEquals(48f, bounds("home_mix_play_ep-0").width, 1f)
        composeRule.onNodeWithTag("home_show_selector_show-0").performClick()
        assertEquals(48f, bounds("home_mix_artwork_episode").width, 1f)
        // Rows draw a 40dp button; Material's minimum touch target keeps the hit area 48dp.
        val rowPlay = composeRule.onNodeWithTag("home_mix_play_episode", useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
        assertEquals(48f, rowPlay.width, 1f)
        assertEquals(48f, rowPlay.height, 1f)
    }

    private fun render() {
        composeRule.setContent {
            val scenario = state.value
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, scenario.scale),
                LocalLayoutDirection provides if (scenario.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(scenario.width.dp).height(if (scenario.parentScrollHost) 700.dp else 1300.dp)) {
                        Column(if (scenario.parentScrollHost) Modifier.verticalScroll(parentScroll) else Modifier) {
                            YourShowsSection(
                                subscribedPodcasts = StablePodcastList(podcasts.take(scenario.count)),
                                latestEpisodes = StablePodcastList(
                                    if (scenario.empty) {
                                    emptyList()
                                } else {
                                    podcasts.take(scenario.count).map {
                                    if (scenario.progressed) it else it.copy(episodeStatus = EpisodeStatus.UNPLAYED, resumeProgress = 0f)
                                }
                                }
                                ),
                                selectedPodcastId = scenario.selected,
                                selectedPodcastEpisodes = StableEpisodeList(
                                    List(scenario.episodeCount) { index ->
                                    episode.copy(id = if (index == 0) episode.id else "selected-$index", title = if (scenario.distinctEpisodes) "${scenario.selected} episode" else episode.title)
                                }
                                ),
                                isSelectedPodcastLoading = false,
                                isSelectedRssRefreshing = false,
                                episodePlaybackState = StablePlaybackStateMap(emptyMap()),
                                onPodcastSelected = { state.value = state.value.copy(selected = it) },
                                onPodcastClick = { infoClicks++ },
                                onEpisodeClick = { _, _, _ -> episodeClicks++ },
                                onPlayMix = {},
                                onMixModeChanged = {},
                                selectedMixMode = scenario.mode,
                                completedDownloads = StableCompletedDownloadList(
                                    if (scenario.mode == HomeMixMode.OFFLINE) listOf(CompletedDownloadItem(episode, podcasts.first(), 1L)) else emptyList(),
                                ),
                                onPlayEpisode = { _, _, _ -> plays++ },
                                onViewLibrary = {},
                                onViewDownloads = {},
                            )
                            if (scenario.parentScrollHost) Spacer(Modifier.height(1200.dp))
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun applySelectionFrame() {
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }
    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
