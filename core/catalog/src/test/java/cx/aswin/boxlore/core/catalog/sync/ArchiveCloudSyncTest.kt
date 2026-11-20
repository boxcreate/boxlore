package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.DaoQueueSyncPort
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.FakePlayback
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.history
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.database.isDismissed
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An archive decision travels with its history row through cloud sync: it is pushed and pulled,
 * the newer decision wins on its own time like a like, and a server that drops the field changes
 * nothing on this device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArchiveCloudSyncTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: BoxLoreDatabase
    private lateinit var prefs: BoxcastPrefs
    private lateinit var server: FakeSyncServer
    private lateinit var coordinator: UserSyncCoordinator
    private lateinit var resolver: HistorySyncResolver
    private val dao get() = database.listeningHistoryDao()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = BoxcastPrefs(context)
        database = SyncIdentityFixtures.directDatabase(context)
        server = FakeSyncServer()
        val playback = FakePlayback()
        val identity = SyncEpisodeIdentity(database)
        val queuePort = DaoQueueSyncPort(database)
        resolver = HistorySyncResolver(listeningHistoryDao = dao, activePlaybackSyncPort = playback, episodeIds = { database.episodeListenerStore().memberIds(it) })
        coordinator = UserSyncCoordinator(
            boxLoreApi = SyncIdentityFixtures.api(server),
            publicKey = "key",
            authUserIdProvider = { "user" },
            tokenProvider = { "token" },
            podcastDao = database.podcastDao(),
            listeningHistoryDao = dao,
            queueSyncPort = queuePort,
            subscriptionSyncResolver = SubscriptionSyncResolver(podcastDao = database.podcastDao()),
            historySyncResolver = resolver,
            queueSyncResolver = QueueSyncResolver(queuePort, database, playback, podcastRepository = null, episodeIdentity = identity),
            boxcastPrefs = prefs,
            activePlaybackSyncPort = playback,
            ioDispatcher = dispatcher,
            episodeIdentityDao = database.episodeIdentityDao(),
            episodeIdentity = identity,
        )
        prefs.setLastSyncedUserId("user")
        prefs.setLastSyncTimestamp(500L)
        prefs.setSyncMetadataVersion(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun anArchivedRowIsPushedWithItsDecisionAndLeftDirtyIfArchivedAgainMidPush() = runTest(dispatcher) {
        dao.upsert(history("ep", playedAt = 1_000L))
        dao.setDismissedForIds(listOf("ep"), 2_000L)

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val pushed = server.pushes.single().history.single()
        assertEquals(2_000L, pushed.dismissedAt)
        assertTrue(pushed.updatedAt >= 2_000L)
        assertEquals(2_000L, server.history.getValue("ep").dismissedAt)
        assertFalse(dao.getHistoryItem("ep")!!.isDirty)
    }

    @Test
    fun theNewerArchiveDecisionWinsOnItsOwnTime() = runTest(dispatcher) {
        dao.upsert(history("ep", playedAt = 1_000L, dirty = false))

        // Another device archived it later; nothing else changed.
        resolver.resolveHistoryItem(remote("ep", playedAt = 1_000L, dismissedAt = 3_000L), syncedAt = 5_000L)
        assertTrue(dao.getHistoryItem("ep")!!.isDismissed)

        // This device brought it back after that: an older archive pulled again doesn't undo it.
        dao.setDismissedForIds(listOf("ep"), -4_000L)
        resolver.resolveHistoryItem(remote("ep", playedAt = 1_000L, dismissedAt = 3_000L), syncedAt = 6_000L)
        assertEquals(-4_000L, dao.getHistoryItem("ep")!!.dismissedAt)

        // A newer bring-back from the other device wins too, even with newer listening attached.
        dao.setDismissedForIds(listOf("ep"), 4_500L)
        resolver.resolveHistoryItem(remote("ep", playedAt = 9_000L, dismissedAt = -5_000L), syncedAt = 7_000L)
        val row = dao.getHistoryItem("ep")!!
        assertEquals(-5_000L, row.dismissedAt)
        assertEquals(9_000L, row.lastPlayedAt)
    }

    @Test
    fun aServerThatDropsTheFieldNeverUndoesAnArchive() = runTest(dispatcher) {
        dao.upsert(history("ep", playedAt = 1_000L, dirty = false))
        dao.setDismissedForIds(listOf("ep"), 2_000L)

        // Newer listening from another device, from a server that doesn't store the field.
        resolver.resolveHistoryItem(remote("ep", playedAt = 3_000L, dismissedAt = 0L), syncedAt = 5_000L)

        val row = dao.getHistoryItem("ep")!!
        assertEquals(2_000L, row.dismissedAt)
        assertEquals(3_000L, row.lastPlayedAt)
        // The decision the server dropped is pushed again.
        assertTrue(row.isDirty)
    }

    @Test
    fun aPulledRowNewToThisDeviceKeepsItsArchive() = runTest(dispatcher) {
        resolver.resolveHistoryItem(remote("trailer", playedAt = 0L, dismissedAt = 7_000L), syncedAt = 8_000L)

        assertTrue(dao.getHistoryItem("trailer")!!.isDismissed)
        assertEquals(setOf("trailer"), database.episodeListenerStore().dismissedIds())
    }

    private fun remote(id: String, playedAt: Long, dismissedAt: Long) = ListeningHistorySyncDto(
        episodeId = id,
        podcastId = SyncIdentityFixtures.SHOW,
        episodeTitle = "Episode $id",
        progressMs = 0L,
        lastPlayedAt = playedAt,
        updatedAt = maxOf(playedAt, kotlin.math.abs(dismissedAt)),
        dismissedAt = dismissedAt,
    )
}
