package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningDayTotals
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.ListeningSessionValues
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: the library backup and the insight counts read the screens' latest 300 history rows,
 * so a backup silently dropped older listening and long histories got low counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryBeyondScreenLimitTest {
    private lateinit var database: BoxLoreDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private val playerStateFlow = MutableStateFlow(PlayerState())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theLibraryBackupExportsEveryStoredRow() = runTest(testDispatcher) {
        database.listeningHistoryDao().upsertAll((0 until TOTAL).map { history("ep-$it", playedAt = it + 1L) })

        val exported = historyStore().exportStoredHistory()

        assertEquals(TOTAL, exported.size)
        assertEquals(TOTAL, exported.map { it.episodeId }.toSet().size)
    }

    @Test
    fun insightCountsIncludeEpisodesOlderThanTheLatest300() = runTest(testDispatcher) {
        // The oldest 100 are finished and liked; the newest 300 are in progress.
        database.listeningHistoryDao().upsertAll(
            (0 until TOTAL).map { index ->
                val old = index < OLD
                history("ep-$index", playedAt = index + 1L, completed = old, liked = old, progress = if (old) 0L else 60_000L)
            },
        )

        val summary = historyStore().observeInsights(ListeningPeriod.ALL).first()

        assertEquals(OLD, summary.completedCount)
        assertEquals(OLD, summary.likedCount)
        assertEquals(TOTAL - OLD, summary.inProgressCount)
    }

    @Test
    fun theFullTimelineListsEveryEpisodeNewestFirstButNotOnesOnlyMarkedPlayed() = runTest(testDispatcher) {
        database.listeningHistoryDao().upsertAll((0 until TOTAL).map { history("ep-$it", playedAt = it + 1L) })
        database.listeningHistoryDao().upsert(history("marked", playedAt = 9_999L, completed = true).copy(isManualCompletion = true))

        val timeline = historyStore().observeFullHistoryTimeline().first()

        assertEquals(TOTAL, timeline.size)
        assertEquals("ep-${TOTAL - 1}", timeline.first().episodeId)
        assertEquals("ep-0", timeline.last().episodeId)
    }

    @Test
    fun listeningByDaySumsEveryPhonesSessionsAndSummaries() = runTest(testDispatcher) {
        database.listeningSessionDao().upsertSessions(
            listOf(
                session("mine", day = 20L, minutes = 10L, device = "me"),
                session("theirs", day = 20L, minutes = 5L, device = "phone-b"),
            ),
        )
        database.listeningRollupDao().upsertRollup(ListeningRollupEntity(20L, "ep-1", SHOW, 30L * 60_000L, 2, 0, lastListenedAt = 99L, deviceId = "phone-c"))
        database.listeningRollupDao().upsertRollup(ListeningRollupEntity(19L, "ep-1", SHOW, 7L * 60_000L, 1, 0, lastListenedAt = 9L, deviceId = "me"))

        val days = historyStore().observeListeningByDay().first()

        assertEquals(listOf(20L to 45L * 60_000L, 19L to 7L * 60_000L), days.map { it.localDay to it.consumedMs })
        assertEquals(4, days.first().sessionCount)
    }

    @Test
    fun listeningByDayIsAddedUpByTheDatabaseWithEachPartOfTheDayAndTheLongestSitting() = runTest(testDispatcher) {
        database.listeningSessionDao().upsertSessions(
            listOf(
                session("morning", day = 20L, minutes = 30L, device = "me", bucket = 0),
                session("evening", day = 20L, minutes = 45L, device = "me", bucket = 2),
                session("night", day = 20L, minutes = 5L, device = "phone-b", bucket = 3),
            ),
        )

        val day = historyStore().observeListeningByDay().first().single()

        assertEquals(80L * 60_000L, day.consumedMs)
        assertEquals(3, day.sessionCount)
        assertEquals(30L * 60_000L, day.morningMs)
        assertEquals(45L * 60_000L, day.eveningMs)
        assertEquals(5L * 60_000L, day.nightMs)
        assertEquals(45L * 60_000L, day.longestSessionMs)
        assertEquals(20L * 86_400_000L + 45L * 60_000L, day.lastListenedAt)
    }

    @Test
    fun eachDaysListeningCarriesWhereItWasHeardWhereItStartedAndHowNewTheEpisodeWas() = runTest(testDispatcher) {
        val released = 20L * 86_400_000L - 2L * 86_400_000L
        database.listeningSessionDao().upsertSessions(
            listOf(
                session("car", day = 20L, minutes = 30L, device = "me").copy(output = ListeningSessionValues.OUTPUT_CAR, source = ListeningSessionValues.SOURCE_LORE, episodePublishedAt = released),
                session("buds", day = 20L, minutes = 20L, device = "me").copy(output = ListeningSessionValues.OUTPUT_BLUETOOTH, source = ListeningSessionValues.SOURCE_SMART_QUEUE),
                session("unknown", day = 20L, minutes = 5L, device = "me"),
            ),
        )
        // A summary from another phone with its totals (Room v41).
        database.listeningRollupDao().upsertRollup(
            ListeningRollupEntity(
                20L, "ep-1", SHOW, 10L * 60_000L, 1, 0, lastListenedAt = 99L, deviceId = "phone-c",
                totals = ListeningDayTotals(longestSessionMs = 10L * 60_000L, speakerMs = 10L * 60_000L, newReleaseMs = 10L * 60_000L),
            ),
        )

        val day = historyStore().observeListeningByDay().first().single()

        assertEquals(30L * 60_000L, day.carMs)
        assertEquals(20L * 60_000L, day.bluetoothMs)
        assertEquals(10L * 60_000L, day.speakerMs)
        assertEquals(60L * 60_000L, day.outputKnownMs)
        assertEquals(30L * 60_000L, day.loreMs)
        assertEquals(20L * 60_000L, day.smartQueueMs)
        assertEquals(40L * 60_000L, day.newReleaseMs)
    }

    @Test
    fun aSummarysStoredLongestSessionIsItsLongestSitting() = runTest(testDispatcher) {
        // Two sessions summed (Room v41 keeps the longer one); before v41 it couldn't be known.
        database.listeningRollupDao().upsertRollup(
            ListeningRollupEntity(20L, "ep-1", SHOW, 50L * 60_000L, 2, 0, lastListenedAt = 99L, deviceId = "phone-c", totals = ListeningDayTotals(longestSessionMs = 30L * 60_000L)),
        )
        database.listeningRollupDao().upsertRollup(ListeningRollupEntity(19L, "ep-1", SHOW, 50L * 60_000L, 2, 0, lastListenedAt = 9L, deviceId = "phone-c"))

        val days = historyStore().observeListeningByDay().first().associateBy { it.localDay }

        assertEquals(30L * 60_000L, days.getValue(20L).longestSessionMs)
        assertEquals(0L, days.getValue(19L).longestSessionMs)
    }

    private fun historyStore(): PlaybackHistoryStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PlaybackHistoryStore(
            player = PlaybackHistoryPlayerDeps(context, TestScope(testDispatcher), playerStateFlow, playerStateFlow),
            data = PlaybackHistoryDataDeps(
                listeningHistoryDao = database.listeningHistoryDao(),
                listeningSessionDao = database.listeningSessionDao(),
                listeningRollupDao = database.listeningRollupDao(),
                listeningInsightsMaintenance = database.listeningInsightsMaintenance(),
                podcastRepository = mock(PodcastRepository::class.java),
                rankingFeedbackRepository = RankingFeedbackRepository.create(null),
                listenerStore = database.episodeListenerStore(),
            ),
        )
    }

    private fun session(id: String, day: Long, minutes: Long, device: String, bucket: Int = 0,) = ListeningSessionEntity(
        sessionId = id,
        episodeId = "ep-1",
        podcastId = SHOW,
        startedAt = day * 86_400_000L,
        endedAt = day * 86_400_000L + minutes * 60_000L,
        consumedMs = minutes * 60_000L,
        completed = false,
        localDay = day,
        timeBucket = bucket,
        deviceId = device,
    )

    private fun history(id: String, playedAt: Long, completed: Boolean = false, liked: Boolean = false, progress: Long = 0L,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 1_800_000L,
        isCompleted = completed,
        isLiked = liked,
        likedAt = if (liked) playedAt else 0L,
        lastPlayedAt = playedAt,
        isDirty = false,
    )

    private companion object {
        const val SHOW = "1001"
        const val OLD = 100
        const val TOTAL = 400
    }
}
