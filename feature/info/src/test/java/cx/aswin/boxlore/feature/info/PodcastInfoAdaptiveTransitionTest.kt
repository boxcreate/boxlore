package cx.aswin.boxlore.feature.info

import android.app.Application
import android.view.MotionEvent
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * Drives one live show page across the shared window gates (600dp width, 480dp height, 840dp
 * landscape split, portrait/landscape and a fold posture change) in both directions, then
 * recreates it with saved state the way a fold density change or process death does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PodcastInfoAdaptiveTransitionTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))

    private val window = mutableStateOf(COMPACT)
    private val podcast =
        TestFixtures.podcast(id = "1", title = "Curious world", imageUrl = PhoneGoldenRule.ARTWORK_URL)
            .copy(description = "Stories about the world around us.")
    private val episodes = List(60) { index ->
        TestFixtures.episode(
            id = "e$index",
            title = "Episode $index",
            podcastId = "1",
            podcastTitle = podcast.title,
            publishedDate = 1_700_000_000L - index * 86_400L,
        )
    }
    private val firstPage = episodes.take(20)
    private val uiState = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, firstPage, isSubscribed = true))
    private val hideCompleted = MutableStateFlow(false)
    private val completed = MutableStateFlow(emptySet<String>())
    private val model = mock(PodcastInfoViewModel::class.java)
    private lateinit var restoration: StateRestorationTester
    private val played = mutableListOf<List<String>>()

    @Test
    fun `search query and results, scroll, sort, hide completed and refresh survive window changes and recreation`() {
        hideCompleted.value = true
        completed.value = setOf("e12")
        uiState.value = success(isRssRefreshing = true)
        render()

        fixture.compose.onNodeWithContentDescription("Search episodes").performClick()
        searchField().performTextInput("Episode 1")
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 8)
        val anchor = "Episode 17"
        fixture.compose.onNodeWithText(anchor).assertIsDisplayed()

        acrossWindowChanges {
            searchField().assert(hasText("Episode 1"))
            fixture.compose.onNodeWithText(anchor).assertIsDisplayed()
        }
        recreate(FOLD_TABLETOP)
        // Still the result list at the same row: Episode 8 would sit here if search had closed.
        fixture.compose.onNodeWithText(anchor).assertIsDisplayed()
        fixture.compose.onNodeWithText("Episode 8").assertDoesNotExist()
        acrossWindowChanges {
            fixture.compose.onNodeWithText(anchor).assertIsDisplayed()
            fixture.compose.onNodeWithText("Episode 8").assertDoesNotExist()
        }
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(0)
        searchField().assert(hasText("Episode 1"))

        // Search ignores hide-completed; closing it returns to the filtered list with the same sort.
        fixture.compose.onNodeWithContentDescription("Close episode search").performClick()
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(0)
        fixture.compose.onNodeWithContentDescription("Sort episodes").performClick()
        // Rows: hero, toolbar, then e0..e11 and e13 onwards because e12 is completed and hidden.
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 11)
        acrossWindowChanges {
            fixture.compose.onNodeWithText("Episode 11").assertIsDisplayed()
            fixture.compose.onNodeWithText("Episode 13").assertExists()
            assertEquals(0, fixture.compose.onAllNodesWithTextCount("Episode 12"))
        }
        assertEquals(EpisodeSort.OLDEST, (uiState.value as PodcastInfoUiState.Success).currentSort)
        verify(model, times(1)).toggleSort()
        verify(model, never()).toggleHideCompleted()
        verify(model, never()).refreshRssFeed()
        // One load for the first composition and one for the recreated page; window changes never reload.
        verify(model, times(2)).loadPodcast("1")
    }

    @Test
    fun `selection survives window changes and recreation and acts once on the same episodes`() {
        render()
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 3)
        fixture.compose.onNodeWithText("Episode 3").performTouchInput { longClick() }
        fixture.compose.onNodeWithText("Episode 5").performClick()
        selectionCount("2")

        acrossWindowChanges { selectionCount("2") }
        recreate(FOLD_FLAT)
        selectionCount("2")
        acrossWindowChanges { selectionCount("2") }

        fixture.compose.onNodeWithContentDescription("Play selected episodes").performClick()
        assertEquals(listOf(listOf("e3", "e5")), playedIds())
        fixture.compose.onNodeWithContentDescription("Play selected episodes").assertDoesNotExist()
    }

    @Test
    fun `a full range selection is rebuilt from its ids after the page is recreated`() {
        val window = PodcastEpisodeSelectionWindow(episodes.take(50), newestFirst = true, isTruncated = false)
        runBlocking { `when`(model.loadEpisodeSelectionWindow()).thenReturn(window) }
        render()
        fixture.compose.onNodeWithText("Episode 0").performTouchInput { longClick() }
        fixture.compose.onNodeWithContentDescription("More selection options").performClick()
        fixture.compose.onNodeWithText("Select all episodes").performClick()
        selectionCount("50")

        // Process death: the page model starts over with only its first page loaded.
        uiState.value = PodcastInfoUiState.Loading
        recreate(COMPACT)
        uiState.value = success()
        fixture.compose.waitForIdle()
        selectionCount("50")
        acrossWindowChanges { selectionCount("50") }

        fixture.compose.onNodeWithContentDescription("Play selected episodes").performClick()
        assertEquals(listOf(episodes.take(50).map(Episode::id)), playedIds())
    }

    @Test
    fun `selected ids the page can no longer find are dropped after recreation`() {
        val window = PodcastEpisodeSelectionWindow(episodes.take(50), newestFirst = true, isTruncated = false)
        runBlocking { `when`(model.loadEpisodeSelectionWindow()).thenReturn(window) }
        render()
        fixture.compose.onNodeWithText("Episode 0").performTouchInput { longClick() }
        fixture.compose.onNodeWithContentDescription("More selection options").performClick()
        fixture.compose.onNodeWithText("Select all episodes").performClick()
        selectionCount("50")

        val shrunk = PodcastEpisodeSelectionWindow(episodes.take(30), newestFirst = true, isTruncated = false)
        runBlocking { `when`(model.loadEpisodeSelectionWindow()).thenReturn(shrunk) }
        recreate(COMPACT)
        selectionCount("30")
        fixture.compose.onNodeWithContentDescription("Play selected episodes").performClick()
        assertEquals(listOf(episodes.take(30).map(Episode::id)), playedIds())
    }

    @Test
    fun `mark all played dialog stays open and confirms once`() {
        render()
        openMenuItem("Mark all as played")
        acrossWindowChanges { fixture.compose.onNodeWithText("Mark all as played?").assertExists() }
        recreate(FOLD_TABLETOP)
        acrossWindowChanges { fixture.compose.onNodeWithText("Mark all as played?").assertExists() }

        fixture.compose.onNodeWithText("Confirm").performClick()
        fixture.compose.onNodeWithText("Mark all as played?").assertDoesNotExist()
        verify(model, times(1)).markAllAsCompleted()
    }

    @Test
    fun `mark all unplayed dialog stays open and Back dismisses it without acting`() {
        render()
        openMenuItem("Mark all as unplayed")
        acrossWindowChanges { fixture.compose.onNodeWithText("Mark all as unplayed?").assertExists() }
        recreate(TABLET)
        fixture.compose.onNodeWithText("Mark all as unplayed?").assertExists()

        pressBackOnDialog()
        fixture.compose.onNodeWithText("Mark all as unplayed?").assertDoesNotExist()
        acrossWindowChanges { fixture.compose.onNodeWithText("Mark all as unplayed?").assertDoesNotExist() }
        verify(model, never()).markAllAsUncompleted()
    }

    @Test
    fun `show playback sheet stays open across its host change and closes on an outside tap`() {
        render()
        openMenuItem("Playback for this show")
        acrossWindowChanges { fixture.compose.onNodeWithText("Use app defaults").assertExists() }
        recreate(TABLET)
        fixture.compose.onNodeWithText("Use app defaults").assertExists()

        fixture.compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Use app defaults").assertDoesNotExist()
        acrossWindowChanges { fixture.compose.onNodeWithText("Use app defaults").assertDoesNotExist() }
    }

    @Test
    fun `genre sheet keeps its draft across window changes and recreation and saves once`() {
        `when`(model.folderNames).thenReturn(MutableStateFlow(emptyList()))
        render()
        openMenuItem("Change tag / genre")
        fixture.compose.onNode(isGenreField()).performTextReplacement("Night walks")
        acrossWindowChanges { fixture.compose.onNode(hasText("Night walks") and isGenreField()).assertExists() }
        recreate(FOLD_TABLETOP)
        acrossWindowChanges { fixture.compose.onNode(hasText("Night walks") and isGenreField()).assertExists() }

        fixture.compose.onNodeWithText("Save").performClick()
        verify(model, times(1)).updateCustomGenre("Night walks", null)
        acrossWindowChanges { fixture.compose.onNode(isGenreField()).assertDoesNotExist() }
    }

    @Test
    fun `missing episodes dialog stays open and confirms once`() {
        uiState.value = success(directFeedChip = DirectFeedChipState.Offer, isSubscribed = false)
        render()
        fixture.compose.onNodeWithText("Missing episodes?").performClick()
        acrossWindowChanges { fixture.compose.onNodeWithText("Find missing episodes?").assertExists() }
        recreate(FOLD_FLAT)
        acrossWindowChanges { fixture.compose.onNodeWithText("Find missing episodes?").assertExists() }

        fixture.compose.onNodeWithText("Allow feed checks").performClick()
        fixture.compose.onNodeWithText("Find missing episodes?").assertDoesNotExist()
        verify(model, times(1)).loadMissingEpisodes()
    }

    @Test
    fun `loading, error, retry and empty states hold across window changes`() {
        uiState.value = PodcastInfoUiState.Loading
        render()
        acrossWindowChanges { fixture.compose.onNodeWithText("Try again").assertDoesNotExist() }

        uiState.value = PodcastInfoUiState.Error
        acrossWindowChanges { fixture.compose.onNodeWithText("Couldn’t load this podcast").assertIsDisplayed() }
        recreate(FOLD_TABLETOP)
        fixture.compose.onNodeWithText("Couldn’t load this podcast").assertIsDisplayed()
        fixture.compose.onNodeWithText("Try again").performClick()
        verify(model, times(1)).retryLoad()

        uiState.value = PodcastInfoUiState.Success(podcast, emptyList(), isSubscribed = true, hasMoreEpisodes = false)
        acrossWindowChanges {
            assertEquals(true, fixture.compose.onAllNodesWithTextCount("Curious world") > 0)
            fixture.compose.onNodeWithText("Couldn’t load this podcast").assertDoesNotExist()
        }
    }

    private fun success(
        isRssRefreshing: Boolean = false,
        directFeedChip: DirectFeedChipState = DirectFeedChipState.Hidden,
        isSubscribed: Boolean = true,
    ) = PodcastInfoUiState.Success(
        podcast = podcast,
        episodes = firstPage,
        isSubscribed = isSubscribed,
        isRssRefreshing = isRssRefreshing,
        directFeedChip = directFeedChip,
    )

    private fun searchField(): SemanticsNodeInteraction = fixture.compose.onNodeWithContentDescription("Search episodes in Curious world")

    private fun isGenreField() = androidx.compose.ui.test.hasSetTextAction()

    private fun selectionCount(count: String) {
        fixture.compose.onNodeWithContentDescription("Play selected episodes").assertExists()
        fixture.compose.onNode(hasText(count) and androidx.compose.ui.test.hasAnySibling(androidx.compose.ui.test.hasContentDescription("Play selected episodes"))).assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun openMenuItem(label: String) {
        fixture.compose.onNodeWithContentDescription("More Options").performClick()
        fixture.compose.onNodeWithText(label).performClick()
        fixture.compose.waitForIdle()
    }

    private fun pressBackOnDialog() {
        fixture.compose.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        fixture.compose.waitForIdle()
    }

    @Suppress("unused")
    private fun tapOutsideDialog() {
        fixture.compose.runOnUiThread {
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_OUTSIDE, 0f, 0f, 0)
            ShadowDialog.getLatestDialog().onTouchEvent(event)
            event.recycle()
        }
        fixture.compose.waitForIdle()
    }

    private fun playedIds(): List<List<String>> = played.toList()

    /** Moves the same live page across every gate and back, asserting after each step. */
    private fun acrossWindowChanges(assertion: () -> Unit) {
        val start = window.value
        (TRANSITIONS + start).forEachIndexed { step, size ->
            fixture.compose.runOnIdle { window.value = size }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.waitForIdle()
            try {
                assertion()
            } catch (failure: AssertionError) {
                throw AssertionError("After window step $step ($size): ${failure.message}", failure)
            }
        }
    }

    /** Saves and restores the page's state while the window changes, as an activity recreation does. */
    private fun recreate(to: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = to }
        restoration.emulateSavedInstanceStateRestore()
        fixture.compose.waitForIdle()
    }

    private fun render() {
        stubModel()
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = window.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
            ) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        Surface {
                            PodcastInfoScreen("1", model, {}, { _, _, _ -> }, {}, {})
                        }
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    private fun stubModel() {
        `when`(model.uiState).thenReturn(uiState)
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(hideCompleted)
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(true))
        `when`(model.completedEpisodesState).thenReturn(completed)
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        doAnswer { invocation ->
            val query = invocation.getArgument<String>(0)
            uiState.update { state ->
                (state as? PodcastInfoUiState.Success)?.copy(
                    searchQuery = query,
                    searchResults = query.takeIf(String::isNotBlank)?.let { q -> state.episodes.filter { it.title.startsWith(q) } },
                ) ?: state
            }
            null
        }.`when`(model).searchEpisodes(anyString())
        doAnswer {
            uiState.update { state ->
                (state as? PodcastInfoUiState.Success)?.let {
                    it.copy(currentSort = if (it.currentSort == EpisodeSort.NEWEST) EpisodeSort.OLDEST else EpisodeSort.NEWEST)
                } ?: state
            }
            null
        }.`when`(model).toggleSort()
        doAnswer { invocation ->
            played += invocation.getArgument<List<Episode>>(0).map(Episode::id)
            null
        }.`when`(model).playEpisodes(anyList())
    }

    private companion object {
        const val LIST_TAG = "podcast_info_episode_list"
        const val RESULT_ROW = 2
        val COMPACT = BoxloreWindowLayout(599f, 800f)
        val TABLET = BoxloreWindowLayout(1_280f, 800f)
        val FOLD_FLAT = BoxloreWindowLayout(673f, 841f)
        val FOLD_TABLETOP = FOLD_FLAT.copy(
            folds = listOf(
                BoxloreFold(
                    BoxloreFoldState.HalfOpened,
                    BoxloreFoldOrientation.Horizontal,
                    BoxloreLayoutBounds(0f, 410f, 673f, 431f),
                    true,
                    true,
                ),
            ),
        )

        /** Each gate is crossed in both directions; the walk ends where it started. */
        val TRANSITIONS = listOf(
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(600f, 960f),
            BoxloreWindowLayout(960f, 600f),
            BoxloreWindowLayout(600f, 960f),
            FOLD_FLAT,
            FOLD_TABLETOP,
            FOLD_FLAT,
        )
    }
}
