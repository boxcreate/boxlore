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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
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
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.anyBoolean
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
 * recreates it with saved state the way a fold density change, process death or a return from
 * the back stack does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Suppress("LargeClass") // One live page fixture drives every window gate; splitting it would copy the fixture.
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

    /** What the selection window lookup answers; it may suspend or throw. */
    private var selectionWindow: suspend (EpisodeSort?) -> PodcastEpisodeSelectionWindow = { newestWindow(50) }
    private val windowSorts = mutableListOf<EpisodeSort?>()

    @Test
    fun `search query and results, scroll, sort, hide completed and refresh survive window changes and recreation`() {
        hideCompleted.value = true
        completed.value = setOf("e12")
        uiState.value = success(isRssRefreshing = true)
        render()
        acrossWindowChanges { refreshIndicatorShown() }

        fixture.compose.onNodeWithContentDescription("Search episodes").performClick()
        searchField().performTextInput("Episode 1")
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 8)
        val anchor = "Episode 17"
        fixture.compose.onNodeWithText(anchor).assertIsDisplayed()

        acrossWindowChanges {
            searchField().assert(hasText("Episode 1"))
            fixture.compose.onNodeWithText(anchor).assertIsDisplayed()
            refreshIndicatorShown()
        }
        recreate(FOLD_TABLETOP)
        // Still the result list at the same row: Episode 8 would sit here if search had closed.
        fixture.compose.onNodeWithText(anchor).assertIsDisplayed()
        fixture.compose.onNodeWithText("Episode 8").assertDoesNotExist()
        refreshIndicatorShown()
        acrossWindowChanges {
            fixture.compose.onNodeWithText(anchor).assertIsDisplayed()
            fixture.compose.onNodeWithText("Episode 8").assertDoesNotExist()
            refreshIndicatorShown()
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
        // One load for the first composition and one for the recreated page; window changes never
        // reload. The ViewModel skips the second call for the same route (PodcastInfoLoadGuardTest).
        verify(model, times(2)).loadPodcast("1")
    }

    @Test
    fun `search query is run again after process death once the first page has loaded`() {
        render()
        fixture.compose.onNodeWithContentDescription("Search episodes").performClick()
        searchField().performTextInput("Episode 1")
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 1)
        fixture.compose.onNodeWithText("Episode 10").assertExists()

        // Process death: the new page model shows its local placeholder, then the first page, both
        // without a query.
        uiState.value = PodcastInfoUiState.Loading
        recreate(FOLD_FLAT)
        uiState.value = placeholder()
        fixture.compose.waitForIdle()
        verify(model, times(1)).searchEpisodes("Episode 1")
        uiState.value = success()
        fixture.compose.waitForIdle()

        verify(model, times(2)).searchEpisodes("Episode 1")
        // The result list, not the full list: Episode 8 is not a match. The scroll position is not
        // kept here, because the placeholder page had no rows to hold it.
        acrossWindowChanges {
            fixture.compose.onNodeWithText("Episode 8").assertDoesNotExist()
            assertEquals("Episode 1", (uiState.value as PodcastInfoUiState.Success).searchQuery)
        }
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 1)
        fixture.compose.onNodeWithText("Episode 10").assertExists()
        // A restored search is not focused, so the field is only composed near the top of the list.
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(0)
        searchField().assert(hasText("Episode 1"))

        // Closing search forgets the saved query, so a later recreation does not search again.
        fixture.compose.onNodeWithContentDescription("Close episode search").performClick()
        recreate(COMPACT)
        fixture.compose.onNodeWithContentDescription("Close episode search").assertDoesNotExist()
        verify(model, times(2)).searchEpisodes("Episode 1")
    }

    @Test
    fun `a return from the back stack keeps the selection, and opening an episode from search closes search first`() {
        render()
        fixture.compose.onNodeWithText("Episode 0").performTouchInput { longClick() }
        selectionCount("1")
        // Navigation disposes the page and restores it with the same saved state and page model.
        recreate(COMPACT)
        selectionCount("1")
        pressPageBack()
        fixture.compose.onNodeWithContentDescription("Play selected episodes").assertDoesNotExist()

        fixture.compose.onNodeWithContentDescription("Search episodes").performClick()
        searchField().performTextInput("Episode 1")
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 1)
        fixture.compose.onNodeWithText("Episode 10").performSemanticsAction(SemanticsActions.OnClick)
        verify(model, times(1)).recordEpisodeClick("e10")
        verify(model, times(1)).searchEpisodes("")
        recreate(COMPACT)
        fixture.compose.onNodeWithContentDescription("Close episode search").assertDoesNotExist()
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW)
        fixture.compose.onNodeWithText("Episode 0").assertExists()
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
        render()
        selectAll()
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
    fun `an oldest first selection waits for the reloaded page and is looked up in its own order`() {
        val oldestFirst = episodes.reversed()
        selectionWindow = { sort ->
            val ordered = if (sort == EpisodeSort.OLDEST) oldestFirst else episodes
            PodcastEpisodeSelectionWindow(ordered.take(50), newestFirst = sort != EpisodeSort.OLDEST, isTruncated = true)
        }
        uiState.value = success(episodes = oldestFirst.take(20), sort = EpisodeSort.OLDEST, isSubscribed = false)
        render()
        fixture.compose.onNodeWithText("Episode 59").performTouchInput { longClick() }
        fixture.compose.onNodeWithContentDescription("More selection options").performClick()
        fixture.compose.onNodeWithText("Select all episodes").performClick()
        selectionCount("50")
        assertEquals(listOf<EpisodeSort?>(EpisodeSort.OLDEST), windowSorts)

        // Process death on a show whose sort was not saved: the local placeholder (no episodes,
        // still loading, default newest sort) comes first, then the first page in newest order.
        uiState.value = PodcastInfoUiState.Loading
        recreate(COMPACT)
        uiState.value = placeholder()
        fixture.compose.waitForIdle()
        assertEquals("No lookup against the placeholder", 1, windowSorts.size)
        selectionCount("50")
        fixture.compose.onNodeWithContentDescription("Play selected episodes").assertIsNotEnabled()

        uiState.value = success(isSubscribed = false)
        fixture.compose.waitForIdle()
        assertEquals(listOf<EpisodeSort?>(EpisodeSort.OLDEST, EpisodeSort.OLDEST), windowSorts)
        selectionCount("50")
        fixture.compose.onNodeWithContentDescription("Play selected episodes").assertIsEnabled().performClick()
        assertEquals(listOf(oldestFirst.take(50).map(Episode::id)), playedIds())
    }

    @Test
    fun `actions wait while a restored selection is looked up, then act on all of it`() {
        render()
        selectAll()
        val lookup = CompletableDeferred<PodcastEpisodeSelectionWindow>()
        selectionWindow = { lookup.await() }
        recreate(TABLET)

        selectionCount("50")
        selectionActionsEnabled(false)
        // The range menu shows progress instead of its button while the lookup runs.
        fixture.compose.onNodeWithContentDescription("More selection options").assertDoesNotExist()
        assertEquals(true, fixture.compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isNotEmpty())

        lookup.complete(newestWindow(50))
        fixture.compose.waitForIdle()
        selectionCount("50")
        selectionActionsEnabled(true)
        fixture.compose.onNodeWithContentDescription("More selection options").assertExists()
        fixture.compose.onNodeWithContentDescription("Play selected episodes").performClick()
        assertEquals(listOf(episodes.take(50).map(Episode::id)), playedIds())
    }

    @Test
    fun `a failed lookup keeps every selected id and tries again when the page changes`() {
        render()
        selectAll()
        selectionWindow = { throw IOException("offline") }
        recreate(COMPACT)

        selectionCount("50")
        selectionActionsEnabled(false)
        fixture.compose.onNodeWithContentDescription("More selection options").assertExists()
        acrossWindowChanges { selectionCount("50") }
        assertEquals("One attempt per page state", 2, windowSorts.size)

        selectionWindow = { newestWindow(50) }
        uiState.update { (it as PodcastInfoUiState.Success).copy(hasMoreEpisodes = false) }
        fixture.compose.waitForIdle()
        assertEquals(3, windowSorts.size)
        selectionCount("50")
        selectionActionsEnabled(true)
        fixture.compose.onNodeWithContentDescription("Play selected episodes").performClick()
        assertEquals(listOf(episodes.take(50).map(Episode::id)), playedIds())
    }

    @Test
    fun `a lookup cut short when the page reloads runs again and resolves`() {
        render()
        selectAll()
        val stalled = CompletableDeferred<PodcastEpisodeSelectionWindow>()
        selectionWindow = { stalled.await() }
        recreate(COMPACT)
        selectionActionsEnabled(false)

        uiState.value = PodcastInfoUiState.Loading
        fixture.compose.waitForIdle()
        selectionWindow = { newestWindow(50) }
        uiState.value = success()
        fixture.compose.waitForIdle()
        // A late answer from the cancelled lookup changes nothing.
        stalled.complete(newestWindow(10))
        fixture.compose.waitForIdle()

        selectionCount("50")
        selectionActionsEnabled(true)
        fixture.compose.onNodeWithContentDescription("Play selected episodes").performClick()
        assertEquals(listOf(episodes.take(50).map(Episode::id)), playedIds())
    }

    @Test
    fun `clearing a selection while it is looked up keeps it clear`() {
        render()
        selectAll()
        val lookup = CompletableDeferred<PodcastEpisodeSelectionWindow>()
        selectionWindow = { lookup.await() }
        recreate(FOLD_TABLETOP)
        selectionCount("50")

        pressPageBack()
        lookup.complete(newestWindow(50))
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithContentDescription("Play selected episodes").assertDoesNotExist()
        acrossWindowChanges { fixture.compose.onNodeWithContentDescription("Play selected episodes").assertDoesNotExist() }
    }

    @Test
    fun `selected ids the page can no longer find are dropped after recreation`() {
        render()
        selectAll()
        selectionCount("50")

        selectionWindow = { newestWindow(30) }
        recreate(COMPACT)
        selectionCount("30")
        fixture.compose.onNodeWithContentDescription("Play selected episodes").performClick()
        assertEquals(listOf(episodes.take(30).map(Episode::id)), playedIds())
    }

    @Test
    fun `mark all played dialog keeps its content and runs each exit once on both hosts`() {
        render()
        var confirms = 0
        forEachHostAndExit(ALERT_EXITS) { host, exit ->
            openMenuItem("Mark all as played")
            acrossWindowChanges { fixture.compose.onNodeWithText("Mark all as played?").assertExists() }
            recreate(host)
            fixture.compose.onNodeWithText("Mark all as played?").assertExists()

            exitAlert(exit, confirm = "Confirm", cancel = "Cancel")
            if (exit == Exit.CONFIRM) confirms += 1
            fixture.compose.onNodeWithText("Mark all as played?").assertDoesNotExist()
            acrossWindowChanges { fixture.compose.onNodeWithText("Mark all as played?").assertDoesNotExist() }
            verify(model, times(confirms)).markAllAsCompleted()
        }
        verify(model, never()).markAllAsUncompleted()
    }

    @Test
    fun `mark all unplayed dialog keeps its content and runs each exit once on both hosts`() {
        render()
        var confirms = 0
        forEachHostAndExit(ALERT_EXITS) { host, exit ->
            openMenuItem("Mark all as unplayed")
            acrossWindowChanges { fixture.compose.onNodeWithText("Mark all as unplayed?").assertExists() }
            recreate(host)
            fixture.compose.onNodeWithText("Mark all as unplayed?").assertExists()

            exitAlert(exit, confirm = "Confirm", cancel = "Cancel")
            if (exit == Exit.CONFIRM) confirms += 1
            fixture.compose.onNodeWithText("Mark all as unplayed?").assertDoesNotExist()
            verify(model, times(confirms)).markAllAsUncompleted()
        }
        verify(model, never()).markAllAsCompleted()
    }

    @Test
    fun `missing episodes dialog keeps its content and runs each exit once on both hosts`() {
        uiState.value = success(directFeedChip = DirectFeedChipState.Offer, isSubscribed = false)
        render()
        var confirms = 0
        forEachHostAndExit(ALERT_EXITS) { host, exit ->
            fixture.compose.onNodeWithText("Missing episodes?").performClick()
            acrossWindowChanges { fixture.compose.onNodeWithText("Find missing episodes?").assertExists() }
            recreate(host)
            fixture.compose.onNodeWithText("Find missing episodes?").assertExists()

            exitAlert(exit, confirm = "Allow feed checks", cancel = "Not now")
            if (exit == Exit.CONFIRM) confirms += 1
            fixture.compose.onNodeWithText("Find missing episodes?").assertDoesNotExist()
            verify(model, times(confirms)).loadMissingEpisodes()
        }
    }

    @Test
    fun `a restored missing episodes dialog waits until the reloaded page offers the check again`() {
        uiState.value = success(directFeedChip = DirectFeedChipState.Offer, isSubscribed = false)
        render()
        fixture.compose.onNodeWithText("Missing episodes?").performClick()

        // Process death: the reloaded page starts with the chip hidden.
        uiState.value = PodcastInfoUiState.Loading
        recreate(FOLD_FLAT)
        uiState.value = success(isSubscribed = false)
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Find missing episodes?").assertDoesNotExist()

        uiState.update { (it as PodcastInfoUiState.Success).copy(directFeedChip = DirectFeedChipState.Offer) }
        acrossWindowChanges { fixture.compose.onNodeWithText("Find missing episodes?").assertExists() }
        fixture.compose.onNodeWithText("Allow feed checks").performClick()
        verify(model, times(1)).loadMissingEpisodes()

        // Once the check has run on its own, a restored dialog is dropped for good.
        fixture.compose.onNodeWithText("Missing episodes?").performClick()
        uiState.value = PodcastInfoUiState.Loading
        recreate(TABLET)
        uiState.value = success(directFeedChip = DirectFeedChipState.Updated, isSubscribed = false)
        fixture.compose.waitForIdle()
        uiState.update { (it as PodcastInfoUiState.Success).copy(directFeedChip = DirectFeedChipState.Offer) }
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Find missing episodes?").assertDoesNotExist()
        verify(model, times(1)).loadMissingEpisodes()
    }

    @Test
    fun `show playback sheet keeps its content across its host change and runs each exit once`() {
        render()
        var toggles = 0
        forEachHostAndExit(listOf(Exit.BACK, Exit.OUTSIDE)) { host, exit ->
            openMenuItem("Playback for this show")
            acrossWindowChanges { fixture.compose.onNodeWithText("Use app defaults").assertExists() }
            recreate(host)
            fixture.compose.onNodeWithText("Use app defaults").assertExists()

            fixture.compose.onNode(isToggleable() and hasParent(hasText("Use app defaults"))).performClick()
            toggles += 1
            verify(model, times(toggles)).setUseAppPlaybackDefaults(anyBoolean())
            exitSheet(exit, host)
            fixture.compose.onNodeWithText("Use app defaults").assertDoesNotExist()
            acrossWindowChanges { fixture.compose.onNodeWithText("Use app defaults").assertDoesNotExist() }
            verify(model, times(toggles)).setUseAppPlaybackDefaults(anyBoolean())
        }
    }

    @Test
    fun `genre sheet keeps its draft across window changes and recreation and runs each exit once`() {
        `when`(model.folderNames).thenReturn(MutableStateFlow(emptyList()))
        render()
        var saves = 0
        forEachHostAndExit(listOf(Exit.CONFIRM, Exit.CANCEL, Exit.BACK, Exit.OUTSIDE)) { host, exit ->
            openMenuItem("Change tag / genre")
            fixture.compose.onNode(isGenreField()).performTextReplacement("Night walks")
            acrossWindowChanges { fixture.compose.onNode(hasText("Night walks") and isGenreField()).assertExists() }
            recreate(host)
            fixture.compose.onNode(hasText("Night walks") and isGenreField()).assertExists()

            when (exit) {
                Exit.CONFIRM -> fixture.compose.onNodeWithText("Save").performClick()
                Exit.CANCEL -> fixture.compose.onNodeWithContentDescription("Close").performClick()
                else -> exitSheet(exit, host)
            }
            fixture.compose.waitForIdle()
            if (exit == Exit.CONFIRM) saves += 1
            acrossWindowChanges { fixture.compose.onNode(isGenreField()).assertDoesNotExist() }
            verify(model, times(saves)).updateCustomGenre("Night walks", null)
        }
    }

    @Test
    fun `the feed status line shows each state across window changes and recreation`() {
        uiState.value = success().copy(feedStatus = ShowFeedStatus.READING_NEWEST)
        render()
        val statuses =
            listOf(
                ShowFeedStatus.READING_NEWEST to "Checking the feed for new episodes…",
                ShowFeedStatus.COMPLETING_ARCHIVE to "Loading all episodes from the feed…",
                ShowFeedStatus.FALLBACK to "Can’t reach this show’s feed. Showing boxlore’s episode list instead.",
                ShowFeedStatus.OFFLINE to "You’re offline. Showing saved episodes.",
            )
        for ((status, text) in statuses) {
            uiState.update { (it as PodcastInfoUiState.Success).copy(feedStatus = status) }
            acrossWindowChanges {
                // On a short window the hero fills the screen: the line sits right below it.
                fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW)
                fixture.compose.onNodeWithText(text).assertIsDisplayed()
            }
        }
        recreate(FOLD_TABLETOP)
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW)
        fixture.compose.onNodeWithText("You’re offline. Showing saved episodes.").assertIsDisplayed()
        fixture.compose.onNodeWithTag(FEED_STATUS_TAG).assertIsDisplayed()

        // A current list and a recent stored copy show no line.
        for (quiet in listOf(ShowFeedStatus.NONE, ShowFeedStatus.CACHED)) {
            uiState.update { (it as PodcastInfoUiState.Success).copy(feedStatus = quiet) }
            acrossWindowChanges { fixture.compose.onNodeWithTag(FEED_STATUS_TAG).assertDoesNotExist() }
        }
        verify(model, never()).refreshRssFeed()
    }

    @Test
    fun `scroll and selection hold while the list grows and switches source during window changes`() {
        uiState.value = success().copy(feedStatus = ShowFeedStatus.READING_NEWEST)
        render()
        // Rows: hero, toolbar, the status line, then the episodes.
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 1 + 5)
        fixture.compose.onNodeWithText("Episode 5").performTouchInput { longClick() }
        selectionCount("1")
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(RESULT_ROW + 1 + 12)
        val anchor = "Episode 12"
        fixture.compose.onNodeWithText(anchor).assertIsDisplayed()

        val newer = List(3) { index ->
            TestFixtures.episode(id = "n$index", title = "New $index", podcastId = "1", podcastTitle = podcast.title, publishedDate = 1_700_000_000L + (index + 1) * 86_400L)
        }
        // Each window step lands one stage of a feed-first page: the stored copy grows with older
        // episodes, newer ones arrive above, the archive is read, and the source switches to the
        // server's list and back, every row keeping its ID.
        val stages: List<(PodcastInfoUiState.Success) -> PodcastInfoUiState.Success> =
            listOf(
                { it.copy(episodes = episodes.take(40), piEpisodes = episodes.take(40), feedStatus = ShowFeedStatus.NONE) },
                { it.copy(feedStatus = ShowFeedStatus.COMPLETING_ARCHIVE, isLoadingMore = true) },
                { it.copy(episodes = newer + episodes, piEpisodes = newer + episodes, feedStatus = ShowFeedStatus.NONE, isLoadingMore = false) },
                { it.copy(feedStatus = ShowFeedStatus.FALLBACK) },
                { it.copy(episodes = episodes, piEpisodes = episodes, feedStatus = ShowFeedStatus.READING_NEWEST) },
                { it.copy(feedStatus = ShowFeedStatus.OFFLINE) },
            )
        acrossWindowChanges(onStep = { step -> uiState.update { stages[step % stages.size](it as PodcastInfoUiState.Success) } }) {
            fixture.compose.onNodeWithText(anchor).assertIsDisplayed()
            selectionCount("1")
        }
        recreate(FOLD_TABLETOP)
        fixture.compose.onNodeWithText(anchor).assertIsDisplayed()
        selectionCount("1")

        fixture.compose.onNodeWithContentDescription("Play selected episodes").performClick()
        assertEquals(listOf(listOf("e5")), playedIds())
        verify(model, times(2)).loadPodcast("1")
        verify(model, never()).refreshRssFeed()
    }

    @Test
    fun `loading, error, retry and empty states hold across window changes and recreation`() {
        uiState.value = PodcastInfoUiState.Loading
        render()
        acrossWindowChanges {
            fixture.compose.onNodeWithTag(LOADING_TAG).assertIsDisplayed()
            fixture.compose.onNodeWithText("Try again").assertDoesNotExist()
        }
        recreate(TABLET)
        fixture.compose.onNodeWithTag(LOADING_TAG).assertIsDisplayed()

        uiState.value = PodcastInfoUiState.Error
        acrossWindowChanges {
            fixture.compose.onNodeWithText("Couldn’t load this podcast").assertIsDisplayed()
            fixture.compose.onNodeWithTag(LOADING_TAG).assertDoesNotExist()
        }
        recreate(FOLD_TABLETOP)
        fixture.compose.onNodeWithText("Couldn’t load this podcast").assertIsDisplayed()
        fixture.compose.onNodeWithText("Try again").performClick()
        verify(model, times(1)).retryLoad()

        uiState.value = PodcastInfoUiState.Success(podcast, emptyList(), isSubscribed = true, hasMoreEpisodes = false)
        val assertEmpty = {
            fixture.compose.onNodeWithTag(LIST_TAG).assertExists()
            assertEquals(true, fixture.compose.onAllNodesWithTextCount("Curious world") > 0)
            assertEquals(0, fixture.compose.onAllNodesWithTextCount("Episode 0"))
            assertEquals(0, fixture.compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().size)
            fixture.compose.onNodeWithTag(LOADING_TAG).assertDoesNotExist()
            fixture.compose.onNodeWithText("Couldn’t load this podcast").assertDoesNotExist()
        }
        acrossWindowChanges(assertEmpty)
        recreate(COMPACT)
        assertEmpty()
    }

    private fun success(
        isRssRefreshing: Boolean = false,
        directFeedChip: DirectFeedChipState = DirectFeedChipState.Hidden,
        isSubscribed: Boolean = true,
        episodes: List<Episode> = firstPage,
        sort: EpisodeSort = EpisodeSort.NEWEST,
    ) = PodcastInfoUiState.Success(
        podcast = podcast,
        episodes = episodes,
        isSubscribed = isSubscribed,
        isRssRefreshing = isRssRefreshing,
        currentSort = sort,
        directFeedChip = directFeedChip,
    )

    /** The local placeholder a new page model shows first: no episodes, still loading, default sort. */
    private fun placeholder() = PodcastInfoUiState.Success(podcast, emptyList(), isSubscribed = true, isLoadingMore = true)

    private fun newestWindow(size: Int) = PodcastEpisodeSelectionWindow(episodes.take(size), newestFirst = true, isTruncated = false)

    private fun searchField(): SemanticsNodeInteraction = fixture.compose.onNodeWithContentDescription("Search episodes in Curious world")

    private fun isGenreField() = androidx.compose.ui.test.hasSetTextAction()

    private fun selectAll() {
        fixture.compose.onNodeWithText("Episode 0").performTouchInput { longClick() }
        fixture.compose.onNodeWithContentDescription("More selection options").performClick()
        fixture.compose.onNodeWithText("Select all episodes").performClick()
        selectionCount("50")
    }

    private fun selectionCount(count: String) {
        fixture.compose.onNodeWithContentDescription("Play selected episodes").assertExists()
        fixture.compose.onNode(hasText(count) and androidx.compose.ui.test.hasAnySibling(androidx.compose.ui.test.hasContentDescription("Play selected episodes"))).assertExists()
    }

    /** Play, Download, Mark and Queue are all on, or all off while the selection is incomplete. */
    private fun selectionActionsEnabled(enabled: Boolean) {
        SELECTION_ACTIONS.forEach { label ->
            val button = fixture.compose.onNodeWithContentDescription(label)
            if (enabled) button.assertIsEnabled() else button.assertIsNotEnabled()
        }
    }

    private fun refreshIndicatorShown() {
        fixture.compose
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate) and hasAnyAncestor(hasTestTag(REFRESH_TAG)), useUnmergedTree = true)
            .assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun openMenuItem(label: String) {
        fixture.compose.onNodeWithContentDescription("More Options").performClick()
        fixture.compose.onNodeWithText(label).performClick()
        fixture.compose.waitForIdle()
    }

    private fun exitAlert(exit: Exit, confirm: String, cancel: String) {
        when (exit) {
            Exit.CONFIRM -> fixture.compose.onNodeWithText(confirm).performClick()
            Exit.CANCEL -> fixture.compose.onNodeWithText(cancel).performClick()
            Exit.BACK -> pressBackOnDialog()
            Exit.OUTSIDE -> tapOutsideDialog()
        }
        fixture.compose.waitForIdle()
    }

    /** Back or an outside tap on the sheet adapter: a bottom sheet on compact hosts, a centered dialog on tablets. */
    private fun exitSheet(exit: Exit, host: BoxloreWindowLayout) {
        when {
            exit == Exit.BACK -> pressBackOnDialog()
            host == TABLET -> fixture.compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
            // The bottom sheet's scrim is the area outside it; the sheet may cover the whole window.
            else -> fixture.compose.onNodeWithContentDescription("Close sheet").performSemanticsAction(SemanticsActions.OnClick)
        }
        fixture.compose.waitForIdle()
    }

    private fun pressBackOnDialog() {
        fixture.compose.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        fixture.compose.waitForIdle()
    }

    private fun pressPageBack() {
        fixture.compose.runOnUiThread { fixture.compose.activity.onBackPressedDispatcher.onBackPressed() }
        fixture.compose.waitForIdle()
    }

    /**
     * A press and release outside an alert dialog's window. A touch-modal dialog window receives
     * such touches with coordinates beyond its bounds, and Compose treats them as an outside tap.
     */
    private fun tapOutsideDialog() {
        fixture.compose.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(0L, 0L, action, -20f, -20f, 0)
                dialog.onTouchEvent(event)
                event.recycle()
            }
        }
        fixture.compose.waitForIdle()
    }

    private fun forEachHostAndExit(exits: List<Exit>, step: (BoxloreWindowLayout, Exit) -> Unit) {
        for (host in listOf(COMPACT, TABLET)) {
            for (exit in exits) {
                try {
                    step(host, exit)
                } catch (failure: AssertionError) {
                    throw AssertionError("On host $host with exit $exit: ${failure.message}", failure)
                }
            }
        }
    }

    private fun playedIds(): List<List<String>> = played.toList()

    /** Moves the same live page across every gate and back, asserting after each step. */
    private fun acrossWindowChanges(assertion: () -> Unit) = acrossWindowChanges(onStep = {}, assertion = assertion)

    /**
     * [acrossWindowChanges], with [onStep] changing the page's state in the same frame as each
     * window change, as a feed read that lands mid-resize does.
     */
    private fun acrossWindowChanges(onStep: (Int) -> Unit, assertion: () -> Unit) {
        val start = window.value
        (TRANSITIONS + start).forEachIndexed { step, size ->
            fixture.compose.runOnIdle {
                window.value = size
                onStep(step)
            }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.waitForIdle()
            try {
                assertion()
            } catch (failure: AssertionError) {
                throw AssertionError("After window step $step ($size): ${failure.message}", failure)
            }
        }
    }

    /**
     * Saves and restores the page's state while the window changes, as an activity recreation or a
     * return from the back stack does.
     */
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
        // Runs [selectionWindow] on the caller's coroutine, so it can suspend, throw or be cancelled.
        val stubbing = doAnswer { invocation ->
            val sort = invocation.getArgument<EpisodeSort?>(0)
            windowSorts += sort
            @Suppress("UNCHECKED_CAST")
            val continuation = invocation.rawArguments.last() as Continuation<PodcastEpisodeSelectionWindow>
            val answer: suspend () -> PodcastEpisodeSelectionWindow = { selectionWindow(sort) }
            answer.startCoroutineUninterceptedOrReturn(continuation)
        }.`when`(model)
        runBlocking { stubbing.loadEpisodeSelectionWindow(ArgumentMatchers.any()) }
    }

    private enum class Exit { CONFIRM, CANCEL, BACK, OUTSIDE }

    private companion object {
        const val LIST_TAG = "podcast_info_episode_list"
        const val LOADING_TAG = "podcast_info_loading"
        const val REFRESH_TAG = "podcast_info_refresh_indicator"
        const val FEED_STATUS_TAG = "podcast_info_feed_status"
        const val RESULT_ROW = 2
        val ALERT_EXITS = listOf(Exit.CONFIRM, Exit.CANCEL, Exit.BACK, Exit.OUTSIDE)
        val SELECTION_ACTIONS = listOf(
            "Play selected episodes",
            "Download selected episodes",
            "Mark selected episodes completed",
            "Add selected episodes to queue",
        )
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
