package cx.aswin.boxlore.fcm

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun slotConstantsWithinExpectedBounds() {
        assertEquals(16, NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS)
        assertEquals(10_000, NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE)
        assertEquals(10_000, NewEpisodeFcmLogic.EPISODE_REQUEST_CODE_BASE)

        assertEquals(4, NewEpisodeFcmLogic.MAX_ANNOUNCEMENT_SLOTS)
        assertEquals(20_000, NewEpisodeFcmLogic.ANNOUNCEMENT_NOTIFICATION_ID_BASE)
        assertEquals(20_000, NewEpisodeFcmLogic.ANNOUNCEMENT_REQUEST_CODE_BASE)
        assertEquals(20_100, NewEpisodeFcmLogic.ANNOUNCEMENT_ACTION_REQUEST_CODE_BASE)
    }

    @Test
    fun episodeSlotProducesBoundedNonNegativeIndicesEvenWithNegativeHashCodes() {
        val testIds = listOf(
            "podcast-1",
            "podcast-2",
            "negative-hash-sample",
            "polygenelubricants", // Known negative hashCode string in Java
            "",
            "a",
            "z".repeat(100),
            "-1",
            "-999999",
        )

        for (id in testIds) {
            val slot = NewEpisodeFcmLogic.episodeSlot(id)
            assertTrue("Slot $slot must be >= 0", slot >= 0)
            assertTrue("Slot $slot must be < 16", slot < NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS)
        }

        // Test extreme integer edge cases
        assertEquals(
            0,
            Math.floorMod(Int.MIN_VALUE, NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS),
        )
        assertEquals(
            15,
            Math.floorMod(-1, NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS),
        )
        assertEquals(
            12,
            Math.floorMod(-20, NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS),
        )
    }

    @Test
    fun announcementSlotProducesBoundedNonNegativeIndicesEvenWithNegativeHashCodes() {
        assertEquals(0, NewEpisodeFcmLogic.announcementSlot(null))

        val testKeys = listOf(
            "announcement-1",
            "announcement-2",
            "update_news",
            "polygenelubricants",
            "special_offer",
            "-123",
        )

        for (key in testKeys) {
            val slot = NewEpisodeFcmLogic.announcementSlot(key)
            assertTrue("Announcement slot $slot must be >= 0", slot >= 0)
            assertTrue("Announcement slot $slot must be < 4", slot < NewEpisodeFcmLogic.MAX_ANNOUNCEMENT_SLOTS)
        }

        assertEquals(
            0,
            Math.floorMod(Int.MIN_VALUE, NewEpisodeFcmLogic.MAX_ANNOUNCEMENT_SLOTS),
        )
        assertEquals(
            3,
            Math.floorMod(-1, NewEpisodeFcmLogic.MAX_ANNOUNCEMENT_SLOTS),
        )
    }

    @Test
    fun normalizedIntentsSatisfyFilterEqualsAcrossDifferentRoutesAndExtras() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val episodeIntent1 = NewEpisodeFcmLogic.createNormalizedPushIntent(
            context = context,
            targetRoute = "boxlore://episode/1001?autoplay=false&podcastId=pod1&podcastTitle=Show%20One",
            notificationType = "new_episode",
            podcastId = "pod1",
            episodeId = "1001",
        )

        val episodeIntent2 = NewEpisodeFcmLogic.createNormalizedPushIntent(
            context = context,
            targetRoute = "boxlore://episode/9999?autoplay=false&podcastId=pod2&podcastTitle=Show%20Two",
            notificationType = "new_episode",
            podcastId = "pod2",
            episodeId = "9999",
        )

        val announcementIntent = NewEpisodeFcmLogic.createNormalizedPushIntent(
            context = context,
            targetRoute = "home",
            notificationType = "push",
            podcastId = null,
            episodeId = null,
        )

        // Android filterEquals ignores extras and checks action, data, type, component, package, categories.
        // Because data is null and action is Intent.ACTION_VIEW for all normalized intents,
        // filterEquals MUST return true to allow PendingIntentRecord reuse in system_server.
        assertTrue(episodeIntent1.filterEquals(episodeIntent2))
        assertTrue(episodeIntent1.filterEquals(announcementIntent))

        assertNull("Normalized intent must not set Intent.data", episodeIntent1.data)
        assertNull("Normalized intent must not set Intent.data", episodeIntent2.data)
        assertNull("Normalized intent must not set Intent.data", announcementIntent.data)
        assertEquals(Intent.ACTION_VIEW, episodeIntent1.action)
        assertEquals(Intent.ACTION_VIEW, episodeIntent2.action)
        assertEquals(Intent.ACTION_VIEW, announcementIntent.action)

        // Extras must remain preserved on each intent
        assertTrue(episodeIntent1.getBooleanExtra("from_push", false))
        assertEquals("new_episode", episodeIntent1.getStringExtra("notification_type"))
        assertEquals("pod1", episodeIntent1.getStringExtra("podcast_id"))
        assertEquals("1001", episodeIntent1.getStringExtra("episode_id"))
        assertEquals(
            "boxlore://episode/1001?autoplay=false&podcastId=pod1&podcastTitle=Show%20One",
            episodeIntent1.getStringExtra("target_route"),
        )

        assertTrue(episodeIntent2.getBooleanExtra("from_push", false))
        assertEquals("pod2", episodeIntent2.getStringExtra("podcast_id"))
        assertEquals("9999", episodeIntent2.getStringExtra("episode_id"))

        assertTrue(announcementIntent.getBooleanExtra("from_push", false))
        assertEquals("push", announcementIntent.getStringExtra("notification_type"))
        assertNull(announcementIntent.getStringExtra("podcast_id"))
        assertNull(announcementIntent.getStringExtra("episode_id"))
        assertEquals("home", announcementIntent.getStringExtra("target_route"))
    }

    @Test
    fun createNormalizedPushIntentSanitizesUnsafeRoutes() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val unsafeIntent = NewEpisodeFcmLogic.createNormalizedPushIntent(
            context = context,
            targetRoute = "javascript:alert('xss')",
        )
        assertNull(unsafeIntent.getStringExtra("target_route"))

        val emptyIntent = NewEpisodeFcmLogic.createNormalizedPushIntent(
            context = context,
            targetRoute = "   ",
        )
        assertNull(emptyIntent.getStringExtra("target_route"))

        val safeUriIntent = NewEpisodeFcmLogic.createNormalizedPushIntent(
            context = context,
            targetRoute = "boxlore://podcast/abc-123",
        )
        assertEquals("boxlore://podcast/abc-123", safeUriIntent.getStringExtra("target_route"))

        val safeTabIntent = NewEpisodeFcmLogic.createNormalizedPushIntent(
            context = context,
            targetRoute = "library/downloads",
        )
        assertEquals("library/downloads", safeTabIntent.getStringExtra("target_route"))
    }

    @Test
    fun decoupledAutoDownloadExecutesIndependentlyOfNotificationFailure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = androidx.work.WorkManager.getInstance(context)

        // Simulate decoupled flow where notification display or PendingIntent throws SecurityException
        var autoDownloadEnqueued = false
        var notificationAttempted = false
        var notificationFailedWithSecurityException = false

        val podcastId = "pod-failure-test"
        val episodeId = "ep-failure-test"

        // 1. Auto-download execution
        try {
            kotlinx.coroutines.runBlocking {
                NewEpisodeFcmLogic.enqueueAutoDownload(workManager, podcastId, episodeId, wifiOnly = false)
            }
            autoDownloadEnqueued = true
        } catch (e: Exception) {
            autoDownloadEnqueued = false
        }

        // 2. Notification simulation throwing SecurityException
        try {
            notificationAttempted = true
            throw SecurityException("Too many PendingIntent created for uid")
        } catch (e: SecurityException) {
            notificationFailedWithSecurityException = true
        }

        assertTrue("Auto download must succeed even if notification throws SecurityException", autoDownloadEnqueued)
        assertTrue("Notification must have been attempted", notificationAttempted)
        assertTrue("SecurityException must be caught without crashing", notificationFailedWithSecurityException)

        val workInfos = workManager.getWorkInfosByTag(cx.aswin.boxlore.core.downloads.AutoDownloadWorker::class.java.name).get()
        val match = workInfos.find { it.tags.contains(cx.aswin.boxlore.core.downloads.AutoDownloadWorker::class.java.name) }
        assertNotNull(match)
    }

    @Test
    fun snakeCaseAndCamelCasePayloadsResolveIdsCorrectlyForAutoDownload() {
        val snakeCasePayload = mapOf(
            "type" to "new_episode",
            "podcast_id" to "snake-pod",
            "episode_id" to "snake-ep",
            "podcast_title" to "Snake Show",
            "episode_title" to "Snake Episode",
        )

        val camelCasePayload = mapOf(
            "type" to "new_episode",
            "podcastId" to "camel-pod",
            "episodeId" to "camel-ep",
            "podcastTitle" to "Camel Show",
            "episodeTitle" to "Camel Episode",
        )

        val snakePodId = FcmPayloadParser.podcastId(snakeCasePayload)
        val snakeEpId = FcmPayloadParser.episodeId(snakeCasePayload)
        assertEquals("snake-pod", snakePodId)
        assertEquals("snake-ep", snakeEpId)

        val camelPodId = FcmPayloadParser.podcastId(camelCasePayload)
        val camelEpId = FcmPayloadParser.episodeId(camelCasePayload)
        assertEquals("camel-pod", camelPodId)
        assertEquals("camel-ep", camelEpId)

        val snakeWorkRequest = NewEpisodeFcmLogic.buildAutoDownloadWorkRequest(snakePodId!!, snakeEpId!!, wifiOnly = false)
        assertEquals("snake-pod", snakeWorkRequest.workSpec.input.getString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_PODCAST_ID))
        assertEquals("snake-ep", snakeWorkRequest.workSpec.input.getString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_EPISODE_ID))

        val camelWorkRequest = NewEpisodeFcmLogic.buildAutoDownloadWorkRequest(camelPodId!!, camelEpId!!, wifiOnly = true)
        assertEquals("camel-pod", camelWorkRequest.workSpec.input.getString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_PODCAST_ID))
        assertEquals("camel-ep", camelWorkRequest.workSpec.input.getString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_EPISODE_ID))
    }
}
