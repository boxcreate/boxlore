package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** One `episode_archived` per action, however many episodes it covers; Undo sends none. */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeArchiverTest {
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit
    private val episodes = List(4) { TestFixtures.episode(id = "e$it") }
    private val show = ArchiveShow("1", "Show", null)

    @Before fun record() {
        restore = AnalyticsHelper.installRecordingSink(events)
    }

    @After fun stopRecording() = restore()

    private fun archived() = events.filter { it.first == "episode_archived" }.map { it.second }

    @Test
    fun `archiving many episodes sends one event and writes them once, and Undo sends none`() = runTest(UnconfinedTestDispatcher()) {
        val ids = MutableStateFlow(setOf("e3"))
        val writes = mutableListOf<Pair<List<String>, Boolean>>()
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val archiver = EpisodeArchiver(scope, scope, ids, EpisodeArchiver.SURFACE_SHOW_PAGE, { show }) { _, list, archive -> writes += list.map(Episode::id) to archive }
        scope.advanceUntilIdle()

        val changed = archiver.setArchived(episodes, archive = true)

        assertEquals(listOf("e0", "e1", "e2"), changed.map(Episode::id))
        assertEquals(listOf(mapOf("is_archived" to true, "surface" to "show_page", "items_count" to 3)), archived())
        assertEquals(listOf(listOf("e0", "e1", "e2") to true), writes)

        archiver.setArchived(changed, archive = false, undo = true)
        assertEquals(1, archived().size)
        assertEquals(listOf("e0", "e1", "e2") to false, writes.last())

        // Nothing to change: nothing written, nothing sent.
        assertTrue(archiver.setArchived(listOf(episodes[3]), archive = true).isEmpty())
        assertEquals(1, archived().size)
        assertEquals(2, writes.size)
    }

    @Test
    fun `an archive is written for the show read when the listener acts, even after the page closes`() = runTest(UnconfinedTestDispatcher()) {
        val page = TestScope(UnconfinedTestDispatcher(testScheduler))
        val app = TestScope(StandardTestDispatcher(testScheduler))
        var current: ArchiveShow? = show
        val writes = mutableListOf<Pair<ArchiveShow, List<String>>>()
        val archiver = EpisodeArchiver(page, app, MutableStateFlow(emptySet()), EpisodeArchiver.SURFACE_SHOW_PAGE, { current }) { target, list, _ ->
            writes += target to list.map(Episode::id)
        }

        archiver.setArchived(episodes, archive = true)
        // The page leaves and starts loading something else before the write runs.
        current = null
        page.cancel()
        app.advanceUntilIdle()

        assertEquals(listOf(show to episodes.map(Episode::id)), writes)
        assertEquals(1, archived().size)
    }

    @Test
    fun `nothing is archived or sent while the page has no show`() = runTest(UnconfinedTestDispatcher()) {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val writes = mutableListOf<List<Episode>>()
        val archiver = EpisodeArchiver(scope, scope, MutableStateFlow(emptySet()), EpisodeArchiver.SURFACE_EPISODE_PAGE, { null }) { _, list, _ -> writes += list }

        assertTrue(archiver.setArchived(episodes, archive = true).isEmpty())
        assertTrue(writes.isEmpty())
        assertTrue(archived().isEmpty())
    }
}
