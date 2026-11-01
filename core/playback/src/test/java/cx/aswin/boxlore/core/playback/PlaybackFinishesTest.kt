package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The player's own record of an episode played to the end, which the NPS survey and the review prompt
 * wait for. A replay counts again, though the stored finish keeps the first time (regression: a replay
 * never made a new finish moment).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackFinishesTest {
    private lateinit var database: BoxLoreDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        PlaybackFinishes.resetForTest()
    }

    @After
    fun tearDown() {
        database.close()
        PlaybackFinishes.resetForTest()
    }

    @Test
    fun playingToTheEndRecordsAFinishEveryTime() = runTest(testDispatcher) {
        database.listeningHistoryDao().upsert(history())
        val progress = progressCoordinator()

        progress.saveProgressSnapshot(snapshot(positionMs = 60_000L, sequence = 1L))
        assertNull("progress mid-episode is no finish", PlaybackFinishes.lastAtMs)

        progress.saveProgressSnapshot(snapshot(positionMs = 1_799_000L, sequence = 2L))
        val first = database.listeningHistoryDao().getHistoryItem(ID)!!.finishedAt
        assertNotNull(PlaybackFinishes.lastAtMs)

        // Played to the end again: a new finish here, while the row keeps its first finish time.
        PlaybackFinishes.resetForTest()
        progress.saveProgressSnapshot(snapshot(positionMs = 1_799_500L, sequence = 3L))
        assertNotNull(PlaybackFinishes.lastAtMs)
        assertTrue(database.listeningHistoryDao().getHistoryItem(ID)!!.finishedAt == first)
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

    private fun snapshot(positionMs: Long, sequence: Long) = PlaybackProgressSnapshot(
        sequence = sequence,
        episodeId = ID,
        positionMs = positionMs,
        durationMs = 1_800_000L,
        hasBeenPlayingFor10s = true,
        allowZeroPosition = false,
        episodeTitle = "Episode",
        episodeImageUrl = null,
        episodeAudioUrl = "https://example.com/$ID.mp3",
        podcastName = "Show",
        enclosureType = null,
    )

    private fun history() = ListeningHistoryEntity(
        episodeId = ID,
        podcastId = "show",
        episodeTitle = "Episode",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$ID.mp3",
        podcastName = "Show",
        progressMs = 0L,
        durationMs = 1_800_000L,
        isCompleted = false,
        isLiked = false,
        lastPlayedAt = 1L,
    )

    private companion object {
        const val ID = "episode"
    }
}
