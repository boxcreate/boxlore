package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hermetic [AutoDownloadWorker] input-validation paths (no network / Media3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoDownloadWorkerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SharedAppDependenciesHolder.instance = null
        DownloadsDependenciesHolder.instance = null
    }

    @After
    fun tearDown() {
        SharedAppDependenciesHolder.instance = null
        DownloadsDependenciesHolder.instance = null
    }

    @Test
    fun `doWork fails when episode id missing`() {
        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data
                        .Builder()
                        .putString(AutoDownloadWorker.KEY_PODCAST_ID, "pod-1")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns success when podcast is not found in database`() {
        val database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val userPrefs = UserPreferencesRepository(context)
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(database, userPrefs)

        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(AutoDownloadWorker.KEY_PODCAST_ID, "missing-pod")
                        .putString(AutoDownloadWorker.KEY_EPISODE_ID, "ep-1")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        database.close()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns success when podcast autoDownloadEnabled is false`() {
        val database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val userPrefs = UserPreferencesRepository(context)
        runBlocking {
            database.podcastDao().upsert(
                cx.aswin.boxlore.core.database.PodcastEntity(
                    podcastId = "pod-disabled",
                    title = "Disabled Pod",
                    author = "Author",
                    imageUrl = "",
                    description = "Desc",
                    isSubscribed = true,
                    autoDownloadEnabled = false,
                ),
            )
        }
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(database, userPrefs)

        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(AutoDownloadWorker.KEY_PODCAST_ID, "pod-disabled")
                        .putString(AutoDownloadWorker.KEY_EPISODE_ID, "ep-1")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        database.close()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns success and promotes smart download when episode is already completed`() {
        val database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val userPrefs = UserPreferencesRepository(context)
        runBlocking {
            database.podcastDao().upsert(
                cx.aswin.boxlore.core.database.PodcastEntity(
                    podcastId = "pod-active",
                    title = "Active Pod",
                    author = "Author",
                    imageUrl = "",
                    description = "Desc",
                    isSubscribed = true,
                    autoDownloadEnabled = true,
                ),
            )
            database.downloadedEpisodeDao().insert(
                cx.aswin.boxlore.core.database.DownloadedEpisodeEntity(
                    episodeId = "ep-completed",
                    podcastId = "pod-active",
                    episodeTitle = "Ep Completed",
                    episodeDescription = null,
                    episodeImageUrl = null,
                    podcastName = "Active Pod",
                    podcastImageUrl = null,
                    durationMs = 1000L,
                    publishedDate = 0L,
                    localFilePath = "CACHED",
                    downloadId = 1L,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes = 1000L,
                    status = cx.aswin.boxlore.core.database.DownloadedEpisodeEntity.STATUS_COMPLETED,
                    isSmartDownloaded = true,
                ),
            )
        }
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(database, userPrefs)
        val downloadRepo =
            DownloadRepository(
                context = context,
                database = database,
                rankingFeedbackRepository = cx.aswin.boxlore.core.ranking.RankingFeedbackRepository.create(null),
            )
        DownloadsDependenciesHolder.instance = FakeDownloadsDependencies(downloadRepo)

        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(AutoDownloadWorker.KEY_PODCAST_ID, "pod-active")
                        .putString(AutoDownloadWorker.KEY_EPISODE_ID, "ep-completed")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        val updatedDownload = runBlocking { database.downloadedEpisodeDao().getDownload("ep-completed") }
        database.close()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(false, updatedDownload?.isSmartDownloaded)
    }

    @Test
    fun `doWork returns failure when episode fetch throws non-retryable error`() {
        val database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val userPrefs = UserPreferencesRepository(context)
        runBlocking {
            database.podcastDao().upsert(
                cx.aswin.boxlore.core.database.PodcastEntity(
                    podcastId = "pod-active",
                    title = "Active Pod",
                    author = "Author",
                    imageUrl = "",
                    description = "Desc",
                    isSubscribed = true,
                    autoDownloadEnabled = true,
                ),
            )
        }
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(database, userPrefs)
        val downloadRepo =
            DownloadRepository(
                context = context,
                database = database,
                rankingFeedbackRepository = cx.aswin.boxlore.core.ranking.RankingFeedbackRepository.create(null),
            )
        DownloadsDependenciesHolder.instance = FakeDownloadsDependencies(downloadRepo)

        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(AutoDownloadWorker.KEY_PODCAST_ID, "pod-active")
                        .putString(AutoDownloadWorker.KEY_EPISODE_ID, "ep-fetch-fail")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        database.close()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork preserves single download when autoDownloadMaxEpisodes is 1`() {
        val database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val userPrefs = UserPreferencesRepository(context)
        runBlocking {
            userPrefs.setAutoDownloadMaxEpisodes(1)
            database.podcastDao().upsert(
                cx.aswin.boxlore.core.database.PodcastEntity(
                    podcastId = "pod-active",
                    title = "Active Pod",
                    author = "Author",
                    imageUrl = "",
                    description = "Desc",
                    isSubscribed = true,
                    autoDownloadEnabled = true,
                ),
            )
            database.downloadedEpisodeDao().insert(
                cx.aswin.boxlore.core.database.DownloadedEpisodeEntity(
                    episodeId = "ep-completed-1",
                    podcastId = "pod-active",
                    episodeTitle = "Ep 1",
                    episodeDescription = null,
                    episodeImageUrl = null,
                    podcastName = "Active Pod",
                    podcastImageUrl = null,
                    durationMs = 1000L,
                    publishedDate = 0L,
                    localFilePath = "CACHED",
                    downloadId = 1L,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes = 1000L,
                    status = cx.aswin.boxlore.core.database.DownloadedEpisodeEntity.STATUS_COMPLETED,
                    isSmartDownloaded = true,
                ),
            )
        }
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(database, userPrefs)
        val downloadRepo =
            DownloadRepository(
                context = context,
                database = database,
                rankingFeedbackRepository = cx.aswin.boxlore.core.ranking.RankingFeedbackRepository.create(null),
            )
        DownloadsDependenciesHolder.instance = FakeDownloadsDependencies(downloadRepo)

        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(AutoDownloadWorker.KEY_PODCAST_ID, "pod-active")
                        .putString(AutoDownloadWorker.KEY_EPISODE_ID, "ep-completed-1")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        val preserved = runBlocking { database.downloadedEpisodeDao().getDownload("ep-completed-1") }
        database.close()

        assertEquals(ListenableWorker.Result.success(), result)
        org.junit.Assert.assertNotNull(preserved)
        assertEquals(false, preserved?.isSmartDownloaded)
    }

    @Test
    fun `doWork trims oldest auto download when autoDownloadMaxEpisodes quota is exceeded`() {
        val database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val userPrefs = UserPreferencesRepository(context)
        runBlocking {
            userPrefs.setAutoDownloadMaxEpisodes(1)
            database.podcastDao().upsert(
                cx.aswin.boxlore.core.database.PodcastEntity(
                    podcastId = "pod-active",
                    title = "Active Pod",
                    author = "Author",
                    imageUrl = "",
                    description = "Desc",
                    isSubscribed = true,
                    autoDownloadEnabled = true,
                ),
            )
            database.downloadedEpisodeDao().insert(
                cx.aswin.boxlore.core.database.DownloadedEpisodeEntity(
                    episodeId = "ep-old",
                    podcastId = "pod-active",
                    episodeTitle = "Ep Old",
                    episodeDescription = null,
                    episodeImageUrl = null,
                    podcastName = "Active Pod",
                    podcastImageUrl = null,
                    durationMs = 1000L,
                    publishedDate = 0L,
                    localFilePath = "CACHED",
                    downloadId = 1L,
                    downloadedAt = 1000L,
                    sizeBytes = 1000L,
                    status = cx.aswin.boxlore.core.database.DownloadedEpisodeEntity.STATUS_COMPLETED,
                    isSmartDownloaded = false,
                ),
            )
            database.downloadedEpisodeDao().insert(
                cx.aswin.boxlore.core.database.DownloadedEpisodeEntity(
                    episodeId = "ep-newer",
                    podcastId = "pod-active",
                    episodeTitle = "Ep Newer",
                    episodeDescription = null,
                    episodeImageUrl = null,
                    podcastName = "Active Pod",
                    podcastImageUrl = null,
                    durationMs = 1000L,
                    publishedDate = 0L,
                    localFilePath = "CACHED",
                    downloadId = 2L,
                    downloadedAt = 2000L,
                    sizeBytes = 1000L,
                    status = cx.aswin.boxlore.core.database.DownloadedEpisodeEntity.STATUS_COMPLETED,
                    isSmartDownloaded = true,
                ),
            )
        }
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(database, userPrefs)
        val downloadRepo =
            DownloadRepository(
                context = context,
                database = database,
                rankingFeedbackRepository = cx.aswin.boxlore.core.ranking.RankingFeedbackRepository.create(null),
            )
        DownloadsDependenciesHolder.instance = FakeDownloadsDependencies(downloadRepo)

        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(AutoDownloadWorker.KEY_PODCAST_ID, "pod-active")
                        .putString(AutoDownloadWorker.KEY_EPISODE_ID, "ep-newer")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        val oldDownload = runBlocking { database.downloadedEpisodeDao().getDownload("ep-old") }
        val newerDownload = runBlocking { database.downloadedEpisodeDao().getDownload("ep-newer") }
        database.close()

        assertEquals(ListenableWorker.Result.success(), result)
        org.junit.Assert.assertNull(oldDownload)
        org.junit.Assert.assertNotNull(newerDownload)
        assertEquals(false, newerDownload?.isSmartDownloaded)
    }

    private class FakeSharedAppDependencies(
        override val database: BoxLoreDatabase,
        override val userPreferencesRepository: UserPreferencesRepository,
    ) : cx.aswin.boxlore.core.catalog.SharedAppDependencies {
        override val podcastRepository: cx.aswin.boxlore.core.catalog.PodcastRepository get() = error("unused")
        override val subscriptionRepository: cx.aswin.boxlore.core.catalog.SubscriptionRepository get() = error("unused")
        override val rssPodcastRepository: cx.aswin.boxlore.core.rss.RssPodcastRepository get() = error("unused")
        override val adaptiveCandidateScorer: cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer get() = error("unused")
        override val rankingFeedbackRepository: cx.aswin.boxlore.core.ranking.RankingFeedbackRepository get() = error("unused")
        override val adaptiveRankingRepository: cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository get() = error("unused")
        override val rankingRuntimeControls: cx.aswin.boxlore.core.ranking.RankingRuntimeControls get() = error("unused")
        override val historyRecommendationSource: cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource get() = error("unused")
        override val subscriptionForegroundSync: cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync get() = error("unused")
    }

    private class FakeDownloadsDependencies(
        override val downloadRepository: DownloadRepository,
    ) : DownloadsDependencies {
        override val smartDownloadManager: SmartDownloadManager get() = error("unused")
    }
}
