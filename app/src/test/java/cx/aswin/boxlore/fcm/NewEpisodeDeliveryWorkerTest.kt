package cx.aswin.boxlore.fcm

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewEpisodeDeliveryWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val payload = mapOf("podcastId" to "123", "guid" to "publisher-release")

    @Test
    fun invalidIdFailsBeforeResolvingDependencies() = runBlocking {
        val deps = FakeDependencies()
        assertEquals(ListenableWorker.Result.failure(), worker(deps, data = emptyMap()).doWork())
        assertEquals(emptyList<String>(), deps.calls)
    }

    @Test
    fun missingOrUnsubscribedShowExitsWithoutHydration() = runBlocking {
        for (show in listOf(null, show().copy(isSubscribed = false))) {
            val deps = FakeDependencies(show = show)
            assertEquals(ListenableWorker.Result.success(), worker(deps).doWork())
            assertEquals(listOf("show"), deps.calls)
        }
    }

    @Test
    fun resolvedReleaseIsAdmittedAndCachedReleasesScannedBeforeNotification() = runBlocking {
        val deps = FakeDependencies()
        assertEquals(ListenableWorker.Result.success(), worker(deps).doWork())
        assertEquals(listOf("show", "hydrate", "accept:-42", "scan", "notify:-42"), deps.calls)
        assertEquals(payload, deps.receivedPayload)
    }

    @Test
    fun notificationsOffStillAdmitsAndScansDownloads() = runBlocking {
        val deps = FakeDependencies(show = show().copy(notificationsEnabled = false))
        assertEquals(ListenableWorker.Result.success(), worker(deps).doWork())
        assertEquals(listOf("show", "hydrate", "accept:-42", "scan"), deps.calls)
    }

    @Test
    fun missingReleaseRetriesThenStopsAtTheLimitWithoutAnAlert() = runBlocking {
        for (attempt in listOf(4, 5)) {
            val deps = FakeDependencies(episode = null)
            val expected = if (attempt == 4) ListenableWorker.Result.retry() else ListenableWorker.Result.success()
            assertEquals(expected, worker(deps, attempt).doWork())
            assertEquals(listOf("show", "hydrate", "scan"), deps.calls)
        }
    }

    @Test
    fun hydrationAndCoordinationExceptionsUseTheSameRetryLimit() = runBlocking {
        for (phase in listOf("show", "hydrate", "accept:-42", "scan")) {
            for (attempt in listOf(4, 5)) {
                val deps = FakeDependencies(failAt = phase, error = IllegalStateException("temporary failure"))
                val expected = if (attempt == 4) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
                assertEquals("$phase at attempt $attempt", expected, worker(deps, attempt).doWork())
            }
        }
    }

    @Test
    fun notificationFailureDoesNotRetryAnAlreadyAdmittedDownload() = runBlocking {
        val deps = FakeDependencies(failAt = "notify:-42", error = SecurityException("permission unavailable"))
        assertEquals(ListenableWorker.Result.success(), worker(deps).doWork())
        assertEquals(listOf("show", "hydrate", "accept:-42", "scan", "notify:-42"), deps.calls)
    }

    @Test
    fun cancellationPropagatesFromHydrationAndNotifications() = runBlocking {
        for (phase in listOf("hydrate", "notify:-42")) {
            val cancellation = CancellationException("stopped")
            val deps = FakeDependencies(failAt = phase, error = cancellation)
            try {
                worker(deps).doWork()
                fail("Cancellation must propagate from $phase")
            } catch (e: CancellationException) {
                assertEquals("stopped", e.message)
            }
        }
    }

    private fun worker(deps: FakeDependencies, attempt: Int = 0, data: Map<String, String> = payload): NewEpisodeDeliveryWorker =
        TestListenableWorkerBuilder<NewEpisodeDeliveryWorker>(context)
            .setInputData(Data.Builder().apply { data.forEach { (key, value) -> putString(key, value) } }.build())
            .setRunAttemptCount(attempt)
            .build().apply { dependencies = deps }

    private class FakeDependencies(
        private val show: PodcastEntity? = show(),
        private val episode: Episode? = Episode("-42", "Release", "", "https://cdn/release.mp3", "123", publishedDate = 100),
        private val failAt: String? = null,
        private val error: Exception = IllegalStateException("failure"),
    ) : NewEpisodeDeliveryWorker.Dependencies {
        val calls = mutableListOf<String>()
        var receivedPayload: Map<String, String>? = null

        private fun record(call: String) {
            calls += call
            if (call == failAt) throw error
        }

        override suspend fun getShow(podcastId: String): PodcastEntity? {
            assertEquals("123", podcastId)
            record("show")
            return show
        }

        override suspend fun resolveEpisode(podcastId: String, data: Map<String, String>): Episode? {
            record("hydrate")
            receivedPayload = data
            return episode
        }

        override suspend fun acceptRelease(podcastId: String, episode: Episode) = record("accept:${episode.id}")
        override suspend fun scanCached(podcastId: String) = record("scan")
        override suspend fun updateNotification(podcastId: String, data: Map<String, String>, episode: Episode) = record("notify:${episode.id}")
    }

    companion object {
        private fun show() = PodcastEntity("123", "Show", "Author", "", "", isSubscribed = true, notificationsEnabled = true)
    }
}
