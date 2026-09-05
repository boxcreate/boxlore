package cx.aswin.boxlore.fcm

import android.app.Application
import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewEpisodeFcmLogicTest {
    @Test
    fun usableEpisodeIdDropsBlankAndZero() {
        assertNull(NewEpisodeFcmLogic.usableEpisodeId(null))
        assertNull(NewEpisodeFcmLogic.usableEpisodeId(" "))
        assertNull(NewEpisodeFcmLogic.usableEpisodeId("0"))
        assertEquals("-12", NewEpisodeFcmLogic.usableEpisodeId("-12"))
        assertEquals("99", NewEpisodeFcmLogic.usableEpisodeId("99"))
    }

    @Test
    fun routeUsesEpisodeWhenPresentOtherwisePodcastPage() {
        assertEquals(
            "boxlore://episode/-12?autoplay=false&podcastId=123&podcastTitle=Show%20Name",
            NewEpisodeFcmLogic.route("123", "-12", "Show Name"),
        )
        assertEquals(
            "boxlore://podcast/123",
            NewEpisodeFcmLogic.route("123", "0", "Show"),
        )
    }

    @Test
    fun durationMinutesPrefersLocalSeconds() {
        assertEquals(30, NewEpisodeFcmLogic.durationMinutes(1800, "9"))
        assertEquals(9, NewEpisodeFcmLogic.durationMinutes(null, "9"))
        assertEquals(0, NewEpisodeFcmLogic.durationMinutes(0, null))
        assertEquals(0, NewEpisodeFcmLogic.durationMinutes(null, "invalid"))
        assertEquals(0, NewEpisodeFcmLogic.durationMinutes(null, "-1"))
    }

    @Test
    fun pickHydratedEpisodeMatchesEnclosureOnly() {
        val extra =
            Episode(
                id = "-8",
                title = "Matched",
                description = "d",
                audioUrl = "https://cdn.example.com/ep.mp3",
                podcastId = "123",
                publishedDate = 100L,
                duration = 60,
            )
        val newest =
            extra.copy(
                id = "-9",
                title = "Newest",
                audioUrl = "https://cdn.example.com/newer.mp3",
                publishedDate = 200L,
            )
        assertEquals(
            "-8",
            NewEpisodeFcmLogic
                .pickHydratedEpisode(
                    extras = listOf(extra, newest),
                    newestTip = newest,
                    enclosureUrl = "https://cdn.example.com/ep.mp3",
                )?.id,
        )
        assertNull(
            NewEpisodeFcmLogic.pickHydratedEpisode(
                extras = listOf(extra, newest),
                newestTip = newest,
                enclosureUrl = "",
            ),
        )
        assertNull(
            NewEpisodeFcmLogic.pickHydratedEpisode(
                extras = listOf(extra, newest),
                newestTip = null,
                enclosureUrl = "",
            ),
        )
        assertNull(
            NewEpisodeFcmLogic.pickHydratedEpisode(
                extras = emptyList(),
                newestTip = null,
                enclosureUrl = "",
            ),
        )
    }

    @Test
    fun buildAutoDownloadWorkRequestConfiguresAutoDownloadWorkerWithWifiOnly() {
        val request = NewEpisodeFcmLogic.buildAutoDownloadWorkRequest("pod-123", "ep-456", wifiOnly = true)
        assertEquals(androidx.work.NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertEquals("pod-123", request.workSpec.input.getString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_PODCAST_ID))
        assertEquals("ep-456", request.workSpec.input.getString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_EPISODE_ID))
        assertEquals(cx.aswin.boxlore.core.downloads.AutoDownloadWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test
    fun buildAutoDownloadWorkRequestConfiguresAutoDownloadWorkerWithConnectedWhenNotWifiOnly() {
        val request = NewEpisodeFcmLogic.buildAutoDownloadWorkRequest("pod-123", "ep-456", wifiOnly = false)
        assertEquals(androidx.work.NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals("pod-123", request.workSpec.input.getString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_PODCAST_ID))
        assertEquals("ep-456", request.workSpec.input.getString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_EPISODE_ID))
        assertEquals(cx.aswin.boxlore.core.downloads.AutoDownloadWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test
    fun enqueueAutoDownloadEnqueuesAndAwaitsOnlyAutoDownloadWorker() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = androidx.work.WorkManager.getInstance(context)

        kotlinx.coroutines.runBlocking {
            NewEpisodeFcmLogic.enqueueAutoDownload(workManager, "pod-test", "ep-test", wifiOnly = true)
        }

        val workInfos = workManager.getWorkInfosByTag(cx.aswin.boxlore.core.downloads.AutoDownloadWorker::class.java.name).get()
        assertEquals(1, workInfos.size)
        val workInfo = workInfos.single()
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, workInfo.state)
    }
}
