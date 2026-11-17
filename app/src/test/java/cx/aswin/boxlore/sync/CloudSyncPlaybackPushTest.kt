package cx.aswin.boxlore.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library pushes while an episode plays: its own progress saves push at most once per
 * [CloudSyncTriggerCoordinator.PLAYING_PROGRESS_PUSH_INTERVAL_MS], everything else as soon as usual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CloudSyncPlaybackPushTest {

    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var listeningHistoryDao: ListeningHistoryDao
    private lateinit var fakeCoordinator: FakeTriggerSyncCoordinator
    private lateinit var fakeAuthRepository: FakeTriggerAuthRepository
    private lateinit var playerStateFlow: MutableStateFlow<PlayerState>
    private var currentTime = 100_000L
    private var currentCoordinator: CloudSyncTriggerCoordinator? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Dispatchers.Unconfined.asExecutor())
            .setTransactionExecutor(Dispatchers.Unconfined.asExecutor())
            .build()
        listeningHistoryDao = database.listeningHistoryDao()
        fakeCoordinator = FakeTriggerSyncCoordinator()
        fakeAuthRepository = FakeTriggerAuthRepository()
        fakeAuthRepository.currentUser.value = BoxLoreUser(
            uid = "user-1",
            email = "test@example.com",
            displayName = "Test User",
            isEmailVerified = true,
            providerId = "password",
        )
        playerStateFlow = MutableStateFlow(PlayerState())
    }

    @After
    fun tearDown() {
        currentCoordinator?.stop()
        database.close()
    }

    private fun createCoordinator(
        testScope: TestScope,
        playingHistoryIds: suspend (String) -> List<String> = { listOf(it) },
    ): CloudSyncTriggerCoordinator {
        val coordinator = CloudSyncTriggerCoordinator(
            context = context,
            applicationScope = testScope,
            userSyncCoordinator = fakeCoordinator,
            authRepository = fakeAuthRepository,
            boxcastPrefs = BoxcastPrefs(context),
            podcastDao = database.podcastDao(),
            listeningHistoryDao = listeningHistoryDao,
            queueDao = database.queueDao(),
            playerStateFlow = playerStateFlow,
            isOnlineFlow = MutableStateFlow(true),
            processLifecycle = TriggerTestLifecycleOwner().lifecycle,
            clock = { currentTime },
            ioDispatcher = StandardTestDispatcher(testScope.testScheduler),
            playingHistoryIds = playingHistoryIds,
        )
        currentCoordinator = coordinator
        return coordinator
    }

    private fun testHistory(id: String, isDirty: Boolean = true) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "pod-1",
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/audio.mp3",
        podcastName = "Podcast",
        progressMs = 100L,
        durationMs = 1000L,
        isCompleted = false,
        isLiked = true,
        lastPlayedAt = 500L,
        isDirty = isDirty,
    )

    private fun createTestEpisode(id: String) = Episode(
        id = id,
        title = "Episode $id",
        description = "Description",
        audioUrl = "https://example.com/$id.mp3",
        podcastId = "pod-1",
        podcastTitle = "Podcast 1",
        duration = 3600,
        publishedDate = 1000L,
    )

    /** A push clears what it sent, like the real one, so later counts reflect only new saves. */
    private fun pushClearsDirtyHistory() {
        fakeCoordinator.onPush = {
            val ids = listeningHistoryDao.getDirtyListeningHistory().map { it.episodeId }
            if (ids.isNotEmpty()) listeningHistoryDao.markListeningHistorySynced(ids, currentTime)
        }
    }

    /** Saves progress for [historyId] every 10 s of virtual time, as the playback ticker does. */
    private suspend fun TestScope.playWithProgressTicks(historyId: String, seconds: Int) {
        for (tick in 1..seconds / PROGRESS_TICK_SECONDS) {
            advanceTimeBy(PROGRESS_TICK_SECONDS * 1_000L)
            listeningHistoryDao.updateProgress(
                historyId,
                progressMs = tick * PROGRESS_TICK_SECONDS * 1_000L,
                durationMs = 3_600_000L,
                lastPlayedAt = currentTime + tick,
            )
            runCurrent()
        }
    }

    // Regression: each 10-second progress save marked the row dirty and started a push of its own.
    @Test
    fun playbackSteadyProgress_zeroNetworkCalls() = runTest {
        listeningHistoryDao.upsert(testHistory("ep-1", isDirty = false))
        pushClearsDirtyHistory()
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        val ep = createTestEpisode("ep-1")
        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep, position = 1000L)
        runCurrent()

        playWithProgressTicks("ep-1", seconds = 180)
        assertTrue(listeningHistoryDao.hasDirtyListeningHistory())
        assertEquals(0, fakeCoordinator.executePushCalls)

        playerStateFlow.value = PlayerState(isPlaying = false, currentEpisode = ep, position = 180_000L)
        advanceTimeBy(CloudSyncTriggerCoordinator.LIBRARY_MUTATION_DEBOUNCE_MS * 2)
        runCurrent()
        assertEquals(1, fakeCoordinator.executePushCalls)
        assertEquals(false, listeningHistoryDao.hasDirtyListeningHistory())
    }

    @Test
    fun longPlayback_pushesProgressAtMostOncePerInterval() = runTest {
        listeningHistoryDao.upsert(testHistory("ep-1", isDirty = false))
        pushClearsDirtyHistory()
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = createTestEpisode("ep-1"))
        runCurrent()

        // An hour of 10-second saves: about 360 pushes before; now one per interval, so the cloud still gets progress.
        playWithProgressTicks("ep-1", seconds = 3_600)
        val perInterval = (3_600_000L / CloudSyncTriggerCoordinator.PLAYING_PROGRESS_PUSH_INTERVAL_MS).toInt()
        assertTrue("pushes: ${fakeCoordinator.executePushCalls}", fakeCoordinator.executePushCalls in 1..perInterval)
    }

    @Test
    fun playback_progressOnTheOwnerIdOfThePlayingEpisode_doesNotPush() = runTest {
        listeningHistoryDao.upsert(testHistory("ep-owner", isDirty = false))
        val coordinator = createCoordinator(this, playingHistoryIds = { listOf("ep-owner", it) })
        coordinator.start()
        advanceUntilIdle()

        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = createTestEpisode("ep-1"))
        runCurrent()

        playWithProgressTicks("ep-owner", seconds = 120)
        assertEquals(0, fakeCoordinator.executePushCalls)
    }

    @Test
    fun playback_otherChangesAndALikeOfThePlayingEpisode_stillPushSoon() = runTest {
        listeningHistoryDao.upsert(testHistory("ep-1", isDirty = false).copy(isLiked = false))
        listeningHistoryDao.upsert(testHistory("ep-other", isDirty = false).copy(isLiked = false))
        pushClearsDirtyHistory()
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        val ep = createTestEpisode("ep-1")
        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep)
        runCurrent()
        playWithProgressTicks("ep-1", seconds = 30)
        assertEquals(0, fakeCoordinator.executePushCalls)

        // A like on another episode pushes after the usual short wait.
        listeningHistoryDao.setLikeStatus("ep-other", isLiked = true, now = currentTime)
        playWithProgressTicks("ep-1", seconds = 10)
        assertEquals(1, fakeCoordinator.executePushCalls)

        // A like on the playing episode does too.
        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep, isLiked = true)
        listeningHistoryDao.setLikeStatus("ep-1", isLiked = true, now = currentTime)
        playWithProgressTicks("ep-1", seconds = 10)
        assertEquals(2, fakeCoordinator.executePushCalls)
    }

    // Regression: while playing, each progress save re-ran the dirty counts, so a row a failed push
    // left dirty started another push on every save.
    @Test
    fun playback_rowsLeftDirtyByAFailedPush_doNotPushOnEveryProgressSave() = runTest {
        listeningHistoryDao.upsert(testHistory("ep-1", isDirty = false))
        listeningHistoryDao.upsert(testHistory("ep-other", isDirty = true))
        database.podcastDao().upsertRow(testPodcast("pod-dirty"))
        fakeCoordinator.executePushResult = Result.failure(IllegalStateException("offline"))
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()
        val pushesBeforePlayback = fakeCoordinator.executePushCalls

        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = createTestEpisode("ep-1"))
        runCurrent()
        playWithProgressTicks("ep-1", seconds = 180)

        assertTrue(listeningHistoryDao.hasDirtyListeningHistory())
        assertTrue(database.podcastDao().hasDirtyPodcasts())
        val pushesWhilePlaying = fakeCoordinator.executePushCalls - pushesBeforePlayback
        assertTrue("pushes while playing: $pushesWhilePlaying", pushesWhilePlaying <= 1)
    }

    @Test
    fun playback_rowsLeftDirtyByAFailedPush_aNewChangeStillPushes() = runTest {
        listeningHistoryDao.upsert(testHistory("ep-1", isDirty = false))
        listeningHistoryDao.upsert(testHistory("ep-other", isDirty = true))
        listeningHistoryDao.upsert(testHistory("ep-new", isDirty = false).copy(isLiked = false))
        fakeCoordinator.executePushResult = Result.failure(IllegalStateException("offline"))
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = createTestEpisode("ep-1"))
        runCurrent()
        playWithProgressTicks("ep-1", seconds = 60)
        val pushesBeforeLike = fakeCoordinator.executePushCalls

        // A like on a third episode raises the waiting count, so it pushes after the usual short wait.
        listeningHistoryDao.setLikeStatus("ep-new", isLiked = true, now = currentTime)
        playWithProgressTicks("ep-1", seconds = 10)
        assertEquals(pushesBeforeLike + 1, fakeCoordinator.executePushCalls)

        playWithProgressTicks("ep-1", seconds = 60)
        assertEquals(pushesBeforeLike + 1, fakeCoordinator.executePushCalls)
    }

    private fun testPodcast(id: String) = PodcastEntity(
        podcastId = id,
        title = "Podcast $id",
        author = "Author",
        imageUrl = "https://example.com/art.jpg",
        description = "Desc",
        isDirty = true,
    )

    private companion object {
        const val PROGRESS_TICK_SECONDS = 10
    }
}
