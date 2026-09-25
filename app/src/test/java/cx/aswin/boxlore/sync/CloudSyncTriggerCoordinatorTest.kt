package cx.aswin.boxlore.sync

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.catalog.sync.SyncResult
import cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CloudSyncTriggerCoordinatorTest {

    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var listeningHistoryDao: ListeningHistoryDao
    private lateinit var queueDao: QueueDao
    private lateinit var prefs: BoxcastPrefs

    private lateinit var fakeCoordinator: FakeUserSyncCoordinator
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var playerStateFlow: MutableStateFlow<PlayerState>
    private lateinit var isOnlineFlow: MutableStateFlow<Boolean>
    private lateinit var testLifecycleOwner: TestLifecycleOwner

    private var currentTime = 100_000L
    private var currentCoordinator: CloudSyncTriggerCoordinator? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = BoxcastPrefs(context)

        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(kotlinx.coroutines.Dispatchers.Unconfined.asExecutor())
            .setTransactionExecutor(kotlinx.coroutines.Dispatchers.Unconfined.asExecutor())
            .build()
        podcastDao = database.podcastDao()
        listeningHistoryDao = database.listeningHistoryDao()
        queueDao = database.queueDao()

        fakeCoordinator = FakeUserSyncCoordinator()
        fakeAuthRepository = FakeAuthRepository()
        playerStateFlow = MutableStateFlow(PlayerState())
        isOnlineFlow = MutableStateFlow(true)
        testLifecycleOwner = TestLifecycleOwner()
        currentTime = 100_000L
        currentCoordinator = null
    }

    @After
    fun tearDown() {
        currentCoordinator?.stop()
        database.close()
    }

    private fun testUser(uid: String = "user-1") = BoxLoreUser(
        uid = uid,
        email = "test@example.com",
        displayName = "Test User",
    )

    private fun testPodcast(id: String, isDirty: Boolean = true) = PodcastEntity(
        podcastId = id,
        title = "Podcast $id",
        author = "Author",
        imageUrl = "https://example.com/art.jpg",
        description = "Desc",
        isDirty = isDirty,
    )

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

    private fun createCoordinator(
        testScope: TestScope,
        onSignOutAction: (() -> Unit)? = null,
    ): CloudSyncTriggerCoordinator {
        val coordinator = CloudSyncTriggerCoordinator(
            context = context,
            applicationScope = testScope,
            userSyncCoordinator = fakeCoordinator,
            authRepository = fakeAuthRepository,
            boxcastPrefs = prefs,
            podcastDao = podcastDao,
            listeningHistoryDao = listeningHistoryDao,
            queueDao = queueDao,
            playbackRepository = null,
            playerStateFlow = playerStateFlow,
            isOnlineFlow = isOnlineFlow,
            processLifecycle = testLifecycleOwner.lifecycle,
            clock = { currentTime },
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScope.testScheduler),
            onSignOutAction = onSignOutAction,
        )
        currentCoordinator = coordinator
        return coordinator
    }

    @Test
    fun initialStatus_idle_whenNoLastSync() = runTest {
        val coordinator = createCoordinator(this)
        assertEquals(CloudSyncUiStatus.Idle, coordinator.syncStatusFlow.value)
    }

    @Test
    fun initialStatus_success_whenLastSyncTimestampPresent() = runTest {
        prefs.setLastSyncTimestamp(55_000L)
        val coordinator = createCoordinator(this)
        assertEquals(CloudSyncUiStatus.Success(55_000L), coordinator.syncStatusFlow.value)
    }

    @Test
    fun authSignIn_triggersSyncNow() = runTest {
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        assertEquals(0, fakeCoordinator.syncNowCalls)

        fakeAuthRepository.currentUser.value = testUser("user-1")
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.syncNowCalls)
        assertTrue(coordinator.syncStatusFlow.value is CloudSyncUiStatus.Success)
    }

    @Test
    fun authSignOut_resetsTimestampsAndStatusToIdle() = runTest {
        prefs.setLastSyncTimestamp(99_000L)
        prefs.setLastSyncedUserId("user-1")
        fakeAuthRepository.currentUser.value = testUser("user-1")

        var signOutActionCalled = false
        val coordinator = createCoordinator(this, onSignOutAction = { signOutActionCalled = true })
        coordinator.start()
        advanceUntilIdle()

        fakeAuthRepository.currentUser.value = null
        advanceUntilIdle()

        assertEquals(0L, prefs.getLastSyncTimestamp())
        assertEquals("user-1", prefs.getLastSyncedUserId())
        assertEquals(CloudSyncUiStatus.Idle, coordinator.syncStatusFlow.value)
        assertTrue(signOutActionCalled)
    }

    @Test
    fun authSignIn_afterSignOutWithDifferentUser_preservesPreviousUserForAccountSwitch() = runTest {
        prefs.setLastSyncTimestamp(99_000L)
        prefs.setLastSyncedUserId("user-1")
        fakeAuthRepository.currentUser.value = testUser("user-1")

        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        // User 1 signs out
        fakeAuthRepository.currentUser.value = null
        advanceUntilIdle()

        assertEquals(0L, prefs.getLastSyncTimestamp())
        // Crucial: lastSyncedUserId must not be erased on sign-out so the next login detects the account switch
        assertEquals("user-1", prefs.getLastSyncedUserId())

        // User 2 signs in
        fakeAuthRepository.currentUser.value = testUser("user-2")
        advanceUntilIdle()

        assertEquals(2, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun onStart_triggersSyncNow_whenAuthenticated() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        // Account initial emit syncs once
        assertEquals(1, fakeCoordinator.syncNowCalls)

        // Advance beyond throttle
        currentTime += 20_000L
        coordinator.onStart(testLifecycleOwner)
        advanceUntilIdle()

        assertEquals(2, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun onStart_throttles_within15Seconds() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        currentTime += 20_000L
        coordinator.onStart(testLifecycleOwner)
        advanceUntilIdle()
        assertEquals(2, fakeCoordinator.syncNowCalls)

        // Try again 5 seconds later
        currentTime += 5_000L
        coordinator.onStart(testLifecycleOwner)
        advanceUntilIdle()

        // Still 2 calls!
        assertEquals(2, fakeCoordinator.syncNowCalls)

        // Try after 15 seconds have elapsed
        currentTime += 16_000L
        coordinator.onStart(testLifecycleOwner)
        advanceUntilIdle()

        // Now 3 calls!
        assertEquals(3, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun onStop_onlineAndPushSucceeds_pushesWithoutWorkManager() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        coordinator.onStop(testLifecycleOwner)
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.executePushCalls)
        assertEquals(CloudSyncUiStatus.Success(77_000L), coordinator.syncStatusFlow.value)
    }

    @Test
    fun onStop_offline_enqueuesWorkManager() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        isOnlineFlow.value = false

        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        coordinator.onStop(testLifecycleOwner)
        advanceUntilIdle()

        // When offline, executePush is skipped
        assertEquals(0, fakeCoordinator.executePushCalls)
    }

    @Test
    fun onStop_pushFails_enqueuesWorkManager() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        fakeCoordinator.executePushResult = Result.failure(RuntimeException("Network timeout"))

        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        coordinator.onStop(testLifecycleOwner)
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.executePushCalls)
    }

    @Test
    fun onStop_pushTimesOut_enqueuesWorkManager() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        // Simulates a stalled/hanging network call that exceeds the 3s background timeout
        fakeCoordinator.executePushDelayMs = 5_000L

        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        coordinator.onStop(testLifecycleOwner)
        // Advance past the 3,000ms timeout
        advanceTimeBy(3_100L)
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.executePushCalls)
    }

    @Test
    fun connectivityReconnect_triggersSyncNow_whenDirtyItemsExist() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        val initialCalls = fakeCoordinator.syncNowCalls

        // Insert dirty row
        podcastDao.upsert(testPodcast("dirty_pod", isDirty = true))

        // Transition false -> true
        isOnlineFlow.value = false
        advanceUntilIdle()
        isOnlineFlow.value = true
        advanceUntilIdle()

        assertEquals(initialCalls + 1, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun connectivityReconnect_doesNotTrigger_whenNoDirtyItems() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        val initialCalls = fakeCoordinator.syncNowCalls

        // Transition false -> true without dirty items
        isOnlineFlow.value = false
        advanceUntilIdle()
        isOnlineFlow.value = true
        advanceUntilIdle()

        assertEquals(initialCalls, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun playbackPause_triggersImmediatePush() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        val ep = createTestEpisode("ep-1")
        // Playing
        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep)
        advanceUntilIdle()

        assertEquals(0, fakeCoordinator.executePushCalls)

        // Paused
        playerStateFlow.value = PlayerState(isPlaying = false, currentEpisode = ep)
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.executePushCalls)
    }

    @Test
    fun playbackComplete_triggersImmediatePush() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        val ep = createTestEpisode("ep-1")
        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep, isCompleted = false)
        advanceUntilIdle()

        assertEquals(0, fakeCoordinator.executePushCalls)

        // Completed
        playerStateFlow.value = PlayerState(isPlaying = false, currentEpisode = ep, isCompleted = true)
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.executePushCalls)
    }

    @Test
    fun playbackTrackChange_triggersImmediatePush() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        val ep1 = createTestEpisode("ep-1")
        val ep2 = createTestEpisode("ep-2")

        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep1)
        advanceUntilIdle()

        assertEquals(0, fakeCoordinator.executePushCalls)

        // Change track
        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep2)
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.executePushCalls)
    }

    @Test
    fun playbackSteadyProgress_zeroNetworkCalls() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        val ep = createTestEpisode("ep-1")
        playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep, position = 1000L)
        advanceUntilIdle()

        // Continuous progress ticks while playing
        for (pos in 2000L..10000L step 1000L) {
            playerStateFlow.value = PlayerState(isPlaying = true, currentEpisode = ep, position = pos)
            advanceUntilIdle()
        }

        // ZERO pushes executed!
        assertEquals(0, fakeCoordinator.executePushCalls)
    }

    @Test
    fun queueMutation_debouncedBy2000Ms() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        assertEquals(0, fakeCoordinator.executePushCalls)

        // Queue marked dirty
        queueDao.upsertQueueMetadata(QueueMetadataEntity(id = 1, isDirty = true, queueSequence = 1L))
        advanceTimeBy(1000L)

        // Still in debounce window
        assertEquals(0, fakeCoordinator.executePushCalls)

        // Rapid second update
        queueDao.upsertQueueMetadata(QueueMetadataEntity(id = 1, isDirty = true, queueSequence = 2L))
        advanceTimeBy(1000L)
        assertEquals(0, fakeCoordinator.executePushCalls)

        // Wait out full 2000ms
        advanceTimeBy(1100L)
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.executePushCalls)
    }

    @Test
    fun libraryMutation_debouncedBy1500Ms() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        val coordinator = createCoordinator(this)
        coordinator.start()
        advanceUntilIdle()

        assertEquals(0, fakeCoordinator.executePushCalls)

        // User likes an episode
        listeningHistoryDao.upsert(testHistory("ep-like", isDirty = true))

        advanceTimeBy(1000L)
        assertEquals(0, fakeCoordinator.executePushCalls)

        // Complete 1500ms debounce
        advanceTimeBy(600L)
        advanceUntilIdle()

        assertEquals(1, fakeCoordinator.executePushCalls)
    }

    @Test
    fun triggerManualSync_returnsResultAndUpdatesStatus() = runTest {
        fakeAuthRepository.currentUser.value = testUser("user-1")
        fakeCoordinator.syncNowResult = SyncResult.Success(
            pushedSubscriptions = 5,
            pushedHistory = 3,
            pushedQueue = false,
            pulledSubscriptions = 2,
            pulledHistory = 1,
            pulledQueue = false,
            syncedAt = 88888L,
        )

        val coordinator = createCoordinator(this)
        coordinator.syncStatusFlow.test {
            assertEquals(CloudSyncUiStatus.Idle, awaitItem())

            val syncJob = this@runTest.launch {
                val result = coordinator.triggerManualSync()
                assertTrue(result is SyncResult.Success)
            }
            assertEquals(CloudSyncUiStatus.Syncing, awaitItem())
            assertEquals(CloudSyncUiStatus.Success(88888L), awaitItem())
            syncJob.join()
        }
    }

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

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class FakeUserSyncCoordinator : UserSyncCoordinator() {
        var syncNowCalls = 0
        var executePushCalls = 0
        var executePushDelayMs = 0L
        var syncNowResult: SyncResult = SyncResult.Success(0, 0, false, 0, 0, false, 12345L)
        var executePushResult: Result<PushBatchSummary> = Result.success(PushBatchSummary(0, 0, false, 77_000L))

        override suspend fun syncNow(): SyncResult {
            syncNowCalls++
            return syncNowResult
        }

        override suspend fun executePush(token: String?): Result<PushBatchSummary> {
            executePushCalls++
            if (executePushDelayMs > 0) {
                kotlinx.coroutines.delay(executePushDelayMs)
            }
            return executePushResult
        }
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUser = MutableStateFlow<BoxLoreUser?>(null)
        override val currentUserId: String? get() = currentUser.value?.uid

        override suspend fun signInWithGoogle(idToken: String): Result<BoxLoreUser> = error("unused")
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<BoxLoreUser> = error("unused")
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<BoxLoreUser> = error("unused")
        override suspend fun sendMagicLink(email: String): Result<Unit> = error("unused")
        override suspend fun signInWithEmailLink(email: String, emailLink: String): Result<BoxLoreUser> = error("unused")
        override fun isSignInWithEmailLink(link: String): Boolean = false
        override suspend fun sendPasswordReset(email: String): Result<Unit> = error("unused")
        override fun signOut() {
            currentUser.value = null
        }
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun getIdToken(forceRefresh: Boolean): String? = "mock-token"
    }
}
