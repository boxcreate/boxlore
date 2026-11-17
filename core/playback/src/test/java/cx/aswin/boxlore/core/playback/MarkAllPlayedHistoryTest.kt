package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.ListeningPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regressions around "Mark all as played" and "Mark all as unplayed":
 * - played and in-progress state came from the newest 300 rows, which one Mark all fills;
 * - episodes finished by listening became marked rows and left Listening history and insights;
 * - marking unplayed put every marked episode into today's Listening history;
 * - recommendation seeds were read from the newest rows before leaving marked ones out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkAllPlayedHistoryTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao get() = database.listeningHistoryDao()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = EpisodeAliasFixtures.directDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aMarkAllOfMoreThan300EpisodesKeepsEveryOtherShowsPlayedAndStartedState() = runTest(testDispatcher) {
        dao.upsertAll(listOf(finished("daily-latest", at = 20L), started("weekly-old", at = 10L)))
        val store = historyStore()

        store.markAllEpisodesCompleted(backCatalogue(BACK_CATALOGUE), "back", "Back catalogue", null)

        assertTrue("daily-latest" in store.completedEpisodeIds.first())
        assertEquals(listOf("weekly-old"), store.inProgressForEpisodeLookup.first().map { it.episodeId })
        val recent = store.getRecentHistory().first().map { it.episodeId }
        assertTrue("daily-latest" in recent && "weekly-old" in recent)
    }

    @Test
    fun markAllLeavesEpisodesFinishedByListeningAsTheyWere() = runTest(testDispatcher) {
        val heard = finished("heard", at = 20L)
        dao.upsertAll(listOf(heard, started("half", at = 30L)))
        val store = historyStore()
        val timelineBefore = store.observeFullHistoryTimeline().first().map { it.episodeId }

        store.markAllEpisodesCompleted(listOf(episode("heard"), episode("half"), episode("never")), SHOW, "Show", null)

        val kept = dao.getHistoryItem("heard")!!
        assertEquals(heard.copy(episodeDescription = kept.episodeDescription), kept)
        assertFalse(kept.isBulkCompletion)
        assertTrue(dao.getHistoryItem("never")!!.isBulkCompletion)
        assertEquals(timelineBefore - "half", store.observeFullHistoryTimeline().first().map { it.episodeId })
        assertEquals(1, store.observeInsights(ListeningPeriod.ALL).first().completedCount)
    }

    @Test
    fun markingAllPlayedThenUnplayedLeavesListeningHistoryAsItWas() = runTest(testDispatcher) {
        dao.upsertAll(listOf(finished("heard", at = 20L), started("half", at = 30L)))
        val store = historyStore()
        val fullBefore = store.observeFullHistoryTimeline().first()
        val recentBefore = store.observeHistoryTimeline().first()
        val never = backCatalogue(5)

        store.markAllEpisodesCompleted(never, "back", "Back catalogue", null)
        store.markAllEpisodesUncompleted(never)

        assertTrue(never.none { dao.getHistoryItem(it.id)!!.isCompleted })
        assertEquals(fullBefore, store.observeFullHistoryTimeline().first())
        assertEquals(recentBefore, store.observeHistoryTimeline().first())
    }

    @Test
    fun markAllLeavesAnEpisodeFinishedUnderItsOtherIdAsItWas() = runTest(testDispatcher) {
        // Regression: only the owner ID's row was checked, so a finish under the other ID became a
        // marked one and left Listening history. A (the owner) holds no row or an older unfinished one.
        EpisodeAliasFixtures.twoIds(database)
        listOf(emptyList(), listOf(started("A", at = 10L))).forEach { ownerRows ->
            dao.deleteAll()
            dao.upsertAll(ownerRows + finished("B", at = 20L))
            val store = historyStore()

            store.markAllEpisodesCompleted(listOf(episode("A"), episode("never")), SHOW, "Show", null)

            assertEquals(ownerRows.singleOrNull(), dao.getHistoryItem("A"))
            assertFalse(database.episodeListenerStore().projectedHistoryOf(listOf("A")).getValue("A").isBulkCompletion)
            val timeline = store.observeFullHistoryTimeline().first()
            assertEquals(listOf(20L), timeline.map { it.lastPlayedAt })
            assertEquals(1, store.observeInsights(ListeningPeriod.ALL).first().completedCount)
            assertTrue(dao.getHistoryItem("never")!!.isBulkCompletion)
        }
    }

    @Test
    fun markingAllPlayedThenUnplayedKeepsEpisodesYouHeardWhereTheyWere() = runTest(testDispatcher) {
        // Regression: marking unplayed moved every episode to now, so ones heard long ago came back
        // under today in Listening history and as fresh plays in ranking.
        dao.upsertAll(listOf(finished("heard", at = 20L), started("half", at = 30L)))
        val store = historyStore()
        val show = listOf(episode("heard"), episode("half"), episode("never"))

        store.markAllEpisodesCompleted(show, SHOW, "Show", null)
        store.markAllEpisodesUncompleted(show)

        // One millisecond on, so the reset still wins over the finished copy in the cloud.
        assertEquals(21L, dao.getHistoryItem("heard")!!.lastPlayedAt)
        val timeline = store.observeFullHistoryTimeline().first().associateBy { it.episodeId }
        assertEquals(21L, timeline.getValue("heard").lastPlayedAt)
        assertFalse("never" in timeline)
        assertTrue(show.none { dao.getHistoryItem(it.id)!!.isCompleted })
    }

    @Test
    fun recommendationSeedsSkipEpisodesOnlyMarkedPlayed() = runTest(testDispatcher) {
        dao.upsertAll((0 until 20).map { started("heard-$it", at = 100L + it) })
        val store = historyStore()

        store.markAllEpisodesCompleted(backCatalogue(BACK_CATALOGUE), "back", "Back catalogue", null)

        assertEquals((19 downTo 5).map { "heard-$it" }, store.getHistoryForRecommendations(15).map { it.episodeId })
    }

    private fun historyStore() = EpisodeAliasFixtures.historyStore(context, database, TestScope(testDispatcher))

    private fun backCatalogue(count: Int) = (0 until count).map { episode("back-$it").copy(podcastId = "back") }

    private fun episode(id: String): Episode = EpisodeAliasFixtures.episode(id)

    private fun finished(id: String, at: Long): ListeningHistoryEntity =
        EpisodeAliasFixtures.history(id, completed = true, playedAt = at, dirty = false).copy(firstPlayedAt = at - 5L, finishedAt = at)

    private fun started(id: String, at: Long): ListeningHistoryEntity =
        EpisodeAliasFixtures.history(id, progress = 600_000L, playedAt = at, dirty = false).copy(firstPlayedAt = at - 5L)

    private companion object {
        const val SHOW = EpisodeAliasFixtures.SHOW
        const val BACK_CATALOGUE = 350
    }
}
