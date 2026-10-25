package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.DaoQueueSyncPort
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.FakePlayback
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.KEY
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.PI_ID
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.RECIPE
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.SHOW
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.alias
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.catalogRow
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.queueItem
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueRemovalSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [QueueSyncResolver] merges per episode (docs/EPISODE_IDS.md §5): an episode queued under one ID
 * here and another ID in the cloud is one queue entry, and a removal under one ID reaches the
 * others. [PI_ID] and [RECIPE] are two trusted IDs of one episode in most cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueSyncIdentityTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var port: DaoQueueSyncPort
    private lateinit var playback: FakePlayback

    @Before
    fun setUp() {
        database = SyncIdentityFixtures.directDatabase(ApplicationProvider.getApplicationContext<Context>())
        port = DaoQueueSyncPort(database)
        playback = FakePlayback()
    }

    @After
    fun tearDown() {
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    private fun resolver(podcastRepository: PodcastRepository? = null) =
        QueueSyncResolver(port, database, playback, podcastRepository, SyncEpisodeIdentity(database))

    private suspend fun oneEpisodeUnderTwoIds() {
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias(RECIPE.toString(), owner = true), alias(PI_ID)))
    }

    /** This phone's queue: [ids] in order, with unsynced changes when [dirty]. */
    private suspend fun localQueue(vararg ids: String, dirty: Boolean, plain: List<String> = emptyList(), rich: List<QueueRemovalIdentity> = emptyList()) {
        database.queueDao().insertQueueItems(ids.mapIndexed { index, id -> queueItem(id, position = index) })
        database.queueDao().upsertQueueMetadata(
            QueueMetadataEntity(
                queueSequence = 1L,
                queueUpdatedAt = 10L,
                isDirty = dirty,
                recentRemovedEpisodeIds = QueueDao.serializeRecentRemovedEpisodeIds(plain),
                recentRemovedEpisodes = QueueRemovalIdentity.serializeList(rich),
            ),
        )
    }

    private fun remote(vararg ids: String, legacy: List<String> = emptyList(), rich: List<QueueRemovalSyncDto>? = null, updatedAt: Long = 20L) = QueueSyncDto(
        items = ids.mapIndexed { index, id -> QueueItemSyncDto(episodeId = id, podcastId = SHOW, title = "Cloud $id", position = index) },
        queueUpdatedAt = updatedAt,
        queueSequence = 2L,
        recentRemovedEpisodeIds = legacy,
        recentRemovedEpisodes = rich,
    )

    private suspend fun queuedIds() = database.queueDao().getAllQueueItemsSync().map { it.episodeId }

    private suspend fun storedMetadata() = database.queueDao().getQueueMetadata()!!

    @Test
    fun aTombstoneUnderAPodcastIndexIdRemovesTheEpisodeQueuedUnderItsRecipeId() = runTest {
        oneEpisodeUnderTwoIds()
        localQueue(RECIPE.toString(), "other", dirty = true)

        // An older build removed the episode under its Podcast Index ID.
        resolver().resolveQueue(remote("other", legacy = listOf(PI_ID)), syncedAt = 99L)

        assertEquals(listOf("other"), queuedIds())
        assertEquals(listOf(PI_ID), QueueDao.parseRecentRemovedEpisodeIds(storedMetadata().recentRemovedEpisodeIds))
    }

    @Test
    fun thisPhonesRemovalDropsTheCloudsItemOfTheSameEpisodeUnderAnotherId() = runTest {
        oneEpisodeUnderTwoIds()
        localQueue("other", dirty = true, plain = listOf(RECIPE.toString()))

        resolver().resolveQueue(remote(PI_ID, "other"), syncedAt = 99L)

        assertEquals(listOf("other"), queuedIds())
    }

    @Test
    fun theLegacyAndRichRemovalListsAreUnited() = runTest {
        oneEpisodeUnderTwoIds()
        localQueue("111", PI_ID, "keep", dirty = true)
        // The rich removal names the episode by show and key under an ID this phone doesn't know.
        val rich = QueueRemovalSyncDto(id = "4503599627379999", recipeId = null, show = SHOW, catalogKey = KEY)

        resolver().resolveQueue(remote("keep", legacy = listOf("111", rich.id), rich = listOf(rich)), syncedAt = 99L)

        assertEquals(listOf("keep"), queuedIds())
        val metadata = storedMetadata()
        assertEquals(setOf("111", rich.id), QueueDao.parseRecentRemovedEpisodeIds(metadata.recentRemovedEpisodeIds).toSet())
        assertEquals(listOf(QueueRemovalIdentity(rich.id, show = SHOW, catalogKey = KEY)), QueueRemovalIdentity.parseList(metadata.recentRemovedEpisodes))
        assertTrue(metadata.isDirty)
    }

    @Test
    fun aRichRemovalThePlainListNoLongerHoldsIsStale() = runTest {
        oneEpisodeUnderTwoIds()
        localQueue(PI_ID, dirty = true)
        // An older build queued the episode again after a v2 device removed it, and replaced the plain list.
        val stale = QueueRemovalSyncDto(id = RECIPE.toString(), recipeId = RECIPE, show = SHOW, catalogKey = KEY)

        resolver().resolveQueue(remote("new", legacy = emptyList(), rich = listOf(stale)), syncedAt = 99L)

        assertEquals(listOf("new", PI_ID), queuedIds())
        assertNull(storedMetadata().recentRemovedEpisodes)
    }

    @Test
    fun aRemovalWhoseEpisodeItsSideStillQueuesUnderAnotherIdIsStale() = runTest {
        oneEpisodeUnderTwoIds()
        localQueue(RECIPE.toString(), dirty = true)

        // The cloud lists the removal of the Podcast Index ID but still queues the episode under its recipe ID.
        resolver().resolveQueue(remote(RECIPE.toString(), legacy = listOf(PI_ID)), syncedAt = 99L)

        assertEquals(listOf(RECIPE.toString()), queuedIds())
    }

    @Test
    fun aPulledItemNeverBringsBackAnIdThatWasRemoved() = runTest {
        oneEpisodeUnderTwoIds()
        localQueue(RECIPE.toString(), dirty = true)

        // The cloud removed the recipe ID's item but queues the episode under its Podcast Index ID.
        resolver().resolveQueue(remote(PI_ID, legacy = listOf(RECIPE.toString())), syncedAt = 99L)

        assertEquals(listOf(PI_ID), queuedIds())
    }

    @Test
    fun aPulledItemOfAnEpisodeQueuedHereKeepsThePhonesItemOnce() = runTest {
        oneEpisodeUnderTwoIds()
        localQueue(RECIPE.toString(), dirty = false)

        resolver().resolveQueue(remote(PI_ID, "999", RECIPE.toString()), syncedAt = 99L)

        val items = database.queueDao().getAllQueueItemsSync()
        assertEquals(listOf(RECIPE.toString(), "999"), items.map { it.episodeId })
        assertEquals("Queued $RECIPE", items.first().title)
        assertEquals(false, storedMetadata().isDirty)
    }

    @Test
    fun thePlayingEpisodeKeepsTheIdItPlaysUnder() = runTest {
        oneEpisodeUnderTwoIds()
        localQueue(dirty = false)
        playback.activeId = RECIPE.toString()

        resolver().resolveQueue(remote("999", PI_ID), syncedAt = 99L)

        assertEquals(listOf("999", RECIPE.toString()), queuedIds())
    }

    @Test
    fun hydrationReadsTheCatalogRowOfAnotherIdOfTheEpisode() = runTest {
        oneEpisodeUnderTwoIds()
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(catalogRow(RECIPE.toString())))
        localQueue(dirty = false)
        val bare = QueueSyncDto(items = listOf(QueueItemSyncDto(episodeId = PI_ID, podcastId = SHOW, position = 0)), queueSequence = 2L)

        resolver().resolveQueue(bare, syncedAt = 99L)

        val item = database.queueDao().getAllQueueItemsSync().single()
        assertEquals(PI_ID, item.episodeId)
        assertEquals("Catalog $RECIPE", item.title)
    }

    @Test
    fun hydrationAsksV2ForTheRecipeIdTheCloudSentAndKeepsTheStoredId() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json").setBody(
                    """{"status":"true","source":"book","episode":{"id":$RECIPE,"servedId":"$PI_ID","catalogKey":"$KEY","feedId":77,
                    "title":"From v2","enclosureUrl":"https://example.com/v2.mp3","duration":61,"datePublished":5}}""",
                ),
            )
            localQueue(dirty = false)
            val bare = QueueSyncDto(items = listOf(QueueItemSyncDto(episodeId = PI_ID, podcastId = SHOW, position = 0, recipeId = RECIPE, catalogKey = KEY)), queueSequence = 2L)

            resolver(podcastRepository(server)).resolveQueue(bare, syncedAt = 99L)

            val item = database.queueDao().getAllQueueItemsSync().single()
            assertEquals(PI_ID, item.episodeId)
            assertEquals("From v2", item.title)
            assertEquals("/v2/episode?id=$RECIPE&show=$SHOW", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        } finally {
            server.shutdown()
        }
    }

    private fun podcastRepository(server: MockWebServer): PodcastRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        return PodcastRepository(
            baseUrl = base,
            publicKey = "unused",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
        )
    }
}
