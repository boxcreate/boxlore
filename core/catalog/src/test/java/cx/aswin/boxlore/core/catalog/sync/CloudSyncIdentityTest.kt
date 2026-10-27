package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.DaoQueueSyncPort
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.FakePlayback
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.KEY
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.KEY_2
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.PI_ID
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.RECIPE
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.RECIPE_2
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.SHOW
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.alias
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.history
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.queueItem
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueRemovalSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

/**
 * Cloud sync carries one episode across its IDs and older builds (docs/EPISODE_IDS.md §5), run
 * through [UserSyncCoordinator] against [FakeSyncServer], which applies the sync API's rules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudSyncIdentityTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: BoxLoreDatabase
    private lateinit var prefs: BoxcastPrefs
    private lateinit var server: FakeSyncServer
    private lateinit var playback: FakePlayback
    private lateinit var coordinator: UserSyncCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = BoxcastPrefs(context)
        database = SyncIdentityFixtures.directDatabase(context)
        server = FakeSyncServer()
        playback = FakePlayback()
        val identity = SyncEpisodeIdentity(database)
        val queuePort = DaoQueueSyncPort(database)
        coordinator = UserSyncCoordinator(
            boxLoreApi = SyncIdentityFixtures.api(server),
            publicKey = "key",
            authUserIdProvider = { "user" },
            tokenProvider = { "token" },
            podcastDao = database.podcastDao(),
            listeningHistoryDao = database.listeningHistoryDao(),
            queueSyncPort = queuePort,
            subscriptionSyncResolver = SubscriptionSyncResolver(podcastDao = database.podcastDao()),
            historySyncResolver = HistorySyncResolver(
                listeningHistoryDao = database.listeningHistoryDao(),
                activePlaybackSyncPort = playback,
                episodeIds = { database.episodeListenerStore().memberIds(it) },
            ),
            queueSyncResolver = QueueSyncResolver(queuePort, database, playback, podcastRepository = null, episodeIdentity = identity),
            boxcastPrefs = prefs,
            activePlaybackSyncPort = playback,
            ioDispatcher = dispatcher,
            episodeIdentityDao = database.episodeIdentityDao(),
            episodeIdentity = identity,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A device that already ran the identity catch-up and last synced at [lastSync]. */
    private fun syncedBefore(lastSync: Long = 500L) {
        prefs.setLastSyncedUserId("user")
        prefs.setLastSyncTimestamp(lastSync)
        prefs.setSyncMetadataVersion(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION)
    }

    private suspend fun aliases(vararg rows: EpisodeAliasEntity) {
        database.episodeIdentityDao().insertAliasesIfAbsent(rows.toList())
    }

    @Test
    fun pushedRowsCarryTheirEpisodesIdentityTheQueueItsRichRemovalsAndPullsAskForThem() = runTest(dispatcher) {
        syncedBefore()
        aliases(alias(RECIPE.toString(), owner = true), alias(PI_ID))
        database.listeningHistoryDao().upsertAll(listOf(history(RECIPE.toString()), history(PI_ID), history("999", show = "88")))
        database.queueDao().insertQueueItem(queueItem(PI_ID))
        val removal = QueueRemovalIdentity("-9", recipeId = RECIPE_2, show = SHOW, catalogKey = KEY_2)
        database.queueDao().upsertQueueMetadata(
            QueueMetadataEntity(
                queueSequence = 2L,
                queueUpdatedAt = 20L,
                isDirty = true,
                recentRemovedEpisodeIds = "[\"-9\"]",
                recentRemovedEpisodes = QueueRemovalIdentity.serializeList(listOf(removal)),
            ),
        )

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val push = server.pushes.single()
        val byId = push.history.associateBy { it.episodeId }
        assertEquals(RECIPE to KEY, byId.getValue(RECIPE.toString()).let { it.recipeId to it.catalogKey })
        assertEquals(RECIPE to KEY, byId.getValue(PI_ID).let { it.recipeId to it.catalogKey })
        assertNull(byId.getValue("999").recipeId)
        assertNull(byId.getValue("999").catalogKey)
        val queue = push.queue!!
        assertEquals(RECIPE to KEY, queue.items.single().let { it.recipeId to it.catalogKey })
        assertEquals(listOf("-9"), queue.recentRemovedEpisodeIds)
        assertEquals(listOf(QueueRemovalSyncDto("-9", RECIPE_2, SHOW, KEY_2)), queue.recentRemovedEpisodes)
        assertTrue(server.pulls.single().richRemovals)
        // The server now knows the Podcast Index row's episode, so devices storing it under its recipe ID find it.
        assertEquals(RECIPE, server.history.getValue(PI_ID).recipeId)
    }

    @Test
    fun aPodcastIndexRowFromAnOldBuildAndARecipeOwnerBecomeOneEpisode() = runTest(dispatcher) {
        syncedBefore()
        aliases(alias(RECIPE.toString(), owner = true))
        database.listeningHistoryDao().upsert(history(RECIPE.toString(), progress = 10_000L, playedAt = 2_000L, dirty = false, syncedAt = 100L))
        // A v2 device pushed the Podcast Index row with its identity; an older build then pushed newer progress without it.
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(PI_ID, SHOW, progressMs = 1L, lastPlayedAt = 1_000L, updatedAt = 1_000L, recipeId = RECIPE, catalogKey = KEY))))
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(PI_ID, SHOW, progressMs = 30_000L, lastPlayedAt = 4_000L, updatedAt = 4_000L))))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val joined = database.episodeIdentityDao().getAlias(PI_ID)!!
        assertEquals(EpisodeAliasState.TRUSTED, joined.state)
        assertEquals(KEY, joined.catalogKey)
        assertFalse(joined.owner)
        assertEquals(EpisodeAliasProvenance.CLOUD_SYNC, joined.provenance)
        val store = database.episodeListenerStore()
        val listed = store.projectRows(database.listeningHistoryDao().getRecentHistoryList(10))
        assertEquals(1, listed.size)
        assertEquals(30_000L, listed.single().progressMs)
        // The phone's own ID keeps the episode's listener state; the pulled row only joins it.
        assertEquals(RECIPE.toString(), store.historyId(PI_ID))
        assertEquals(RECIPE.toString(), playback.handedOff)
    }

    @Test
    fun anUnknownRowFromTheCloudIsStoredAsItIsAndOwnsNothing() = runTest(dispatcher) {
        syncedBefore()
        aliases(alias(RECIPE.toString(), owner = true))
        database.listeningHistoryDao().upsert(history(RECIPE.toString(), progress = 10_000L, playedAt = 2_000L, dirty = false, syncedAt = 100L))
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(PI_ID, SHOW, progressMs = 30_000L, lastPlayedAt = 4_000L, updatedAt = 4_000L))))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val recorded = database.episodeIdentityDao().getAlias(PI_ID)!!
        assertEquals(EpisodeAliasState.UNRESOLVED, recorded.state)
        assertFalse(recorded.owner)
        assertEquals(30_000L, database.listeningHistoryDao().getHistoryItem(PI_ID)!!.progressMs)
        assertEquals(listOf(PI_ID), database.episodeListenerStore().memberIds(PI_ID))
        assertEquals(10_000L, database.listeningHistoryDao().getHistoryItem(RECIPE.toString())!!.progressMs)
    }

    @Test
    fun aRowWhoseRecipeIdNamesAnotherEpisodeHereIsPushedWithoutItSoARemovalOfThatEpisodeNeverReachesIt() = runTest(dispatcher) {
        syncedBefore()
        // KEY took the recipe ID; KEY_2's episode, whose recipe ID is the same, took a reserved ID.
        aliases(alias(RECIPE.toString(), owner = true), alias("-5", key = KEY_2, recipeId = RECIPE, owner = true, provenance = EpisodeAliasProvenance.FEED_RESERVED))
        database.listeningHistoryDao().upsert(history("-5", progress = 7_000L))
        database.queueDao().insertQueueItem(queueItem("-5"))
        database.queueDao().upsertQueueMetadata(QueueMetadataEntity(queueSequence = 1L, queueUpdatedAt = 10L, isDirty = true))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val push = server.pushes.single()
        val sent = push.history.single { it.episodeId == "-5" }
        assertNull(sent.recipeId)
        assertEquals(KEY_2, sent.catalogKey)
        val queued = push.queue!!.items.single()
        assertNull(queued.recipeId)
        assertEquals(KEY_2, queued.catalogKey)
        assertNull(server.history.getValue("-5").recipeId)

        // A device that doesn't know the clash removes KEY's episode by its recipe ID. The server
        // removes every queue row of the show with that recipe ID, which would include -5's had
        // this phone sent it.
        server.push(
            SyncPushRequest(
                queue = QueueSyncDto(
                    queueUpdatedAt = 50L,
                    queueSequence = 2L,
                    recentRemovedEpisodeIds = listOf(RECIPE.toString()),
                    recentRemovedEpisodes = listOf(QueueRemovalSyncDto(RECIPE.toString(), RECIPE, SHOW, KEY)),
                ),
            ),
        )

        assertEquals(setOf("-5"), server.queueItems.keys)
        assertEquals(7_000L, server.history.getValue("-5").progressMs)
    }

    @Test
    fun aServedIdThatIsNotItsEpisodesRecipeIdIsPushedWithoutAKeySoTheServerStillTakesItsProgress() = runTest(dispatcher) {
        syncedBefore()
        aliases(alias(RECIPE_2.toString(), key = KEY, provenance = EpisodeAliasProvenance.RSS_SOURCE_AUDIO_MATCH))
        database.listeningHistoryDao().upsert(history(RECIPE_2.toString(), progress = 8_000L))
        // Another device stored the same served ID with the key it read.
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(RECIPE_2.toString(), SHOW, progressMs = 5L, updatedAt = 1L, catalogKey = "guid-server"))))
        // With this phone's key the server would refuse the write and still answer ok.
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(RECIPE_2.toString(), SHOW, progressMs = 6L, updatedAt = 2L, catalogKey = KEY))))
        assertEquals(listOf(RECIPE_2.toString()), server.refused)

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val sent = server.pushes.last().history.single()
        assertNull(sent.recipeId)
        assertNull(sent.catalogKey)
        assertEquals(listOf(RECIPE_2.toString()), server.refused)
        assertEquals(8_000L, server.history.getValue(RECIPE_2.toString()).progressMs)
        assertEquals("guid-server", server.history.getValue(RECIPE_2.toString()).catalogKey)
    }

    /** A device on [version] that last synced at 5,000 and has [rows] stored, all synced. */
    private suspend fun upgradedDevice(vararg rows: ListeningHistoryEntity, version: Int = 2) {
        prefs.setLastSyncedUserId("user")
        prefs.setLastSyncTimestamp(5_000L)
        prefs.setSyncMetadataVersion(version)
        if (rows.isNotEmpty()) database.listeningHistoryDao().upsertAll(rows.toList())
    }

    /** A history row an older build pushed: no identity, stored at [updatedAt]. */
    @Suppress("LongParameterList")
    private fun oldBuildRow(id: String, progress: Long = 1_000L, playedAt: Long = 1_000L, updatedAt: Long = playedAt, completed: Boolean = false) =
        ListeningHistorySyncDto(id, SHOW, episodeTitle = "Cloud $id", progressMs = progress, isCompleted = completed, lastPlayedAt = playedAt, updatedAt = updatedAt)

    @Test
    fun theVersion3CatchUpPullsEverythingFirstThenPushesBackTheCloudCopiesThatLackAnIdentityOnce() = runTest(dispatcher) {
        aliases(alias(PI_ID, owner = true))
        upgradedDevice(
            history(PI_ID, progress = 1_000L, playedAt = 1_000L, dirty = false, syncedAt = 100L),
            history("777", dirty = false, syncedAt = 100L),
        )
        database.queueDao().insertQueueItem(queueItem(PI_ID))
        database.queueDao().upsertQueueMetadata(QueueMetadataEntity(queueSequence = 3L, queueUpdatedAt = 30L, syncedAt = 100L))
        // An older build pushed newer progress for the row after this phone's last pull, and the queue.
        server.push(SyncPushRequest(history = listOf(oldBuildRow(PI_ID, progress = 20_000L, playedAt = 6_000L), oldBuildRow("777"))))
        server.push(SyncPushRequest(queue = QueueSyncDto(items = listOf(QueueItemSyncDto(PI_ID, SHOW, title = "Cloud $PI_ID", position = 0, updatedAt = 30L)), queueUpdatedAt = 30L, queueSequence = 3L)))
        server.pushes.clear()

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(0L, server.pulls.single().since)
        assertTrue(server.pulls.single().richRemovals)
        val catchUp = server.pushes.single()
        val row = catchUp.history.single()
        assertEquals(PI_ID, row.episodeId)
        assertEquals(RECIPE to KEY, row.recipeId to row.catalogKey)
        // The cloud's own copy goes back: its newer progress and its own time.
        assertEquals(20_000L, row.progressMs)
        assertEquals(6_000L, row.updatedAt)
        val queue = catchUp.queue!!
        assertEquals(RECIPE to KEY, queue.items.single().let { it.recipeId to it.catalogKey })
        assertEquals(30L, queue.queueUpdatedAt)
        // The server keeps its rich removals and expands none of them again.
        assertNull(queue.recentRemovedEpisodes)
        assertEquals(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION, prefs.getSyncMetadataVersion())
        assertEquals(RECIPE, server.history.getValue(PI_ID).recipeId)
        assertEquals(RECIPE, server.queueItems.getValue(PI_ID).recipeId)
        assertEquals(20_000L, database.listeningHistoryDao().getHistoryItem(PI_ID)!!.progressMs)

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(server.now, server.pulls.last().since)
        assertEquals(1, server.pushes.size)
    }

    @Test
    fun aCatchUpAskedForAfterTheAddressRepairSendsTheIdentityOfEpisodesItsReadsSettled() = runTest(dispatcher) {
        // Own-episode-IDs P4 PR 15: the repair's reads settle aliases after the first catch-up ran.
        upgradedDevice(history(PI_ID, dirty = false, syncedAt = 100L))
        server.push(SyncPushRequest(history = listOf(oldBuildRow(PI_ID))))
        server.pushes.clear()
        assertTrue(coordinator.syncNow() is SyncResult.Success)
        assertEquals(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION, prefs.getSyncMetadataVersion())
        assertNull("nothing known yet", server.history.getValue(PI_ID).recipeId)

        // The repair's read of the feed settles the episode: its alias becomes trusted with a key.
        database.episodeIdentityDao().upsertAlias(alias(PI_ID, owner = true, provenance = EpisodeAliasProvenance.FEED_CONFIRMED_KEY))
        assertTrue(coordinator.syncNow() is SyncResult.Success)
        assertNull("a delta sync doesn't send it", server.history.getValue(PI_ID).recipeId)

        coordinator.requestIdentityCatchUp()
        assertEquals(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION - 1, prefs.getSyncMetadataVersion())
        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(0L, server.pulls.last().since)
        assertEquals(RECIPE to KEY, server.history.getValue(PI_ID).let { it.recipeId to it.catalogKey })
        assertEquals(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION, prefs.getSyncMetadataVersion())
    }

    @Test
    fun aCatchUpRequestLeavesAPhoneThatHasntCaughtUpYetAsItIs() = runTest(dispatcher) {
        coordinator.requestIdentityCatchUp()
        assertEquals("never synced", 0, prefs.getSyncMetadataVersion())

        prefs.setSyncMetadataVersion(2)
        coordinator.requestIdentityCatchUp()
        assertEquals(2, prefs.getSyncMetadataVersion())
    }

    @Test
    fun theCatchUpKeepsEachCloudRowsTimeSoOtherDevicesPullNothingNew() = runTest(dispatcher) {
        aliases(alias(PI_ID, owner = true))
        upgradedDevice(history(PI_ID, progress = 9_000L, playedAt = 6_000L, dirty = false, syncedAt = 100L))
        server.push(SyncPushRequest(history = listOf(oldBuildRow(PI_ID, progress = 9_000L, playedAt = 6_000L))))
        // Another device last pulled after the row was stored.
        val otherDevicesLastPull = 7_000L

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val stored = server.history.getValue(PI_ID)
        assertEquals(RECIPE to KEY, stored.recipeId to stored.catalogKey)
        assertEquals(6_000L, stored.updatedAt)
        assertEquals(9_000L, stored.progressMs)
        // Nothing reaches the other device again, so a row it removed stays removed there.
        assertTrue(server.pull(SyncPullRequest(since = otherDevicesLastPull, richRemovals = true)).history.isEmpty())
    }

    @Test
    fun aRowRemovedHereBeforeTheUpdateStaysRemovedAfterTheCatchUpPull() = runTest(dispatcher) {
        aliases(alias(PI_ID, owner = true))
        upgradedDevice(history("kept", dirty = false, syncedAt = 100L))
        // PI_ID reached this phone before its last sync (5,000) and the listener removed it here;
        // "new" was stored after that sync and was never pulled.
        server.push(SyncPushRequest(history = listOf(oldBuildRow(PI_ID, updatedAt = 4_000L), oldBuildRow("kept", updatedAt = 3_000L), oldBuildRow("new", updatedAt = 6_000L))))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(0L, server.pulls.single().since)
        assertNull(database.listeningHistoryDao().getHistoryItem(PI_ID))
        assertEquals("Cloud new", database.listeningHistoryDao().getHistoryItem("new")!!.episodeTitle)
        // Its cloud copy still gains the identity, at its own time, so no device gets it back either.
        assertEquals(RECIPE, server.history.getValue(PI_ID).recipeId)
        assertEquals(4_000L, server.history.getValue(PI_ID).updatedAt)
    }

    @Test
    fun theCatchUpNeverRevertsACompletionTheCloudHasAtTheSameLastPlay() = runTest(dispatcher) {
        aliases(alias(PI_ID, owner = true))
        upgradedDevice(history(PI_ID, progress = 5_000L, playedAt = 1_000L, dirty = false, syncedAt = 100L))
        // Another device marked the episode played without playing it, so its last play is unchanged.
        server.push(SyncPushRequest(history = listOf(oldBuildRow(PI_ID, progress = 5_000L, playedAt = 1_000L, updatedAt = 6_000L, completed = true))))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertTrue(server.pushes.last().history.single().isCompleted)
        val stored = server.history.getValue(PI_ID)
        assertTrue(stored.isCompleted)
        assertEquals(6_000L, stored.updatedAt)
        assertEquals(RECIPE to KEY, stored.recipeId to stored.catalogKey)
    }

    @Test
    fun theCatchUpPushesEveryBatchInItsOwnSync() = runTest(dispatcher) {
        val ids = (1..150).map { "6184209${it.toString().padStart(4, '0')}" }
        aliases(*ids.map { alias(it, key = "guid-$it", owner = true) }.toTypedArray())
        upgradedDevice(*ids.map { history(it, dirty = false, syncedAt = 100L) }.toTypedArray())
        server.push(SyncPushRequest(history = ids.map { oldBuildRow(it) }))
        server.pushes.clear()

        val result = coordinator.syncNow() as SyncResult.Success

        assertEquals(listOf(100, 50), server.pushes.map { it.history.size })
        assertEquals(150, result.pushedHistory)
        assertTrue(ids.all { server.history.getValue(it).catalogKey == "guid-$it" })
        assertTrue(database.listeningHistoryDao().getDirtyListeningHistory().isEmpty())
        assertEquals(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION, prefs.getSyncMetadataVersion())
    }

    @Test
    fun aCatchUpCutShortMarksNoRowAndStartsAgainWithoutOverwritingNewerProgress() = runTest(dispatcher) {
        val ids = (1..150).map { "6184209${it.toString().padStart(4, '0')}" }
        aliases(*ids.map { alias(it, key = "guid-$it", owner = true) }.toTypedArray())
        upgradedDevice(*ids.map { history(it, dirty = false, syncedAt = 100L) }.toTypedArray())
        server.push(SyncPushRequest(history = ids.map { oldBuildRow(it) }))
        server.pushes.clear()
        // The catch-up's second push fails.
        server.failingPushAttempts = setOf(2)

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(listOf(100), server.pushes.map { it.history.size })
        assertTrue(database.listeningHistoryDao().getDirtyListeningHistory().isEmpty())
        assertTrue(prefs.getSyncMetadataVersion() < UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION)

        // An older build plays a row of the unsent batch further, and one of the sent batch.
        val unsent = ids[120]
        val sent = ids[10]
        server.now = 20_000L
        server.push(SyncPushRequest(history = listOf(oldBuildRow(unsent, progress = 50_000L, playedAt = 15_000L), oldBuildRow(sent, progress = 40_000L, playedAt = 15_000L))))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(0L, server.pulls.last().since)
        assertEquals(50, server.pushes.last().history.size)
        assertEquals(50_000L, server.history.getValue(unsent).progressMs)
        assertEquals("guid-$unsent", server.history.getValue(unsent).catalogKey)
        assertEquals(40_000L, server.history.getValue(sent).progressMs)
        assertEquals("guid-$sent", server.history.getValue(sent).catalogKey)
        assertEquals(50_000L, database.listeningHistoryDao().getHistoryItem(unsent)!!.progressMs)
        assertEquals(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION, prefs.getSyncMetadataVersion())
    }

    @Test
    fun aLargeBacklogIsPushedOneBoundedBatchPerSyncTheNewestFirst() = runTest(dispatcher) {
        syncedBefore()
        val ids = (1..150).map { "-${1_000 + it}" }
        database.listeningHistoryDao().upsertAll(ids.mapIndexed { index, id -> history(id, playedAt = 1_000L + index) })

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(ids.takeLast(100).reversed(), server.pushes.single().history.map { it.episodeId })
        assertEquals(50, database.listeningHistoryDao().getDirtyListeningHistory().size)

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(ids.take(50).reversed(), server.pushes.last().history.map { it.episodeId })
        assertTrue(database.listeningHistoryDao().getDirtyListeningHistory().isEmpty())
    }

    @Test
    fun anAccountSwitchClearsTheQueueRemovalsWithIdentityTooAndCatchesUpTheNewAccount() = runTest(dispatcher) {
        prefs.setLastSyncedUserId("someone-else")
        prefs.setLastSyncTimestamp(5_000L)
        prefs.setSyncMetadataVersion(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION)
        aliases(alias(PI_ID, owner = true))
        database.listeningHistoryDao().upsert(history("previous-account"))
        database.queueDao().insertQueueItem(queueItem(PI_ID))
        database.queueDao().upsertQueueMetadata(
            QueueMetadataEntity(
                queueSequence = 4L,
                isDirty = true,
                recentRemovedEpisodeIds = "[\"-9\"]",
                recentRemovedEpisodes = QueueRemovalIdentity.serializeList(listOf(QueueRemovalIdentity("-9", RECIPE, SHOW, KEY))),
            ),
        )
        // The new account's row, pushed by an older build.
        server.push(SyncPushRequest(history = listOf(oldBuildRow(PI_ID, updatedAt = 4_000L))))
        server.pushes.clear()

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val metadata = database.queueDao().getQueueMetadata()!!
        assertNull(metadata.recentRemovedEpisodeIds)
        assertNull(metadata.recentRemovedEpisodes)
        assertTrue(database.queueDao().getAllQueueItemsSync().isEmpty())
        assertNull(database.listeningHistoryDao().getHistoryItem("previous-account"))
        // Only the new account's cloud copy goes back, with its identity and its own time.
        val catchUp = server.pushes.single()
        assertEquals(listOf(PI_ID), catchUp.history.map { it.episodeId })
        assertNull(catchUp.queue)
        assertEquals(RECIPE to KEY, server.history.getValue(PI_ID).let { it.recipeId to it.catalogKey })
        assertEquals(4_000L, server.history.getValue(PI_ID).updatedAt)
        assertEquals(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION, prefs.getSyncMetadataVersion())
    }

    @Test
    fun aPulledRowOfThePlayingEpisodeUnderAnIdWithoutARowHereIsStoredAndNeverTouchesTheOwnerRow() = runTest(dispatcher) {
        syncedBefore()
        aliases(alias(RECIPE.toString(), owner = true))
        database.listeningHistoryDao().upsert(history(RECIPE.toString(), progress = 10_000L, playedAt = 2_000L, dirty = false, syncedAt = 100L))
        playback.activeId = RECIPE.toString()
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(PI_ID, SHOW, progressMs = 30_000L, lastPlayedAt = 4_000L, updatedAt = 4_000L, recipeId = RECIPE, catalogKey = KEY))))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        // The pulled row is that ID's only copy, so it is stored as the cloud has it...
        assertEquals(EpisodeAliasState.TRUSTED, database.episodeIdentityDao().getAlias(PI_ID)!!.state)
        assertEquals(30_000L, database.listeningHistoryDao().getHistoryItem(PI_ID)!!.progressMs)
        // ...while the owner row, which playback writes, keeps the playing progress and stays the owner.
        val store = database.episodeListenerStore()
        assertEquals(10_000L, database.listeningHistoryDao().getHistoryItem(RECIPE.toString())!!.progressMs)
        assertEquals(RECIPE.toString(), store.historyId(PI_ID))
        // The next progress save is the newest row of the episode again.
        database.listeningHistoryDao().upsert(history(RECIPE.toString(), progress = 12_000L, playedAt = 5_000L))
        assertEquals(12_000L, store.projectedHistory(RECIPE.toString())!!.progressMs)
    }

    @Test
    fun aPulledRowOfThePlayingEpisodeIsIdentifiedBeforeTheMergeSoItsStoredRowKeepsItsProgress() = runTest(dispatcher) {
        syncedBefore()
        aliases(alias(RECIPE.toString(), owner = true))
        // PI_ID's row is stored here but its ID has no alias yet; only the pulled identity names its episode.
        database.listeningHistoryDao().upsertAll(
            listOf(
                history(RECIPE.toString(), progress = 10_000L, playedAt = 2_000L, dirty = false, syncedAt = 100L),
                history(PI_ID, progress = 1_000L, playedAt = 1_000L, dirty = false, syncedAt = 100L),
            ),
        )
        playback.activeId = RECIPE.toString()
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(PI_ID, SHOW, progressMs = 30_000L, lastPlayedAt = 4_000L, updatedAt = 4_000L, recipeId = RECIPE, catalogKey = KEY))))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(listOf(RECIPE.toString(), PI_ID), database.episodeListenerStore().memberIds(PI_ID))
        // Joined before the history merge, so the playing episode's guard covers it.
        assertEquals(1_000L, database.listeningHistoryDao().getHistoryItem(PI_ID)!!.progressMs)
        assertEquals(10_000L, database.listeningHistoryDao().getHistoryItem(RECIPE.toString())!!.progressMs)
    }

    /** This phone's queue removal of [ids], recorded as `QueueRepository.removeFromQueue` records it. */
    private suspend fun removeHere(vararg ids: String) {
        val store = database.episodeListenerStore()
        ids.forEach { database.queueDao().deleteQueueItemByEpisodeId(it) }
        val removed = store.queueRemovalIds(ids.toList())
        database.queueDao().bumpQueueVersion(updatedAt = 100L, removedEpisodeIds = removed, removedIdentities = store.queueIdentities(removed))
    }

    @Test
    fun removingAnEpisodeQueuedHereUnderAnotherIdAlsoRemovesTheCopyAnOlderBuildQueued() = runTest(dispatcher) {
        syncedBefore()
        // The listener switched the show to its RSS feed here: -21 is the RSS ID of PI_ID's episode.
        aliases(alias(PI_ID, owner = true), alias("-21", provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK))
        server.push(SyncPushRequest(queue = QueueSyncDto(items = listOf(QueueItemSyncDto(PI_ID, SHOW, title = "Cloud", position = 0, updatedAt = 10L)), queueUpdatedAt = 10L, queueSequence = 1L)))
        database.queueDao().insertQueueItem(queueItem("-21", show = "rss:feed"))
        database.queueDao().upsertQueueMetadata(QueueMetadataEntity(queueSequence = 1L, queueUpdatedAt = 20L, isDirty = true))
        assertTrue(coordinator.syncNow() is SyncResult.Success)
        assertEquals(setOf(PI_ID, "-21"), server.queueItems.keys)

        removeHere("-21")
        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertTrue(server.queueItems.isEmpty())
        assertEquals(listOf("-21", PI_ID), server.legacyRemovals)
    }

    @Test
    fun aRemovalReachesTheCloudCopyTheMergeFoldedIntoThePhonesItemAndItNeverComesBack() = runTest(dispatcher) {
        syncedBefore()
        aliases(alias(RECIPE.toString(), owner = true), alias(PI_ID))
        // An older build queued the episode as PI_ID (no identity); a fresh v2 install queued it as RECIPE.
        server.push(
            SyncPushRequest(
                queue = QueueSyncDto(
                    items = listOf(QueueItemSyncDto(PI_ID, SHOW, title = "Cloud", position = 0, updatedAt = 20L), QueueItemSyncDto(RECIPE.toString(), SHOW, title = "Cloud", position = 1, updatedAt = 20L, recipeId = RECIPE, catalogKey = KEY)),
                    queueUpdatedAt = 20L,
                    queueSequence = 2L,
                ),
            ),
        )
        database.queueDao().insertQueueItem(queueItem(RECIPE.toString()))
        database.queueDao().upsertQueueMetadata(QueueMetadataEntity(queueSequence = 1L, queueUpdatedAt = 10L, syncedAt = 100L))
        assertTrue(coordinator.syncNow() is SyncResult.Success)
        assertEquals(listOf(RECIPE.toString()), database.queueDao().getAllQueueItemsSync().map { it.episodeId })

        removeHere(RECIPE.toString())
        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertTrue(server.queueItems.isEmpty())
        // The older build pulled the removal, then queued another episode.
        server.push(
            SyncPushRequest(
                queue = QueueSyncDto(
                    items = listOf(QueueItemSyncDto("other", SHOW, title = "Other", position = 0, updatedAt = 500L)),
                    queueUpdatedAt = 500L,
                    queueSequence = 5L,
                    recentRemovedEpisodeIds = listOf(RECIPE.toString(), PI_ID),
                ),
            ),
        )
        server.now = 20_000L

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(listOf("other"), database.queueDao().getAllQueueItemsSync().map { it.episodeId })
    }

    @Test
    fun aStaleRichRemovalNeverReachesTheEpisodeAnotherDeviceQueuedAgain() = runTest(dispatcher) {
        syncedBefore()
        // Device B queues and removes an episode of a show this phone never read, then queues it again under another ID.
        val otherShow = "88"
        val key = "guid-88"
        val removal = QueueRemovalSyncDto("70001", recipeId = null, show = otherShow, catalogKey = key)
        server.push(SyncPushRequest(queue = QueueSyncDto(items = listOf(QueueItemSyncDto(removal.id, otherShow, title = "Cloud", position = 0, updatedAt = 10L, catalogKey = key)), queueUpdatedAt = 10L, queueSequence = 1L)))
        server.push(SyncPushRequest(queue = QueueSyncDto(queueUpdatedAt = 20L, queueSequence = 2L, recentRemovedEpisodeIds = listOf(removal.id), recentRemovedEpisodes = listOf(removal))))
        assertTrue(coordinator.syncNow() is SyncResult.Success)
        server.push(
            SyncPushRequest(
                queue = QueueSyncDto(
                    items = listOf(QueueItemSyncDto("70002", otherShow, title = "Cloud", position = 0, updatedAt = 30L, catalogKey = key)),
                    queueUpdatedAt = 30L,
                    queueSequence = 3L,
                    recentRemovedEpisodeIds = listOf(removal.id),
                    recentRemovedEpisodes = emptyList(),
                ),
            ),
        )
        server.now = 20_000L
        assertTrue(coordinator.syncNow() is SyncResult.Success)
        assertEquals(listOf("70002"), database.queueDao().getAllQueueItemsSync().map { it.episodeId })
        assertNull(database.queueDao().getQueueMetadata()!!.recentRemovedEpisodes)

        // This phone queues something else, and its push carries no removal of the re-queued episode.
        database.queueDao().insertQueueItem(queueItem("mine", position = 1))
        database.queueDao().bumpQueueVersion(updatedAt = 40L)
        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(emptyList<QueueRemovalSyncDto>(), server.pushes.last().queue!!.recentRemovedEpisodes)
        assertEquals(setOf("70002", "mine"), server.queueItems.keys)
    }
}
