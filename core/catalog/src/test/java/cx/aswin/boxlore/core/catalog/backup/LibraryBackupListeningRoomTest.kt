package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.PlayedShowEntity
import cx.aswin.boxlore.core.domain.ports.DeviceIdentityPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JSON backup v8 on real Room: the listening dates of history rows, timed listening (sessions and
 * daily summaries, each under the phone that recorded it) and the played shows; restoring twice,
 * onto the same or another phone, and from older backups.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryBackupListeningRoomTest {
    private lateinit var source: BoxLoreDatabase
    private lateinit var target: BoxLoreDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        target = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun aV8BackupRestoresDatesTimingAndPlayedShowsOnAnotherPhone() = runTest {
        seedSource()

        val json = manager(source, PHONE_A).exportLibraryAsJson()
        manager(target, PHONE_B).importLibraryFromJson(json)
        manager(target, PHONE_B).importLibraryFromJson(json)

        val exported = JsonParser.parseString(json).asJsonObject
        assertEquals(8, exported["version"].asInt)
        val listening = exported["listening"].asJsonObject
        assertEquals(PHONE_A, listening["deviceId"].asString)
        assertEquals(PHONE_A, listening["dailySummaries"].asJsonArray[0].asJsonObject["deviceId"].asString)

        val history = target.listeningHistoryDao().getHistoryItem(EPISODE)!!
        assertEquals(100L to 200L, history.firstPlayedAt to history.finishedAt)
        // Restored twice, counted once: this phone's own listening went over as phone A's, phone C's stayed C's.
        assertEquals(
            setOf("mine" to PHONE_A, "theirs" to PHONE_C),
            target.listeningSessionDao().getAllSessions().map { it.sessionId to it.deviceId }.toSet(),
        )
        val summary = target.listeningRollupDao().getAllRollups().single()
        assertEquals(PHONE_A to 90_000L, summary.deviceId to summary.consumedMs)
        assertEquals(PlayedShowEntity(SHOW, "Show", null, "History", 0L, 300L, isDirty = true), target.playedShowDao().getPlayedShow(SHOW))
    }

    @Test
    fun restoringOntoTheSameInstallationKeepsItsListeningUnderItsOwnId() = runTest {
        seedSource()

        manager(target, PHONE_A).importLibraryFromJson(manager(source, PHONE_A).exportLibraryAsJson())

        // Its own listening ID, so its next cloud push sends what the restore brought back.
        val sessions = target.listeningSessionDao().getAllSessions().associate { it.sessionId to it.deviceId }
        assertEquals(PHONE_A, sessions["mine"])
        assertEquals(PHONE_C, sessions["theirs"])
        assertEquals(PHONE_A, target.listeningRollupDao().getAllRollups().single().deviceId)
    }

    @Test
    fun sessionsNewerThanARestoredSummaryStayBesideItAndAnOlderBackupChangesNothing() = runTest {
        val listening = LibraryBackupListening(target, PHONE_B)
        // Made mid-day: phone A's day 5 summed up to 65 s, and a later session.
        listening.restore(
            ListeningBackup(
                deviceId = PHONE_A,
                sessions = listOf(sessionBackup("late", day = 5L, device = null, endedOffset = 120_000L)),
                dailySummaries = listOf(rollupBackup(day = 5L, device = PHONE_A, consumed = 60_000L)),
            ),
            refusedEpisodeIds = emptySet(),
        )
        // An older backup of phone A: a larger total that ends earlier, and an earlier session.
        listening.restore(
            ListeningBackup(
                deviceId = PHONE_A,
                sessions = listOf(sessionBackup("early", day = 5L, device = null, endedOffset = 30_000L)),
                dailySummaries = listOf(rollupBackup(day = 5L, device = PHONE_A, consumed = 999_000L).copy(lastListenedAt = 5_000L + 30_000L)),
            ),
            refusedEpisodeIds = emptySet(),
        )

        assertEquals(listOf("late"), target.listeningSessionDao().getAllSessions().map { it.sessionId })
        assertEquals(60_000L, target.listeningRollupDao().getRollup(PHONE_A, 5L, EPISODE)!!.consumedMs)
    }

    @Test
    fun aDailySummaryReplacesTheSessionsItSumsAndALargerStoredOneIsKept() = runTest {
        val listening = LibraryBackupListening(target, PHONE_B)
        target.listeningSessionDao().upsertSessions(listOf(session("a-1", day = 3L, device = PHONE_A), session("own", day = 3L, device = null)))
        target.listeningRollupDao().upsertRollup(rollup(day = 4L, device = PHONE_A, consumed = 500_000L))

        listening.restore(
            ListeningBackup(
                deviceId = PHONE_A,
                sessions = listOf(sessionBackup("a-2", day = 4L, device = PHONE_A)),
                dailySummaries = listOf(rollupBackup(day = 3L, device = PHONE_A, consumed = 120_000L), rollupBackup(day = 4L, device = PHONE_A, consumed = 1_000L)),
            ),
            refusedEpisodeIds = emptySet(),
        )

        // Phone A's day 3 is now one summary; this phone's own session of that day stays.
        assertEquals(listOf("own"), target.listeningSessionDao().getAllSessions().map { it.sessionId })
        assertEquals(120_000L, target.listeningRollupDao().getRollup(PHONE_A, 3L, EPISODE)!!.consumedMs)
        assertEquals(500_000L, target.listeningRollupDao().getRollup(PHONE_A, 4L, EPISODE)!!.consumedMs)
    }

    @Test
    fun refusedEpisodesAndIncompleteRowsAreLeftOut() = runTest {
        LibraryBackupListening(target, PHONE_B).restore(
            ListeningBackup(
                sessions = listOf(sessionBackup("refused", day = 1L, device = PHONE_A), ListeningSessionBackup(sessionId = "broken", episodeId = EPISODE)),
                dailySummaries = listOf(ListeningRollupBackup(deviceId = PHONE_A, episodeId = EPISODE)),
                playedShows = listOf(PlayedShowBackup(podcastId = SHOW), PlayedShowBackup(podcastId = "2", podcastName = "Two", genre = "Podcast", lastPlayedAt = 9L)),
            ),
            refusedEpisodeIds = setOf(EPISODE),
        )

        assertTrue(target.listeningSessionDao().getAllSessions().isEmpty())
        assertTrue(target.listeningRollupDao().getAllRollups().isEmpty())
        assertEquals(listOf(PlayedShowEntity("2", "Two", null, null, 0L, 9L, isDirty = true)), target.playedShowDao().getAllPlayedShows())
    }

    @Test
    fun anRssShowTheRestoreDidNotFollowAgainKeepsNoTiming() = runTest {
        LibraryBackupListening(target, PHONE_B).restore(
            ListeningBackup(
                sessions = listOf(sessionBackup("rss", day = 1L, device = PHONE_A).copy(podcastId = "rss:gone"), sessionBackup("kept", day = 1L, device = PHONE_A).copy(podcastId = "rss:back")),
                playedShows = listOf(PlayedShowBackup(podcastId = "rss:gone", podcastName = "Gone", lastPlayedAt = 1L)),
            ),
            refusedEpisodeIds = emptySet(),
            importedShowIds = listOf("rss:back"),
        )

        assertEquals(listOf("kept"), target.listeningSessionDao().getAllSessions().map { it.sessionId })
        assertTrue(target.playedShowDao().getAllPlayedShows().isEmpty())
    }

    @Test
    fun aVersion7BackupRestoresWithoutListening() = runTest {
        val v7 = """{"version":7,"subscriptions":[],"history":[{"episodeId":"$EPISODE","podcastId":"$SHOW","episodeTitle":"E",""" +
            """"podcastName":"Show","progressMs":5,"durationMs":10,"isCompleted":false,"isLiked":false,"lastPlayedAt":7}]}"""

        manager(target, PHONE_B).importLibraryFromJson(v7)

        val history = target.listeningHistoryDao().getHistoryItem(EPISODE)!!
        assertNull(history.firstPlayedAt)
        assertNull(history.finishedAt)
        assertTrue(target.listeningSessionDao().getAllSessions().isEmpty())
    }

    private suspend fun seedSource() {
        source.listeningHistoryDao().upsert(
            ListeningHistoryEntity(
                episodeId = EPISODE,
                podcastId = SHOW,
                episodeTitle = "Episode",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://cdn.example.com/e.mp3",
                podcastName = "Show",
                progressMs = 0L,
                durationMs = 60_000L,
                isCompleted = true,
                lastPlayedAt = 200L,
                firstPlayedAt = 100L,
                finishedAt = 200L,
            ),
        )
        source.listeningSessionDao().upsertSessions(listOf(session("mine", day = 20L, device = null), session("theirs", day = 21L, device = PHONE_C)))
        source.listeningRollupDao().upsertRollup(rollup(day = 2L, device = ListeningRollupEntity.THIS_DEVICE, consumed = 90_000L))
        source.playedShowDao().mergePlayedShows(listOf(PlayedShowEntity(SHOW, "Show", null, "History", 0L, 300L)), fromCloud = true)
    }

    private fun manager(database: BoxLoreDatabase, deviceId: String) = LibraryBackupManager(
        subscriptionRepository = SubscriptionRepository(database.podcastDao()),
        listeningHistory = RoomHistory(database),
        podcastRepository = mock(PodcastRepository::class.java),
        context = ApplicationProvider.getApplicationContext(),
        adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java),
        rssPodcastRepository = mock(RssPodcastRepository::class.java),
        database = database,
        deviceIdentity = DeviceIdentityPort { deviceId },
    )

    private fun session(id: String, day: Long, device: String?,) = ListeningSessionEntity(
        sessionId = id,
        episodeId = EPISODE,
        podcastId = SHOW,
        startedAt = day * 1_000L,
        endedAt = day * 1_000L + 60_000L,
        consumedMs = 60_000L,
        completed = false,
        localDay = day,
        timeBucket = 1,
        deviceId = device,
        wallMs = 40_000L,
    )

    private fun sessionBackup(id: String, day: Long, device: String?, endedOffset: Long = 60_000L,) = ListeningSessionBackup(
        sessionId = id, episodeId = EPISODE, podcastId = SHOW, startedAt = day * 1_000L + endedOffset - 60_000L, endedAt = day * 1_000L + endedOffset,
        consumedMs = 60_000L, completed = false, localDay = day, timeBucket = 1, deviceId = device,
    )

    private fun rollup(day: Long, device: String, consumed: Long,) =
        ListeningRollupEntity(day, EPISODE, SHOW, consumed, sessionCount = 1, completionCount = 0, lastListenedAt = day * 1_000L + 60_000L, deviceId = device)

    private fun rollupBackup(day: Long, device: String, consumed: Long,) =
        ListeningRollupBackup(deviceId = device, localDay = day, episodeId = EPISODE, podcastId = SHOW, consumedMs = consumed, sessionCount = 1, lastListenedAt = day * 1_000L + 60_000L)

    /** History through Room, as the app's playback repository stores it. */
    private class RoomHistory(private val database: BoxLoreDatabase) : ListeningHistoryBackupPort {
        override fun getAllHistory(): Flow<List<ListeningHistoryEntity>> = database.listeningHistoryDao().getAllHistory()

        override suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = database.listeningHistoryDao().getAllStoredHistory()

        override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) = database.listeningHistoryDao().upsert(entity)

        override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) = Unit
    }

    private companion object {
        const val SHOW = "100"
        const val EPISODE = "111"
        const val PHONE_A = "phone-a"
        const val PHONE_B = "phone-b"
        const val PHONE_C = "phone-c"
    }
}
