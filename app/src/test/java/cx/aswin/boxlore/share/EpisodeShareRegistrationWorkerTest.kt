package cx.aswin.boxlore.share

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
import cx.aswin.boxlore.core.catalog.EpisodeShareRegistrationOutcome
import cx.aswin.boxlore.core.network.model.V2EpisodeRegisterRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Background registration of a shared episode the share couldn't register in time (own-IDs plan
 * section 9, links): one unique work item per episode, network-gated, capped retries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EpisodeShareRegistrationWorkerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val request = V2EpisodeRegisterRequest(show = 920666L, guid = "guid-1")

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @Test
    fun `a share that couldn't register is enqueued once per episode under its permanent name`() {
        EpisodeShareRegistrationWorker.enqueue(context, request)
        EpisodeShareRegistrationWorker.enqueue(context, request)

        val name = EpisodeShareRegistrationWorker.workName(request)
        assertTrue(name.startsWith("episode-share-registration-"))
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertNotEquals(name, EpisodeShareRegistrationWorker.workName(request.copy(guid = "guid-2")))
        assertNotEquals(name, EpisodeShareRegistrationWorker.workName(V2EpisodeRegisterRequest(show = 920666L, enclosureUrl = "guid-1")))
    }

    @Test
    fun `the work waits for a network and backs off exponentially`() {
        val work = EpisodeShareRegistrationWorker.workRequest(request).workSpec
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, work.backoffPolicy)
        assertEquals(30_000L, work.backoffDelayDuration)
        assertEquals(request, EpisodeShareRegistrationWorker.requestFrom(work.input))
    }

    @Test
    fun `a GUID request carries its audio address, which doesn't change its name`() {
        val withAudio = request.copy(enclosureUrl = "https://cdn.example/1.mp3")

        assertEquals(EpisodeShareRegistrationWorker.workName(request), EpisodeShareRegistrationWorker.workName(withAudio))
        assertEquals(withAudio, EpisodeShareRegistrationWorker.requestFrom(EpisodeShareRegistrationWorker.workRequest(withAudio).workSpec.input))
    }

    @Test
    fun `an answer ends the work, and a failure retries until the cap`() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker(EpisodeShareRegistrationOutcome.DONE).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(EpisodeShareRegistrationOutcome.DROPPED).doWork())
        assertEquals(ListenableWorker.Result.retry(), worker(EpisodeShareRegistrationOutcome.RETRY, attempt = 0).doWork())
        assertEquals(
            ListenableWorker.Result.retry(),
            worker(EpisodeShareRegistrationOutcome.RETRY, attempt = EpisodeShareRegistrationWorker.MAX_ATTEMPTS - 2).doWork(),
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            worker(EpisodeShareRegistrationOutcome.RETRY, attempt = EpisodeShareRegistrationWorker.MAX_ATTEMPTS - 1).doWork(),
        )
    }

    @Test
    fun `work without a valid request fails without calling the API`() = runBlocking {
        var calls = 0
        val worker = TestListenableWorkerBuilder<EpisodeShareRegistrationWorker>(context).build().apply {
            register = {
                calls++
                EpisodeShareRegistrationOutcome.DONE
            }
        }

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
        assertEquals(0, calls)
        assertNull(EpisodeShareRegistrationWorker.requestFrom(androidx.work.Data.EMPTY))
    }

    private fun worker(outcome: EpisodeShareRegistrationOutcome, attempt: Int = 0) =
        TestListenableWorkerBuilder<EpisodeShareRegistrationWorker>(context)
            .setInputData(EpisodeShareRegistrationWorker.workRequest(request).workSpec.input)
            .setRunAttemptCount(attempt)
            .build()
            .apply {
                register = { sent ->
                    assertEquals(request, sent)
                    outcome
                }
            }
}
