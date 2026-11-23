package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Archive on one live show page across the shared window gates (600dp width, 480dp height, 840dp
 * split, portrait/landscape and a fold posture) in both directions, and through recreation:
 * archived episodes stay hidden, Show archived and the selection survive every change, and
 * Archive acts once on the selection with Undo.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PodcastInfoArchiveAdaptiveTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))

    private val window = mutableStateOf(COMPACT)
    private val podcast = TestFixtures.podcast(id = "1", title = "Curious world", imageUrl = PhoneGoldenRule.ARTWORK_URL)
    private val episodes = List(20) { index ->
        TestFixtures.episode(id = "e$index", title = "Episode $index", podcastId = "1", podcastTitle = podcast.title, publishedDate = 1_700_000_000L - index * 86_400L)
    }
    private val uiState = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, episodes, isSubscribed = true))
    private val archived = MutableStateFlow(setOf("e1", "e3"))
    private val archivedCount = MutableStateFlow(2)
    private val model = mock(PodcastInfoViewModel::class.java)
    private val archiveCalls = mutableListOf<Triple<List<String>, Boolean, Boolean>>()
    private lateinit var restoration: StateRestorationTester

    @Test
    fun `archived episodes stay hidden and Show archived lists them, marked, across window changes and recreation`() {
        render()
        listed("Episode 2")
        notListed("Episode 1")

        openMenuItem("Show archived episodes (2)")
        acrossWindowChanges { archivedRowShown("Episode 1") }
        recreate(FOLD_TABLETOP)
        archivedRowShown("Episode 1")
        recreate(COMPACT)
        archivedRowShown("Episode 1")

        openMenuItem("Show archived episodes (2)")
        acrossWindowChanges { notListed("Episode 1") }
    }

    @Test
    fun `Show archived counts the show's archived episodes before their page loads`() {
        // Trailers are usually the oldest: archived, but not on the pages loaded yet.
        archived.value = setOf("old-trailer", "e1")
        archivedCount.value = 3
        render()

        openMenuItem("Show archived episodes (3)")
        acrossWindowChanges { archivedRowShown("Episode 1") }
    }

    @Test
    fun `a search finds archived episodes and marks them`() {
        render()
        fixture.compose.onNodeWithContentDescription("Search episodes").performClick()
        fixture.compose.onNodeWithContentDescription("Search episodes in Curious world").performTextInput("Episode 3")

        acrossWindowChanges {
            // The search field holds the query; the other match is the archived result's row.
            fixture.compose.onNodeWithTag(LIST_TAG).performScrollToNode(hasText("Episode 3") and hasSetTextAction().not())
            assertEquals(2, fixture.compose.onAllNodes(hasText("Episode 3")).fetchSemanticsNodes().size)
            archivedMarkShown()
        }
    }

    @Test
    fun `archiving a selection survives window changes, acts once on it and Undo puts back exactly that`() {
        render()
        fixture.compose.onNodeWithText("Episode 0").performTouchInput { longClick() }
        fixture.compose.onNodeWithText("Episode 2").performClick()
        selectionCount("2")
        acrossWindowChanges { selectionCount("2") }
        recreate(FOLD_FLAT)
        selectionCount("2")

        fixture.compose.onNodeWithContentDescription("More selection options").performClick()
        fixture.compose.onNodeWithText("Archive selected episodes").performClick()
        fixture.compose.waitForIdle()

        assertEquals(listOf(Triple(listOf("e0", "e2"), true, false)), archiveCalls)
        fixture.compose.onNodeWithContentDescription("Play selected episodes").assertDoesNotExist()
        notListed("Episode 0")
        fixture.compose.onNodeWithText("Archived 2 episodes").assertExists()
        fixture.compose.onNodeWithText("Undo").performClick()
        fixture.compose.waitForIdle()
        assertEquals(Triple(listOf("e0", "e2"), false, true), archiveCalls.last())
        assertEquals(2, archiveCalls.size)
        listed("Episode 0")
    }

    @Test
    fun `an Undo still on offer comes back after a fold recreates the page, and acts once`() {
        render()
        fixture.compose.onNodeWithText("Episode 0").performTouchInput { longClick() }
        fixture.compose.onNodeWithContentDescription("More selection options").performClick()
        fixture.compose.onNodeWithText("Archive selected episodes").performClick()
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Archived 1 episode").assertExists()

        // Reported: folding or unfolding dropped the Undo for Archive.
        recreate(FOLD_FLAT)
        fixture.compose.onNodeWithText("Archived 1 episode").assertExists()
        recreate(COMPACT)
        fixture.compose.onAllNodes(hasText("Archived 1 episode")).fetchSemanticsNodes().let { assertEquals("offered once", 1, it.size) }
        fixture.compose.onNodeWithText("Undo").performClick()
        fixture.compose.waitForIdle()
        assertEquals(listOf(Triple(listOf("e0"), true, false), Triple(listOf("e0"), false, true)), archiveCalls)
    }

    @Test
    fun `an Undo whose time ran out isn't offered again after recreation`() {
        render()
        fixture.compose.onNodeWithText("Episode 0").performTouchInput { longClick() }
        fixture.compose.onNodeWithContentDescription("More selection options").performClick()
        fixture.compose.onNodeWithText("Archive selected episodes").performClick()
        fixture.compose.waitForIdle()
        val now = System.currentTimeMillis()
        undoClockMs = { now + 60_000L }
        try {
            recreate(FOLD_FLAT)
            fixture.compose.onNodeWithText("Archived 1 episode").assertDoesNotExist()
        } finally {
            undoClockMs = System::currentTimeMillis
        }
        assertEquals(1, archiveCalls.size)
    }

    @Test
    fun `a selection of archived episodes offers Unarchive`() {
        render()
        openMenuItem("Show archived episodes (2)")
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToNode(hasText("Episode 1"))
        fixture.compose.onNodeWithText("Episode 1").performTouchInput { longClick() }
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToNode(hasText("Episode 3"))
        fixture.compose.onNodeWithText("Episode 3").performClick()

        fixture.compose.onNodeWithContentDescription("More selection options").performClick()
        fixture.compose.onNodeWithText("Unarchive selected episodes").performClick()
        fixture.compose.waitForIdle()

        assertEquals(listOf(Triple(listOf("e1", "e3"), false, false)), archiveCalls)
        fixture.compose.onNodeWithText("Unarchived 2 episodes").assertExists()
    }

    private fun listed(title: String) {
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToIndex(0)
        fixture.compose.onNodeWithText(title).assertExists()
    }

    private fun archivedRowShown(title: String) {
        fixture.compose.onNodeWithTag(LIST_TAG).performScrollToNode(hasText(title))
        fixture.compose.onNodeWithText(title).assertExists()
        archivedMarkShown()
    }

    /** An archived row is on screen with its Archived mark. */
    private fun archivedMarkShown() {
        val marks = fixture.compose.onAllNodes(hasText("Archived"), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("an Archived mark is shown", marks.isNotEmpty())
    }

    private fun notListed(title: String) {
        fixture.compose.onNodeWithText(title).assertDoesNotExist()
    }

    private fun selectionCount(count: String) {
        fixture.compose.onNode(hasText(count) and hasAnySibling(hasContentDescription("Play selected episodes"))).assertIsDisplayed()
    }

    /** Turns a row of the sort button's Sort and view menu on or off. */
    private fun openMenuItem(label: String) {
        fixture.compose.onNodeWithContentDescription("Sort and view").performClick()
        fixture.compose.onNodeWithText(label).performClick()
        fixture.compose.waitForIdle()
    }

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
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        Surface { PodcastInfoScreen("1", model, {}, { _, _, _ -> }, {}, {}) }
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
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.episodeTitlesOnly).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.isHidden).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalPlaybackSpeed).thenReturn(MutableStateFlow(1f))
        `when`(model.globalTrimSilence).thenReturn(MutableStateFlow(false))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(true))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        stubArchive(model, archived, archivedCount)
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
        // The ViewModel's archive, as far as the page sees it: the IDs change and the changed episodes come back.
        doAnswer { invocation ->
            val selected = invocation.getArgument<List<Episode>>(0)
            val archive = invocation.getArgument<Boolean>(1)
            val undo = invocation.getArgument<Boolean>(2)
            val changed = if (undo) selected else selected.filter { (it.id in archived.value) != archive }
            archiveCalls += Triple(changed.map(Episode::id), archive, undo)
            archived.update { ids -> if (archive) ids + changed.map(Episode::id) else ids - changed.map(Episode::id).toSet() }
            changed
        }.`when`(model).setEpisodesArchived(anyList(), anyBoolean(), anyBoolean())
    }

    private companion object {
        const val LIST_TAG = "podcast_info_episode_list"
        val COMPACT = BoxloreWindowLayout(599f, 800f)
        val FOLD_FLAT = BoxloreWindowLayout(673f, 841f)
        val FOLD_TABLETOP = FOLD_FLAT.copy(
            folds = listOf(
                BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true),
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
