package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.countsAsListening
import cx.aswin.boxlore.core.database.isDismissed
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
 * Archiving from the playback layer: [setEpisodesDismissed] archives a selection without marking it
 * played (creating rows for episodes never played), archived episodes leave Continue listening, a
 * saved position keeps the archive, and playing an archived episode brings it back (but not one
 * archived while it plays), whichever of its IDs holds the archive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeArchivePlaybackTest {
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

    private fun historyStore() = EpisodeAliasFixtures.historyStore(context, database, TestScope(testDispatcher))

    @Test
    fun archivingASelectionNeverMarksItPlayedAndCreatesRowsForUnplayedEpisodes() = runTest(testDispatcher) {
        dao.upsert(EpisodeAliasFixtures.history("started", progress = 60_000L, playedAt = 10L))
        val store = historyStore()

        store.setEpisodesDismissed(
            listOf(EpisodeAliasFixtures.episode("started"), EpisodeAliasFixtures.episode("trailer")),
            EpisodeAliasFixtures.SHOW,
            "Show",
            null,
            dismissed = true,
        )

        assertEquals(setOf("started", "trailer"), store.dismissedEpisodeIds.first())
        val trailer = dao.getHistoryItem("trailer")!!
        assertTrue(trailer.isDismissed)
        assertFalse(trailer.isCompleted)
        assertEquals(0L to 0L, trailer.progressMs to trailer.lastPlayedAt)
        assertFalse(trailer.countsAsListening)
        assertTrue(trailer.isDirty)
        val started = dao.getHistoryItem("started")!!
        assertEquals(60_000L to false, started.progressMs to started.isCompleted)
        assertTrue(store.completedEpisodeIds.first().isEmpty())
        // Archived episodes leave Continue listening and the listening history.
        assertTrue(store.resumeSessions.first().isEmpty())
        assertTrue(store.observeHistoryTimeline().first().none { it.episodeId == "trailer" })

        store.setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("trailer")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = false)
        assertEquals(setOf("started"), store.dismissedEpisodeIds.first())
    }

    @Test
    fun bringingBackAnEpisodeThatWasNeverArchivedWritesNothing() = runTest(testDispatcher) {
        historyStore().setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("never")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = false)

        assertEquals(null, dao.getHistoryItem("never"))
    }

    @Test
    fun archivingAnEpisodeStoredUnderTwoIdsArchivesBoth() = runTest(testDispatcher) {
        EpisodeAliasFixtures.twoIds(database)
        dao.upsertAll(listOf(EpisodeAliasFixtures.history("A", playedAt = 5L), EpisodeAliasFixtures.history("B", playedAt = 4L)))
        val store = historyStore()

        store.setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("B")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = true)

        assertEquals(setOf("A", "B"), store.dismissedEpisodeIds.first())
    }

    @Test
    fun aSavedPositionKeepsTheArchive() = runTest(testDispatcher) {
        dao.upsert(EpisodeAliasFixtures.history("ep", progress = 10L, playedAt = 5L))
        val store = historyStore()
        store.setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("ep")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = true)

        store.savePlaybackState(
            ListeningHistoryUpsertLogic.ProgressSaveInput(
                podcastId = EpisodeAliasFixtures.SHOW,
                episodeId = "ep",
                positionMs = 90_000L,
                durationMs = 1_800_000L,
                episodeTitle = "Episode ep",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://example.com/ep.mp3",
                podcastName = "Show",
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = 50L,
            ),
        )

        assertTrue(dao.getHistoryItem("ep")!!.isDismissed)
        assertEquals(90_000L, dao.getHistoryItem("ep")!!.progressMs)
    }

    @Test
    fun playingAnArchivedEpisodeBringsItBackButALoadWithoutPlayDoesNot() = runTest(testDispatcher) {
        dao.upsert(EpisodeAliasFixtures.history("ep", progress = 10_000L, playedAt = 5L))
        val store = historyStore()
        store.setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("ep")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = true)
        val playStart = dao.getHistoryItem("ep")!!.dismissedAt + 1L
        val coordinator = progressCoordinator()

        // A restored, paused episode saves its place without playing: still archived.
        coordinator.saveProgressSnapshot(snapshot("ep", 20_000L, playing = false, sequence = 1L))
        assertTrue(dao.getHistoryItem("ep")!!.isDismissed)

        coordinator.saveProgressSnapshot(snapshot("ep", 30_000L, playing = true, sequence = 2L, startedAt = playStart))
        assertFalse(dao.getHistoryItem("ep")!!.isDismissed)
        assertTrue(dao.getHistoryItem("ep")!!.dismissedAt < 0L)
        assertTrue(store.dismissedEpisodeIds.first().isEmpty())
    }

    @Test
    fun archivingTheEpisodeThatIsPlayingKeepsItArchivedUntilPlayResumes() = runTest(testDispatcher) {
        dao.upsert(EpisodeAliasFixtures.history("ep", progress = 10_000L, playedAt = 5L))
        val store = historyStore()
        val coordinator = progressCoordinator()
        val playStart = System.currentTimeMillis() - 60_000L
        coordinator.saveProgressSnapshot(snapshot("ep", 20_000L, playing = true, sequence = 1L, startedAt = playStart))

        // Archived while playing: the saves every 10 s that follow keep the archive.
        store.setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("ep")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = true)
        val archivedAt = dao.getHistoryItem("ep")!!.dismissedAt
        coordinator.saveProgressSnapshot(snapshot("ep", 30_000L, playing = true, sequence = 2L, startedAt = playStart))
        coordinator.saveProgressSnapshot(snapshot("ep", 40_000L, playing = true, sequence = 3L, startedAt = playStart))
        assertEquals(archivedAt, dao.getHistoryItem("ep")!!.dismissedAt)
        assertEquals(setOf("ep"), store.listenerStore.dismissedIds())

        // Paused, then played again for 10 s: that is a new choice to listen, so it comes back.
        coordinator.saveProgressSnapshot(snapshot("ep", 41_000L, playing = false, sequence = 4L))
        coordinator.saveProgressSnapshot(snapshot("ep", 55_000L, playing = true, sequence = 5L, startedAt = archivedAt + 1L))
        assertFalse(dao.getHistoryItem("ep")!!.isDismissed)
        assertTrue(store.listenerStore.dismissedIds().isEmpty())
    }

    @Test
    fun playingBringsBackAnArchiveHeldByAnotherIdOfTheEpisode() = runTest(testDispatcher) {
        EpisodeAliasFixtures.twoIds(database)
        // Only B, which is not the owner, holds the archive (say a pull wrote it under B).
        dao.upsertAll(
            listOf(
                EpisodeAliasFixtures.history("A", progress = 10_000L, playedAt = 5L),
                EpisodeAliasFixtures.history("B", playedAt = 4L).copy(dismissedAt = 100L),
            ),
        )
        val store = historyStore()
        assertEquals(setOf("A", "B"), store.listenerStore.dismissedIds())

        progressCoordinator().saveProgressSnapshot(snapshot("A", 30_000L, playing = true, sequence = 1L, startedAt = 1_000L))

        assertTrue(store.listenerStore.dismissedIds().isEmpty())
        assertFalse(dao.getHistoryItem("B")!!.isDismissed)
    }

    @Test
    fun undoingAHistoryRemovalPutsTheArchiveBack() = runTest(testDispatcher) {
        dao.upsert(EpisodeAliasFixtures.history("ep", progress = 60_000L, playedAt = 5L))
        val store = historyStore()
        store.setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("ep")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = true)
        val archivedAt = dao.getHistoryItem("ep")!!.dismissedAt

        val removal = store.removeHistoryItem("ep")!!
        store.restoreHistoryRemoval(removal)

        assertEquals(archivedAt, dao.getHistoryItem("ep")!!.dismissedAt)
        assertEquals(setOf("ep"), store.listenerStore.dismissedIds())
    }

    @Test
    fun removingAnArchivedEpisodeFromHistoryKeepsItArchived() = runTest(testDispatcher) {
        dao.upsert(EpisodeAliasFixtures.history("ep", progress = 60_000L, liked = true, likedAt = 3L, playedAt = 5L))
        val store = historyStore()
        store.setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("ep")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = true)

        store.removeHistoryItem("ep")

        val kept = dao.getHistoryItem("ep")!!
        assertEquals(setOf("ep"), store.dismissedEpisodeIds.first())
        assertEquals(0L to 0L, kept.progressMs to kept.lastPlayedAt)
        assertFalse(kept.isLiked)
        assertFalse(kept.countsAsListening)
        assertFalse(kept.isDirty)
    }

    @Test
    fun clearingHistoryKeepsEveryArchiveAcrossIds() = runTest(testDispatcher) {
        EpisodeAliasFixtures.twoIds(database)
        dao.upsertAll(
            listOf(
                EpisodeAliasFixtures.history("A", progress = 60_000L, playedAt = 5L),
                EpisodeAliasFixtures.history("B", playedAt = 4L),
                EpisodeAliasFixtures.history("played", progress = 90_000L, completed = true, playedAt = 6L),
            ),
        )
        val store = historyStore()
        store.setEpisodesDismissed(listOf(EpisodeAliasFixtures.episode("B")), EpisodeAliasFixtures.SHOW, "Show", null, dismissed = true)

        store.clearHistory()

        assertEquals(setOf("A", "B"), store.dismissedEpisodeIds.first())
        assertEquals(null, dao.getHistoryItem("played"))
        assertEquals(0L, dao.getHistoryItem("A")!!.progressMs)
        assertTrue(store.completedEpisodeIds.first().isEmpty())
    }

    private fun progressCoordinator() = PlaybackProgressCoordinator(
        mainDispatcher = testDispatcher,
        database = database,
        mediaSessionProvider = { null },
        isEffectiveEndLatched = { false },
        effectiveSkipEndingMs = { 0L },
        updateConsumedAudio = {},
        dispatchHeartbeatTelemetry = {},
        missingHistorySeedProvider = { null },
    )

    private fun snapshot(id: String, positionMs: Long, playing: Boolean, sequence: Long, startedAt: Long = 0L) = PlaybackProgressSnapshot(
        sequence = sequence,
        episodeId = id,
        positionMs = positionMs,
        durationMs = 1_800_000L,
        hasBeenPlayingFor10s = playing,
        allowZeroPosition = false,
        playStartedAtMs = if (playing) startedAt else 0L,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        enclosureType = null,
    )
}
