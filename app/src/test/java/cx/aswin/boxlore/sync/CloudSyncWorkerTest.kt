package cx.aswin.boxlore.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import cx.aswin.boxlore.LegacyWorkerFactory
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SharedAppDependencies
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.sync.SyncResult
import cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CloudSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SharedAppDependenciesHolder.instance = null
    }

    @After
    fun tearDown() {
        SharedAppDependenciesHolder.instance = null
    }

    @Test
    fun doWork_returnsSuccess_whenCoordinatorIsNull() = runBlocking {
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(userSyncCoordinator = null)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun doWork_returnsSuccess_whenSyncNowSucceeds() = runBlocking {
        val fakeCoordinator = FakeUserSyncCoordinator(
            syncResult = SyncResult.Success(
                pushedSubscriptions = 1,
                pushedHistory = 2,
                pushedQueue = true,
                pulledSubscriptions = 3,
                pulledHistory = 4,
                pulledQueue = false,
                syncedAt = 12345L,
            ),
        )
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(userSyncCoordinator = fakeCoordinator)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun doWork_returnsSuccess_whenSyncNowSkippedNoAuth() = runBlocking {
        val fakeCoordinator = FakeUserSyncCoordinator(syncResult = SyncResult.SkippedNoAuth)
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(userSyncCoordinator = fakeCoordinator)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun doWork_returnsRetry_whenSyncNowFails() = runBlocking {
        val fakeCoordinator = FakeUserSyncCoordinator(syncResult = SyncResult.Failure(RuntimeException("Network error")))
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(userSyncCoordinator = fakeCoordinator)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun doWork_returnsRetry_whenSyncNowSkippedOffline() = runBlocking {
        val fakeCoordinator = FakeUserSyncCoordinator(syncResult = SyncResult.SkippedOffline)
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(userSyncCoordinator = fakeCoordinator)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, fakeCoordinator.syncNowCalls)
    }

    @Test
    fun legacyWorkerFactory_createsCloudSyncWorker() {
        val factory = LegacyWorkerFactory()
        val worker = factory.createWorker(
            context,
            CloudSyncWorker::class.java.name,
            TestListenableWorkerBuilder<CloudSyncWorker>(context).build().let { workerInstance ->
                val field = ListenableWorker::class.java.getDeclaredField("mWorkerParams")
                field.isAccessible = true
                field.get(workerInstance) as WorkerParameters
            },
        )

        assertNotNull(worker)
        assertTrue(worker is CloudSyncWorker)
    }

    @Test
    fun staticEnqueueMethods_executeWithoutCrashing() {
        CloudSyncWorker.enqueueOneShotSync(context)
        CloudSyncWorker.schedulePeriodicSync(context)
    }

    private class FakeUserSyncCoordinator(
        var syncResult: SyncResult,
    ) : UserSyncCoordinator() {
        var syncNowCalls = 0

        override suspend fun syncNow(): SyncResult {
            syncNowCalls++
            return syncResult
        }
    }

    private class FakeSharedAppDependencies(
        override val userSyncCoordinator: UserSyncCoordinator?,
    ) : SharedAppDependencies {
        override val database: BoxLoreDatabase get() = error("unused")
        override val podcastRepository: PodcastRepository get() = error("unused")
        override val subscriptionRepository: SubscriptionRepository get() = error("unused")
        override val userPreferencesRepository: UserPreferencesRepository get() = error("unused")
        override val rssPodcastRepository: RssPodcastRepository get() = error("unused")
        override val adaptiveCandidateScorer: AdaptiveCandidateScorer get() = error("unused")
        override val rankingFeedbackRepository: RankingFeedbackRepository get() = error("unused")
        override val adaptiveRankingRepository: AdaptiveRankingRepository get() = error("unused")
        override val rankingRuntimeControls: RankingRuntimeControls get() = error("unused")
        override val historyRecommendationSource: HistoryRecommendationSource get() = error("unused")
        override val subscriptionForegroundSync: SubscriptionForegroundSync get() = error("unused")
        override val folderRepository: FolderRepository get() = error("unused")
    }
}
