package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regressions: one "Mark all as played" stamps every row it marks as played now, so reads of the
 * newest 300 rows held only marked rows. Played state, progress, ranking and recommendation seeds
 * then lost every other show; and the completed IDs were read again on every progress save.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentHistoryReadsTest {
    private lateinit var database: BoxLoreDatabase
    private val dao get() = database.listeningHistoryDao()

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
    fun aMarkAllOfMoreThan300EpisodesLeavesTheListeningAndStartedEpisodesInRecentHistory() = runTest {
        dao.upsertAll(realListening() + markedPlayed(BULK_ROWS))

        val recent = dao.getRecentHistory().first().map { it.episodeId }

        assertTrue("finished" in recent)
        assertTrue("started-long-ago" in recent)
        // The newest 300 rows (all marked), plus the listening behind them.
        assertEquals(RANKING_WINDOW + 2, recent.size)
        // Still newest first.
        assertEquals("bulk-${BULK_ROWS - 1}", recent.first())
        assertEquals("started-long-ago", recent.last())
    }

    @Test
    fun episodesStartedLongAgoStayOutOfRecentHistoryAndRanking() = runTest {
        // Regression: every started episode, however old, was in recent history, so 60 abandoned
        // plays of a show kept it above a show played every day.
        val abandoned = (0 until 60).map { history("abandoned-$it", progress = 120_000L, playedAt = 10L + it).copy(podcastId = "old-news") }
        val daily = (0 until RANKING_WINDOW).map { history("daily-$it", completed = true, playedAt = 1_000L + it).copy(finishedAt = 1_000L + it) }
        dao.upsertAll(abandoned + daily)

        val recent = dao.getRecentHistory().first()

        assertTrue(recent.none { it.podcastId == "old-news" })
        assertEquals(60, dao.getInProgressHistory().first().size)
        val shows = listOf("old-news", "daily").map { ScorablePodcast(id = it, subscribedAt = 0L, latestEpisode = null, notificationsEnabled = false, autoDownloadEnabled = false) }
        val scores = PodcastScoring.calculateScores(podcasts = shows, allHistory = recent, includeSubscriptionRecency = false, nowMs = 2_000L)
        assertTrue(scores.getValue("daily") > scores.getValue("old-news"))
    }

    @Test
    fun recentHistoryReadsNeverScanTheWholeTable() {
        // Regression: an unindexed "or in progress" branch read every row on every progress save.
        assertEquals(emptyList<String>(), fullScans(RECENT_HISTORY_SQL))
        assertEquals(emptyList<String>(), fullScans(RECENT_WITH_LISTENING_SQL.replace(":limit", "$RANKING_WINDOW")))
        // The check itself finds a full read (the in-progress read is one, shared by every screen).
        assertEquals(1, fullScans("SELECT * FROM listening_history WHERE isCompleted = 0 AND progressMs > 0").size)
    }

    @Test
    fun rankingReadsKeepTheListeningBehindMarkedRows() = runTest {
        dao.upsertAll(realListening() + markedPlayed(BULK_ROWS))

        val ids = dao.getRecentHistoryWithListeningList(RANKING_WINDOW).map { it.episodeId }

        assertTrue("finished" in ids && "started-long-ago" in ids)
        assertEquals(RANKING_WINDOW + 2, ids.size)
    }

    @Test
    fun everyStartedEpisodeIsReadWhateverItsAge() = runTest {
        dao.upsertAll(realListening() + markedPlayed(BULK_ROWS) + history("done", completed = true, playedAt = 3L))

        assertEquals(listOf("started-long-ago"), dao.getInProgressHistory().first().map { it.episodeId })
    }

    @Test
    fun recommendationSeedsAreFilteredBeforeTheLimit() = runTest {
        val listened = (0 until 20).map { history("heard-$it", progress = 120_000L, playedAt = 100L + it) }
        val short = history("short", progress = 30_000L, playedAt = 500L)
        val manual = history("manual", completed = true, playedAt = 600L).copy(isManualCompletion = true)
        dao.upsertAll(listened + short + manual + markedPlayed(BULK_ROWS))

        val seeds = dao.getRecommendationHistoryList(15).map { it.episodeId }

        assertEquals((19 downTo 5).map { "heard-$it" }, seeds)
    }

    @Test
    fun theCompletionSignalHoldsThroughProgressSavesAndMovesOnEveryCompletionChange() = runTest {
        dao.upsertAll(listOf(history("started", progress = 10_000L, playedAt = 5L), history("done", completed = true, playedAt = 4L)))
        val before = dao.completedHistorySignal().first()

        dao.updateProgress("started", progressMs = 20_000L, durationMs = DURATION, lastPlayedAt = 6L)
        assertEquals(before, dao.completedHistorySignal().first())

        dao.setCompletionStatus("started", true)
        val finished = dao.completedHistorySignal().first()
        assertNotEquals(before, finished)

        dao.setCompletionStatus("started", false)
        assertNotEquals(finished, dao.completedHistorySignal().first())

        // A completion kept only in an episode's shared settings changes it too.
        val withRows = dao.completedHistorySignal().first()
        database.episodeIdentityDao().upsertGroupState(EpisodeGroupStateEntity("show", "key", completedAt = 9L))
        assertNotEquals(withRows, dao.completedHistorySignal().first())
    }

    @Test
    fun aProgressSaveDoesNotReadTheCompletedIdsAgain() = runBlocking {
        dao.upsertAll(listOf(history("started", progress = 10_000L, playedAt = 5L), history("done", completed = true, playedAt = 4L)))
        val emissions = CopyOnWriteArrayList<List<String>>()
        val collector = launch(Dispatchers.Default) { dao.getCompletedEpisodeIdsFlow().collect { emissions += it } }
        try {
            awaitEmissions(emissions, 1)

            dao.updateProgress("started", progressMs = 20_000L, durationMs = DURATION, lastPlayedAt = 6L)
            delay(SETTLE_MS)
            assertEquals(1, emissions.size)

            dao.setCompletionStatus("started", true)
            awaitEmissions(emissions, 2)
            assertEquals(setOf("done", "started"), emissions.last().toSet())
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun onlyRealListeningCountsAsListening() {
        assertTrue(history("heard", progress = 1L).countsAsListening)
        assertTrue(history("finished", completed = true).countsAsListening)
        assertTrue(history("liked").copy(isLiked = true).countsAsListening)
        // Rows from before boxlore recorded listening dates carry 0: played, date unknown.
        assertTrue(history("old").copy(firstPlayedAt = 0L).countsAsListening)
        assertFalse(history("bulk", completed = true).copy(isBulkCompletion = true).countsAsListening)
        assertFalse(history("manual", completed = true).copy(isManualCompletion = true).countsAsListening)
        // Marked played and then unplayed without being heard.
        assertFalse(history("unmarked").countsAsListening)
    }

    private suspend fun awaitEmissions(emissions: List<List<String>>, count: Int) {
        // A suspending wait, so the timeout can end it and fail the test instead of hanging.
        val reached = withTimeoutOrNull(WAIT_MS) {
            while (emissions.size < count) delay(POLL_MS)
            true
        }
        assertTrue("expected $count emissions, got ${emissions.size}", reached == true)
    }

    /** The plan's steps that read every row of `listening_history` rather than an index. */
    private fun fullScans(sql: String): List<String> {
        val steps = ArrayList<String>()
        database.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
            val detail = cursor.getColumnIndexOrThrow("detail")
            while (cursor.moveToNext()) steps += cursor.getString(detail)
        }
        assertTrue(steps.isNotEmpty())
        return steps.filter { Regex("""^SCAN (TABLE )?listening_history( AS \w+)?$""").matches(it.trim()) }
    }

    /** One finished episode and one started long ago, both older than every marked row. */
    private fun realListening() = listOf(
        history("finished", completed = true, playedAt = 20L).copy(firstPlayedAt = 10L, finishedAt = 20L),
        history("started-long-ago", progress = 600_000L, playedAt = 10L).copy(firstPlayedAt = 9L),
    )

    private fun markedPlayed(count: Int) = (0 until count).map { history("bulk-$it", completed = true, playedAt = 1_000L + it).copy(isBulkCompletion = true) }

    private fun history(id: String, progress: Long = 0L, completed: Boolean = false, playedAt: Long = 1L,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = if (id.startsWith("bulk")) "back-catalogue" else "daily",
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = DURATION,
        isCompleted = completed,
        lastPlayedAt = playedAt,
    )

    private companion object {
        const val BULK_ROWS = 301
        const val RANKING_WINDOW = 300
        const val DURATION = 3_600_000L
        const val SETTLE_MS = 500L
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
