package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListeningHistoryDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: ListeningHistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.listeningHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun history(
        episodeId: String,
        podcastId: String = "pod-1",
        progressMs: Long = 0L,
        durationMs: Long = 3_600_000L,
        isCompleted: Boolean = false,
        isLiked: Boolean = false,
        lastPlayedAt: Long = 0L,
        isDirty: Boolean = true,
    ) = ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode $episodeId",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$episodeId.mp3",
        podcastName = "Podcast",
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        isLiked = isLiked,
        lastPlayedAt = lastPlayedAt,
        isDirty = isDirty,
    )

    @Test
    fun upsertAndGetHistoryItem() = runTest {
        dao.upsert(history("ep-1"))
        assertEquals("Episode ep-1", dao.getHistoryItem("ep-1")?.episodeTitle)
        assertNull(dao.getHistoryItem("missing"))
    }

    @Test
    fun upsertReplacesExistingRow() = runTest {
        dao.upsert(history("ep-1", progressMs = 100))
        dao.upsert(history("ep-1", progressMs = 500))
        assertEquals(500L, dao.getHistoryItem("ep-1")?.progressMs)
    }

    @Test
    fun insertIfAbsentNeverReplacesConcurrentHistoryState() = runTest {
        dao.insertIfAbsent(history("ep-1", progressMs = 500, isLiked = true))
        dao.insertIfAbsent(history("ep-1", progressMs = 100, isLiked = false))

        val stored = dao.getHistoryItem("ep-1")!!
        assertEquals(500L, stored.progressMs)
        assertTrue(stored.isLiked)
    }

    @Test
    fun metadataEnrichmentFillsGapsWithoutReplacingPlaybackOrLikes() = runTest {
        dao.insertIfAbsent(
            history(
                episodeId = "ep-1",
                podcastId = "",
                progressMs = 500,
                durationMs = 0,
                isLiked = true,
            ).copy(
                podcastName = "",
                podcastImageUrl = null,
            ),
        )

        dao.enrichMetadataIfMissing(
            episodeId = "ep-1",
            podcastId = "pod-1",
            episodeTitle = "Richer title",
            episodeImageUrl = "episode.jpg",
            podcastImageUrl = "podcast.jpg",
            episodeAudioUrl = "https://example.com/richer.mp3",
            podcastName = "Richer podcast",
            durationMs = 1_000,
            enclosureType = "audio/mpeg",
            episodeDescription = "Description",
        )

        val stored = dao.getHistoryItem("ep-1")!!
        assertEquals("pod-1", stored.podcastId)
        assertEquals("Richer podcast", stored.podcastName)
        assertEquals("podcast.jpg", stored.podcastImageUrl)
        assertEquals(1_000L, stored.durationMs)
        assertEquals(500L, stored.progressMs)
        assertTrue(stored.isLiked)
    }

    @Test
    fun getResumeItemsReturnsIncompleteWithProgressNewestFirst() = runTest {
        dao.upsertAll(
            listOf(
                history("ep-old", progressMs = 10, lastPlayedAt = 1L),
                history("ep-new", progressMs = 10, lastPlayedAt = 5L),
                history("ep-done", progressMs = 10, isCompleted = true, lastPlayedAt = 9L),
                history("ep-zero", progressMs = 0, lastPlayedAt = 8L),
            ),
        )

        val resume = dao.getResumeItems().first().map { it.episodeId }

        assertEquals(listOf("ep-new", "ep-old"), resume)
    }

    @Test
    fun getResumeItemsListCapsAtTwenty() = runTest {
        dao.upsertAll((1..25).map { history("ep-$it", progressMs = 10, lastPlayedAt = it.toLong()) })
        assertEquals(20, dao.getResumeItemsList().size)
    }

    @Test
    fun getAllHistoryOrdersByLastPlayed() = runTest {
        dao.upsertAll(
            listOf(
                history("a", lastPlayedAt = 1L),
                history("b", lastPlayedAt = 3L),
                history("c", lastPlayedAt = 2L),
            ),
        )
        assertEquals(listOf("b", "c", "a"), dao.getAllHistory().first().map { it.episodeId })
    }

    @Test
    fun dirtyItemsAndMarkAsSyncedClearFlag() = runTest {
        dao.upsertAll(
            listOf(
                history("dirty-1", isDirty = true),
                history("clean-1", isDirty = false),
            ),
        )
        assertEquals(listOf("dirty-1"), dao.getDirtyItems().map { it.episodeId })

        dao.markAsSynced(listOf("dirty-1"), timestamp = 999L)

        assertTrue(dao.getDirtyItems().isEmpty())
        assertEquals(999L, dao.getHistoryItem("dirty-1")?.syncedAt)
    }

    @Test
    fun deleteAndDeleteAll() = runTest {
        dao.upsertAll(listOf(history("a"), history("b")))
        dao.delete("a")
        assertNull(dao.getHistoryItem("a"))
        dao.deleteAll()
        assertTrue(dao.getAllHistory().first().isEmpty())
    }

    @Test
    fun getHistoryForPodcastFiltersByPodcastId() = runTest {
        dao.upsertAll(
            listOf(
                history("a", podcastId = "p1"),
                history("b", podcastId = "p2"),
                history("c", podcastId = "p1"),
            ),
        )
        assertEquals(setOf("a", "c"), dao.getHistoryForPodcast("p1").map { it.episodeId }.toSet())
    }

    @Test
    fun lastPlayedSessionPrefersIncompleteWhereasAnyIncludesCompleted() = runTest {
        dao.upsertAll(
            listOf(
                history("incomplete", isCompleted = false, lastPlayedAt = 1L),
                history("completed", isCompleted = true, lastPlayedAt = 10L),
            ),
        )
        assertEquals("incomplete", dao.getLastPlayedSession()?.episodeId)
        assertEquals("completed", dao.getLastPlayedSessionAny()?.episodeId)
    }

    @Test
    fun likeStatusRoundTrips() = runTest {
        dao.upsert(history("ep-1", isDirty = false, lastPlayedAt = 500L))
        dao.setLikeStatus("ep-1", true, now = 1000L)

        val liked = dao.getHistoryItem("ep-1")!!
        assertTrue(liked.isLiked)
        assertEquals(1000L, liked.likedAt)
        assertEquals(500L, liked.lastPlayedAt)
        assertTrue(liked.isDirty)
        assertEquals(listOf("ep-1"), dao.getLikedEpisodesList().map { it.episodeId })
        assertEquals(listOf("ep-1"), dao.getLikedEpisodes().first().map { it.episodeId })

        dao.setLikeStatus("ep-1", false, now = 1100L)
        assertTrue(dao.getLikedEpisodesList().isEmpty())
    }

    @Test
    fun updateProgressMarksDirty() = runTest {
        dao.upsert(history("ep-1", isDirty = false))
        dao.updateProgress("ep-1", progressMs = 200, durationMs = 1000, lastPlayedAt = 50)

        val item = dao.getHistoryItem("ep-1")!!
        assertEquals(200L, item.progressMs)
        assertEquals(50L, item.lastPlayedAt)
        assertTrue(item.isDirty)
    }

    @Test
    fun completionStatusAndCompletedIds() = runTest {
        dao.upsertAll(listOf(history("a", isDirty = false), history("b", isDirty = false)))
        dao.setCompletionStatus("a", true)

        val itemA = dao.getHistoryItem("a")!!
        assertTrue(itemA.isCompleted)
        assertTrue(itemA.isDirty)
        assertEquals(listOf("a"), dao.getCompletedEpisodeIds())
        assertEquals(listOf("a"), dao.getCompletedEpisodeIdsFlow().first())
    }

    @Test
    fun dirtyListeningHistoryAndMarkSynced() = runTest {
        dao.upsert(history("clean-1", isDirty = false))
        assertTrue(dao.getDirtyListeningHistory().isEmpty())

        dao.setLikeStatus("clean-1", true, now = 1234L)
        val dirty = dao.getDirtyListeningHistory()
        assertEquals(1, dirty.size)
        assertEquals("clean-1", dirty.first().episodeId)

        dao.markListeningHistorySynced(listOf("clean-1"), timestamp = 5678L)
        assertTrue(dao.getDirtyListeningHistory().isEmpty())
        val synced = dao.getHistoryItem("clean-1")!!
        assertFalse(synced.isDirty)
        assertEquals(5678L, synced.syncedAt)
    }

    @Test
    fun completeFromPlaybackChangesOnlyPlaybackOwnedFields() = runTest {
        dao.upsert(
            history(
                episodeId = "ep-1",
                progressMs = 500,
                durationMs = 1_000,
                isLiked = true,
            ),
        )

        dao.completeFromPlayback(
            episodeId = "ep-1",
            durationMs = 1_200,
            lastPlayedAt = 50,
            isManualCompletion = false,
        )

        val stored = dao.getHistoryItem("ep-1")!!
        assertEquals(0L, stored.progressMs)
        assertEquals(1_200L, stored.durationMs)
        assertTrue(stored.isCompleted)
        assertTrue(stored.isLiked)
        assertEquals(50L, stored.lastPlayedAt)
    }

    @Test
    fun staleProgressCannotMutateACompletedRow() = runTest {
        dao.upsert(history("ep-1", progressMs = 900, durationMs = 1_000))
        dao.completeFromPlayback(
            episodeId = "ep-1",
            durationMs = 1_000,
            lastPlayedAt = 50,
            isManualCompletion = false,
        )

        val updatedRows =
            dao.updateProgress(
                episodeId = "ep-1",
                progressMs = 800,
                durationMs = 900,
                lastPlayedAt = 40,
            )

        val stored = dao.getHistoryItem("ep-1")!!
        assertEquals(0, updatedRows)
        assertEquals(0L, stored.progressMs)
        assertEquals(1_000L, stored.durationMs)
        assertEquals(50L, stored.lastPlayedAt)
    }

    @Test
    fun recentlyPlayedPodcastsAreDistinctAndTimeFiltered() = runTest {
        dao.upsertAll(
            listOf(
                history("a", podcastId = "p1", lastPlayedAt = 100L),
                history("b", podcastId = "p1", lastPlayedAt = 150L),
                history("c", podcastId = "p2", lastPlayedAt = 50L),
            ),
        )
        assertEquals(setOf("p1"), dao.getRecentlyPlayedPodcasts(sinceTimestamp = 60L).toSet())
    }

    @Test
    fun recentHistoryListRespectsLimit() = runTest {
        dao.upsertAll((1..5).map { history("ep-$it", lastPlayedAt = it.toLong()) })
        assertEquals(3, dao.getRecentHistoryList(3).size)
        assertEquals("ep-5", dao.getRecentHistoryList(3).first().episodeId)
    }

    @Test
    fun reopenProgressResetsCompletionAndUpdatesTimestamp() = runTest {
        dao.upsert(history("ep-1", progressMs = 900, durationMs = 1_000))
        dao.completeFromPlayback(
            episodeId = "ep-1",
            durationMs = 1_000,
            lastPlayedAt = 50,
            isManualCompletion = false,
        )

        val updatedRows =
            dao.reopenProgress(
                episodeId = "ep-1",
                progressMs = 300,
                durationMs = 1_000,
                lastPlayedAt = 120,
            )

        val stored = dao.getHistoryItem("ep-1")!!
        assertEquals(1, updatedRows)
        assertEquals(300L, stored.progressMs)
        assertEquals(1_000L, stored.durationMs)
        assertEquals(120L, stored.lastPlayedAt)
        assertEquals(false, stored.isCompleted)
        assertEquals(false, stored.isManualCompletion)
        assertEquals(false, stored.isBulkCompletion)
        assertEquals(true, stored.isDirty)
    }

    @Test
    fun updateLastPlayedAtRefreshesTimestampAndMarksDirty() = runTest {
        dao.upsert(history("ep-1", progressMs = 100, durationMs = 1_000, lastPlayedAt = 50L))

        dao.updateLastPlayedAt("ep-1", lastPlayedAt = 200L)

        val stored = dao.getHistoryItem("ep-1")!!
        assertEquals(200L, stored.lastPlayedAt)
        assertEquals(100L, stored.progressMs)
        assertEquals(true, stored.isDirty)
    }

    @Test
    fun markHistorySyncedIfUnchanged_clearsDirtyWhenSnapshotMatches() = runTest {
        dao.upsert(
            history(
                episodeId = "ep-sync-1",
                progressMs = 5000L,
                isCompleted = false,
                isLiked = true,
                lastPlayedAt = 1000L,
                isDirty = true,
            ).copy(likedAt = 800L)
        )

        val updated = dao.markHistorySyncedIfUnchanged(
            episodeId = "ep-sync-1",
            snapshotLastPlayedAt = 1000L,
            snapshotLikedAt = 800L,
            snapshotProgressMs = 5000L,
            snapshotIsCompleted = false,
            snapshotIsLiked = true,
            syncedAt = 9999L,
        )

        assertEquals(1, updated)
        val stored = dao.getHistoryItem("ep-sync-1")!!
        assertEquals(false, stored.isDirty)
        assertEquals(9999L, stored.syncedAt)
    }

    @Test
    fun markHistorySyncedIfUnchanged_doesNotClearWhenProgressChanged() = runTest {
        dao.upsert(
            history(
                episodeId = "ep-sync-2",
                progressMs = 15000L, // user listened further
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = 2000L,
                isDirty = true,
            )
        )

        // Snapshot had older progressMs 10000L
        val updated = dao.markHistorySyncedIfUnchanged(
            episodeId = "ep-sync-2",
            snapshotLastPlayedAt = 2000L,
            snapshotLikedAt = 0L,
            snapshotProgressMs = 10000L,
            snapshotIsCompleted = false,
            snapshotIsLiked = false,
            syncedAt = 9999L,
        )

        assertEquals(0, updated)
        val stored = dao.getHistoryItem("ep-sync-2")!!
        assertEquals(true, stored.isDirty)
        assertEquals(0L, stored.syncedAt)
    }

    @Test
    fun getDirtyCountFlow_emitsCorrectCount() = runTest {
        assertEquals(0, dao.getDirtyCountFlow().first())

        dao.upsert(history("ep-1", isDirty = true))
        assertEquals(1, dao.getDirtyCountFlow().first())

        dao.upsert(history("ep-2", isDirty = true))
        assertEquals(2, dao.getDirtyCountFlow().first())

        dao.markListeningHistorySynced(listOf("ep-1", "ep-2"), 1000L)
        assertEquals(0, dao.getDirtyCountFlow().first())
    }
}
