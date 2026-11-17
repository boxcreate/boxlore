package cx.aswin.boxlore.core.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.WritableDownloadIndex
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeDao
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncher
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncherHolder
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.flow.first
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
 * A download the listener asked for that Media3 gives up on stays listed as failed and can be
 * started again; only a Smart Download row goes, since Smart Downloads picks again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadFailureTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: DownloadedEpisodeDao
    private lateinit var repository: DownloadRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DownloadRepository.resetForTesting()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.downloadedEpisodeDao()
        DownloadServiceLauncherHolder.instance = DownloadServiceLauncher { androidx.media3.exoplayer.offline.DownloadService::class.java }
        repository = DownloadRepository(context, database, RankingFeedbackRepository.create(null))
    }

    @After
    fun tearDown() {
        DownloadRepository.resetForTesting()
        database.close()
        DownloadServiceLauncherHolder.instance = null
    }

    @Test
    fun `a manual download that fails keeps its row as failed`() = withDownloadTestLooper {
        // Reported: two of eight downloads before a flight failed and vanished from Downloads without a word.
        dao.insert(row("manual", DownloadedEpisodeEntity.ORIGIN_MANUAL))

        keepFailedDownload(database, "manual")

        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, dao.getDownload("manual")!!.status)
        assertFalse("a failed download never reads as downloaded", repository.isDownloaded("manual").first())
        assertFalse(repository.isDownloading("manual").first())
        assertFalse("manual" in repository.completedDownloadIds.first())
    }

    @Test
    fun `an auto-download that fails stays failed and a smart one is removed`() = withDownloadTestLooper {
        dao.insert(row("auto", DownloadedEpisodeEntity.ORIGIN_AUTO))
        dao.insert(row("smart", DownloadedEpisodeEntity.ORIGIN_SMART, isSmart = true))
        dao.insert(row("done", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_COMPLETED))

        keepFailedDownload(database, "auto")
        keepFailedDownload(database, "smart")
        keepFailedDownload(database, "done")

        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, dao.getDownload("auto")!!.status)
        assertNull(dao.getDownload("smart"))
        assertEquals("a finished download is never marked failed", DownloadedEpisodeEntity.STATUS_COMPLETED, dao.getDownload("done")!!.status)
    }

    @Test
    fun `reconcile keeps a failed manual download as failed and deletes a failed smart one`() = withDownloadTestLooper {
        dao.insert(row("manual", DownloadedEpisodeEntity.ORIGIN_MANUAL))
        dao.insert(row("smart", DownloadedEpisodeEntity.ORIGIN_SMART, isSmart = true))
        putMedia3("manual", Download.STATE_FAILED)
        putMedia3("smart", Download.STATE_FAILED)

        val kept = repository.reconcileDownloadStatus("manual")
        val gone = repository.reconcileDownloadStatus("smart")

        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, kept!!.status)
        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, dao.getDownload("manual")!!.status)
        assertNull(gone)
        assertNull(dao.getDownload("smart"))
    }

    @Test
    fun `reconcile still deletes a download Media3 is removing`() = withDownloadTestLooper {
        dao.insert(row("removing", DownloadedEpisodeEntity.ORIGIN_MANUAL))
        putMedia3("removing", Download.STATE_REMOVING)

        assertNull(repository.reconcileDownloadStatus("removing"))
        assertNull(dao.getDownload("removing"))
    }

    @Test
    fun `retrying a failed download starts it again`() = withDownloadTestLooper {
        dao.insert(row("manual", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_FAILED))
        putMedia3("manual", Download.STATE_FAILED)

        assertTrue(repository.retryDownload("manual", isForeground = false))

        assertEquals(DownloadedEpisodeEntity.STATUS_DOWNLOADING, dao.getDownload("manual")!!.status)
        assertTrue(repository.isDownloading("manual").first())
    }

    @Test
    fun `only a failed download the listener asked for can be retried`() = withDownloadTestLooper {
        dao.insert(row("auto", DownloadedEpisodeEntity.ORIGIN_AUTO, status = DownloadedEpisodeEntity.STATUS_FAILED))
        dao.insert(row("running", DownloadedEpisodeEntity.ORIGIN_MANUAL))
        dao.insert(row("unknown-audio", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_FAILED))
        putMedia3("auto", Download.STATE_FAILED)
        putMedia3("running", Download.STATE_DOWNLOADING)

        assertFalse("auto-downloads try again on their own", repository.retryDownload("auto", isForeground = false))
        assertFalse(repository.retryDownload("running", isForeground = false))
        assertFalse("no request and no stored audio address", repository.retryDownload("unknown-audio", isForeground = false))
        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, dao.getDownload("auto")!!.status)
        assertEquals(DownloadedEpisodeEntity.STATUS_DOWNLOADING, dao.getDownload("running")!!.status)
        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, dao.getDownload("unknown-audio")!!.status)
    }

    @Test
    fun `canRetryDownload is true only for a failed download that is not an auto-download`() {
        assertTrue(row("m", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_FAILED).canRetryDownload())
        assertTrue(row("u", DownloadedEpisodeEntity.ORIGIN_UNKNOWN, status = DownloadedEpisodeEntity.STATUS_FAILED).canRetryDownload())
        assertFalse(row("a", DownloadedEpisodeEntity.ORIGIN_AUTO, status = DownloadedEpisodeEntity.STATUS_FAILED).canRetryDownload())
        assertFalse(row("d", DownloadedEpisodeEntity.ORIGIN_MANUAL).canRetryDownload())
        assertFalse(row("c", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_COMPLETED).canRetryDownload())
    }

    @Test
    fun `the Media3 listener keeps a failed manual download and clears a failed smart one`() = withDownloadTestLooper {
        // The live path: Media3 gives up on a transfer (a CDN 403, Wi-Fi gone too long) and tells the listener.
        dao.insert(row("manual", DownloadedEpisodeEntity.ORIGIN_MANUAL))
        dao.insert(row("auto", DownloadedEpisodeEntity.ORIGIN_AUTO))
        dao.insert(row("smart", DownloadedEpisodeEntity.ORIGIN_SMART, isSmart = true))
        dao.insert(row("done", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_COMPLETED))
        val reported = mutableListOf<String>()

        for (id in listOf("manual", "auto", "smart", "done")) {
            onMedia3DownloadFailed(database, media3Download(id, Download.STATE_FAILED), RuntimeException("403")) { _, episodeId, podcastId, _ ->
                reported += "$episodeId@$podcastId"
            }
        }

        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, dao.getDownload("manual")!!.status)
        assertTrue(dao.getDownload("manual")!!.canRetryDownload())
        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, dao.getDownload("auto")!!.status)
        assertNull(dao.getDownload("smart"))
        assertEquals(DownloadedEpisodeEntity.STATUS_COMPLETED, dao.getDownload("done")!!.status)
        assertEquals(listOf("manual@pod-1", "auto@pod-1", "smart@pod-1", "done@pod-1"), reported)
    }

    @Test
    fun `retrying uses the episode's current audio address over the expired one Media3 kept`() = withDownloadTestLooper {
        // Reported: links that expired (CDN 403) failed again on every Download Again after the feed had new ones.
        dao.insert(row("manual", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_FAILED))
        putMedia3("manual", Download.STATE_FAILED)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(catalogEpisode("manual", "https://cdn.example/manual.mp3?token=new")))

        val (_, request) = prepareDownloadRetry(database, downloadIndex(), "manual")!!

        assertEquals("https://cdn.example/manual.mp3?token=new", request.uri.toString())
        assertEquals("manual", request.customCacheKey)
        assertEquals("pod-1|Podcast|Episode manual|manual", String(request.data, Charsets.UTF_8))
    }

    @Test
    fun `retrying an episode the catalog doesn't hold uses the request Media3 kept`() = withDownloadTestLooper {
        dao.insert(row("manual", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_FAILED))
        putMedia3("manual", Download.STATE_FAILED)

        val (_, request) = prepareDownloadRetry(database, downloadIndex(), "manual")!!

        assertEquals("https://cdn.example/manual.mp3", request.uri.toString())
    }

    @Test
    fun `retrying a failed download another ID of the episode holds removes it instead of a second copy`() = withDownloadTestLooper {
        // The old Podcast Index ID failed; the same episode then downloaded under its feed ID.
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(
                EpisodeAliasEntity("pi-1", "920666", "guid-a", null, EpisodeAliasState.TRUSTED, true, EpisodeAliasProvenance.UPGRADE_CATALOG, 1L),
                EpisodeAliasEntity("-1", "920666", "guid-a", null, EpisodeAliasState.TRUSTED, false, EpisodeAliasProvenance.RSS_SOURCE_LINK, 2L),
            ),
        )
        dao.insert(row("pi-1", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_FAILED))
        dao.insert(row("-1", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_COMPLETED))
        putMedia3("pi-1", Download.STATE_FAILED)

        assertFalse(repository.retryDownload("pi-1", isForeground = false))

        assertNull("the failed row goes", dao.getDownload("pi-1"))
        assertEquals("the copy the other ID holds stays", DownloadedEpisodeEntity.STATUS_COMPLETED, dao.getDownload("-1")!!.status)
    }

    @Test
    fun `an auto-download takes over a failed manual download but never a running one`() = withDownloadTestLooper {
        // Reported: a manual download that failed on mobile data stopped the Wi-Fi auto-download for good.
        dao.insert(row("failed", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_FAILED))
        dao.insert(row("running", DownloadedEpisodeEntity.ORIGIN_MANUAL))
        val podcast = Podcast(id = "pod-1", title = "Podcast", artist = "", imageUrl = "")

        assertTrue(repository.addAutoDownload(episode("failed"), podcast))
        assertFalse(repository.addAutoDownload(episode("running"), podcast))

        val adopted = dao.getDownload("failed")!!
        assertEquals(DownloadedEpisodeEntity.ORIGIN_AUTO, adopted.downloadOrigin)
        assertEquals(DownloadedEpisodeEntity.STATUS_DOWNLOADING, adopted.status)
        assertEquals(DownloadedEpisodeEntity.ORIGIN_MANUAL, dao.getDownload("running")!!.downloadOrigin)
    }

    @Test
    fun `only a manual download that hasn't failed keeps background downloads away`() {
        assertTrue(row("m", DownloadedEpisodeEntity.ORIGIN_MANUAL).isOwnedByManualRequest())
        assertTrue(row("c", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_COMPLETED).isOwnedByManualRequest())
        assertFalse(row("f", DownloadedEpisodeEntity.ORIGIN_MANUAL, status = DownloadedEpisodeEntity.STATUS_FAILED).isOwnedByManualRequest())
        assertFalse(row("a", DownloadedEpisodeEntity.ORIGIN_AUTO).isOwnedByManualRequest())
        assertFalse(row("s", DownloadedEpisodeEntity.ORIGIN_SMART, isSmart = true).isOwnedByManualRequest())
    }

    private fun downloadIndex() = DownloadRepository.getDownloadManager(context).downloadIndex

    private fun media3Download(episodeId: String, state: Int): Download {
        val now = System.currentTimeMillis()
        val request = DownloadRequest.Builder(episodeId, Uri.parse("https://cdn.example/$episodeId.mp3"))
            .setData("pod-1|Podcast|Episode $episodeId|manual".toByteArray())
            .build()
        return Download(request, state, now, now, 1_000L, Download.STOP_REASON_NONE, if (state == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else Download.FAILURE_REASON_NONE)
    }

    private fun putMedia3(episodeId: String, state: Int) {
        (downloadIndex() as WritableDownloadIndex).putDownload(media3Download(episodeId, state))
    }

    private fun episode(id: String) = Episode(id = id, title = "Episode $id", description = "", audioUrl = "https://cdn.example/$id.mp3", podcastId = "pod-1")

    private fun catalogEpisode(id: String, audioUrl: String) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = "pod-1",
        guid = "guid-$id",
        title = "Episode $id",
        description = "",
        audioUrl = audioUrl,
        imageUrl = null,
        duration = 60,
        publishedDate = 0L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    private fun row(
        episodeId: String,
        origin: String,
        status: Int = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
        isSmart: Boolean = false,
    ) = DownloadedEpisodeEntity(
        episodeId = episodeId,
        podcastId = "pod-1",
        episodeTitle = "Episode $episodeId",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Podcast",
        podcastImageUrl = null,
        durationMs = 1_000L,
        publishedDate = 0L,
        localFilePath = "",
        downloadId = 0L,
        downloadedAt = System.currentTimeMillis(),
        sizeBytes = 0L,
        status = status,
        isSmartDownloaded = isSmart,
        downloadOrigin = origin,
    )
}
