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
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueRemovalSyncDto
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

    private suspend fun aliases(vararg rows: cx.aswin.boxlore.core.database.EpisodeAliasEntity) {
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
    fun aRowWhoseRecipeIdConflictsLocallyIsPushedWithoutItSoTheServerStoresIt() = runTest(dispatcher) {
        syncedBefore()
        // KEY took the recipe ID; KEY_2's episode, whose recipe ID is the same, took a reserved ID.
        aliases(alias(RECIPE.toString(), owner = true), alias("-5", key = KEY_2, recipeId = RECIPE, owner = true, provenance = EpisodeAliasProvenance.FEED_RESERVED))
        database.listeningHistoryDao().upsert(history("-5", progress = 7_000L))
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(RECIPE.toString(), SHOW, progressMs = 5L, updatedAt = 1L, recipeId = RECIPE, catalogKey = KEY))))

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val sent = server.pushes.last().history.single { it.episodeId == "-5" }
        assertNull(sent.recipeId)
        assertEquals(KEY_2, sent.catalogKey)
        assertTrue(server.refused.isEmpty())
        val stored = server.history.getValue("-5")
        assertEquals(7_000L, stored.progressMs)
        assertNull(stored.recipeId)
        assertEquals(KEY_2, stored.catalogKey)
        // The other episode's row keeps its own identity.
        assertEquals(KEY, server.history.getValue(RECIPE.toString()).catalogKey)
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

    @Test
    fun theVersion3CatchUpPullsEverythingFirstThenPushesEveryRowWithAnIdentityOnce() = runTest(dispatcher) {
        prefs.setLastSyncedUserId("user")
        prefs.setLastSyncTimestamp(5_000L)
        prefs.setSyncMetadataVersion(2)
        aliases(alias(PI_ID, owner = true))
        database.listeningHistoryDao().upsertAll(
            listOf(
                history(PI_ID, progress = 1_000L, playedAt = 1_000L, dirty = false, syncedAt = 100L),
                history("777", dirty = false, syncedAt = 100L),
            ),
        )
        database.queueDao().insertQueueItem(queueItem(PI_ID))
        database.queueDao().upsertQueueMetadata(QueueMetadataEntity(queueSequence = 3L, queueUpdatedAt = 30L, syncedAt = 100L))
        // An older build pushed newer progress for the same row after this phone's last pull.
        server.push(SyncPushRequest(history = listOf(ListeningHistorySyncDto(PI_ID, SHOW, progressMs = 20_000L, lastPlayedAt = 6_000L, updatedAt = 6_000L))))
        server.pushes.clear()

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(0L, server.pulls.single().since)
        assertTrue(server.pulls.single().richRemovals)
        val catchUp = server.pushes.single()
        val row = catchUp.history.single()
        assertEquals(PI_ID, row.episodeId)
        assertEquals(RECIPE to KEY, row.recipeId to row.catalogKey)
        // Pulled first, so the re-pushed row carries the newer progress, not this phone's stale copy.
        assertEquals(20_000L, row.progressMs)
        assertEquals(RECIPE, catchUp.queue!!.items.single().recipeId)
        assertEquals(UserSyncCoordinator.IDENTITY_SYNC_METADATA_VERSION, prefs.getSyncMetadataVersion())
        assertEquals(RECIPE, server.history.getValue(PI_ID).recipeId)

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        assertEquals(server.now, server.pulls.last().since)
        assertEquals(1, server.pushes.size)
    }

    @Test
    fun theCatchUpPushesEveryBatchInItsOwnSync() = runTest(dispatcher) {
        prefs.setLastSyncedUserId("user")
        prefs.setLastSyncTimestamp(5_000L)
        prefs.setSyncMetadataVersion(2)
        val ids = (1..150).map { "6184209${it.toString().padStart(4, '0')}" }
        aliases(*ids.map { alias(it, key = "guid-$it", owner = true) }.toTypedArray())
        database.listeningHistoryDao().upsertAll(ids.map { history(it, dirty = false, syncedAt = 100L) })

        val result = coordinator.syncNow() as SyncResult.Success

        assertEquals(listOf(100, 50), server.pushes.map { it.history.size })
        assertEquals(150, result.pushedHistory)
        assertTrue(ids.all { server.history.getValue(it).catalogKey == "guid-$it" })
        assertTrue(database.listeningHistoryDao().getDirtyListeningHistory().isEmpty())
    }

    @Test
    fun anAccountSwitchClearsTheQueueRemovalsWithIdentityToo() = runTest(dispatcher) {
        prefs.setLastSyncedUserId("someone-else")
        prefs.setLastSyncTimestamp(5_000L)
        database.queueDao().insertQueueItem(queueItem(PI_ID))
        database.queueDao().upsertQueueMetadata(
            QueueMetadataEntity(
                queueSequence = 4L,
                isDirty = true,
                recentRemovedEpisodeIds = "[\"-9\"]",
                recentRemovedEpisodes = QueueRemovalIdentity.serializeList(listOf(QueueRemovalIdentity("-9", RECIPE, SHOW, KEY))),
            ),
        )

        assertTrue(coordinator.syncNow() is SyncResult.Success)

        val metadata = database.queueDao().getQueueMetadata()!!
        assertNull(metadata.recentRemovedEpisodeIds)
        assertNull(metadata.recentRemovedEpisodes)
        assertTrue(database.queueDao().getAllQueueItemsSync().isEmpty())
        assertTrue(server.pushes.isEmpty())
    }
}
