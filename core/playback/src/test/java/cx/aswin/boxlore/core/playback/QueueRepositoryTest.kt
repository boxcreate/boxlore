package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.Transcript
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * In-memory Room coverage for [QueueRepository]. The [PodcastRepository] collaborator is stored
 * but never touched by the exercised methods, so a throwaway instance (no network) suffices.
 * The Podcast 2.0 persons/transcripts JSON codecs use Android's `org.json`, provided by Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueRepositoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastRepository: PodcastRepository
    private lateinit var repository: QueueRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val rss = RssPodcastRepository.createForTests(context = context, database = database)
        val api = NetworkModule.createBoxLoreApi("http://localhost/", context)
        podcastRepository =
            PodcastRepository(
                baseUrl = "http://localhost/",
                publicKey = "test-key",
                context = context,
                rssRepository = rss,
                ioDispatcher = UnconfinedTestDispatcher(),
                boxLoreApi = api,
            )
        repository = QueueRepository(database, podcastRepository)
    }

    @After
    fun tearDown() {
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    private fun episodeItem(id: Long, title: String = "Episode $id", audioUrl: String = "https://example.com/$id.mp3",) = EpisodeItem(
        id = id,
        title = title,
        enclosureUrl = audioUrl,
        duration = 120,
        datePublished = id,
    )

    private fun podcast(id: String = "pod-1") = Podcast(
        id = id,
        title = "Podcast",
        artist = "Artist",
        imageUrl = "https://example.com/art.jpg",
        genre = "Technology",
    )

    @Test
    fun addToQueueAppendsAndExposesViaFlow() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())

        val queued = repository.queue.first()
        assertEquals(listOf("1", "2"), queued.map { it.id.toString() })
    }

    @Test
    fun addToQueueSkipsDuplicateEpisode() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(1), podcast())

        assertEquals(1, database.queueDao().getAllQueueItemsSync().size)
    }

    @Test
    fun addToQueueAssignsIncrementingPositions() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())

        val positions = database.queueDao().getAllQueueItemsSync().associate { it.episodeId to it.position }
        assertEquals(1, positions["1"])
        assertEquals(2, positions["2"])
    }

    @Test
    fun clearQueueRemovesAll() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.clearQueue()

        assertTrue(repository.queue.first().isEmpty())
    }

    @Test
    fun replaceQueueDeduplicatesAndOrders() = runTest {
        repository.replaceQueue(
            listOf(
                domainEpisode("1"),
                domainEpisode("2"),
                domainEpisode("1"),
            ),
        )

        val snapshot = repository.getQueueEpisodeSnapshot()
        assertEquals(listOf("1", "2"), snapshot.map { it.id })
    }

    @Test
    fun replaceQueueRoundTripsPodcast20Metadata() = runTest {
        val episode =
            domainEpisode("1").copy(
                persons = listOf(Person(name = "Host", role = "host", img = "https://img", href = "https://href")),
                transcripts = listOf(Transcript(url = "https://t.vtt", type = "text/vtt")),
            )

        repository.replaceQueue(listOf(episode))

        val restored = repository.getQueueEpisodeSnapshot().single()
        assertEquals("Host", restored.persons?.single()?.name)
        assertEquals("host", restored.persons?.single()?.role)
        assertEquals("https://t.vtt", restored.transcripts?.single()?.url)
        assertEquals("text/vtt", restored.transcripts?.single()?.type)
    }

    @Test
    fun getQueueEpisodeSnapshotRepairsDuplicateRows() = runTest {
        // Insert duplicate episodeIds directly (bypassing addToQueue's dedup guard).
        database.queueDao().insertQueueItem(rawItem("dup", position = 0))
        database.queueDao().insertQueueItem(rawItem("dup", position = 1))
        database.queueDao().insertQueueItem(rawItem("unique", position = 2))

        val snapshot = repository.getQueueEpisodeSnapshot()

        assertEquals(setOf("dup", "unique"), snapshot.map { it.id }.toSet())
        assertEquals(2, database.queueDao().getAllQueueItemsSync().size)
    }

    @Test
    fun reorderQueueRewritesPositions() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())
        repository.addToQueue(episodeItem(3), podcast())

        repository.reorderQueue(listOf("3", "1", "2"))

        assertEquals(listOf("3", "1", "2"), repository.queue.first().map { it.id.toString() })
    }

    @Test
    fun reorderQueueAppendsUnlistedRowsAtTail() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())
        repository.addToQueue(episodeItem(3), podcast())

        repository.reorderQueue(listOf("2"))

        val ordered = repository.queue.first().map { it.id.toString() }
        assertEquals("2", ordered.first())
        assertEquals(setOf("1", "3"), ordered.drop(1).toSet())
    }

    @Test
    fun getQueueItemByEpisodeIdReturnsRowOrNull() = runTest {
        repository.addToQueue(episodeItem(1), podcast())

        assertEquals("Episode 1", repository.getQueueItemByEpisodeId("1")?.title)
        assertNull(repository.getQueueItemByEpisodeId("missing"))
    }

    @Test
    fun addToQueueAndReplaceQueuePersistContextSourceId() = runTest {
        repository.addToQueue(episodeItem(1), podcast(), contextSourceId = "podcast_detail")
        val item = database.queueDao().getQueueItemByEpisodeId("1")
        assertEquals("podcast_detail", item?.contextSourceId)

        val domainEp = domainEpisode("2").copy(contextSourceId = "podcast_detail")
        repository.replaceQueue(listOf(domainEp))
        val replaced = database.queueDao().getQueueItemByEpisodeId("2")
        assertEquals("podcast_detail", replaced?.contextSourceId)
    }

    @Test
    fun addToQueueBumpsQueueMetadataSequence() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        val meta1 = repository.getQueueMetadata()!!
        assertEquals(1L, meta1.queueSequence)
        assertTrue(meta1.queueUpdatedAt > 0L)
        assertTrue(meta1.isDirty)

        repository.addToQueue(episodeItem(2), podcast())
        val meta2 = repository.getQueueMetadata()!!
        assertEquals(2L, meta2.queueSequence)
        assertTrue(meta2.queueUpdatedAt >= meta1.queueUpdatedAt)
    }

    @Test
    fun removeFromQueueDeletesItemAndRecordsRemovedEpisodeId() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())

        repository.removeFromQueue("1")

        assertNull(repository.getQueueItemByEpisodeId("1"))
        val remaining = repository.queue.first()
        assertEquals(listOf("2"), remaining.map { it.id.toString() })

        val meta = repository.getQueueMetadata()!!
        assertTrue(meta.isDirty)
        assertEquals(listOf("1"), QueueDao.parseRecentRemovedEpisodeIds(meta.recentRemovedEpisodeIds))

        // Remove another item
        repository.removeFromQueue("2")
        val meta2 = repository.getQueueMetadata()!!
        assertEquals(listOf("1", "2"), QueueDao.parseRecentRemovedEpisodeIds(meta2.recentRemovedEpisodeIds))
    }

    @Test
    fun clearQueueOnEmptyQueueBumpsMonotonicSequence() = runTest {
        val initialSeq = repository.getQueueMetadata()?.queueSequence ?: 0L
        repository.clearQueue()
        val meta1 = repository.getQueueMetadata()!!
        assertEquals(initialSeq + 1L, meta1.queueSequence)
        assertTrue(meta1.isDirty)

        // Clear again when already empty — must still increment sequence
        repository.clearQueue()
        val meta2 = repository.getQueueMetadata()!!
        assertEquals(initialSeq + 2L, meta2.queueSequence)
    }

    @Test
    fun markQueueSyncedUpdatesTimestampAndClearsDirty() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        assertTrue(repository.getQueueMetadata()!!.isDirty)

        repository.markQueueSynced(7777L)
        val syncedMeta = repository.getQueueMetadata()!!
        assertFalse(syncedMeta.isDirty)
        assertEquals(7777L, syncedMeta.syncedAt)
    }

    @Test
    fun reAddingRemovedEpisodePrunesFromRecentRemovedEpisodeIds() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.removeFromQueue("1")
        assertEquals(listOf("1"), QueueDao.parseRecentRemovedEpisodeIds(repository.getQueueMetadata()!!.recentRemovedEpisodeIds))

        // Re-adding the episode must prune it from tombstones so sync won't treat it as deleted
        repository.addToQueue(episodeItem(1), podcast())
        val meta = repository.getQueueMetadata()!!
        assertNull(meta.recentRemovedEpisodeIds)
        assertTrue(meta.isDirty)
    }

    @Test
    fun replaceQueuePrunesActiveEpisodesFromRecentRemovedEpisodeIds() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())
        repository.removeFromQueue("1")
        repository.removeFromQueue("2")
        assertEquals(listOf("1", "2"), QueueDao.parseRecentRemovedEpisodeIds(repository.getQueueMetadata()!!.recentRemovedEpisodeIds))

        // Restoring episode 1 via replaceQueue should prune "1" and leave "2" tombstoned
        repository.replaceQueue(listOf(domainEpisode("1")))
        val meta = repository.getQueueMetadata()!!
        assertEquals(listOf("2"), QueueDao.parseRecentRemovedEpisodeIds(meta.recentRemovedEpisodeIds))
    }

    @Test
    fun reRemovingEpisodeMaintainsFifoTailRecency() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())
        repository.removeFromQueue("1")
        repository.removeFromQueue("2")
        assertEquals(listOf("1", "2"), QueueDao.parseRecentRemovedEpisodeIds(repository.getQueueMetadata()!!.recentRemovedEpisodeIds))

        // Re-removing "1" must move it to the tail of the buffer (most recent)
        repository.removeFromQueue("1")
        assertEquals(listOf("2", "1"), QueueDao.parseRecentRemovedEpisodeIds(repository.getQueueMetadata()!!.recentRemovedEpisodeIds))
    }

    @Test
    fun clearQueuePreservesBulkRemovedTombstones() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())
        repository.addToQueue(episodeItem(3), podcast())

        repository.clearQueue()

        val meta = repository.getQueueMetadata()!!
        assertTrue(meta.isDirty)
        assertEquals(listOf("1", "2", "3"), QueueDao.parseRecentRemovedEpisodeIds(meta.recentRemovedEpisodeIds))
    }

    @Test
    fun replaceQueuePreservesOmittedItemsAsTombstones() = runTest {
        repository.addToQueue(episodeItem(1), podcast())
        repository.addToQueue(episodeItem(2), podcast())
        repository.addToQueue(episodeItem(3), podcast())

        // Replace queue with only episode 2: episodes 1 and 3 are removed in bulk
        repository.replaceQueue(listOf(domainEpisode("2")))

        val meta = repository.getQueueMetadata()!!
        assertTrue(meta.isDirty)
        assertEquals(listOf("1", "3"), QueueDao.parseRecentRemovedEpisodeIds(meta.recentRemovedEpisodeIds))
    }

    @Test
    fun localQueueMutationsRecordDeviceIdFromPort() = runTest {
        val fakeDevicePort = cx.aswin.boxlore.core.testing.fakes.FakeDeviceIdentityPort("phone-alpha")
        val customRepo = QueueRepository(database, podcastRepository, fakeDevicePort)

        customRepo.addToQueue(episodeItem(10), podcast())
        val metaAdd = customRepo.getQueueMetadata()!!
        assertEquals("phone-alpha", metaAdd.lastModifiedDeviceId)

        fakeDevicePort.currentDeviceId = "phone-beta"
        customRepo.removeFromQueue("10")
        val metaRemove = customRepo.getQueueMetadata()!!
        assertEquals("phone-beta", metaRemove.lastModifiedDeviceId)
    }

    @Test
    fun queueSyncPort_applyRemoteQueueState_setsItemsAndMetadataWithoutDirty() = runTest {
        val items = listOf(
            rawItem("remote-1", position = 0),
            rawItem("remote-2", position = 1),
        )
        val metadata = cx.aswin.boxlore.core.database.entities.QueueMetadataEntity(
            id = 1,
            queueSequence = 10L,
            queueUpdatedAt = 5000L,
            lastModifiedDeviceId = "device-remote",
            isDirty = false,
            syncedAt = 5000L,
        )

        repository.applyRemoteQueueState(items, metadata)

        val rawQueue = repository.getQueueSnapshot()
        assertEquals(2, rawQueue.size)
        assertEquals("remote-1", rawQueue[0].episodeId)
        assertEquals("remote-2", rawQueue[1].episodeId)

        val storedMeta = repository.getQueueMetadata()!!
        assertEquals(10L, storedMeta.queueSequence)
        assertEquals(false, storedMeta.isDirty)
    }

    @Test
    fun queueSyncPort_markQueueSynced_returnsTrueOnSequenceMatch() = runTest {
        repository.applyRemoteQueueState(
            emptyList(),
            cx.aswin.boxlore.core.database.entities.QueueMetadataEntity(
                id = 1,
                queueSequence = 42L,
                isDirty = true,
            ),
        )

        val success = repository.markQueueSynced(expectedSequence = 42L, syncedAt = 9999L)
        assertTrue(success)

        val failed = repository.markQueueSynced(expectedSequence = 43L, syncedAt = 10000L)
        assertFalse(failed)
    }

    @Test
    fun queueSyncPort_applyRemoteQueueState_preservesDirtyFlagWhenTrue() = runTest {
        val metadata = cx.aswin.boxlore.core.database.entities.QueueMetadataEntity(
            id = 1,
            queueSequence = 15L,
            queueUpdatedAt = 6000L,
            lastModifiedDeviceId = "device-1",
            isDirty = true,
            syncedAt = 6000L,
        )

        repository.applyRemoteQueueState(emptyList(), metadata)

        val storedMeta = repository.getQueueMetadata()!!
        assertEquals(15L, storedMeta.queueSequence)
        assertTrue(storedMeta.isDirty)
    }

    private fun domainEpisode(id: String) = Episode(
        id = id,
        title = "Episode $id",
        description = "desc $id",
        audioUrl = "https://example.com/$id.mp3",
        podcastId = "pod-1",
        podcastTitle = "Podcast",
        duration = 120,
        publishedDate = id.hashCode().toLong(),
    )

    private fun rawItem(episodeId: String, position: Int,) = QueueItem(
        episodeId = episodeId,
        title = "Episode $episodeId",
        podcastId = "pod-1",
        podcastTitle = "Podcast",
        imageUrl = null,
        audioUrl = "https://example.com/$episodeId.mp3",
        duration = 120,
        pubDate = 0L,
        description = null,
        position = position,
    )
}
