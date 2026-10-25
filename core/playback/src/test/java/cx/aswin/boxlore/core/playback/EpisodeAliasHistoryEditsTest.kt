package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.KEY
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.SHOW
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.alias
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.episode
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.history
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.twoIds
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * History edits for an episode stored under two IDs, owner "A" and "B" (docs/EPISODE_IDS.md §5):
 * removal and Undo, clearing history, marking a show played or unplayed, and the Smart Queue
 * sources. An episode stored under one ID must write exactly what it wrote before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeAliasHistoryEditsTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao get() = database.listeningHistoryDao()
    private val identity get() = database.episodeIdentityDao()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun removingAnEpisodeStoredUnderTwoIdsAndUndoingPutsEverythingBackUnchanged() = runTest(testDispatcher) {
        twoIds(database)
        // A was liked at 10 and B unliked later at 20: the episode reads unliked.
        val rowA = history("A", progress = 30L, liked = true, likedAt = 10L, completed = true, playedAt = 50L, dirty = false, manual = true).copy(syncedAt = 7L)
        val rowB = history("B", progress = 5L, liked = false, likedAt = 20L, playedAt = 40L).copy(isBulkCompletion = false)
        dao.upsertAll(listOf(rowA, rowB))
        val state = EpisodeGroupStateEntity(SHOW, KEY, likeClearedAt = 20L, completedAt = 50L, version = 2L)
        identity.upsertGroupState(state)
        listOf("A", "B").forEach { id ->
            database.listeningSessionDao().upsertSessions(listOf(session(id)))
            database.listeningRollupDao().upsertRollup(rollup(id))
        }
        val store = database.episodeListenerStore()
        val projectedBefore = store.projectedHistory("B")
        assertFalse(projectedBefore!!.isLiked)
        val historyStore = historyStore()

        val removal = historyStore.removeHistoryItem("A")!!

        assertNull(dao.getHistoryItem("A"))
        assertNull(dao.getHistoryItem("B"))
        assertNull(identity.getGroupState(SHOW, KEY))
        assertTrue(database.listeningSessionDao().getSessionsForEpisode("B").isEmpty())
        assertNull(store.projectedHistory("A"))

        historyStore.restoreHistoryRemoval(removal)

        assertEquals(rowA, dao.getHistoryItem("A"))
        assertEquals(rowB, dao.getHistoryItem("B"))
        assertEquals(state, identity.getGroupState(SHOW, KEY))
        listOf("A", "B").forEach { id ->
            assertEquals(listOf(session(id)), database.listeningSessionDao().getSessionsForEpisode(id))
            assertEquals(listOf(rollup(id)), database.listeningRollupDao().getRollupsForEpisode(id))
        }
        assertEquals(projectedBefore, store.projectedHistory("B"))
    }

    @Test
    fun removingAndRestoringAnEpisodeStoredUnderOneIdWorksAsBefore() = runTest(testDispatcher) {
        dao.upsert(history("solo", progress = 30L, liked = true, likedAt = 10L, playedAt = 50L))
        val historyStore = historyStore()

        val removal = historyStore.removeHistoryItem("solo")!!
        assertTrue(removal.rows.isEmpty())
        assertNull(removal.groupState)
        historyStore.restoreHistoryRemoval(removal)

        // As before: the row comes back from the removed item and is marked for sync.
        val restored = dao.getHistoryItem("solo")!!
        assertEquals(history("solo", progress = 30L, liked = true, likedAt = 0L, playedAt = 50L), restored)
    }

    @Test
    fun clearingHistoryClearsTheSharedSettingsSoALikeDoesNotComeBack() = runTest(testDispatcher) {
        twoIds(database)
        dao.upsertAll(listOf(history("A", playedAt = 50L), history("B", playedAt = 40L)))
        val historyStore = historyStore()
        historyStore.toggleLike(episode("B"), SHOW, "Show", null)
        assertNotNull(identity.getGroupState(SHOW, KEY))

        historyStore.clearHistory()

        assertNull(identity.getGroupState(SHOW, KEY))
        // Playing the episode again starts it without the old like.
        dao.upsert(history("A", progress = 10L, playedAt = 60L))
        dao.upsert(history("B", progress = 5L, playedAt = 55L))
        assertFalse(database.episodeListenerStore().projectedHistory("B")!!.isLiked)
        assertEquals(listOf("A", "B"), database.episodeListenerStore().memberIds("B"))
    }

    @Test
    fun markingAShowPlayedAndUnplayedWritesEpisodesStoredUnderOneIdAsBefore() = runTest(testDispatcher) {
        // "plain" has no alias, "keyed" one trusted alias of its own; neither shares an episode.
        identity.insertAliasesIfAbsent(listOf(alias("keyed", owner = true, key = "guid-keyed")))
        dao.upsert(history("plain", progress = 40L, liked = true, likedAt = 3L, playedAt = 5L))
        val historyStore = historyStore()

        historyStore.markAllEpisodesCompleted(listOf(episode("plain"), episode("keyed")), SHOW, "Show", null)

        val plain = dao.getHistoryItem("plain")!!
        val keyed = dao.getHistoryItem("keyed")!!
        assertTrue(plain.isCompleted && plain.isBulkCompletion && plain.isLiked && plain.progressMs == 0L)
        assertTrue(keyed.isCompleted && keyed.isBulkCompletion)
        assertEquals(plain.lastPlayedAt, keyed.lastPlayedAt)
        // Only the keyed episode records the decision, for an alias found later; no other row is touched.
        assertNull(identity.getGroupState(SHOW, KEY))
        assertTrue(identity.getGroupState(SHOW, "guid-keyed")!!.completedAt > 0L)
        assertEquals(setOf("plain", "keyed"), historyStore.completedEpisodeIds.first())

        historyStore.markAllEpisodesUncompleted(listOf(episode("plain"), episode("keyed"), episode("never-played")))

        assertFalse(dao.getHistoryItem("plain")!!.isCompleted)
        assertFalse(dao.getHistoryItem("keyed")!!.isCompleted)
        assertNull(dao.getHistoryItem("never-played"))
        assertTrue(historyStore.completedEpisodeIds.first().isEmpty())
    }

    @Test
    fun markingAShowPlayedReachesEveryIdOfAnEpisodeStoredUnderTwo() = runTest(testDispatcher) {
        twoIds(database)
        dao.upsertAll(listOf(history("A", progress = 30L, playedAt = 50L), history("B", playedAt = 40L)))
        val historyStore = historyStore()

        historyStore.markAllEpisodesCompleted(listOf(episode("B")), SHOW, "Show", null)

        assertTrue(dao.getHistoryItem("A")!!.isBulkCompletion)
        assertTrue(dao.getHistoryItem("B")!!.isCompleted)
        assertEquals(setOf("A", "B"), historyStore.completedEpisodeIds.first())

        historyStore.markAllEpisodesUncompleted(listOf(episode("B")))
        assertFalse(dao.getHistoryItem("A")!!.isCompleted)
        assertFalse(dao.getHistoryItem("B")!!.isCompleted)
        assertTrue(identity.getGroupState(SHOW, KEY)!!.completeClearedAt > 0L)
    }

    @Test
    fun smartQueueSourcesReadEachEpisodeOnceWithItsProjectedState() = runTest(testDispatcher) {
        twoIds(database)
        dao.upsertAll(
            listOf(
                history("A", progress = 300L, playedAt = 50L),
                history("B", progress = 10L, completed = true, playedAt = 60L),
                history("solo", progress = 20L, playedAt = 40L),
            ),
        )
        val sources = DefaultSmartQueueSources(
            context = context,
            database = database,
            podcastRepository = mock(PodcastRepository::class.java),
            subscriptionRepository = mock(SubscriptionRepository::class.java),
            userPreferencesRepository = UserPreferencesRepository(context),
        )

        // B finished the episode after A's last play, so it is completed under both IDs and no longer resumes.
        assertEquals(setOf("A", "B"), sources.getCompletedEpisodeIds())
        assertEquals(listOf("solo"), sources.getResumeCandidates().map { it.episodeId })
        assertEquals(listOf("A", "solo"), sources.getRecentHistory(10).map { it.episodeId })
    }

    private fun historyStore() = EpisodeAliasFixtures.historyStore(context, database, kotlinx.coroutines.test.TestScope(testDispatcher))

    private fun session(id: String) = ListeningSessionEntity(
        sessionId = "session-$id",
        episodeId = id,
        podcastId = SHOW,
        startedAt = 1L,
        endedAt = 2L,
        consumedMs = 60_000L,
        completed = false,
        localDay = 19_000L,
        timeBucket = 0,
    )

    private fun rollup(id: String) = ListeningRollupEntity(
        localDay = 19_000L,
        episodeId = id,
        podcastId = SHOW,
        consumedMs = 60_000L,
        sessionCount = 1,
        completionCount = 0,
        lastListenedAt = 2L,
    )
}
