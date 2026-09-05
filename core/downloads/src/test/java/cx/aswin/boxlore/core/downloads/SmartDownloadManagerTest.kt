package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartDownloadManagerTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var testDownloadRepo: TestDownloadRepository
    private lateinit var manager: SmartDownloadManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DownloadRepository.resetForTesting()
        database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        userPrefs = UserPreferencesRepository(context)
        testDownloadRepo = TestDownloadRepository(context, database)

        val rssRepo = RssPodcastRepository.createForTests(context, database)
        val podcastRepo = PodcastRepository("https://placeholder.example.com", "key", context, rssRepo)
        val subRepo = SubscriptionRepository(database.podcastDao())
        val rankingRepo = AdaptiveRankingRepository.create(context)
        val runtimeControls = RankingRuntimeControls.create(context)
        val adaptiveScorer = AdaptiveCandidateScorer.create(rankingRepo, runtimeControls)
        val historySource = HistoryRecommendationSource { emptyList() }

        manager = SmartDownloadManager(
            context = context,
            database = database,
            podcastRepository = podcastRepo,
            historyRecommendationSource = historySource,
            downloadRepository = testDownloadRepo,
            subscriptionRepository = subRepo,
            userPrefs = userPrefs,
            adaptiveScorer = adaptiveScorer,
        )
    }

    @After
    fun tearDown() {
        database.close()
        DownloadRepository.resetForTesting()
    }

    @Test
    fun `sequential download loop successfully completes downloads in order`() = runBlocking {
        val ep1 = createEpisode("ep-1", "pod-1", duration = 60)
        val ep2 = createEpisode("ep-2", "pod-1", duration = 120)
        val pod = createPodcastEntity("pod-1")

        testDownloadRepo.completionResultProvider = { true }

        manager.triggerDownloads(
            combinedEpisodes = listOf(ep1, ep2),
            existingDownloads = emptyList(),
            subs = listOf(pod),
            budget = SmartDownloadBudget(
                maxCount = 5,
                storageBudgetMb = 500L,
            ),
        )

        assertEquals(listOf("ep-1", "ep-2"), testDownloadRepo.addedDownloads)
        assertTrue(testDownloadRepo.removedDownloads.isEmpty())
    }

    @Test
    fun `per-episode timeout triggers cleanup and allows next download to proceed`() = runBlocking {
        val ep1 = createEpisode("ep-timeout", "pod-1", duration = 60)
        val ep2 = createEpisode("ep-success", "pod-1", duration = 60)
        val pod = createPodcastEntity("pod-1")

        testDownloadRepo.completionResultProvider = { episodeId ->
            episodeId != "ep-timeout"
        }

        manager.triggerDownloads(
            combinedEpisodes = listOf(ep1, ep2),
            existingDownloads = emptyList(),
            subs = listOf(pod),
            budget = SmartDownloadBudget(
                maxCount = 5,
                storageBudgetMb = 500L,
            ),
        )

        assertEquals(listOf("ep-timeout", "ep-success"), testDownloadRepo.addedDownloads)
        assertEquals(listOf("ep-timeout"), testDownloadRepo.removedDownloads)
    }

    @Test
    fun `cumulative time budget exhaustion halts sequential downloads immediately`() = runBlocking {
        val ep1 = createEpisode("ep-1", "pod-1", duration = 60)
        val ep2 = createEpisode("ep-2", "pod-1", duration = 60)
        val ep3 = createEpisode("ep-3", "pod-1", duration = 60)
        val pod = createPodcastEntity("pod-1")

        testDownloadRepo.completionResultProvider = { true }

        var simulatedTime = 1000L
        manager.triggerDownloads(
            combinedEpisodes = listOf(ep1, ep2, ep3),
            existingDownloads = emptyList(),
            subs = listOf(pod),
            budget = SmartDownloadBudget(
                maxCount = 5,
                storageBudgetMb = 500L,
                maxTimeBudgetMs = 100L,
            ),
            currentTimeMillis = {
                val current = simulatedTime
                simulatedTime += 60L
                current
            },
        )

        assertEquals(listOf("ep-1"), testDownloadRepo.addedDownloads)
        assertFalse(testDownloadRepo.addedDownloads.contains("ep-2"))
        assertFalse(testDownloadRepo.addedDownloads.contains("ep-3"))
    }

    @Test
    fun `storage budget exhaustion halts sequential downloads before exceeding limit`() = runBlocking {
        // Each episode with duration 60s has estimated size = 60 * 12_000 = 720_000 bytes (~0.68 MB)
        // With duration 0, fallback DEFAULT_EPISODE_SIZE_BYTES = 50 MB
        val ep1 = createEpisode("ep-50mb-1", "pod-1", duration = 0)
        val ep2 = createEpisode("ep-50mb-2", "pod-1", duration = 0)
        val pod = createPodcastEntity("pod-1")

        testDownloadRepo.completionResultProvider = { true }

        manager.triggerDownloads(
            combinedEpisodes = listOf(ep1, ep2),
            existingDownloads = emptyList(),
            subs = listOf(pod),
            budget = SmartDownloadBudget(
                maxCount = 5,
                storageBudgetMb = 60L,
            ),
        )

        assertEquals(listOf("ep-50mb-1"), testDownloadRepo.addedDownloads)
        assertFalse(testDownloadRepo.addedDownloads.contains("ep-50mb-2"))
    }

    @Test
    fun `max count limit halts sequential downloads once count reached`() = runBlocking {
        val ep1 = createEpisode("ep-1", "pod-1", duration = 60)
        val ep2 = createEpisode("ep-2", "pod-1", duration = 60)
        val pod = createPodcastEntity("pod-1")

        testDownloadRepo.completionResultProvider = { true }

        manager.triggerDownloads(
            combinedEpisodes = listOf(ep1, ep2),
            existingDownloads = emptyList(),
            subs = listOf(pod),
            budget = SmartDownloadBudget(
                maxCount = 1,
                storageBudgetMb = 500L,
            ),
        )

        assertEquals(listOf("ep-1"), testDownloadRepo.addedDownloads)
        assertFalse(testDownloadRepo.addedDownloads.contains("ep-2"))
    }

    private fun createEpisode(id: String, podcastId: String, duration: Int): Episode = Episode(
        id = id,
        title = "Episode $id",
        description = "Desc",
        audioUrl = "https://example.com/$id.mp3",
        imageUrl = "",
        podcastImageUrl = "",
        podcastTitle = "Podcast $podcastId",
        podcastId = podcastId,
        duration = duration,
        publishedDate = 0L,
    )

    private fun createPodcastEntity(id: String): PodcastEntity = PodcastEntity(
        podcastId = id,
        title = "Podcast $id",
        author = "Author",
        imageUrl = "",
        description = "Desc",
        isSubscribed = true,
    )

    private class TestDownloadRepository(
        context: Context,
        database: BoxLoreDatabase,
    ) : DownloadRepository(
        context = context,
        database = database,
        rankingFeedbackRepository = RankingFeedbackRepository.create(null),
    ) {
        val addedDownloads = mutableListOf<String>()
        val removedDownloads = mutableListOf<String>()
        var completionResultProvider: (String) -> Boolean = { true }

        override fun addDownload(
            episode: Episode,
            podcast: Podcast,
            isSmartDownloaded: Boolean,
            isForeground: Boolean,
        ) {
            addedDownloads.add(episode.id)
        }

        override fun removeDownload(episodeId: String, isForeground: Boolean): Job {
            removedDownloads.add(episodeId)
            return CompletableDeferred(Unit)
        }

        override suspend fun awaitDownloadCompletion(episodeId: String, timeoutMs: Long): Boolean =
            completionResultProvider(episodeId)
    }
}
