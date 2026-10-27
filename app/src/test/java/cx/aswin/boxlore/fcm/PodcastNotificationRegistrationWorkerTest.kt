package cx.aswin.boxlore.fcm

import android.app.Application
import android.content.Context
import androidx.room.Room
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
import cx.aswin.boxlore.core.catalog.FeedAddressUpdater
import cx.aswin.boxlore.core.catalog.MemoryPendingNotificationRegistrations
import cx.aswin.boxlore.core.catalog.NotificationRegistrationOutcome
import cx.aswin.boxlore.core.database.BoxLoreDatabase
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
 * P4 PR 14, plan section 11): one permanent unique work item per show, network-gated, capped retries,
 * and a show the job gave up on queued again by the next pass.
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
    fun `a request returns once the work is saved, and never saves one for rss shows`() = runBlocking {
        PodcastNotificationRegistrationWorker.request(context, "920666")
        PodcastNotificationRegistrationWorker.request(context, "rss:abc")

        val manager = WorkManager.getInstance(context)
        val work = manager.getWorkInfosForUniqueWork(PodcastNotificationRegistrationWorker.workName("920666")).get()
        assertEquals(1, work.count { it.state == WorkInfo.State.ENQUEUED })
        assertTrue(manager.getWorkInfosForUniqueWork(PodcastNotificationRegistrationWorker.workName("rss:abc")).get().isEmpty())
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
    fun `a show the job gave up on stays pending and is queued again by the next pass`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        try {
            val pending = MemoryPendingNotificationRegistrations().apply { add("920666") }
            val updater =
                FeedAddressUpdater(
                    database.podcastDao(),
                    requestNotificationRegistration = { id -> PodcastNotificationRegistrationWorker.enqueue(context, id) },
                    pendingRegistrations = pending,
                )
            val last = worker(NotificationRegistrationOutcome.RETRY, attempt = PodcastNotificationRegistrationWorker.MAX_ATTEMPTS - 1)
            last.pendingMark = updater::pendingMark
            last.settle = updater::registrationSettled

            assertEquals(ListenableWorker.Result.failure(), last.doWork())
            assertEquals("given up, not forgotten", setOf("920666"), pending.all())

            updater.resumePendingRegistrations()
            val queued = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PodcastNotificationRegistrationWorker.workName("920666")).get()
            assertEquals(1, queued.count { it.state == WorkInfo.State.ENQUEUED })

            val acknowledged =
                worker(NotificationRegistrationOutcome.DONE).apply {
                    pendingMark = updater::pendingMark
                    settle = updater::registrationSettled
                }
            assertEquals(ListenableWorker.Result.success(), acknowledged.doWork())
            assertTrue(pending.all().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun `a job that read an older change never clears the newer one made while it ran`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        try {
            val pending = MemoryPendingNotificationRegistrations().apply { add("920666") }
            val updater = FeedAddressUpdater(database.podcastDao(), pendingRegistrations = pending)
            val older =
                worker(NotificationRegistrationOutcome.DONE).apply {
                    pendingMark = updater::pendingMark
                    register = {
                        pending.add("920666") // a newer address change lands while this run registers the old one
                        NotificationRegistrationOutcome.DONE
                    }
                    settle = updater::registrationSettled
                }

            assertEquals(ListenableWorker.Result.success(), older.doWork())
            assertEquals("the newer change waits for its own job", setOf("920666"), pending.all())

            val newer =
                worker(NotificationRegistrationOutcome.DONE).apply {
                    pendingMark = updater::pendingMark
                    settle = updater::registrationSettled
                }
            assertEquals(ListenableWorker.Result.success(), newer.doWork())
            assertTrue(pending.all().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun `an error counts as a retry, and work without a show fails without registering`() = runBlocking {
        val settled = mutableListOf<NotificationRegistrationOutcome>()
        val failing =
            worker(NotificationRegistrationOutcome.DONE).apply {
                register = { error("Firebase not ready") }
                settle = { _, outcome, _ -> settled += outcome }
            }
        assertEquals(ListenableWorker.Result.retry(), failing.doWork())
        assertEquals(listOf(NotificationRegistrationOutcome.RETRY), settled)

        var calls = 0
        val empty = TestListenableWorkerBuilder<PodcastNotificationRegistrationWorker>(context).build().apply {
            register = {
                calls++
                NotificationRegistrationOutcome.DONE
            }
            pendingMark = { null }
            settle = { _, _, _ -> calls++ }
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
                pendingMark = { null }
                settle = { _, _, _ -> }
            }
}
