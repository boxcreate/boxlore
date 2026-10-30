package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.PlayedShowEntity
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.model.ListeningDaySyncDto
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.PlayedShowSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Cloud sync of listening (Room v40): history dates both ways, this phone's daily listening and the
 * played shows pushed and marked sent only by a server that stores them, every phone's listening pulled
 * from its own mark and counted once, and an older server leaving everything as it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListeningCloudSyncTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var prefs: BoxcastPrefs
    private lateinit var coordinator: UserSyncCoordinator
    private val testDispatcher = UnconfinedTestDispatcher()
    private val pushes = mutableListOf<SyncPushRequest>()
    private val pulls = mutableListOf<SyncPullRequest>()
    private var serverStoresListening = true
    private var pullAnswer = SyncPullResponse(syncedAt = 9_000L)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = BoxcastPrefs(context)
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        val api =
            Proxy.newProxyInstance(BoxLoreApi::class.java.classLoader, arrayOf(BoxLoreApi::class.java)) { _, method, args ->
                when (method.name) {
                    "syncPush" -> {
                        pushes += args[2] as SyncPushRequest
                        FakeCall(Response.success(SyncPushResponse(syncedAt = 5_000L, listeningAccepted = serverStoresListening)))
                    }
                    "syncPull" -> {
                        pulls += args[2] as SyncPullRequest
                        FakeCall(Response.success(pullAnswer))
                    }
                    "hashCode" -> 42
                    "equals" -> false
                    "toString" -> "FakeBoxLoreApi"
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as BoxLoreApi
        coordinator =
            UserSyncCoordinator(
                boxLoreApi = api,
                publicKey = "key",
                authUserIdProvider = { "user" },
                tokenProvider = { "token" },
                podcastDao = database.podcastDao(),
                listeningHistoryDao = database.listeningHistoryDao(),
                queueSyncPort = CleanQueue(),
                subscriptionSyncResolver = SubscriptionSyncResolver(database.podcastDao(), null, null, null),
                historySyncResolver = HistorySyncResolver(database.listeningHistoryDao(), null),
                queueSyncResolver = QueueSyncResolver(CleanQueue(), database, null, null),
                boxcastPrefs = prefs,
                listeningSessionDao = database.listeningSessionDao(),
                listeningRollupDao = database.listeningRollupDao(),
                ioDispatcher = testDispatcher,
                episodeIdentityDao = database.episodeIdentityDao(),
                playedShowDao = database.playedShowDao(),
                listeningInsightsMaintenance = database.listeningInsightsMaintenance(),
            )
        prefs.setLastSyncedUserId("user")
        prefs.setLastSyncTimestamp(1L)
        prefs.setSyncMetadataVersion(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun thisPhonesListeningGoesUpAndIsMarkedSentOnlyByAServerThatStoresIt() = runTest(testDispatcher) {
        seedOwnListening()
        serverStoresListening = false

        coordinator.syncNow()

        // An older server: everything was sent but nothing is marked, so it all goes again later.
        val first = pushes.single()
        assertEquals(listOf(10L to 120_000L, 11L to 60_000L), first.listeningDays.map { it.localDay to it.consumedMs })
        assertEquals(setOf(prefs.getOrCreateSyncDeviceId()), first.listeningDays.map { it.deviceId }.toSet())
        assertEquals(listOf("100"), first.playedShows.map { it.podcastId })
        assertEquals(0L, prefs.getListeningPushedUntil())
        assertTrue(database.playedShowDao().getPlayedShow("100")!!.isDirty)

        serverStoresListening = true
        coordinator.syncNow()

        assertEquals(2, pushes[1].listeningDays.size)
        assertEquals(11L * 1_000L + 60_000L, prefs.getListeningPushedUntil())
        assertFalse(database.playedShowDao().getPlayedShow("100")!!.isDirty)

        // Nothing new: no listening goes up (the last minutes' days go again only with a newer push).
        pushes.clear()
        coordinator.syncNow()
        assertTrue(pushes.isEmpty() || pushes.single().playedShows.isEmpty())
    }

    @Test
    fun historyDatesGoUpAndComeDownEarliestFirst() = runTest(testDispatcher) {
        database.listeningHistoryDao().upsert(history("ep-1").copy(firstPlayedAt = 500L, finishedAt = 900L))
        pullAnswer =
            SyncPullResponse(
                history = listOf(ListeningHistorySyncDto(episodeId = "ep-1", podcastId = "100", lastPlayedAt = 50L, updatedAt = 60L, firstPlayedAt = 0L, finishedAt = 950L)),
                syncedAt = 9_000L,
            )

        coordinator.syncNow()

        val sent = pushes.single().history.single()
        assertEquals(500L to 900L, sent.firstPlayedAt to sent.finishedAt)
        val stored = database.listeningHistoryDao().getHistoryItem("ep-1")!!
        assertEquals(0L to 900L, stored.firstPlayedAt to stored.finishedAt)
    }

    @Test
    fun everyPhonesListeningComesDownFromItsOwnMarkAndCountsOnce() = runTest(testDispatcher) {
        val me = prefs.getOrCreateSyncDeviceId()
        // A backup of phone B brought two of its sessions; this phone listened on day 30 itself.
        database.listeningSessionDao().upsertSessions(listOf(session("b-1", day = 20L, device = "phone-b"), session("mine", day = 30L, device = null)))
        pullAnswer =
            SyncPullResponse(
                listeningDays =
                listOf(
                    day("phone-b", 20L, 300_000L),
                    // This phone's own days from before a reinstall: kept only where it holds nothing.
                    day(me, 30L, 999L),
                    day(me, 31L, 70_000L),
                ),
                playedShows = listOf(PlayedShowSyncDto(podcastId = "200", podcastName = "Other", genre = "News", firstPlayedAt = 5L, lastPlayedAt = 6L)),
                syncedAt = 9_000L,
            )

        coordinator.syncNow()

        val request = pulls.single()
        assertTrue(request.listening)
        assertEquals(0L, request.listeningSince)
        assertEquals(8_999L, prefs.getListeningPulledUntil())
        assertEquals(listOf("mine"), database.listeningSessionDao().getAllSessions().map { it.sessionId })
        val summaries = database.listeningRollupDao().getAllRollups().associateBy { it.deviceId to it.localDay }
        assertEquals(300_000L, summaries.getValue("phone-b" to 20L).consumedMs)
        assertEquals(70_000L, summaries.getValue(ListeningRollupEntity.THIS_DEVICE to 31L).consumedMs)
        assertNull(summaries[ListeningRollupEntity.THIS_DEVICE to 30L])
        assertEquals(PlayedShowEntity("200", "Other", null, "News", 5L, 6L, isDirty = false), database.playedShowDao().getPlayedShow("200"))
    }

    @Test
    fun anOlderServerPullMovesNoListeningMark() = runTest(testDispatcher) {
        pullAnswer = SyncPullResponse(syncedAt = 9_000L)

        coordinator.syncNow()

        assertEquals(0L, prefs.getListeningPulledUntil())
    }

    @Test
    fun anAccountSwitchStartsTheListeningMarksOver() = runTest(testDispatcher) {
        prefs.setListeningPushedUntil(100L)
        prefs.setListeningPulledUntil(200L)
        database.playedShowDao().recordPlay("100", "Show", null, null, playedAt = 1L)
        prefs.setLastSyncedUserId("someone-else")

        coordinator.syncNow()

        assertEquals(0L, prefs.getListeningPushedUntil())
        assertTrue(database.playedShowDao().getAllPlayedShows().isEmpty())
        assertEquals(0L, pulls.single().listeningSince)
    }

    private suspend fun seedOwnListening() {
        database.listeningSessionDao().upsertSessions(listOf(session("s-1", day = 10L, device = null), session("s-2", day = 11L, device = null)))
        database.listeningRollupDao().upsertRollup(
            ListeningRollupEntity(10L, "ep-1", "100", 60_000L, sessionCount = 1, completionCount = 0, lastListenedAt = 1_000L),
        )
        database.playedShowDao().recordPlay("100", "Show", null, "Comedy", playedAt = 10_000L)
    }

    private fun session(id: String, day: Long, device: String?,) = ListeningSessionEntity(
        sessionId = id,
        episodeId = "ep-1",
        podcastId = "100",
        startedAt = day * 1_000L,
        endedAt = day * 1_000L + 60_000L,
        consumedMs = 60_000L,
        completed = false,
        localDay = day,
        timeBucket = 0,
        deviceId = device,
    )

    private fun day(device: String, localDay: Long, consumed: Long,) =
        ListeningDaySyncDto(deviceId = device, localDay = localDay, episodeId = "ep-1", podcastId = "100", consumedMs = consumed, sessionCount = 1, lastListenedAt = localDay)

    private fun history(id: String) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "100",
        episodeTitle = "Episode",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example/$id.mp3",
        podcastName = "Show",
        progressMs = 1_000L,
        durationMs = 60_000L,
        isCompleted = false,
        lastPlayedAt = 100L,
        isDirty = true,
    )

    private class CleanQueue : QueueSyncPort {
        override suspend fun getQueueSnapshot(): List<QueueItem> = emptyList()
        override suspend fun getQueueMetadata(): QueueMetadataEntity? = QueueMetadataEntity(id = 1)
        override suspend fun applyRemoteQueueState(items: List<QueueItem>, metadata: QueueMetadataEntity) = Unit
        override suspend fun markQueueSynced(expectedSequence: Long, syncedAt: Long): Boolean = true
        override suspend fun markQueueDirty() = Unit
    }

    private class FakeCall<T>(private val response: Response<T>) : Call<T> {
        override fun execute(): Response<T> = response
        override fun enqueue(callback: Callback<T>) = callback.onResponse(this, response)
        override fun isExecuted(): Boolean = true
        override fun cancel() = Unit
        override fun isCanceled(): Boolean = false
        override fun clone(): Call<T> = this
        override fun request(): Request = Request.Builder().url("https://example.com/sync").build()
        override fun timeout(): Timeout = Timeout.NONE
    }
}
