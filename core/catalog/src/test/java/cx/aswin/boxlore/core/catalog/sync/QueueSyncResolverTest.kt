package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueSyncResolverTest {

    private lateinit var database: BoxLoreDatabase
    private lateinit var fakeQueueSyncPort: FakeQueueSyncPort
    private lateinit var fakeActivePlaybackSyncPort: FakeActivePlaybackSyncPort
    private lateinit var resolver: QueueSyncResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeQueueSyncPort = FakeQueueSyncPort()
        fakeActivePlaybackSyncPort = FakeActivePlaybackSyncPort()
        resolver = QueueSyncResolver(
            queueSyncPort = fakeQueueSyncPort,
            database = database,
            activePlaybackSyncPort = fakeActivePlaybackSyncPort,
            podcastRepository = null,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resolveQueue_case3_localAheadOrEqual_noOp() = runTest {
        fakeQueueSyncPort.metadata = QueueMetadataEntity(
            id = 1,
            queueSequence = 5L,
            queueUpdatedAt = 5000L,
            isDirty = false,
        )

        val remoteQueue = QueueSyncDto(
            queueSequence = 4L, // Older than local
            queueUpdatedAt = 4000L,
            items = listOf(
                QueueItemSyncDto(episodeId = "remote-1", podcastId = "pod-1", position = 0),
            ),
        )

        resolver.resolveQueue(remoteQueue, syncedAt = 6000L)

        assertNull(fakeQueueSyncPort.appliedItems)
        assertNull(fakeQueueSyncPort.appliedMetadata)
    }

    @Test
    fun resolveQueue_case1_linearAdvance_appliesRemoteState() = runTest {
        fakeQueueSyncPort.metadata = QueueMetadataEntity(
            id = 1,
            queueSequence = 2L,
            queueUpdatedAt = 2000L,
            isDirty = false,
        )
        fakeQueueSyncPort.items = mutableListOf(
            createQueueItem(episodeId = "ep-local", position = 0),
        )

        val remoteQueue = QueueSyncDto(
            queueSequence = 3L,
            queueUpdatedAt = 3000L,
            items = listOf(
                QueueItemSyncDto(episodeId = "ep-remote-1", podcastId = "pod-1", position = 1),
                QueueItemSyncDto(episodeId = "ep-remote-0", podcastId = "pod-1", position = 0),
            ),
            recentRemovedEpisodeIds = listOf("ep-old-removed"),
        )

        resolver.resolveQueue(remoteQueue, syncedAt = 4000L)

        val applied = fakeQueueSyncPort.appliedItems
        val appliedMeta = fakeQueueSyncPort.appliedMetadata
        assertNotNull(applied)
        assertNotNull(appliedMeta)

        assertEquals(2, applied!!.size)
        assertEquals("ep-remote-0", applied[0].episodeId)
        assertEquals(0, applied[0].position)
        assertEquals("ep-remote-1", applied[1].episodeId)
        assertEquals(1, applied[1].position)

        assertEquals(3L, appliedMeta!!.queueSequence)
        assertEquals(3000L, appliedMeta.queueUpdatedAt)
        assertFalse(appliedMeta.isDirty)
        assertEquals(4000L, appliedMeta.syncedAt)
        assertEquals(listOf("ep-old-removed"), QueueDao.parseRecentRemovedEpisodeIds(appliedMeta.recentRemovedEpisodeIds))
    }

    @Test
    fun resolveQueue_case1_linearAdvance_retainsActivePlayingEpisodeAtHead() = runTest {
        val activeItem = createQueueItem(episodeId = "ep-active-playing", position = 0)
        fakeQueueSyncPort.items = mutableListOf(activeItem)
        fakeQueueSyncPort.metadata = QueueMetadataEntity(
            id = 1,
            queueSequence = 2L,
            isDirty = false,
        )
        fakeActivePlaybackSyncPort.activeEpisodeId = "ep-active-playing"

        val remoteQueue = QueueSyncDto(
            queueSequence = 5L,
            queueUpdatedAt = 5000L,
            items = listOf(
                QueueItemSyncDto(episodeId = "ep-remote-1", podcastId = "pod-1", position = 0),
                QueueItemSyncDto(episodeId = "ep-remote-2", podcastId = "pod-1", position = 1),
            ),
        )

        resolver.resolveQueue(remoteQueue, syncedAt = 6000L)

        val applied = fakeQueueSyncPort.appliedItems
        assertNotNull(applied)
        assertEquals(3, applied!!.size)
        // Active item retained at index 0:
        assertEquals("ep-active-playing", applied[0].episodeId)
        assertEquals(0, applied[0].position)
        assertEquals("ep-remote-1", applied[1].episodeId)
        assertEquals(1, applied[1].position)
        assertEquals("ep-remote-2", applied[2].episodeId)
        assertEquals(2, applied[2].position)
    }

    @Test
    fun resolveQueue_activeEpisodeNotInQueueCache_hydratedAndRetainedAtHead() = runTest {
        val remoteItem = createQueueItem(episodeId = "ep-remote-1", position = 0)
        fakeQueueSyncPort.items = mutableListOf(remoteItem)
        fakeQueueSyncPort.metadata = QueueMetadataEntity(id = 1, queueSequence = 1L, isDirty = false)

        fakeActivePlaybackSyncPort.activeEpisodeId = "ep-playing-external"

        database.localEpisodeCatalogDao().upsertEpisodes(
            listOf(
                LocalEpisodeEntity(
                    episodeId = "ep-playing-external",
                    podcastId = "pod-ext",
                    guid = "guid-ext",
                    title = "External Playing Title",
                    description = "Desc",
                    audioUrl = "https://example.com/audio.mp3",
                    imageUrl = "https://example.com/img.jpg",
                    duration = 300,
                    publishedDate = 2000L,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeType = null,
                    enclosureType = null,
                ),
            ),
        )

        val remoteQueue = QueueSyncDto(
            queueSequence = 2L,
            queueUpdatedAt = 3000L,
            items = listOf(
                QueueItemSyncDto(episodeId = "ep-remote-1", podcastId = "pod-1", position = 0),
            ),
        )

        resolver.resolveQueue(remoteQueue, syncedAt = 4000L)

        val applied = fakeQueueSyncPort.appliedItems
        assertNotNull(applied)
        assertEquals(2, applied!!.size)
        assertEquals("ep-playing-external", applied[0].episodeId)
        assertEquals(0, applied[0].position)
        assertEquals("External Playing Title", applied[0].title)
        assertEquals("ep-remote-1", applied[1].episodeId)
        assertEquals(1, applied[1].position)
    }

    @Test
    fun resolveQueue_case2_divergedBranches_3wayTombstoneMergeAndActiveHeadRetention() = runTest {
        val activeItem = createQueueItem(episodeId = "ep-active", position = 0)
        val localOnlyItem = createQueueItem(episodeId = "ep-local-survivor", position = 1)

        fakeQueueSyncPort.items = mutableListOf(activeItem, localOnlyItem)
        fakeQueueSyncPort.metadata = QueueMetadataEntity(
            id = 1,
            queueSequence = 3L,
            queueUpdatedAt = 3000L,
            isDirty = true, // Diverged locally
            recentRemovedEpisodeIds = QueueDao.serializeRecentRemovedEpisodeIds(listOf("ep-removed-by-local")),
        )
        fakeActivePlaybackSyncPort.activeEpisodeId = "ep-active"

        val remoteQueue = QueueSyncDto(
            queueSequence = 4L,
            queueUpdatedAt = 4000L,
            items = listOf(
                QueueItemSyncDto(episodeId = "ep-removed-by-local", podcastId = "pod-1", position = 0), // Local tombstone
                QueueItemSyncDto(episodeId = "ep-remote-survivor", podcastId = "pod-1", position = 1),
            ),
            recentRemovedEpisodeIds = listOf("ep-local-survivor"), // Remote tombstone
        )

        resolver.resolveQueue(remoteQueue, syncedAt = 5000L)

        val applied = fakeQueueSyncPort.appliedItems
        val appliedMeta = fakeQueueSyncPort.appliedMetadata
        assertNotNull(applied)
        assertNotNull(appliedMeta)

        // ep-removed-by-local filtered out by local tombstone
        // ep-local-survivor filtered out by remote tombstone
        // ep-active guarded and placed at head (0)
        // ep-remote-survivor retained
        assertEquals(2, applied!!.size)
        assertEquals("ep-active", applied[0].episodeId)
        assertEquals(0, applied[0].position)
        assertEquals("ep-remote-survivor", applied[1].episodeId)
        assertEquals(1, applied[1].position)

        // Sequence bumped to max(3, 4) + 1 = 5
        assertEquals(5L, appliedMeta!!.queueSequence)
        assertEquals(4000L, appliedMeta.queueUpdatedAt)
        assertTrue(appliedMeta.isDirty) // Flagged dirty to push merged result back
        assertEquals(5000L, appliedMeta.syncedAt)
        val combinedTombstones = QueueDao.parseRecentRemovedEpisodeIds(appliedMeta.recentRemovedEpisodeIds).toSet()
        assertTrue(combinedTombstones.contains("ep-removed-by-local"))
        assertTrue(combinedTombstones.contains("ep-local-survivor"))
    }

    @Test
    fun resolveQueue_5tierHydrationCascade() = runTest {
        // Tier 1: Existing local queue item
        val tier1Local = createQueueItem(
            episodeId = "ep-tier1",
            title = "Tier 1 Title",
            position = 0,
        )
        fakeQueueSyncPort.items = mutableListOf(tier1Local)
        fakeQueueSyncPort.metadata = QueueMetadataEntity(id = 1, queueSequence = 1L, isDirty = false)

        // Tier 2: Local catalog DB entity
        database.localEpisodeCatalogDao().upsertEpisodes(
            listOf(
                LocalEpisodeEntity(
                    episodeId = "ep-tier2",
                    podcastId = "pod-2",
                    guid = "guid-2",
                    title = "Tier 2 DB Title",
                    description = "Tier 2 description",
                    audioUrl = "https://example.com/tier2.mp3",
                    imageUrl = "https://example.com/tier2.jpg",
                    duration = 1800,
                    publishedDate = 1000L,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeType = null,
                    enclosureType = null,
                ),
            ),
        )

        // Tier 3: Listening history cache
        database.listeningHistoryDao().upsert(
            ListeningHistoryEntity(
                episodeId = "ep-tier3",
                podcastId = "pod-3",
                episodeTitle = "Tier 3 History Title",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://example.com/tier3.mp3",
                podcastName = "Tier 3 Podcast",
                progressMs = 0L,
                durationMs = 90000L,
                isCompleted = false,
                lastPlayedAt = 500L,
            ),
        )

        // Tier 5: Fallback unknown
        // "ep-tier5"

        val remoteQueue = QueueSyncDto(
            queueSequence = 2L,
            queueUpdatedAt = 2000L,
            items = listOf(
                QueueItemSyncDto(episodeId = "ep-tier1", podcastId = "pod-1", position = 0),
                QueueItemSyncDto(episodeId = "ep-tier2", podcastId = "pod-2", position = 1),
                QueueItemSyncDto(episodeId = "ep-tier3", podcastId = "pod-3", position = 2),
                QueueItemSyncDto(episodeId = "ep-tier5", podcastId = "pod-5", position = 3),
            ),
        )

        resolver.resolveQueue(remoteQueue, syncedAt = 3000L)

        val applied = fakeQueueSyncPort.appliedItems
        assertNotNull(applied)
        assertEquals(4, applied!!.size)

        // Verify Tier 1
        assertEquals("ep-tier1", applied[0].episodeId)
        assertEquals("Tier 1 Title", applied[0].title)

        // Verify Tier 2
        assertEquals("ep-tier2", applied[1].episodeId)
        assertEquals("Tier 2 DB Title", applied[1].title)
        assertEquals("https://example.com/tier2.mp3", applied[1].audioUrl)
        assertEquals(1800, applied[1].duration)

        // Verify Tier 3
        assertEquals("ep-tier3", applied[2].episodeId)
        assertEquals("Tier 3 History Title", applied[2].title)
        assertEquals("Tier 3 Podcast", applied[2].podcastTitle)
        assertEquals(90, applied[2].duration) // 90000ms / 1000

        // Verify Tier 5
        assertEquals("ep-tier5", applied[3].episodeId)
        assertEquals("Episode ep-tier5", applied[3].title)
        assertEquals("Podcast pod-5", applied[3].podcastTitle)
    }

    private fun createQueueItem(
        episodeId: String,
        podcastId: String = "pod-1",
        title: String = "Test Episode",
        position: Int = 0,
    ) = QueueItem(
        episodeId = episodeId,
        title = title,
        podcastId = podcastId,
        podcastTitle = "Test Podcast",
        imageUrl = null,
        audioUrl = "https://example.com/audio.mp3",
        duration = 120,
        pubDate = 0L,
        description = null,
        position = position,
    )

    private class FakeQueueSyncPort(
        var items: MutableList<QueueItem> = mutableListOf(),
        var metadata: QueueMetadataEntity = QueueMetadataEntity(id = 1),
    ) : QueueSyncPort {
        var appliedItems: List<QueueItem>? = null
        var appliedMetadata: QueueMetadataEntity? = null
        var markedSyncedSequence: Long? = null
        var markedSyncedAt: Long? = null

        override suspend fun getQueueSnapshot(): List<QueueItem> = items.toList()

        override suspend fun getQueueMetadata(): QueueMetadataEntity? = metadata

        override suspend fun applyRemoteQueueState(items: List<QueueItem>, metadata: QueueMetadataEntity) {
            this.items = items.toMutableList()
            this.metadata = metadata
            this.appliedItems = items
            this.appliedMetadata = metadata
        }

        override suspend fun markQueueSynced(expectedSequence: Long, syncedAt: Long): Boolean {
            markedSyncedSequence = expectedSequence
            markedSyncedAt = syncedAt
            return if (metadata.queueSequence == expectedSequence) {
                metadata = metadata.copy(isDirty = false, syncedAt = syncedAt)
                true
            } else {
                false
            }
        }
    }

    private class FakeActivePlaybackSyncPort(
        var activeEpisodeId: String? = null,
    ) : ActivePlaybackSyncPort {
        override fun getActivePlayingEpisodeId(): String? = activeEpisodeId
    }
}
