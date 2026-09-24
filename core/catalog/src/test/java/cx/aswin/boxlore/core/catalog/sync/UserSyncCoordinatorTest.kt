package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserSyncCoordinatorTest {

    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var listeningHistoryDao: ListeningHistoryDao
    private lateinit var prefs: BoxcastPrefs
    private lateinit var fakeQueueSyncPort: FakeQueueSyncPort
    private lateinit var fakeBoxLoreApi: BoxLoreApi

    private var syncPushHandler: ((publicKey: String, auth: String?, request: SyncPushRequest) -> Call<SyncPushResponse>)? = null
    private var syncPullHandler: ((publicKey: String, auth: String?, request: SyncPullRequest) -> Call<SyncPullResponse>)? = null

    private lateinit var subscriptionSyncResolver: SubscriptionSyncResolver
    private lateinit var historySyncResolver: HistorySyncResolver
    private lateinit var queueSyncResolver: QueueSyncResolver
    private lateinit var coordinator: UserSyncCoordinator

    private val testDispatcher = UnconfinedTestDispatcher()
    private var currentUserId: String? = "test-user-123"
    private var currentToken: String? = "mock-jwt-token"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = BoxcastPrefs(context)

        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        podcastDao = database.podcastDao()
        listeningHistoryDao = database.listeningHistoryDao()

        fakeQueueSyncPort = FakeQueueSyncPort()
        syncPushHandler = null
        syncPullHandler = null

        fakeBoxLoreApi = Proxy.newProxyInstance(
            BoxLoreApi::class.java.classLoader,
            arrayOf(BoxLoreApi::class.java),
        ) { _, method, args ->
            when (method.name) {
                "syncPush" -> syncPushHandler?.invoke(args[0] as String, args[1] as? String, args[2] as SyncPushRequest)
                "syncPull" -> syncPullHandler?.invoke(args[0] as String, args[1] as? String, args[2] as SyncPullRequest)
                "hashCode" -> 42
                "equals" -> false
                "toString" -> "FakeBoxLoreApi"
                else -> throw UnsupportedOperationException("Method ${method.name} not implemented in FakeBoxLoreApi")
            }
        } as BoxLoreApi

        subscriptionSyncResolver = SubscriptionSyncResolver(
            podcastDao = podcastDao,
            folderRepository = null,
            podcastRepository = null,
            rssPodcastRepository = null,
        )

        historySyncResolver = HistorySyncResolver(
            listeningHistoryDao = listeningHistoryDao,
            activePlaybackSyncPort = null,
        )

        queueSyncResolver = QueueSyncResolver(
            queueSyncPort = fakeQueueSyncPort,
            database = database,
            activePlaybackSyncPort = null,
            podcastRepository = null,
        )

        coordinator = UserSyncCoordinator(
            boxLoreApi = fakeBoxLoreApi,
            publicKey = "test-public-key",
            authUserIdProvider = { currentUserId },
            tokenProvider = { currentToken },
            podcastDao = podcastDao,
            listeningHistoryDao = listeningHistoryDao,
            queueSyncPort = fakeQueueSyncPort,
            subscriptionSyncResolver = subscriptionSyncResolver,
            historySyncResolver = historySyncResolver,
            queueSyncResolver = queueSyncResolver,
            boxcastPrefs = prefs,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun syncNow_unauthenticated_returnsSkippedNoAuth() = runTest(testDispatcher) {
        currentUserId = null

        val result = coordinator.syncNow()

        assertEquals(SyncResult.SkippedNoAuth, result)
    }

    @Test
    fun syncNow_noTokenAvailable_returnsFailure() = runTest(testDispatcher) {
        currentUserId = "user-123"
        currentToken = null

        val result = coordinator.syncNow()

        assertTrue(result is SyncResult.Failure)
        assertEquals("No auth token available", (result as SyncResult.Failure).error.message)
    }

    @Test
    fun executePush_boundsBatchesToMax100() = runTest(testDispatcher) {
        // Insert 120 dirty podcasts
        for (i in 1..120) {
            podcastDao.upsert(
                createTestPodcastEntity(
                    podcastId = "pod-$i",
                    title = "Podcast $i",
                    isSubscribed = true,
                    isDirty = true,
                ),
            )
        }

        // Insert 110 dirty history items
        for (i in 1..110) {
            listeningHistoryDao.upsert(
                createTestHistoryEntity(
                    episodeId = "ep-$i",
                    podcastId = "pod-$i",
                    progressMs = 1000L,
                    isDirty = true,
                ),
            )
        }

        var capturedRequest: SyncPushRequest? = null
        syncPushHandler = { _, _, request ->
            capturedRequest = request
            FakeCall(Response.success(SyncPushResponse(status = "ok", syncedAt = 5000L)))
        }

        val pushResult = coordinator.executePush()
        assertTrue(pushResult.isSuccess)

        val request = checkNotNull(capturedRequest)
        assertEquals(UserSyncCoordinator.MAX_SUBSCRIPTION_BATCH_SIZE, request.subscriptions.size)
        assertEquals(100, request.subscriptions.size)
        assertEquals(UserSyncCoordinator.MAX_HISTORY_BATCH_SIZE, request.history.size)
        assertEquals(100, request.history.size)
    }

    @Test
    fun executePush_clearsDirtyFlagsOptimisticallyWhenUnchanged() = runTest(testDispatcher) {
        podcastDao.upsert(
            createTestPodcastEntity(
                podcastId = "pod-clean-test",
                title = "Clean Test",
                isSubscribed = true,
                subscribedAt = 1000L,
                unsubscribedAt = 0L,
                autoDownloadEnabled = false,
                notificationsEnabled = false,
                customGenre = null,
                isDirty = true,
            ),
        )

        listeningHistoryDao.upsert(
            createTestHistoryEntity(
                episodeId = "ep-clean-test",
                podcastId = "pod-clean-test",
                progressMs = 5000L,
                lastPlayedAt = 1000L,
                isCompleted = false,
                isLiked = false,
                likedAt = 0L,
                isDirty = true,
            ),
        )

        fakeQueueSyncPort.metadata = QueueMetadataEntity(
            id = 1,
            queueSequence = 3L,
            isDirty = true,
        )

        syncPushHandler = { _, _, _ ->
            FakeCall(Response.success(SyncPushResponse(status = "ok", syncedAt = 7000L)))
        }

        val pushResult = coordinator.executePush()
        assertTrue(pushResult.isSuccess)

        val podcast = podcastDao.getPodcast("pod-clean-test")
        assertNotNull(podcast)
        assertFalse(podcast!!.isDirty)
        assertEquals(7000L, podcast.syncedAt)

        val history = listeningHistoryDao.getHistoryItem("ep-clean-test")
        assertNotNull(history)
        assertFalse(history!!.isDirty)
        assertEquals(7000L, history.syncedAt)

        assertFalse(fakeQueueSyncPort.metadata.isDirty)
        assertEquals(7000L, fakeQueueSyncPort.metadata.syncedAt)
    }

    @Test
    fun executePush_preservesDirtyFlagWhenEntityModifiedConcurrently() = runTest(testDispatcher) {
        podcastDao.upsert(
            createTestPodcastEntity(
                podcastId = "pod-concurrent",
                title = "Concurrent Show",
                isSubscribed = true,
                subscribedAt = 1000L,
                unsubscribedAt = 0L,
                autoDownloadEnabled = false,
                notificationsEnabled = false,
                customGenre = null,
                isDirty = true,
            ),
        )

        syncPushHandler = { _, _, _ ->
            // Concurrent local update while push is in flight
            runBlocking {
                podcastDao.upsert(
                    createTestPodcastEntity(
                        podcastId = "pod-concurrent",
                        title = "Concurrent Show",
                        isSubscribed = true,
                        subscribedAt = 1000L,
                        unsubscribedAt = 0L,
                        autoDownloadEnabled = true, // Mutated!
                        notificationsEnabled = false,
                        customGenre = "New Genre", // Mutated!
                        isDirty = true,
                    ),
                )
            }
            FakeCall(Response.success(SyncPushResponse(status = "ok", syncedAt = 8000L)))
        }

        val pushResult = coordinator.executePush()
        assertTrue(pushResult.isSuccess)

        // Must still be dirty because snapshot fields did not match
        val podcast = podcastDao.getPodcast("pod-concurrent")
        assertNotNull(podcast)
        assertTrue(podcast!!.isDirty)
        assertTrue(podcast.autoDownloadEnabled)
        assertEquals("New Genre", podcast.customGenre)
    }

    @Test
    fun executePull_invokesResolversAndUpdateLastSyncTimestamp() = runTest(testDispatcher) {
        val pullResponse = SyncPullResponse(
            subscriptions = listOf(
                UserSubscriptionSyncDto(
                    podcastId = "pod-pulled",
                    isSubscribed = true,
                    subscribedAt = 1000L,
                    unsubscribedAt = 0L,
                    updatedAt = 1000L,
                ),
            ),
            history = listOf(
                ListeningHistorySyncDto(
                    episodeId = "ep-pulled",
                    podcastId = "pod-pulled",
                    progressMs = 12000L,
                    durationMs = 60000L,
                    isCompleted = false,
                    isLiked = true,
                    likedAt = 1500L,
                    lastPlayedAt = 1500L,
                    updatedAt = 1500L,
                ),
            ),
            queue = QueueSyncDto(
                queueSequence = 2L,
                queueUpdatedAt = 2000L,
                items = listOf(
                    QueueItemSyncDto(episodeId = "ep-pulled", podcastId = "pod-pulled", position = 0),
                ),
            ),
            syncedAt = 9000L,
        )

        syncPullHandler = { _, _, _ ->
            FakeCall(Response.success(pullResponse))
        }

        val pullResult = coordinator.executePull(since = 0L)
        assertTrue(pullResult.isSuccess)

        // Verify subscriptions resolved
        val sub = podcastDao.getPodcast("pod-pulled")
        assertNotNull(sub)
        assertTrue(sub!!.isSubscribed)

        // Verify history resolved
        val hist = listeningHistoryDao.getHistoryItem("ep-pulled")
        assertNotNull(hist)
        assertEquals(12000L, hist!!.progressMs)
        assertTrue(hist.isLiked)

        // Verify queue resolved
        val queueItems = fakeQueueSyncPort.appliedItems
        assertNotNull(queueItems)
        assertEquals(1, queueItems!!.size)
        assertEquals("ep-pulled", queueItems[0].episodeId)

        // Verify prefs lastSyncTimestamp updated
        assertEquals(9000L, prefs.getLastSyncTimestamp())
    }

    @Test
    fun syncNow_fullFlow_pushesThenPullsAndReturnsSuccess() = runTest(testDispatcher) {
        podcastDao.upsert(
            createTestPodcastEntity(
                podcastId = "pod-local-delta",
                title = "Local Delta",
                isSubscribed = true,
                isDirty = true,
            ),
        )

        syncPushHandler = { _, _, _ ->
            FakeCall(Response.success(SyncPushResponse(status = "ok", syncedAt = 4000L)))
        }

        val pullResponse = SyncPullResponse(
            subscriptions = listOf(
                UserSubscriptionSyncDto(
                    podcastId = "pod-remote-incoming",
                    isSubscribed = true,
                    subscribedAt = 3000L,
                    unsubscribedAt = 0L,
                    updatedAt = 3000L,
                ),
            ),
            history = emptyList(),
            queue = null,
            syncedAt = 5000L,
        )

        syncPullHandler = { _, _, _ ->
            FakeCall(Response.success(pullResponse))
        }

        val result = coordinator.syncNow()
        assertTrue(result is SyncResult.Success)

        val success = result as SyncResult.Success
        assertEquals(1, success.pushedSubscriptions)
        assertEquals(0, success.pushedHistory)
        assertFalse(success.pushedQueue)
        assertEquals(1, success.pulledSubscriptions)
        assertEquals(0, success.pulledHistory)
        assertFalse(success.pulledQueue)
        assertEquals(5000L, success.syncedAt)
        assertEquals(5000L, prefs.getLastSyncTimestamp())
    }

    @Test
    fun syncNow_accountSwitch_purgesLocalDataAndPullsWithoutPushingOldData() = runTest(testDispatcher) {
        // User A was synced previously
        prefs.setLastSyncedUserId("user-A")
        prefs.setLastSyncTimestamp(5000L)

        // Local data from User A exists in DB
        podcastDao.upsert(
            createTestPodcastEntity(
                podcastId = "user-a-pod",
                title = "User A Podcast",
                isSubscribed = true,
                isDirty = true,
            ),
        )
        listeningHistoryDao.upsert(
            createTestHistoryEntity(
                episodeId = "user-a-ep",
                podcastId = "user-a-pod",
                progressMs = 12345L,
                isDirty = true,
            ),
        )
        fakeQueueSyncPort.items = mutableListOf(
            QueueItem(
                episodeId = "user-a-ep",
                title = "User A Ep",
                podcastId = "user-a-pod",
                podcastTitle = "User A Podcast",
                imageUrl = null,
                audioUrl = "",
                duration = 100,
                pubDate = 0L,
                description = null,
                position = 0,
            ),
        )
        fakeQueueSyncPort.metadata = QueueMetadataEntity(id = 1, queueSequence = 5L, isDirty = true)

        // Now User B is logged in
        currentUserId = "user-B"
        currentToken = "user-b-token"

        var pushCalled = false
        syncPushHandler = { _, _, _ ->
            pushCalled = true
            FakeCall(Response.success(SyncPushResponse(status = "ok", syncedAt = 6000L)))
        }

        var pullSince: Long? = null
        syncPullHandler = { _, _, req ->
            pullSince = req.since
            FakeCall(
                Response.success(
                    SyncPullResponse(
                        subscriptions = listOf(
                            UserSubscriptionSyncDto(
                                podcastId = "user-b-pod",
                                isSubscribed = true,
                                subscribedAt = 1000L,
                                unsubscribedAt = 0L,
                                updatedAt = 1000L,
                            ),
                        ),
                        history = emptyList(),
                        queue = null,
                        syncedAt = 8000L,
                    ),
                ),
            )
        }

        val result = coordinator.syncNow()
        assertTrue(result is SyncResult.Success)

        // Push MUST NOT have been called with User A's data!
        assertFalse("Push must not be called on account switch", pushCalled)

        // Pull MUST have been called with since = 0L
        assertEquals(0L, pullSince)

        // User A's history must be completely purged
        val historyA = listeningHistoryDao.getHistoryItem("user-a-ep")
        org.junit.Assert.assertNull(historyA)

        // User A's podcast subscription must be reset
        val podA = podcastDao.getPodcast("user-a-pod")
        assertNotNull(podA)
        assertFalse(podA!!.isSubscribed)
        assertFalse(podA.isDirty)

        // User B's podcast subscription must be present
        val podB = podcastDao.getPodcast("user-b-pod")
        assertNotNull(podB)
        assertTrue(podB!!.isSubscribed)

        // Prefs must be updated to User B
        assertEquals("user-B", prefs.getLastSyncedUserId())
        assertEquals(8000L, prefs.getLastSyncTimestamp())
    }

    @Suppress("LongParameterList")
    private fun createTestPodcastEntity(
        podcastId: String,
        title: String = "Test Show",
        isSubscribed: Boolean = false,
        subscribedAt: Long = 0L,
        unsubscribedAt: Long = 0L,
        isDirty: Boolean = false,
        syncedAt: Long = 0L,
        autoDownloadEnabled: Boolean = false,
        notificationsEnabled: Boolean = false,
        customGenre: String? = null,
    ) = PodcastEntity(
        podcastId = podcastId,
        title = title,
        author = "Test Author",
        imageUrl = "https://example.com/art.jpg",
        description = "Test description",
        isSubscribed = isSubscribed,
        subscribedAt = subscribedAt,
        unsubscribedAt = unsubscribedAt,
        isDirty = isDirty,
        syncedAt = syncedAt,
        autoDownloadEnabled = autoDownloadEnabled,
        notificationsEnabled = notificationsEnabled,
        customGenre = customGenre,
    )

    @Suppress("LongParameterList")
    private fun createTestHistoryEntity(
        episodeId: String,
        podcastId: String,
        progressMs: Long = 0L,
        durationMs: Long = 120000L,
        isCompleted: Boolean = false,
        isLiked: Boolean = false,
        likedAt: Long = 0L,
        lastPlayedAt: Long = 0L,
        isDirty: Boolean = false,
        syncedAt: Long = 0L,
    ) = ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode Title $episodeId",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/audio.mp3",
        podcastName = "Podcast Name $podcastId",
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        isLiked = isLiked,
        likedAt = likedAt,
        lastPlayedAt = lastPlayedAt,
        isDirty = isDirty,
        syncedAt = syncedAt,
    )

    private class FakeCall<T>(private val response: Response<T>) : Call<T> {
        override fun execute(): Response<T> = response
        override fun enqueue(callback: Callback<T>) {
            callback.onResponse(this, response)
        }
        override fun isExecuted(): Boolean = true
        override fun cancel() {}
        override fun isCanceled(): Boolean = false
        override fun clone(): Call<T> = this
        override fun request(): Request = Request.Builder().url("https://example.com/sync").build()
        override fun timeout(): Timeout = Timeout.NONE
    }

    private class FakeQueueSyncPort(
        var items: MutableList<QueueItem> = mutableListOf(),
        var metadata: QueueMetadataEntity = QueueMetadataEntity(id = 1),
    ) : QueueSyncPort {
        var appliedItems: List<QueueItem>? = null
        var appliedMetadata: QueueMetadataEntity? = null

        override suspend fun getQueueSnapshot(): List<QueueItem> = items.toList()
        override suspend fun getQueueMetadata(): QueueMetadataEntity? = metadata
        override suspend fun applyRemoteQueueState(items: List<QueueItem>, metadata: QueueMetadataEntity) {
            this.items = items.toMutableList()
            this.metadata = metadata
            this.appliedItems = items
            this.appliedMetadata = metadata
        }
        override suspend fun markQueueSynced(expectedSequence: Long, syncedAt: Long): Boolean =
            if (metadata.queueSequence == expectedSequence) {
                metadata = metadata.copy(isDirty = false, syncedAt = syncedAt)
                true
            } else {
                false
            }
    }
}
