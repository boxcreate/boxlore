package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.DaoQueueSyncPort
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.FakePlayback
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.KEY
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.PI_ID
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.RECIPE
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.SHOW
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.alias
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.history
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
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
 * A linked watch is one more device: what it did merges by the rules of a cloud pull, and is then
 * marked for the next cloud push, through [UserSyncCoordinator.applyLinkedDeviceChanges].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedDeviceSyncTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: BoxLoreDatabase
    private lateinit var playback: FakePlayback
    private lateinit var coordinator: UserSyncCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = SyncIdentityFixtures.directDatabase(context)
        playback = FakePlayback()
        val identity = SyncEpisodeIdentity(database)
        val queuePort = DaoQueueSyncPort(database)
        coordinator = UserSyncCoordinator(
            boxLoreApi = SyncIdentityFixtures.api(FakeSyncServer()),
            publicKey = "key",
            authUserIdProvider = { null },
            tokenProvider = { null },
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
            boxcastPrefs = BoxcastPrefs(context),
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

    private fun watchRow(id: String, progress: Long, playedAt: Long, completed: Boolean = false, recipeId: Long? = null, key: String? = null) =
        ListeningHistorySyncDto(
            episodeId = id,
            podcastId = SHOW,
            episodeTitle = "Episode $id",
            episodeAudioUrl = "https://example.com/$id.mp3",
            progressMs = progress,
            durationMs = 60_000L,
            isCompleted = completed,
            lastPlayedAt = playedAt,
            updatedAt = playedAt,
            recipeId = recipeId,
            catalogKey = key,
        )

    private fun show(id: String, subscribed: Boolean, subscribedAt: Long = 100L) =
        PodcastEntity(
            podcastId = id,
            title = "Show $id",
            author = "Author",
            imageUrl = "",
            description = null,
            isSubscribed = subscribed,
            subscribedAt = subscribedAt,
            isDirty = false,
            syncedAt = 50L,
        )

    private suspend fun stored(id: String) = database.listeningHistoryDao().getHistoryItem(id)

    @Test
    fun `newer listening on the watch replaces the phone's and is marked for the cloud`() = runTest(dispatcher) {
        database.listeningHistoryDao().upsert(history("a", progress = 5_000L, playedAt = 1_000L, dirty = false, syncedAt = 900L))

        coordinator.applyLinkedDeviceChanges(emptyList(), listOf(watchRow("a", progress = 30_000L, playedAt = 2_000L)))

        val row = stored("a")!!
        assertEquals(30_000L, row.progressMs)
        assertEquals(2_000L, row.lastPlayedAt)
        assertTrue(row.isDirty)
    }

    @Test
    fun `older listening on the watch never moves the phone back`() = runTest(dispatcher) {
        database.listeningHistoryDao().upsert(history("a", progress = 40_000L, playedAt = 5_000L))

        coordinator.applyLinkedDeviceChanges(emptyList(), listOf(watchRow("a", progress = 10_000L, playedAt = 2_000L)))

        assertEquals(40_000L, stored("a")!!.progressMs)
        assertEquals(5_000L, stored("a")!!.lastPlayedAt)
    }

    @Test
    fun `an episode only the watch played, or finished, becomes part of the phone's history`() = runTest(dispatcher) {
        coordinator.applyLinkedDeviceChanges(
            emptyList(),
            listOf(watchRow("new", progress = 12_000L, playedAt = 2_000L), watchRow("done", progress = 60_000L, playedAt = 3_000L, completed = true)),
        )

        assertEquals(12_000L, stored("new")!!.progressMs)
        assertTrue(stored("new")!!.isDirty)
        assertTrue(stored("done")!!.isCompleted)
        assertEquals("done", playback.handedOff)
    }

    @Test
    fun `the episode playing on the phone is left alone`() = runTest(dispatcher) {
        database.listeningHistoryDao().upsert(history("a", progress = 5_000L, playedAt = 1_000L))
        playback.activeId = "a"

        coordinator.applyLinkedDeviceChanges(emptyList(), listOf(watchRow("a", progress = 30_000L, playedAt = 2_000L)))

        assertEquals(5_000L, stored("a")!!.progressMs)
    }

    @Test
    fun `a watch row under the shared ID joins the episode the phone stores under its own ID`() = runTest(dispatcher) {
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias(PI_ID, owner = true)))
        database.listeningHistoryDao().upsert(history(PI_ID, progress = 5_000L, playedAt = 1_000L))

        coordinator.applyLinkedDeviceChanges(emptyList(), listOf(watchRow(RECIPE.toString(), progress = 30_000L, playedAt = 2_000L, recipeId = RECIPE, key = KEY)))

        val members = database.episodeListenerStore().memberIds(PI_ID)
        assertTrue("members were $members", RECIPE.toString() in members)
        assertEquals(30_000L, stored(RECIPE.toString())!!.progressMs)
    }

    @Test
    fun `a follow made on the watch is added and an unfollow removes the show, both marked for the cloud`() = runTest(dispatcher) {
        database.podcastDao().upsert(show("old", subscribed = true, subscribedAt = 100L))

        coordinator.applyLinkedDeviceChanges(
            listOf(
                UserSubscriptionSyncDto(podcastId = "9001", isSubscribed = true, subscribedAt = 500L, updatedAt = 500L),
                UserSubscriptionSyncDto(podcastId = "old", isSubscribed = false, subscribedAt = 0L, unsubscribedAt = 600L, updatedAt = 600L),
            ),
            emptyList(),
        )

        val followed = database.podcastDao().getPodcast("9001")!!
        assertTrue(followed.isSubscribed)
        assertEquals(500L, followed.subscribedAt)
        assertTrue(followed.isDirty)
        val unfollowed = database.podcastDao().getPodcast("old")!!
        assertFalse(unfollowed.isSubscribed)
        assertTrue(unfollowed.isDirty)
    }

    @Test
    fun `an unfollow older than the phone's own follow is ignored`() = runTest(dispatcher) {
        database.podcastDao().upsert(show("kept", subscribed = true, subscribedAt = 900L))

        coordinator.applyLinkedDeviceChanges(
            listOf(UserSubscriptionSyncDto(podcastId = "kept", isSubscribed = false, subscribedAt = 0L, unsubscribedAt = 600L, updatedAt = 600L)),
            emptyList(),
        )

        assertTrue(database.podcastDao().getPodcast("kept")!!.isSubscribed)
    }

    @Test
    fun `the watch is told every followed show and the newest listening first, with shared IDs`() = runTest(dispatcher) {
        database.podcastDao().upsert(show("1", subscribed = true))
        database.podcastDao().upsert(show("2", subscribed = false))
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias(PI_ID, owner = true)))
        database.listeningHistoryDao().upsert(history(PI_ID, progress = 5_000L, playedAt = 1_000L))
        database.listeningHistoryDao().upsert(history("b", progress = 7_000L, playedAt = 3_000L))
        database.listeningHistoryDao().upsert(history("c", progress = 9_000L, playedAt = 2_000L))

        val rows = coordinator.linkedDeviceRows(historyLimit = 2)
        val all = coordinator.linkedDeviceRows(historyLimit = 10)

        assertEquals(listOf("1"), rows.shows.map { it.podcastId })
        assertEquals(listOf("b", "c"), rows.history.map { it.episodeId })
        assertEquals(RECIPE, all.history.single { it.episodeId == PI_ID }.recipeId)
        assertEquals(1_000L, all.history.single { it.episodeId == PI_ID }.updatedAt)
    }
}
