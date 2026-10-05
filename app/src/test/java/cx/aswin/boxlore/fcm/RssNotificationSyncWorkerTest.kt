package cx.aswin.boxlore.fcm

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssNotificationSyncWorkerTest {
    @Test fun acknowledgedCompletionSucceedsAndRemoteFailuresRetryWithoutALimit() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker { true }.doWork())
        assertEquals(ListenableWorker.Result.retry(), worker { false }.doWork())
        assertEquals(ListenableWorker.Result.retry(), worker { throw IllegalStateException("offline") }.doWork())
    }

    @Test(expected = CancellationException::class)
    fun cancellationLeavesWorkReplayable() = runBlocking {
        worker { throw CancellationException("stopped") }.doWork()
        Unit
    }

    private fun worker(action: suspend () -> Boolean) = TestListenableWorkerBuilder<RssNotificationSyncWorker>(ApplicationProvider.getApplicationContext<Context>())
        .setRunAttemptCount(12).build().apply { reconcile = action }
}
