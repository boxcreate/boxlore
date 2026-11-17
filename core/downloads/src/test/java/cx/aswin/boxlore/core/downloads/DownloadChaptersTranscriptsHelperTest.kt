package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.ChapterOfflineStorage
import cx.aswin.boxlore.core.catalog.TranscriptOfflineStorage
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import java.io.File
import kotlinx.coroutines.awaitCancellation
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
class DownloadChaptersTranscriptsHelperTest {

    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteLocalFileIfValid_deletesExistingLocalFile() {
        val tempFile = File.createTempFile("delete_test", ".tmp")
        assertTrue(tempFile.exists())

        DownloadChaptersTranscriptsHelper.deleteLocalFileIfValid(tempFile.absolutePath)
        assertFalse(tempFile.exists())

        // File URI format
        val tempFile2 = File.createTempFile("delete_test2", ".tmp")
        assertTrue(tempFile2.exists())

        DownloadChaptersTranscriptsHelper.deleteLocalFileIfValid("file://${tempFile2.absolutePath}")
        assertFalse(tempFile2.exists())

        // Remote URL should not be deleted
        DownloadChaptersTranscriptsHelper.deleteLocalFileIfValid("https://example.com/audio.mp3")
        DownloadChaptersTranscriptsHelper.deleteLocalFileIfValid(null)
    }

    @Test
    fun downloadTextUrl_givesUpOnAStalledRemoteRead() = runTest {
        val reader = DownloadChaptersTranscriptsHelper.readRemote
        val timeout = DownloadChaptersTranscriptsHelper.remoteTimeoutMs
        DownloadChaptersTranscriptsHelper.readRemote = { awaitCancellation() }
        DownloadChaptersTranscriptsHelper.remoteTimeoutMs = 1_000L
        try {
            assertNull(DownloadChaptersTranscriptsHelper.downloadTextUrl("https://cdn.example/e1/chapters.json"))
        } finally {
            DownloadChaptersTranscriptsHelper.readRemote = reader
            DownloadChaptersTranscriptsHelper.remoteTimeoutMs = timeout
        }
    }

    @Test
    fun downloadTextUrl_handlesLocalFileAndFileUri() = runTest {
        val tempFile = File.createTempFile("sample_text", ".txt")
        tempFile.writeText("sample content")

        val fromPath = DownloadChaptersTranscriptsHelper.downloadTextUrl(tempFile.absolutePath)
        assertEquals("sample content", fromPath)

        val fromUri = DownloadChaptersTranscriptsHelper.downloadTextUrl("file://${tempFile.absolutePath}")
        assertEquals("sample content", fromUri)

        assertNull(DownloadChaptersTranscriptsHelper.downloadTextUrl(null))
        assertNull(DownloadChaptersTranscriptsHelper.downloadTextUrl(""))

        tempFile.delete()
    }

    @Test
    fun cleanupChaptersAndTranscripts_deletesBothFiles() {
        val episodeId = "test_cleanup_ep"
        val chJson = """{"version":"1.2.0","chapters":[{"title":"Intro","startTime":0}]}"""
        val srt = "1\n00:00:01,000 --> 00:00:03,000\nHello"

        ChapterOfflineStorage.saveOfflineChapters(context, episodeId, chJson)
        TranscriptOfflineStorage.saveOfflineTranscript(context, episodeId, srt)

        assertTrue(ChapterOfflineStorage.hasOfflineChapters(context, episodeId))
        assertTrue(TranscriptOfflineStorage.hasOfflineTranscript(context, episodeId))

        DownloadChaptersTranscriptsHelper.cleanupChaptersAndTranscripts(context, episodeId)

        assertFalse(ChapterOfflineStorage.hasOfflineChapters(context, episodeId))
        assertFalse(TranscriptOfflineStorage.hasOfflineTranscript(context, episodeId))
    }

    @Test
    fun persistOfflineChaptersAndTranscripts_descriptionFallbackWhenChaptersUrlNull() = runTest {
        val episodeId = "desc_fallback_ep"
        val description = "<p>00:00 Introduction</p><p>05:00 Topic Discussion</p>"
        val episode = Episode(
            id = episodeId,
            title = "Episode with description chapters",
            description = description,
            audioUrl = "https://example.com/audio.mp3",
            chaptersUrl = null,
            transcriptUrl = null,
            duration = 600,
            publishedDate = 1000L,
        )

        // Seed DB with entity
        database.downloadedEpisodeDao().insert(
            DownloadedEpisodeEntity(
                episodeId = episodeId,
                podcastId = "pod_1",
                episodeTitle = episode.title,
                episodeDescription = episode.description,
                episodeImageUrl = null,
                podcastName = "Podcast 1",
                podcastImageUrl = null,
                durationMs = 600000L,
                publishedDate = 1000L,
                localFilePath = "/downloads/$episodeId.mp3",
                downloadId = 1L,
                downloadedAt = 1000L,
                sizeBytes = 1024L,
                status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
            )
        )

        DownloadChaptersTranscriptsHelper.persistOfflineChaptersAndTranscripts(context, database, episode)

        assertTrue(ChapterOfflineStorage.hasOfflineChapters(context, episodeId))
        val chapters = ChapterOfflineStorage.getOfflineChapters(context, episodeId)
        assertEquals(2, chapters.size)
        assertEquals("Introduction", chapters[0].title)
        assertEquals("Topic Discussion", chapters[1].title)

        val updatedEntity = database.downloadedEpisodeDao().getDownload(episodeId)
        assertNotNull(updatedEntity?.chaptersUrl)
        assertTrue(updatedEntity!!.chaptersUrl!!.contains("chapter_$episodeId.json"))
    }
}
