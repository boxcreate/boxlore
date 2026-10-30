package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
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
