package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.DaoQueueSyncPort
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.FakePlayback
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.history
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Several phones of one listener, each with its own database and prefs file, syncing through one
 * [FakeSyncServer] that applies the live action clocks: a phone that sends an older copy later never
 * replaces newer listening, and a phone holding a newer copy than the cloud's sends it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudSyncNewerCopyTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val server = FakeSyncServer(now = 1_000L)
    private val phones = mutableListOf<Phone>()

    @After
    fun tearDown() = phones.forEach { it.db.close() }

    @Test
    fun anOlderPlaySentLaterLeavesTheNewerOneOnEveryPhone() = runTest(dispatcher) {
        val (a, b) = twoPhonesHolding(progress = 10 * MIN, playedAt = T1)

        a.dao.updateProgress(EPISODE, 15 * MIN, HOUR, T3) // A plays offline
        b.dao.updateProgress(EPISODE, 25 * MIN, HOUR, T5) // B plays later
        b.sync()
        a.sync() // back online: A sends its older copy before it pulls
        b.sync()
        val c = phone("c").also { it.sync() }

        assertEquals(25 * MIN, server.history.getValue(EPISODE).progressMs)
        for (phone in listOf(a, b, c)) assertEquals(25 * MIN, phone.row().progressMs)
    }

    @Test
    fun aLikeAndANewerPlayFromDifferentPhonesBothReachEveryPhone() = runTest(dispatcher) {
        val (a, b) = twoPhonesHolding(progress = 10 * MIN, playedAt = T1)

        a.dao.updateProgress(EPISODE, 15 * MIN, HOUR, T3)
        b.dao.setLikeStatus(EPISODE, isLiked = true, now = T5)
        b.sync()
        a.sync()
        b.sync()
        val c = phone("c").also { it.sync() }

        for (phone in listOf(a, b, c)) {
            val row = phone.row()
            assertEquals(15 * MIN to true, row.progressMs to row.isLiked)
        }
    }

    @Test
    fun signingInOnAPhoneUsedSignedOutKeepsTheCloudsNewerListening() = runTest(dispatcher) {
        val b = phone("b")
        b.dao.upsert(row(progress = 25 * MIN, playedAt = T5))
        b.sync()
        val a = phone("a")
        a.dao.upsert(row(progress = 15 * MIN, playedAt = T3, dirty = false)) // listened while signed out

        a.sync() // its first sync sends every row before it pulls

        assertEquals(25 * MIN, server.history.getValue(EPISODE).progressMs)
        assertEquals(25 * MIN, a.row().progressMs)
    }

    @Test
    fun aPhoneHoldingTheNewerCopyOfARowTheCloudLostSendsItOnceItsCheckRuns() = runTest(dispatcher) {
        // Before the fix: the cloud took B's older copy of the row after A's newer one, and A, which
        // pulled it then, kept its own clean and never sent it again.
        val a = phone("a")
        a.dao.upsert(row(progress = 25 * MIN, playedAt = T5, dirty = false).copy(syncedAt = 900L))
        a.syncedBefore(lastSync = 900L)
        server.seed(cloudRow(progress = 15 * MIN, playedAt = T3, updatedAt = 800L))

        a.sync() // the one-time check pulls from the start and marks the row
        val checked = server.now
        assertEquals(0L, server.pulls.last().since)
        assertTrue(a.row().isDirty)
        a.sync() // and the next push sends it; the pull is a delta pull again
        assertEquals(checked, server.pulls.last().since)

        assertEquals(25 * MIN, server.history.getValue(EPISODE).progressMs)
        assertEquals(25 * MIN, phone("c").also { it.sync() }.row().progressMs)
    }

    @Test
    fun theOneTimeCheckRunsOnceAndBringsBackNoRowRemovedHere() = runTest(dispatcher) {
        val a = phone("a")
        a.syncedBefore(lastSync = 900L)
        // Delivered by an earlier pull (changed before the last sync), then removed on this phone.
        server.seed(cloudRow(progress = 15 * MIN, playedAt = T3, updatedAt = 800L))

        a.sync()
        val checked = server.now
        a.sync()

        assertNull(a.dao.getHistoryItem(EPISODE))
        assertEquals(listOf(0L, checked), server.pulls.map { it.since })
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    /** Phones A and B, both synced and holding [EPISODE] clean at [progress], played at [playedAt]. */
    private suspend fun twoPhonesHolding(progress: Long, playedAt: Long): Pair<Phone, Phone> {
        val a = phone("a")
        a.dao.upsert(row(progress = progress, playedAt = playedAt))
        a.sync()
        val b = phone("b").also { it.sync() }
        assertEquals(progress, b.row().progressMs)
        assertFalse(b.row().isDirty)
        return a to b
    }

    private fun row(progress: Long, playedAt: Long, dirty: Boolean = true): ListeningHistoryEntity =
        history(EPISODE, progress = progress, playedAt = playedAt, dirty = dirty).copy(durationMs = HOUR)

    private fun cloudRow(progress: Long, playedAt: Long, updatedAt: Long) = ListeningHistorySyncDto(
        episodeId = EPISODE,
        podcastId = SyncIdentityFixtures.SHOW,
        episodeTitle = "Episode $EPISODE",
        progressMs = progress,
        durationMs = HOUR,
        lastPlayedAt = playedAt,
        updatedAt = updatedAt,
    )

    private fun phone(name: String) = Phone(name).also { phones += it }

    private inner class Phone(name: String) {
        val db: BoxLoreDatabase = SyncIdentityFixtures.directDatabase(app)
        val dao: ListeningHistoryDao = db.listeningHistoryDao()
        private val prefs = BoxcastPrefs(PhonePrefsContext(app, name))
        private val playback = FakePlayback()
        private val queuePort = DaoQueueSyncPort(db)
        private val coordinator = UserSyncCoordinator(
            boxLoreApi = SyncIdentityFixtures.api(server),
            publicKey = "key",
            authUserIdProvider = { "user" },
            tokenProvider = { "token" },
            podcastDao = db.podcastDao(),
            listeningHistoryDao = dao,
            queueSyncPort = queuePort,
            subscriptionSyncResolver = SubscriptionSyncResolver(podcastDao = db.podcastDao()),
            historySyncResolver = HistorySyncResolver(dao, playback),
            queueSyncResolver = QueueSyncResolver(queuePort, db, playback),
            boxcastPrefs = prefs,
            activePlaybackSyncPort = playback,
            ioDispatcher = dispatcher,
        )

        /** A phone that synced before at [lastSync], on a build from before the one-time check. */
        fun syncedBefore(lastSync: Long) {
            prefs.setLastSyncedUserId("user")
            prefs.setLastSyncTimestamp(lastSync)
            prefs.setSyncMetadataVersion(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION)
        }

        suspend fun sync() {
            server.now += 1_000L // the server's clock moves on between syncs
            assertTrue(coordinator.syncNow() is SyncResult.Success)
        }

        suspend fun row(): ListeningHistoryEntity = checkNotNull(dao.getHistoryItem(EPISODE))
    }

    /** Each phone's own prefs files, as on separate devices. */
    private class PhonePrefsContext(base: Context, private val phone: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("$phone-$name", mode)
    }

    private companion object {
        const val EPISODE = "4242"
        const val MIN = 60_000L
        const val HOUR = 60 * MIN
        const val T1 = 100_000L
        const val T3 = 300_000L
        const val T5 = 500_000L
    }
}
