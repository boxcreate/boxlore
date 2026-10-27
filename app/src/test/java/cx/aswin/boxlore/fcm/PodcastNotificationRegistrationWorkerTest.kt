package cx.aswin.boxlore.fcm

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import cx.aswin.boxlore.core.catalog.NotificationRegistrationOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Acknowledged notification registration after a Podcast Index show's address changed (own-episode-IDs
 * P4 PR 14, plan section 11): one permanent unique work item per show, network-gated, capped retries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PodcastNotificationRegistrationWorkerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @Test
    fun `one work item per show under its permanent name, and never for rss shows`() {
        PodcastNotificationRegistrationWorker.enqueue(context, "920666")
        PodcastNotificationRegistrationWorker.enqueue(context, "920666")
        PodcastNotificationRegistrationWorker.enqueue(context, "rss:abc")

        val name = PodcastNotificationRegistrationWorker.workName("920666")
        assertEquals("podcast-notification-registration-920666", name)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
        assertEquals("a newer address change replaces the waiting one", 1, work.count { it.state == WorkInfo.State.ENQUEUED })
        assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork(PodcastNotificationRegistrationWorker.workName("rss:abc")).get().isEmpty())
    }

    @Test
    fun `the work waits for a network and backs off exponentially`() {
        val work = PodcastNotificationRegistrationWorker.workRequest("920666").workSpec
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, work.backoffPolicy)
        assertEquals(30_000L, work.backoffDelayDuration)
    }

    @Test
    fun `an acknowledgement ends the work, and the retries stop at the cap`() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker(NotificationRegistrationOutcome.DONE).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(NotificationRegistrationOutcome.DROPPED).doWork())
        assertEquals(ListenableWorker.Result.retry(), worker(NotificationRegistrationOutcome.RETRY, attempt = 0).doWork())
        assertEquals(
            ListenableWorker.Result.retry(),
            worker(NotificationRegistrationOutcome.RETRY, attempt = PodcastNotificationRegistrationWorker.MAX_ATTEMPTS - 2).doWork(),
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            worker(NotificationRegistrationOutcome.RETRY, attempt = PodcastNotificationRegistrationWorker.MAX_ATTEMPTS - 1).doWork(),
        )
    }

    @Test
    fun `an error counts as a retry, and work without a show fails without registering`() = runBlocking {
        val failing = worker(NotificationRegistrationOutcome.DONE).apply { register = { error("Firebase not ready") } }
        assertEquals(ListenableWorker.Result.retry(), failing.doWork())

        var calls = 0
        val empty = TestListenableWorkerBuilder<PodcastNotificationRegistrationWorker>(context).build().apply {
            register = {
                calls++
                NotificationRegistrationOutcome.DONE
            }
        }
        assertEquals(ListenableWorker.Result.failure(), empty.doWork())
        assertEquals(0, calls)
    }

    private fun worker(outcome: NotificationRegistrationOutcome, attempt: Int = 0) =
        TestListenableWorkerBuilder<PodcastNotificationRegistrationWorker>(context)
            .setInputData(PodcastNotificationRegistrationWorker.workRequest("920666").workSpec.input)
            .setRunAttemptCount(attempt)
            .build()
            .apply {
                register = { id ->
                    assertEquals("920666", id)
                    outcome
                }
            }
}
