package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.model.BriefingArtwork
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BriefingArtworkCompatibilityTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        database = openDatabase(compatibility = false)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun upgradeRepairsAllRegionsWithoutChangingListenerOrDownloadState() = runTest {
        val originals = BriefingArtwork.entries.flatMap { artwork ->
            artwork.regionAliases.map { region -> history("briefing_${region}_2026-10-07", "briefing_$region") }
        }
        originals.forEachIndexed { position, item ->
            database.listeningHistoryDao().upsert(item)
            database.queueDao().insertQueueItem(queue(item, position))
            database.downloadedEpisodeDao().insert(download(item))
        }
        val queueBefore = database.queueDao().getAllQueueItemsSync()
        val downloadsBefore = database.downloadedEpisodeDao().getAllDownloadsSync()
        val metadata = QueueMetadataEntity(queueSequence = 19, isDirty = true, syncedAt = 321)
        database.queueDao().upsertQueueMetadata(metadata)

        reopenWithCompatibility()

        originals.forEach { item ->
            val region = item.podcastId.removePrefix("briefing_")
            val uri = BriefingArtwork.forRegion(region).uri(APPLICATION_ID)
            assertEquals(item.copy(episodeImageUrl = uri, podcastImageUrl = uri), storedHistory(item))
        }
        assertEquals(
            queueBefore.map { item ->
                val uri = BriefingArtwork.forRegion(item.podcastId.removePrefix("briefing_")).uri(APPLICATION_ID)
                item.copy(imageUrl = uri, podcastImageUrl = uri)
            },
            database.queueDao().getAllQueueItemsSync(),
        )
        assertEquals(
            downloadsBefore.map { item ->
                val uri = BriefingArtwork.forRegion(item.podcastId.removePrefix("briefing_")).uri(APPLICATION_ID)
                item.copy(episodeImageUrl = uri, podcastImageUrl = uri)
            },
            database.downloadedEpisodeDao().getAllDownloadsSync(),
        )
        assertEquals(metadata, database.queueDao().getQueueMetadata())

        val repaired = originals.map { storedHistory(it) }
        reopenWithCompatibility()
        assertEquals(repaired, originals.map { storedHistory(it) })
    }

    @Test
    fun laterCloudAndBackupWritesAreRepairedIncludingArtworkUpdates() = runTest {
        reopenWithCompatibility()
        val item = history("briefing_gb_2026-09-22", "briefing_gb")
        val uri = BriefingArtwork.UK.uri(APPLICATION_ID)
        database.listeningHistoryDao().upsert(item)
        database.queueDao().insertQueueItem(queue(item, 2))
        database.downloadedEpisodeDao().insert(download(item))
        assertEquals(item.copy(episodeImageUrl = uri, podcastImageUrl = uri), storedHistory(item))

        val advanced = item.copy(progressMs = 900_000, isCompleted = true, isDirty = false, syncedAt = 777)
        database.listeningHistoryDao().upsert(advanced)
        // Finishing the stored, unfinished row dates the finish with its play time (Room v40 listening dates).
        assertEquals(advanced.copy(episodeImageUrl = uri, podcastImageUrl = uri, finishedAt = advanced.lastPlayedAt), storedHistory(item))

        val queued = database.queueDao().getQueueItemByEpisodeId(item.episodeId)!!
        database.queueDao().updateQueueItem(queued.copy(imageUrl = LEGACY_URI, position = 4))
        assertEquals(queued.copy(position = 4), database.queueDao().getQueueItemByEpisodeId(item.episodeId))
        database.downloadedEpisodeDao().updateArtwork(item.episodeId, LEGACY_URI, LEGACY_URI)
        assertEquals(
            download(item).copy(episodeImageUrl = uri, podcastImageUrl = uri),
            database.downloadedEpisodeDao().getDownload(item.episodeId),
        )
    }

    @Test
    fun reopeningReplacesTriggersFromAnotherBuildBeforeRestoredWrites() = runTest {
        database.close()
        database = Room.databaseBuilder(context, BoxLoreDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .addCallback(BriefingArtworkCompatibility("cx.aswin.boxlore.old"))
            .build()
        database.openHelper.writableDatabase
        reopenWithCompatibility()
        val item = history("briefing_in_2026-10-07", "briefing_in")
        val uri = BriefingArtwork.INDIA.uri(APPLICATION_ID)
        database.listeningHistoryDao().upsert(item)
        database.queueDao().insertQueueItem(queue(item, 2))
        database.downloadedEpisodeDao().insert(download(item))
        assertEquals(item.copy(episodeImageUrl = uri, podcastImageUrl = uri), storedHistory(item))
        assertEquals(uri, database.queueDao().getQueueItemByEpisodeId(item.episodeId)?.imageUrl)
        assertEquals(uri, database.downloadedEpisodeDao().getDownload(item.episodeId)?.episodeImageUrl)
        database.downloadedEpisodeDao().updateArtwork(item.episodeId, LEGACY_URI, LEGACY_URI)
        assertEquals(uri, database.downloadedEpisodeDao().getDownload(item.episodeId)?.podcastImageUrl)
    }

    @Test
    fun nonBriefRemoteNamedNullAndMalformedArtworkRemainUntouched() = runTest {
        val urls = listOf(
            null,
            "https://cdn.example/2131230927.jpg?signature=abc",
            BriefingArtwork.INDIA.uri(APPLICATION_ID),
            "android.resource://cx.aswin.boxlore/drawable/custom_123",
            "android.resource://cx.aswin.boxlore/123abc",
            "android.resource://cx.aswin.boxlore/123/456",
            "android.resource://cx.aswin.boxlore/123?value=1",
            "android.resource://cx.aswin.boxlore/-123",
            "android.resource://cx.aswin.boxlore/",
        )
        val originals = urls.mapIndexed { index, url ->
            history("briefing_in_2026-10-$index", "briefing_in").copy(episodeImageUrl = url, podcastImageUrl = url)
        } + listOf(
            history("ordinary-episode", "ordinary-show"),
            history("briefing_unknown_2026-10-07", "briefing_unknown"),
        )
        originals.forEach { database.listeningHistoryDao().upsert(it) }
        reopenWithCompatibility()
        originals.forEach { assertEquals(it, storedHistory(it)) }
        originals.forEach { database.listeningHistoryDao().upsert(it) }
        originals.forEach { assertEquals(it, storedHistory(it)) }
    }

    @Test
    fun episodeRegionTakesPrecedenceAndShowIdentityRecoversIncompleteHistory() = runTest {
        reopenWithCompatibility()
        val conflicting = history("briefing_in_2026-10-07", "briefing_us")
        val incomplete = history("legacy-brief-id", "briefing_gb")
        listOf(conflicting, incomplete).forEach { database.listeningHistoryDao().upsert(it) }
        val india = BriefingArtwork.INDIA.uri(APPLICATION_ID)
        val uk = BriefingArtwork.UK.uri(APPLICATION_ID)
        assertEquals(conflicting.copy(episodeImageUrl = india, podcastImageUrl = india), storedHistory(conflicting))
        assertEquals(incomplete.copy(episodeImageUrl = uk, podcastImageUrl = uk), storedHistory(incomplete))
    }

    @Test
    fun mixedArtworkChangesOnlyTheNumericFieldAndRecursiveTriggersTerminate() = runTest {
        reopenWithCompatibility()
        database.openHelper.writableDatabase.execSQL("PRAGMA recursive_triggers = ON")
        val item = history("briefing_us_2026-10-07", "briefing_us").copy(podcastImageUrl = "https://cdn.example/show.webp")
        database.listeningHistoryDao().upsert(item)
        val expected = item.copy(episodeImageUrl = BriefingArtwork.USA.uri(APPLICATION_ID))
        assertEquals(expected, storedHistory(item))
        database.listeningHistoryDao().upsert(item)
        assertEquals(expected, storedHistory(item))
    }

    private fun openDatabase(compatibility: Boolean): BoxLoreDatabase {
        val builder = Room.databaseBuilder(context, BoxLoreDatabase::class.java, DATABASE_NAME).allowMainThreadQueries()
        if (compatibility) builder.addCallback(BriefingArtworkCompatibility(APPLICATION_ID))
        return builder.build()
    }

    private fun reopenWithCompatibility() {
        database.close()
        database = openDatabase(compatibility = true)
    }

    private suspend fun storedHistory(item: ListeningHistoryEntity): ListeningHistoryEntity? =
        database.listeningHistoryDao().getHistoryItem(item.episodeId)

    private fun history(id: String, podcastId: String) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = podcastId,
        episodeTitle = "Saved brief",
        episodeImageUrl = LEGACY_URI,
        podcastImageUrl = LEGACY_URI,
        episodeAudioUrl = "https://example.com/brief.mp3?v=2",
        podcastName = "The Boxlore Brief",
        progressMs = 123_456,
        durationMs = 900_000,
        isCompleted = false,
        isLiked = true,
        likedAt = 123,
        lastPlayedAt = 456,
        isDirty = false,
        syncedAt = 789,
        enclosureType = "audio/mpeg",
        episodeDescription = "Saved description",
    )

    private fun queue(item: ListeningHistoryEntity, position: Int) = QueueItem(
        episodeId = item.episodeId,
        title = item.episodeTitle,
        podcastId = item.podcastId,
        podcastTitle = item.podcastName,
        podcastImageUrl = item.podcastImageUrl,
        imageUrl = item.episodeImageUrl,
        audioUrl = item.episodeAudioUrl!!,
        duration = 900,
        pubDate = 123,
        description = item.episodeDescription,
        position = position,
        addedAt = 456,
        contextType = "BRIEFING",
    )

    private fun download(item: ListeningHistoryEntity) = DownloadedEpisodeEntity(
        episodeId = item.episodeId,
        podcastId = item.podcastId,
        episodeTitle = item.episodeTitle,
        episodeDescription = item.episodeDescription,
        episodeImageUrl = item.episodeImageUrl,
        podcastName = item.podcastName,
        podcastImageUrl = item.podcastImageUrl,
        durationMs = item.durationMs,
        publishedDate = 123,
        localFilePath = "/saved/brief.mp3",
        downloadId = 7,
        downloadedAt = 456,
        sizeBytes = 789,
        status = DownloadedEpisodeEntity.STATUS_COMPLETED,
        downloadOrigin = DownloadedEpisodeEntity.ORIGIN_MANUAL,
    )

    private companion object {
        const val DATABASE_NAME = "brief-artwork-compatibility-test"
        const val APPLICATION_ID = "cx.aswin.boxlore"
        const val LEGACY_URI = "android.resource://cx.aswin.boxlore/2131230927"
    }
}
