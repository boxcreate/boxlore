package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeDao
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncher
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncherHolder
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
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
class DownloadRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: DownloadedEpisodeDao
    private lateinit var repository: DownloadRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DownloadRepository.resetForTesting()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.downloadedEpisodeDao()
        DownloadServiceLauncherHolder.instance =
            DownloadServiceLauncher {
                androidx.media3.exoplayer.offline.DownloadService::class.java
            }
        repository =
            DownloadRepository(
                context = context,
                database = database,
                rankingFeedbackRepository = RankingFeedbackRepository.create(null),
            )
    }

    @After
    fun tearDown() {
        DownloadRepository.resetForTesting()
        database.close()
        DownloadServiceLauncherHolder.instance = null
    }

    private suspend fun <T> pollUntilNotNull(
        timeoutMs: Long = 2_000L,
        intervalMs: Long = 20L,
        block: suspend () -> T?,
    ): T? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val result = block()
            if (result != null) return result
            kotlinx.coroutines.delay(intervalMs)
        }
        return block()
    }

    private suspend fun pollUntil(
        timeoutMs: Long = 2_000L,
        intervalMs: Long = 20L,
        condition: suspend () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            kotlinx.coroutines.delay(intervalMs)
        }
        return condition()
    }

    @Test
    fun `reconcileDownloadStatus deletes stale orphaned row when Media3 has no record`() =
        withDownloadTestLooper {
            dao.insert(
                downloadEntity(
                    episodeId = "orphaned-ep",
                    status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                ),
            )
            assertNotNull(dao.getDownload("orphaned-ep"))

            val result = repository.reconcileDownloadStatus("orphaned-ep")
            assertNull(result)
            assertNull(dao.getDownload("orphaned-ep"))
        }

    @Test
    fun `reconcileDownloadStatus returns existing row unchanged if already completed`() =
        withDownloadTestLooper {
            val completed =
                downloadEntity(
                    episodeId = "completed-ep",
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                )
            dao.insert(completed)

            val result = repository.reconcileDownloadStatus("completed-ep")
            assertNotNull(result)
            assertEquals(DownloadedEpisodeEntity.STATUS_COMPLETED, result!!.status)
            assertEquals("completed-ep", result.episodeId)
        }

    @Test
    fun `reconcileStaleDownloads purges all orphaned downloading rows`() =
        withDownloadTestLooper {
            dao.insert(downloadEntity("ep-1", status = DownloadedEpisodeEntity.STATUS_DOWNLOADING))
            dao.insert(downloadEntity("ep-2", status = DownloadedEpisodeEntity.STATUS_DOWNLOADING))
            dao.insert(downloadEntity("ep-3", status = DownloadedEpisodeEntity.STATUS_COMPLETED))

            repository.reconcileStaleDownloads()

            assertNull(dao.getDownload("ep-1"))
            assertNull(dao.getDownload("ep-2"))
            assertNotNull(dao.getDownload("ep-3"))
        }

    @Test
    fun `reconcileDownloadStatus deletes stale download in STATE_STOPPED`() = withDownloadTestLooper {
        val now = System.currentTimeMillis()
        dao.insert(
            downloadEntity(
                episodeId = "ep-stopped",
                status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                downloadedAt = now - 35 * 60 * 1000L,
            )
        )
        val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder("ep-stopped", android.net.Uri.parse("https://example.com/audio.mp3")).build()
        val download = androidx.media3.exoplayer.offline.Download(
            request,
            androidx.media3.exoplayer.offline.Download.STATE_STOPPED,
            now - 35 * 60 * 1000L,
            now - 35 * 60 * 1000L,
            1000L,
            0,
            0,
        )
        (DownloadRepository.getDownloadManager(context).downloadIndex as? androidx.media3.exoplayer.offline.WritableDownloadIndex)?.putDownload(download)

        val result = repository.reconcileDownloadStatus("ep-stopped")
        assertNull(result)
        assertNull(dao.getDownload("ep-stopped"))
    }

    @Test
    fun `paused auto download keeps partial bytes beyond generic stale threshold`() = withDownloadTestLooper {
        val time = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dao.insert(
            downloadEntity("auto-paused", status = DownloadedEpisodeEntity.STATUS_QUEUED, downloadedAt = time)
            .copy(downloadOrigin = DownloadedEpisodeEntity.ORIGIN_AUTO)
        )
        val manager = DownloadRepository.getDownloadManager(context)
        val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder("auto-paused", android.net.Uri.parse("https://cdn/audio.mp3"))
            .setData("show|Show|Episode|auto".toByteArray()).setCustomCacheKey("auto-paused").build()
        val download = androidx.media3.exoplayer.offline.Download(
            request,
            androidx.media3.exoplayer.offline.Download.STATE_STOPPED,
            time,
            time,
            1000L,
            AutoDownloadTransfers.WAITING_FOR_WORK,
            0
        )
        (manager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(download)
        val cache = DownloadRepository.getDownloadCache(context)
        val hole = cache.startReadWrite("auto-paused", 0, 4)
        try {
            val file = cache.startFile("auto-paused", 0, 4)
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            cache.commitFile(file, 4)
        } finally {
            cache.releaseHoleSpan(hole)
        }
        assertNotNull(repository.reconcileDownloadStatus("auto-paused"))
        assertNotNull(manager.downloadIndex.getDownload("auto-paused"))
        assertEquals(4L, cache.getCachedBytes("auto-paused", 0, 4))
    }

    @Test
    fun `intentional manual download still completes after an auto removal tombstone`() = withDownloadTestLooper {
        val id = "manual-retry"
        database.autoDownloadDao().insertRelease(cx.aswin.boxlore.core.database.AutoDownloadReleaseEntity(id, "show"))
        database.autoDownloadDao().markRemoved(id)
        dao.insert(downloadEntity(id, status = DownloadedEpisodeEntity.STATUS_DOWNLOADING).copy(downloadOrigin = DownloadedEpisodeEntity.ORIGIN_MANUAL))
        val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder(id, android.net.Uri.parse("https://cdn/audio.mp3"))
            .setData("show|Show|Episode|manual".toByteArray()).build()
        val download = androidx.media3.exoplayer.offline.Download(
            request,
            androidx.media3.exoplayer.offline.Download.STATE_COMPLETED,
            1,
            1,
            1000L,
            0,
            0
        )
        (DownloadRepository.getDownloadManager(context).downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(download)
        assertTrue(repository.awaitDownloadCompletion(id, 100))
        assertEquals(DownloadedEpisodeEntity.STATUS_COMPLETED, dao.getDownload(id)?.status)
        assertEquals(DownloadedEpisodeEntity.ORIGIN_MANUAL, dao.getDownload(id)?.downloadOrigin)
        assertEquals(cx.aswin.boxlore.core.database.AutoDownloadReleaseEntity.REMOVED, database.autoDownloadDao().getRelease(id)?.state)
    }

    @Test
    fun `reconcileDownloadStatus preserves actively progressing download even if downloadedAt is old`() = withDownloadTestLooper {
        val now = System.currentTimeMillis()
        dao.insert(
            downloadEntity(
                episodeId = "ep-active-progress",
                status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                downloadedAt = now - 40 * 60 * 1000L,
            )
        )
        val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder("ep-active-progress", android.net.Uri.parse("https://example.com/audio.mp3")).build()
        val download = androidx.media3.exoplayer.offline.Download(
            request,
            androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING,
            now - 40 * 60 * 1000L,
            now - 10 * 1000L,
            1000L,
            0,
            0,
        )
        (DownloadRepository.getDownloadManager(context).downloadIndex as? androidx.media3.exoplayer.offline.WritableDownloadIndex)?.putDownload(download)

        val result = repository.reconcileDownloadStatus("ep-active-progress")
        assertNotNull(result)
        assertNotNull(dao.getDownload("ep-active-progress"))
    }

    @Test
    fun `reconcileDownloadStatus preserves recently stopped download even if downloadedAt is old`() = withDownloadTestLooper {
        val now = System.currentTimeMillis()
        dao.insert(
            downloadEntity(
                episodeId = "ep-recently-stopped",
                status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                downloadedAt = now - 40 * 60 * 1000L,
            ),
        )
        val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder("ep-recently-stopped", android.net.Uri.parse("https://example.com/audio.mp3")).build()
        val download = androidx.media3.exoplayer.offline.Download(
            request,
            androidx.media3.exoplayer.offline.Download.STATE_STOPPED,
            now - 40 * 60 * 1000L,
            now - 5 * 60 * 1000L,
            1000L,
            0,
            0,
        )
        (DownloadRepository.getDownloadManager(context).downloadIndex as? androidx.media3.exoplayer.offline.WritableDownloadIndex)?.putDownload(download)

        val result = repository.reconcileDownloadStatus("ep-recently-stopped")
        assertNotNull(result)
        assertNotNull(dao.getDownload("ep-recently-stopped"))
    }

    @Test
    fun `addDownload with background flag enqueues directly without crashing`() {
        val episode =
            Episode(
                id = "ep-bg",
                title = "Test Episode",
                description = "Desc",
                audioUrl = "https://example.com/audio.mp3",
                imageUrl = "",
                podcastImageUrl = "",
                podcastTitle = "Test Pod",
                podcastId = "pod-1",
                duration = 100,
                publishedDate = 0L,
            )
        val podcast =
            Podcast(
                id = "pod-1",
                title = "Test Pod",
                artist = "Artist",
                imageUrl = "",
            )

        repository.addDownload(episode, podcast, isSmartDownloaded = false, isForeground = false)
        val inserted = withDownloadTestLooper { pollUntilNotNull { dao.getDownload("ep-bg") } }
        assertNotNull(inserted)
        assertEquals(DownloadedEpisodeEntity.STATUS_DOWNLOADING, inserted!!.status)
        assertFalse(inserted.isSmartDownloaded)
    }

    @Test
    fun `addDownload preserves isSmartDownloaded = false if existing manual download exists`() {
        withDownloadTestLooper {
            dao.insert(
                downloadEntity(
                    episodeId = "ep-manual",
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                    isSmartDownloaded = false,
                ),
            )
        }

        val episode =
            Episode(
                id = "ep-manual",
                title = "Test Episode",
                description = "Desc",
                audioUrl = "https://example.com/audio.mp3",
                imageUrl = "",
                podcastImageUrl = "",
                podcastTitle = "Test Pod",
                podcastId = "pod-1",
                duration = 100,
                publishedDate = 0L,
            )
        val podcast =
            Podcast(
                id = "pod-1",
                title = "Test Pod",
                artist = "Artist",
                imageUrl = "",
            )

        repository.addDownload(episode, podcast, isSmartDownloaded = true, isForeground = false)
        val updated = withDownloadTestLooper { pollUntilNotNull { dao.getDownload("ep-manual") } }
        assertNotNull(updated)
        assertFalse(updated!!.isSmartDownloaded)
    }

    @Test
    fun `addDownload does not revert existing completed download to downloading`() {
        withDownloadTestLooper {
            dao.insert(
                downloadEntity(
                    episodeId = "ep-already-completed",
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                    isSmartDownloaded = false,
                ),
            )
        }

        val episode =
            Episode(
                id = "ep-already-completed",
                title = "Test Episode",
                description = "Desc",
                audioUrl = "https://example.com/audio.mp3",
                imageUrl = "",
                podcastImageUrl = "",
                podcastTitle = "Test Pod",
                podcastId = "pod-1",
                duration = 100,
                publishedDate = 0L,
            )
        val podcast =
            Podcast(
                id = "pod-1",
                title = "Test Pod",
                artist = "Artist",
                imageUrl = "",
            )

        repository.addDownload(episode, podcast, isSmartDownloaded = true, isForeground = false)
        val updated = withDownloadTestLooper { pollUntilNotNull { dao.getDownload("ep-already-completed") } }
        assertNotNull(updated)
        assertEquals(DownloadedEpisodeEntity.STATUS_COMPLETED, updated!!.status)
        assertFalse(updated.isSmartDownloaded)
    }

    @Test
    fun `removeDownload purges database row and executes direct removeDownload`() {
        withDownloadTestLooper {
            dao.insert(
                downloadEntity(
                    episodeId = "ep-del",
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                ),
            )
        }

        withDownloadTestLooper { repository.removeDownload("ep-del").join() }
        val isDeleted = withDownloadTestLooper { pollUntil { dao.getDownload("ep-del") == null } }
        assertTrue(isDeleted)
        val deleted = withDownloadTestLooper { dao.getDownload("ep-del") }
        assertNull(deleted)
    }

    @Test
    fun `removeDownload with isForeground false executes direct removal without launching service`() {
        withDownloadTestLooper {
            dao.insert(
                downloadEntity(
                    episodeId = "ep-del-bg",
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                ),
            )
        }

        withDownloadTestLooper { repository.removeDownload("ep-del-bg", isForeground = false).join() }
        val isDeleted = withDownloadTestLooper { pollUntil { dao.getDownload("ep-del-bg") == null } }
        assertTrue(isDeleted)
        val deleted = withDownloadTestLooper { dao.getDownload("ep-del-bg") }
        assertNull(deleted)
    }

    @Test
    fun `awaitDownloadCompletion returns true immediately if already completed in DB`() =
        withDownloadTestLooper {
            dao.insert(
                downloadEntity(
                    episodeId = "ep-already-done",
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                ),
            )

            val completed = repository.awaitDownloadCompletion("ep-already-done", timeoutMs = 1_000L)
            assertTrue(completed)
        }

    @Test
    fun `awaitDownloadCompletion returns false on timeout if download never completes`() =
        withDownloadTestLooper {
            dao.insert(
                downloadEntity(
                    episodeId = "ep-stalled",
                    status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                ),
            )

            val completed = repository.awaitDownloadCompletion("ep-stalled", timeoutMs = 200L)
            assertFalse(completed)
        }

    @Test
    fun `removeDownload evicts versioned briefing cache keys from SimpleCache`() = withDownloadTestLooper {
        val cache = DownloadRepository.getDownloadCache(context)
        val briefingId = "briefing_us_2026-09-05"
        val customKey = "${briefingId}_abc123"

        val span = cache.startReadWrite(customKey, 0, 100)
        val newFile = cache.startFile(customKey, 0, 100)
        newFile.writeText("test briefing payload")
        cache.commitFile(newFile, 100)
        cache.releaseHoleSpan(span)
        assertTrue(cache.keys.contains(customKey))

        dao.insert(downloadEntity(episodeId = briefingId))

        repository.removeDownload(briefingId).join()

        assertFalse(cache.keys.contains(customKey))
        assertFalse(cache.keys.contains(briefingId))
        assertNull(dao.getDownload(briefingId))
    }

    @Test
    fun `getMedia3DownloadCompletionStatus returns false when onCompleted throws exception`() = withDownloadTestLooper {
        val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder("ep-status-fail", android.net.Uri.parse("https://example.com/audio.mp3")).build()
        val download = androidx.media3.exoplayer.offline.Download(
            request,
            androidx.media3.exoplayer.offline.Download.STATE_COMPLETED,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            1000L,
            0,
            0,
        )
        (DownloadRepository.getDownloadManager(context).downloadIndex as? androidx.media3.exoplayer.offline.WritableDownloadIndex)?.putDownload(download)

        val result = getMedia3DownloadCompletionStatus(
            downloadManager = DownloadRepository.getDownloadManager(context),
            episodeId = "ep-status-fail",
            onCompleted = { throw RuntimeException("DB failure") },
        )
        assertEquals(false, result)
    }

    @Test
    fun `DownloadCompletionObserver resumes false when onCompleted throws exception`() = withDownloadTestLooper {
        val completed = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
            val obs = DownloadCompletionObserver(
                episodeId = "ep-throw",
                cont = cont,
                onCompleted = { throw RuntimeException("DB failure") },
            )
            obs.handleCompletion(1000L)
        }
        assertFalse(completed)
    }

    private fun downloadEntity(
        episodeId: String,
        podcastId: String = "pod-1",
        status: Int = DownloadedEpisodeEntity.STATUS_COMPLETED,
        downloadedAt: Long = System.currentTimeMillis(),
        isSmartDownloaded: Boolean = false,
    ) = DownloadedEpisodeEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode $episodeId",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Podcast",
        podcastImageUrl = null,
        durationMs = 1_000L,
        publishedDate = 0L,
        localFilePath = "CACHED",
        downloadId = 1L,
        downloadedAt = downloadedAt,
        sizeBytes = 1_000L,
        status = status,
        isSmartDownloaded = isSmartDownloaded,
    )
}
