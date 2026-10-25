package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
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
    fun `an episode whose other stored ID holds a download is not downloaded again`() = runBlocking {
        // "ep-a" and "ep-b" are one episode stored under two IDs; "ep-b" holds the completed download.
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias("ep-a", owner = true), alias("ep-b")))
        val held = completedDownload("ep-b")
        database.downloadedEpisodeDao().insert(held)
        testDownloadRepo.completionResultProvider = { true }

        manager.triggerDownloads(
            combinedEpisodes = listOf(createEpisode("ep-a", "pod-1", duration = 60), createEpisode("ep-solo", "pod-1", duration = 60)),
            existingDownloads = listOf(held),
            subs = listOf(createPodcastEntity("pod-1")),
            budget = SmartDownloadBudget(maxCount = 5, storageBudgetMb = 500L),
        )

        // An episode stored under one ID downloads as before.
        assertEquals(listOf("ep-solo"), testDownloadRepo.addedDownloads)
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

    @Test
    fun `a serial show switched to its feed continues after the episode its old show was playing`() = runBlocking {
        // The feed show replaced Podcast Index show "920666": its listening stays under that show's
        // IDs ("pi-1" finished, "pi-2" in progress), linked to the feed's episodes "-1" and "-2".
        database.podcastDao().upsert(
            createPodcastEntity(FEED_SHOW).copy(sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = PI_SHOW, preferredSort = "oldest"),
        )
        database.rssEpisodeDao().upsertAll((1..3).map { rssEpisode("-$it", publishedDate = it * 1_000L) })
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(
                showAlias("pi-1", "k1", owner = true),
                showAlias("-1", "k1").copy(provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK),
                showAlias("pi-2", "k2", owner = true),
                showAlias("-2", "k2").copy(provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK),
            ),
        )
        val dao = database.listeningHistoryDao()
        dao.upsert(historyRow("pi-1", completed = true, playedAt = 10L))
        dao.upsert(historyRow("pi-2", progress = 5_000L, playedAt = 20L))
        val history = database.episodeListenerStore().projectRows(dao.getRecentHistoryList(200))

        val next = manager.resolveOldestSerialNextEpisodes(database.podcastDao().getSubscribedPodcastsList(), history, dao.getCompletedEpisodeIds())

        assertEquals("-3", next.getValue(FEED_SHOW).id)
    }

    private fun rssEpisode(id: String, publishedDate: Long) = cx.aswin.boxlore.core.database.RssEpisodeEntity(
        episodeId = id,
        podcastId = FEED_SHOW,
        guid = "k${id.removePrefix("-")}",
        title = "Episode $id",
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        imageUrl = null,
        duration = 60,
        publishedDate = publishedDate,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    private fun showAlias(id: String, key: String, owner: Boolean = false) = alias(id, owner).copy(podcastId = PI_SHOW, catalogKey = key)

    private fun historyRow(id: String, progress: Long = 0L, completed: Boolean = false, playedAt: Long) = cx.aswin.boxlore.core.database.ListeningHistoryEntity(
        episodeId = id,
        podcastId = PI_SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://pi.example/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 60_000L,
        isCompleted = completed,
        lastPlayedAt = playedAt,
    )

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

    private fun alias(id: String, owner: Boolean = false) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = "pod-1",
        catalogKey = "guid-a",
        recipeId = 4_503_599_627_370_500L,
        state = EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
    )

    private fun completedDownload(id: String) = DownloadedEpisodeEntity(
        episodeId = id,
        podcastId = "pod-1",
        episodeTitle = "Episode $id",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Podcast pod-1",
        podcastImageUrl = null,
        durationMs = 60_000L,
        publishedDate = 0L,
        localFilePath = "",
        downloadId = 0L,
        downloadedAt = 1L,
        sizeBytes = 1L,
        status = DownloadedEpisodeEntity.STATUS_COMPLETED,
    )

    private fun createPodcastEntity(id: String): PodcastEntity = PodcastEntity(
        podcastId = id,
        title = "Podcast $id",
        author = "Author",
        imageUrl = "",
        description = "Desc",
        isSubscribed = true,
    )

    private companion object {
        const val PI_SHOW = "920666"
        const val FEED_SHOW = "rss:feed"
    }

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
