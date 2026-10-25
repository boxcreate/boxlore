package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: QueueDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.queueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun item(episodeId: String, position: Int, podcastId: String = "pod-1",) = QueueItem(
        episodeId = episodeId,
        title = "Episode $episodeId",
        podcastId = podcastId,
        podcastTitle = "Podcast",
        imageUrl = null,
        audioUrl = "https://example.com/$episodeId.mp3",
        duration = 100,
        pubDate = 0L,
        description = null,
        position = position,
    )

    @Test
    fun insertAndGetAllOrderedByPosition() = runTest {
        dao.insertQueueItem(item("b", position = 2))
        dao.insertQueueItem(item("a", position = 1))
        dao.insertQueueItem(item("c", position = 3))

        assertEquals(listOf("a", "b", "c"), dao.getAllQueueItemsSync().map { it.episodeId })
        assertEquals(listOf("a", "b", "c"), dao.getAllQueueItems().first().map { it.episodeId })
    }

    @Test
    fun insertQueueItemsBulk() = runTest {
        dao.insertQueueItems(listOf(item("a", 1), item("b", 2)))
        assertEquals(2, dao.getAllQueueItemsSync().size)
    }

    @Test
    fun getMaxPositionReflectsHighest() = runTest {
        assertNull(dao.getMaxPosition())
        dao.insertQueueItems(listOf(item("a", 1), item("b", 5)))
        assertEquals(5, dao.getMaxPosition())
    }

    @Test
    fun countEpisodeAndLookupByEpisodeId() = runTest {
        dao.insertQueueItem(item("a", 1))
        assertEquals(1, dao.countEpisode("a"))
        assertEquals(0, dao.countEpisode("missing"))
        assertEquals("Episode a", dao.getQueueItemByEpisodeId("a")?.title)
        assertNull(dao.getQueueItemByEpisodeId("missing"))
    }

    @Test
    fun deleteQueueItemByGeneratedId() = runTest {
        dao.insertQueueItem(item("a", 1))
        val stored = dao.getQueueItemByEpisodeId("a")!!
        dao.deleteQueueItem(stored.id)
        assertNull(dao.getQueueItemByEpisodeId("a"))
    }

    @Test
    fun clearQueueRemovesEverything() = runTest {
        dao.insertQueueItems(listOf(item("a", 1), item("b", 2)))
        dao.clearQueue()
        assertTrue(dao.getAllQueueItemsSync().isEmpty())
    }

    @Test
    fun updateQueuePositionsPersistsNewOrder() = runTest {
        dao.insertQueueItems(listOf(item("a", 1), item("b", 2)))
        val stored = dao.getAllQueueItemsSync()
        val reordered =
            stored.map {
                if (it.episodeId == "a") it.copy(position = 5) else it.copy(position = 0)
            }

        dao.updateQueuePositions(reordered)

        assertEquals(listOf("b", "a"), dao.getAllQueueItemsSync().map { it.episodeId })
    }

    @Test
    fun updateQueueItemMutatesRow() = runTest {
        dao.insertQueueItem(item("a", 1))
        val stored = dao.getQueueItemByEpisodeId("a")!!
        dao.updateQueueItem(stored.copy(title = "Renamed"))
        assertEquals("Renamed", dao.getQueueItemByEpisodeId("a")?.title)
    }

    @Test
    fun bumpQueueVersionPreservesEpisodeIdsWithCommas() = runTest {
        val commaId = "rss:https://feed.example.com/audio,part1.mp3"
        dao.bumpQueueVersion(removedEpisodeId = commaId)

        val metadata = dao.getQueueMetadata()
        val parsed = QueueDao.parseRecentRemovedEpisodeIds(metadata?.recentRemovedEpisodeIds)
        assertEquals(listOf(commaId), parsed)
    }

    @Test
    fun bumpQueueVersionKeepsTheRichRemovalListInStepWithThePlainOne() = runTest {
        val identity = QueueRemovalIdentity("A", 4_503_599_627_370_500L, "100", "guid-a")
        dao.bumpQueueVersion(removedEpisodeIds = listOf("A", "-7"), removedIdentities = listOf(identity))

        val removed = dao.getQueueMetadata()!!
        assertEquals(listOf("A", "-7"), QueueDao.parseRecentRemovedEpisodeIds(removed.recentRemovedEpisodeIds))
        assertEquals(listOf(identity, QueueRemovalIdentity("-7")), QueueRemovalIdentity.parseList(removed.recentRemovedEpisodes))

        // Re-adding the episode under another of its IDs clears its rich removal; the plain list keeps "A".
        dao.bumpQueueVersion(restoredEpisodeIds = listOf("B"), restoredIdentities = listOf(identity.copy(id = "B")))

        val restored = dao.getQueueMetadata()!!
        assertEquals(listOf("A", "-7"), QueueDao.parseRecentRemovedEpisodeIds(restored.recentRemovedEpisodeIds))
        assertEquals(listOf(QueueRemovalIdentity("-7")), QueueRemovalIdentity.parseList(restored.recentRemovedEpisodes))
        assertEquals(removed.queueSequence + 1L, restored.queueSequence)
    }

    @Test
    fun parseRecentRemovedEpisodeIdsSupportsLegacyCsvFallback() {
        val legacyCsv = "ep-1, ep-2, ep-3"
        val parsed = QueueDao.parseRecentRemovedEpisodeIds(legacyCsv)
        assertEquals(listOf("ep-1", "ep-2", "ep-3"), parsed)
    }

    @Test
    fun parseRecentRemovedEpisodeIds_malformedBracketedJson_fallsBackToCsv() {
        // Bracketed string with invalid token syntax fails JSON parsing and falls back to CSV
        val malformedJson = "[ep-1, :invalid]"
        val parsed = QueueDao.parseRecentRemovedEpisodeIds(malformedJson)
        assertEquals(listOf("[ep-1", ":invalid]"), parsed)
    }

    @Test
    fun markQueueSyncedIfSequenceMatches_clearsDirtyWhenSequenceMatches() = runTest {
        dao.upsertQueueMetadata(
            cx.aswin.boxlore.core.database.entities.QueueMetadataEntity(
                id = 1,
                queueUpdatedAt = 1000L,
                queueSequence = 5L,
                isDirty = true,
                syncedAt = 0L,
            )
        )

        val updated = dao.markQueueSyncedIfSequenceMatches(snapshotSequence = 5L, syncedAt = 8888L)
        assertEquals(1, updated)

        val stored = dao.getQueueMetadata()!!
        assertEquals(false, stored.isDirty)
        assertEquals(8888L, stored.syncedAt)
        assertEquals(5L, stored.queueSequence)
    }

    @Test
    fun markQueueSyncedIfSequenceMatches_doesNotClearWhenSequenceChanged() = runTest {
        dao.upsertQueueMetadata(
            cx.aswin.boxlore.core.database.entities.QueueMetadataEntity(
                id = 1,
                queueUpdatedAt = 2000L,
                queueSequence = 6L, // bumped to 6 in the meantime
                isDirty = true,
                syncedAt = 0L,
            )
        )

        // Snapshot was taken at sequence 5
        val updated = dao.markQueueSyncedIfSequenceMatches(snapshotSequence = 5L, syncedAt = 8888L)
        assertEquals(0, updated)

        val stored = dao.getQueueMetadata()!!
        assertEquals(true, stored.isDirty)
        assertEquals(0L, stored.syncedAt)
        assertEquals(6L, stored.queueSequence)
    }

    // Regression: the repaired queue stayed clean, so the cloud copy kept the dead artwork links.
    @Test
    fun `a queue repair marks a synced queue for the next push without changing its sequence`() = runTest {
        dao.upsertQueueMetadata(QueueMetadataEntity(id = 1, queueSequence = 5L, isDirty = false, syncedAt = 100L))
        dao.insertQueueItem(item("a", 1).copy(imageUrl = "content://cx.aswin.boxlore.collage/art/abc", podcastImageUrl = "https://art/show.jpg"))

        assertEquals(1, dao.repairPlaybackArtworkLinksForSync())
        val marked = dao.getQueueMetadata()!!
        assertEquals(true, marked.isDirty)
        assertEquals(5L, marked.queueSequence)

        dao.upsertQueueMetadata(marked.copy(isDirty = false))
        assertEquals(0, dao.repairPlaybackArtworkLinksForSync())
        assertEquals(false, dao.getQueueMetadata()!!.isDirty)
    }

    // Regression: queue rows built from the player could keep its Android Auto artwork link.
    @Test
    fun `player-only artwork links in the queue are swapped for real covers without reordering`() = runTest {
        val dead = "content://cx.aswin.boxlore.collage/art/abc"
        database.podcastDao().upsert(PodcastEntity(podcastId = "pod-1", title = "Podcast", author = "Host", imageUrl = "https://art/show.jpg", description = null))
        dao.insertQueueItem(item("a", 1).copy(imageUrl = dead, podcastImageUrl = dead))
        dao.insertQueueItem(item("b", 2).copy(imageUrl = "https://art/b.jpg"))
        // An unsubscribed show: only the row's own show cover is left to fall back on.
        dao.insertQueueItem(item("c", 3).copy(podcastId = "unsubscribed", imageUrl = dead, podcastImageUrl = "https://art/own-show.jpg"))

        assertEquals(2, dao.repairPlaybackArtworkLinks())

        val items = dao.getAllQueueItemsSync()
        assertEquals(listOf("a", "b", "c"), items.map { it.episodeId })
        assertEquals("https://art/show.jpg", items[0].imageUrl)
        assertEquals("https://art/show.jpg", items[0].podcastImageUrl)
        assertEquals("https://art/b.jpg", items[1].imageUrl)
        assertEquals("https://art/own-show.jpg", items[2].imageUrl)
        assertEquals(0, dao.repairPlaybackArtworkLinks())
    }
}
