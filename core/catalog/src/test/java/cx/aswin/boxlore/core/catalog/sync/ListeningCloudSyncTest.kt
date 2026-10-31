package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningDayTotals
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.ListeningSessionValues
import cx.aswin.boxlore.core.database.PlayedShowEntity
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.model.ListeningDaySyncDto
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.PlayedShowSyncDto
import cx.aswin.boxlore.core.network.model.SyncDeleteAccountResponse
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Cloud sync of listening (Room v40): history dates both ways, this installation's changed days of
 * listening and the played shows pushed and marked sent only by a server that stores them, every
 * phone's listening pulled from its own mark and counted once, older listening never replacing newer,
 * and an older server leaving everything as it was.
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
    private var serverStoresTotals = false
    private var serverStoresReleaseKnown = false
    private var pullAnswer = SyncPullResponse(syncedAt = 9_000L)
    private var onPush: (() -> Unit)? = null
    private var pushesInFlight = 0
    private var mostPushesInFlight = 0

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
                        pushesInFlight++
                        mostPushesInFlight = maxOf(mostPushesInFlight, pushesInFlight)
                        pushes += args[2] as SyncPushRequest
                        onPush?.invoke()
                        pushesInFlight--
                        FakeCall(Response.success(SyncPushResponse(syncedAt = 5_000L, listeningAccepted = serverStoresListening, listeningTotals = serverStoresTotals, listeningReleaseKnown = serverStoresReleaseKnown)))
                    }
                    "syncPull" -> {
                        pulls += args[2] as SyncPullRequest
                        FakeCall(Response.success(pullAnswer.copy(listeningTotals = serverStoresTotals, listeningReleaseKnown = serverStoresReleaseKnown)))
                    }
                    "deleteSyncAccount" -> FakeCall(Response.success(SyncDeleteAccountResponse()))
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
        // Under this installation's listening ID, never the (backed-up) sync device ID.
        val me = prefs.getOrCreateListeningDeviceId()
        assertEquals(setOf(me), first.listeningDays.map { it.deviceId }.toSet())
        assertTrue(me != prefs.getOrCreateSyncDeviceId())
        assertEquals(setOf(me), database.listeningSessionDao().getAllSessions().map { it.deviceId }.toSet())
        assertEquals(listOf("100"), first.playedShows.map { it.podcastId })
        assertTrue(database.listeningPushDao().getPushedDays(me).isEmpty())
        assertTrue(database.playedShowDao().getPlayedShow("100")!!.isDirty)

        serverStoresListening = true
        coordinator.syncNow()

        assertEquals(2, pushes[1].listeningDays.size)
        assertEquals(2, database.listeningPushDao().getPushedDays(me).size)
        assertFalse(database.playedShowDao().getPlayedShow("100")!!.isDirty)

        // Nothing new: no listening goes up.
        pushes.clear()
        coordinator.syncNow()
        assertTrue(pushes.isEmpty() || pushes.single().listeningDays.isEmpty())

        // New listening on day 11 sends day 11 only, with its whole total.
        database.listeningSessionDao().upsertSessions(listOf(session("s-3", day = 11L, device = me, endedOffset = 90_000L)))
        pushes.clear()
        coordinator.syncNow()
        assertEquals(listOf(11L to 120_000L), pushes.single().listeningDays.map { it.localDay to it.consumedMs })
    }

    @Test
    fun listeningFromBeforeTheUpdateGoesUpUnderTheLegacyOriginACopiedDatabaseShares() = runTest(testDispatcher) {
        database.listeningSessionDao().upsertSessions(listOf(session("old", day = 3L, device = null)))
        database.listeningRollupDao().upsertRollup(ListeningRollupEntity(2L, "ep-1", "100", 30_000L, 1, 0, lastListenedAt = 500L))

        coordinator.syncNow()

        val legacy = prefs.getListeningLegacyOrigin()
        assertEquals(BoxcastPrefs.LISTENING_LEGACY_ORIGIN_PREFIX + prefs.getOrCreateSyncDeviceId(), legacy)
        assertEquals(listOf(legacy to 2L, legacy to 3L), pushes.single().listeningDays.map { it.deviceId to it.localDay })
    }

    @Test
    fun aBackgroundPushWaitsForThePushInFlight() = runTest(testDispatcher) {
        seedOwnListening()
        var started = false
        onPush = {
            if (!started) {
                started = true
                launch { coordinator.executePush() }
            }
        }

        coordinator.syncNow()
        advanceUntilIdle()

        // Answered out of order, two pushes could record totals the cloud no longer holds.
        assertEquals(1, mostPushesInFlight)
    }

    @Test
    fun listeningMovedToAnotherEpisodeIdSendsAZeroDayForTheOldOne() = runTest(testDispatcher) {
        seedOwnListening()
        coordinator.syncNow()
        val me = prefs.getOrCreateListeningDeviceId()

        // The episode got another ID: day 11's session now belongs to ep-2.
        val moved = database.listeningSessionDao().getAllSessions().single { it.sessionId == "s-2" }
        database.listeningSessionDao().deleteSessionsForEpisode("ep-1")
        database.listeningSessionDao().upsertSessions(listOf(moved.copy(episodeId = "ep-2"), session("s-1", day = 10L, device = me)))
        pushes.clear()

        coordinator.syncNow()

        val sent = pushes.single().listeningDays.associate { (it.localDay to it.episodeId) to it.consumedMs }
        assertEquals(mapOf((11L to "ep-1") to 0L, (11L to "ep-2") to 60_000L), sent)
        assertEquals(
            setOf(10L to "ep-1", 11L to "ep-2"),
            database.listeningPushDao().getPushedDays(me).map { it.localDay to it.episodeId }.toSet(),
        )
    }

    @Test
    fun listeningRestoredOnThisPhoneGoesUpEvenWhenItIsOlderThanWhatWasSent() = runTest(testDispatcher) {
        seedOwnListening()
        coordinator.syncNow()
        val me = prefs.getOrCreateListeningDeviceId()
        // A backup made earlier on this installation brings back day 5, older than everything pushed.
        database.listeningRollupDao().upsertRollup(ListeningRollupEntity(5L, "ep-1", "100", 30_000L, sessionCount = 1, completionCount = 0, lastListenedAt = 500L, deviceId = me))
        pushes.clear()

        coordinator.syncNow()

        assertEquals(listOf(5L to 30_000L), pushes.single().listeningDays.map { it.localDay to it.consumedMs })
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
    fun datesOfRowsAlreadyInTheCloudGoUpOnceToAServerThatStoresThem() = runTest(testDispatcher) {
        // Synced before the update (not waiting for a push), dated on this phone since.
        database.listeningHistoryDao().upsert(history("ep-1").copy(isDirty = false, syncedAt = 1L, firstPlayedAt = 500L))
        database.listeningHistoryDao().upsert(history("ep-2").copy(isDirty = false, syncedAt = 1L))
        // Played before boxlore recorded dates: known too, and the cloud may hold nothing for it.
        database.listeningHistoryDao().upsert(history("ep-3").copy(isDirty = false, syncedAt = 1L, firstPlayedAt = 0L, finishedAt = 0L))
        serverStoresListening = false

        coordinator.syncNow()
        coordinator.syncNow()

        // Only the dated rows, and again after an older server ignored them. Their playback clock is 1,
        // never 0: the JSON leaves 0 out, and the server stores a row without a clock at its own time,
        // which would replace another phone's newer progress.
        assertEquals(listOf("ep-1", "ep-3", "ep-1", "ep-3"), pushes.flatMap { push -> push.history.map { it.episodeId } })
        assertTrue(pushes.first().history.all { it.updatedAt == ListeningSync.DATES_ONLY_UPDATED_AT })
        assertTrue(Json.encodeToString(pushes.first()).contains("\"updatedAt\":1"))

        serverStoresListening = true
        pushes.clear()
        coordinator.syncNow()
        coordinator.syncNow()

        assertEquals(listOf("ep-1", "ep-3"), pushes.flatMap { push -> push.history.map { it.episodeId } })
        assertTrue(prefs.areListeningDatesSent())
    }

    @Test
    fun everyPhonesListeningComesDownFromItsOwnMarkAndOnlyNewerListeningReplacesWhatIsHeld() = runTest(testDispatcher) {
        val me = prefs.getOrCreateListeningDeviceId()
        val rollups = database.listeningRollupDao()
        // A backup of phone B brought its sessions and summaries; this phone listened on day 30 itself.
        database.listeningSessionDao().upsertSessions(listOf(session("b-1", day = 20L, device = "phone-b"), session("mine", day = 30L, device = me)))
        rollups.upsertRollup(ListeningRollupEntity(21L, "ep-1", "100", 90_000L, 2, 0, lastListenedAt = 90_000L, deviceId = "phone-b"))
        rollups.upsertRollup(ListeningRollupEntity(22L, "ep-1", "100", 40_000L, 1, 0, lastListenedAt = 22_000L, deviceId = "phone-b"))
        pullAnswer =
            SyncPullResponse(
                listeningDays =
                listOf(
                    // Covers phone B's restored session: replaces it.
                    day("phone-b", 20L, 300_000L),
                    // Older than the restored summary (a later backup of phone B came here): ignored.
                    day("phone-b", 21L, 10_000L, lastListenedAt = 50_000L),
                    // Phone B no longer holds day 22.
                    day("phone-b", 22L, 0L, lastListenedAt = 22_000L, sessions = 0),
                    // This installation's own days: kept only where it holds nothing of the day.
                    day(me, 30L, 999L),
                    day(me, 32L, 45_000L),
                    // A reinstall of this phone is another installation: counted as such.
                    day("old-install", 31L, 70_000L),
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
        val summaries = rollups.getAllRollups().associate { (it.deviceId to it.localDay) to it.consumedMs }
        assertEquals(
            mapOf(("phone-b" to 20L) to 300_000L, ("phone-b" to 21L) to 90_000L, ("old-install" to 31L) to 70_000L, (me to 32L) to 45_000L),
            summaries,
        )
        assertEquals(PlayedShowEntity("200", "Other", null, "News", 5L, 6L, isDirty = false), database.playedShowDao().getPlayedShow("200"))
    }

    @Test
    fun anOlderServerPullMovesNoListeningMark() = runTest(testDispatcher) {
        pullAnswer = SyncPullResponse(syncedAt = 9_000L)

        coordinator.syncNow()

        assertEquals(0L, prefs.getListeningPulledUntil())
    }

    @Test
    fun anAccountSwitchStartsTheListeningMarksOverAndBringsBackThisPhonesListeningInThatAccount() = runTest(testDispatcher) {
        seedOwnListening()
        coordinator.syncNow()
        val me = prefs.getOrCreateListeningDeviceId()
        prefs.setListeningPulledUntil(200L)
        prefs.setLastSyncedUserId("someone-else")
        pulls.clear()
        // The account switched to already holds days this installation pushed to it before.
        pullAnswer = SyncPullResponse(listeningDays = listOf(day(me, 40L, 30_000L)), syncedAt = 9_000L)

        coordinator.syncNow()

        // What this phone knows the cloud holds starts over: only the new account's day is known.
        assertEquals(listOf(40L), database.listeningPushDao().getPushedDays(me).map { it.localDay })
        assertTrue(database.playedShowDao().getAllPlayedShows().isEmpty())
        assertEquals(0L, pulls.single().listeningSince)
        assertEquals(listOf(40L to 30_000L), database.listeningRollupDao().getAllRollups().map { it.localDay to it.consumedMs })
        assertTrue(database.listeningSessionDao().getAllSessions().isEmpty())
    }

    @Test
    fun deletingTheCloudCopySendsEverythingAgainNextTime() = runTest(testDispatcher) {
        seedOwnListening()
        database.listeningHistoryDao().upsert(history("ep-1").copy(isDirty = false, syncedAt = 1L, firstPlayedAt = 500L))
        coordinator.syncNow()
        coordinator.syncNow()
        assertTrue(prefs.areListeningDatesSent())

        assertTrue(coordinator.deleteCloudSyncData().isSuccess)
        pushes.clear()
        pulls.clear()
        coordinator.syncNow()

        val push = pushes.first()
        assertEquals(2, push.listeningDays.size)
        assertEquals(listOf("100"), push.playedShows.map { it.podcastId })
        assertTrue(push.history.any { it.episodeId == "ep-1" && it.firstPlayedAt == 500L })
        assertEquals(0L, pulls.single().listeningSince)
    }

    @Test
    fun eachDaysRicherTotalsGoUpAndComeDown() = runTest(testDispatcher) {
        val me = prefs.getOrCreateListeningDeviceId()
        database.listeningSessionDao().upsertSessions(
            listOf(
                session("car", day = 10L, device = me).copy(output = ListeningSessionValues.OUTPUT_CAR, source = ListeningSessionValues.SOURCE_LORE),
                session("buds", day = 10L, device = me, endedOffset = 200_000L).copy(consumedMs = 90_000L, output = ListeningSessionValues.OUTPUT_HEADPHONES),
            ),
        )
        pullAnswer =
            SyncPullResponse(
                syncedAt = 9_000L,
                listeningDays = listOf(day("phone-b", 12L, 120_000L).copy(longestSessionMs = 80_000L, carMs = 120_000L, newReleaseMs = 40_000L)),
                playedShows = emptyList(),
            )

        coordinator.syncNow()

        assertTrue(pushes.single().listeningTotals)
        val sent = pushes.single().listeningDays.single()
        assertEquals(90_000L, sent.longestSessionMs)
        assertEquals(60_000L, sent.carMs)
        assertEquals(90_000L, sent.headphonesMs)
        assertEquals(60_000L, sent.loreMs)
        val pulled = database.listeningRollupDao().getRollup("phone-b", 12L, "ep-1")!!.totals
        assertEquals(ListeningDayTotals(longestSessionMs = 80_000L, carMs = 120_000L, newReleaseMs = 40_000L), pulled)
    }

    @Test
    fun eachDaysReleaseDateTimeGoesUpAndComesDown() = runTest(testDispatcher) {
        val me = prefs.getOrCreateListeningDeviceId()
        prefs.setListeningTotalsSent(true)
        serverStoresTotals = true
        serverStoresReleaseKnown = true
        database.listeningSessionDao().upsertSessions(
            listOf(
                session("new", day = 10L, device = me).copy(episodePublishedAt = 9_000L),
                session("undated", day = 10L, device = me, endedOffset = 200_000L),
            ),
        )
        pullAnswer =
            SyncPullResponse(
                syncedAt = 9_000L,
                listeningDays = listOf(day("phone-b", 12L, 120_000L).copy(carMs = 120_000L, newReleaseMs = 40_000L, releaseKnownMs = 100_000L)),
                playedShows = emptyList(),
            )

        coordinator.syncNow()

        assertTrue(pushes.single().listeningReleaseKnown)
        val sent = pushes.single().listeningDays.single()
        assertEquals(60_000L to 60_000L, sent.newReleaseMs to sent.releaseKnownMs)
        val pulled = database.listeningRollupDao().getRollup("phone-b", 12L, "ep-1")!!.totals
        assertEquals(ListeningDayTotals(carMs = 120_000L, newReleaseMs = 40_000L, releaseKnownMs = 100_000L), pulled)
    }

    @Test
    fun daysGoUpOnceMoreTheFirstTimeAServerStoresTheirReleaseDateTime() = runTest(testDispatcher) {
        val me = prefs.getOrCreateListeningDeviceId()
        // A server with the v41 totals already stored; this phone sent them before.
        prefs.setListeningTotalsSent(true)
        serverStoresTotals = true
        database.listeningSessionDao().upsertSessions(listOf(session("dated", day = 10L, device = me).copy(episodePublishedAt = 9_000L), session("undated", day = 11L, device = me)))
        coordinator.syncNow()
        val sent = pushes.single().listeningDays
        assertEquals(listOf(60_000L, 0L), sent.map { it.releaseKnownMs })

        // It keeps the days without their release date time and sends them back that way: nothing goes up again.
        pullAnswer = SyncPullResponse(syncedAt = 9_000L, listeningDays = sent.map { it.copy(releaseKnownMs = 0L) }, playedShows = emptyList())
        pushes.clear()
        coordinator.syncNow()
        coordinator.syncNow()
        assertTrue(pushes.all { it.listeningDays.isEmpty() })
        assertFalse(prefs.areListeningReleaseKnownSent())

        // The server updates: its first answer that stores it sends the dated day once more...
        serverStoresReleaseKnown = true
        pullAnswer = SyncPullResponse(syncedAt = 9_500L, listeningDays = emptyList(), playedShows = emptyList())
        coordinator.syncNow()
        assertTrue(prefs.areListeningReleaseKnownSent())
        pushes.clear()
        coordinator.syncNow()
        assertEquals(listOf(10L to 60_000L), pushes.single().listeningDays.map { it.localDay to it.releaseKnownMs })

        // ...and only once.
        pushes.clear()
        coordinator.syncNow()
        assertTrue(pushes.isEmpty() || pushes.single().listeningDays.isEmpty())
    }

    @Test
    fun aServerWithoutTotalsSendingThisPhonesDaysBackDoesNotMakeThemGoUpAgain() = runTest(testDispatcher) {
        seedOwnListening()
        coordinator.syncNow()
        val sent = pushes.single().listeningDays
        assertTrue(sent.any { it.longestSessionMs > 0L })
        // The server keeps the days without their totals and sends them back that way.
        pullAnswer =
            SyncPullResponse(
                syncedAt = 9_000L,
                listeningDays = sent.map { it.copy(longestSessionMs = 0L, speakerMs = 0L, headphonesMs = 0L, bluetoothMs = 0L, carMs = 0L, castMs = 0L, loreMs = 0L, smartQueueMs = 0L, newReleaseMs = 0L) },
            )
        pushes.clear()
        coordinator.syncNow()
        pushes.clear()

        coordinator.syncNow()

        assertTrue(pushes.isEmpty() || pushes.single().listeningDays.isEmpty())
    }

    @Test
    fun daysGoUpOnceMoreTheFirstTimeAServerStoresTheirTotals() = runTest(testDispatcher) {
        seedOwnListening()
        val me = prefs.getOrCreateListeningDeviceId()

        // A server without the totals: days are sent and marked, and stay marked.
        coordinator.syncNow()
        assertEquals(2, database.listeningPushDao().getPushedDays(me).size)
        pushes.clear()
        coordinator.syncNow()
        assertTrue(pushes.isEmpty() || pushes.single().listeningDays.isEmpty())
        assertFalse(prefs.areListeningTotalsSent())

        // The server updates: its first answer that stores totals (here a pull, with nothing to push)
        // sends every day once more...
        serverStoresTotals = true
        coordinator.syncNow()
        assertTrue(prefs.areListeningTotalsSent())
        pushes.clear()
        coordinator.syncNow()
        assertEquals(listOf(10L, 11L), pushes.single().listeningDays.map { it.localDay })

        // ...and only once.
        pushes.clear()
        coordinator.syncNow()
        assertTrue(pushes.isEmpty() || pushes.single().listeningDays.isEmpty())
    }

    @Test
    fun aDayRemovedBeforeTheServerStoresTotalsStillGetsItsZeroDay() = runTest(testDispatcher) {
        seedOwnListening()
        coordinator.syncNow()
        // Day 11's only session is removed here, then the server starts storing totals.
        database.listeningSessionDao().deleteAllSessions()
        serverStoresTotals = true
        pushes.clear()

        coordinator.syncNow()
        coordinator.syncNow()

        val sentDays = pushes.flatMap { it.listeningDays }
        assertTrue(sentDays.any { it.localDay == 11L && it.consumedMs == 0L && it.sessionCount == 0 })
        assertTrue(database.listeningPushDao().getPushedDays(prefs.getOrCreateListeningDeviceId()).none { it.localDay == 11L })
    }

    @Test
    fun ownDaysPulledWithoutTotalsStillSendTheirTotals() = runTest(testDispatcher) {
        val me = prefs.getOrCreateListeningDeviceId()
        database.listeningSessionDao().upsertSessions(listOf(session("car", day = 10L, device = me).copy(output = ListeningSessionValues.OUTPUT_CAR)))
        coordinator.syncNow()
        // The server updates and its first pull hands back this phone's day as an older server stored it.
        serverStoresTotals = true
        pullAnswer = SyncPullResponse(syncedAt = 9_000L, listeningDays = listOf(day(me, 10L, 60_000L, lastListenedAt = 70_000L)), playedShows = emptyList())
        pushes.clear()

        coordinator.syncNow()
        // Later pulls bring only what changed since.
        pullAnswer = SyncPullResponse(syncedAt = 9_500L, listeningDays = emptyList(), playedShows = emptyList())
        coordinator.syncNow()

        val sent = pushes.flatMap { it.listeningDays }.last { it.localDay == 10L }
        assertEquals(60_000L, sent.carMs)
        assertEquals(60_000L, sent.longestSessionMs)
        assertEquals(60_000L, database.listeningPushDao().getPushedDays(me).single().totals.carMs)
    }

    @Test
    fun anEquallyCompleteDayWithoutTotalsKeepsTheHeldOnes() = runTest(testDispatcher) {
        // A backup of phone B brought a session heard in the car; phone B, still on an older build, pushed the same day.
        database.listeningSessionDao().upsertSessions(listOf(session("b-car", day = 20L, device = "phone-b").copy(output = ListeningSessionValues.OUTPUT_CAR)))
        pullAnswer = SyncPullResponse(syncedAt = 9_000L, listeningDays = listOf(day("phone-b", 20L, 60_000L)), playedShows = emptyList())

        coordinator.syncNow()

        val kept = database.listeningRollupDao().getRollup("phone-b", 20L, "ep-1")!!
        assertEquals(60_000L, kept.totals.carMs)
        assertEquals(60_000L, kept.totals.longestSessionMs)
        assertTrue(database.listeningSessionDao().getAllSessions().isEmpty())
    }

    private suspend fun seedOwnListening() {
        val me = prefs.getOrCreateListeningDeviceId()
        database.listeningSessionDao().upsertSessions(listOf(session("s-1", day = 10L, device = me), session("s-2", day = 11L, device = me)))
        database.listeningRollupDao().upsertRollup(
            ListeningRollupEntity(10L, "ep-1", "100", 60_000L, sessionCount = 1, completionCount = 0, lastListenedAt = 1_000L, deviceId = me),
        )
        database.playedShowDao().recordPlay("100", "Show", null, "Comedy", playedAt = 10_000L)
    }

    private fun session(id: String, day: Long, device: String?, endedOffset: Long = 60_000L,) = ListeningSessionEntity(
        sessionId = id,
        episodeId = "ep-1",
        podcastId = "100",
        startedAt = day * 1_000L + endedOffset - 60_000L,
        endedAt = day * 1_000L + endedOffset,
        consumedMs = 60_000L,
        completed = false,
        localDay = day,
        timeBucket = 0,
        deviceId = device,
    )

    private fun day(device: String, localDay: Long, consumed: Long, lastListenedAt: Long = localDay * 1_000L + 60_000L, sessions: Int = 1,) =
        ListeningDaySyncDto(deviceId = device, localDay = localDay, episodeId = "ep-1", podcastId = "100", consumedMs = consumed, sessionCount = sessions, lastListenedAt = lastListenedAt)

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
