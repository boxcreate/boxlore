package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowPageSource
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
    fun `smart downloads leave a failed download the listener started for them to retry`() = runBlocking {
        // Reported: Smart sync picked up a listener's failed download, failed again and deleted it from Downloads.
        database.downloadedEpisodeDao().insert(
            completedDownload("manual-failed").copy(status = DownloadedEpisodeEntity.STATUS_FAILED, downloadOrigin = DownloadedEpisodeEntity.ORIGIN_MANUAL),
        )
        database.downloadedEpisodeDao().insert(
            completedDownload("smart-failed").copy(
                status = DownloadedEpisodeEntity.STATUS_FAILED,
                isSmartDownloaded = true,
                downloadOrigin = DownloadedEpisodeEntity.ORIGIN_SMART,
            ),
        )
        testDownloadRepo.completionResultProvider = { false }

        manager.triggerDownloads(
            combinedEpisodes = listOf(createEpisode("manual-failed", "pod-1", duration = 60), createEpisode("smart-failed", "pod-1", duration = 60)),
            existingDownloads = database.downloadedEpisodeDao().getAllDownloadsSync(),
            subs = listOf(createPodcastEntity("pod-1")),
            budget = SmartDownloadBudget(maxCount = 5, storageBudgetMb = 500L),
        )

        assertEquals("only the Smart Download's own row is tried again", listOf("smart-failed"), testDownloadRepo.addedDownloads)
        assertEquals(listOf("smart-failed"), testDownloadRepo.removedDownloads)
        assertEquals(DownloadedEpisodeEntity.STATUS_FAILED, database.downloadedEpisodeDao().getDownload("manual-failed")?.status)
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

        val next = manager.resolveOldestSerialNextEpisodes(database.podcastDao().getSubscribedPodcastsList(), completedIds())

        assertEquals("-3", next.getValue(FEED_SHOW).id)
    }

    @Test
    fun `a serial show continues past its first 100 episodes from history older than the latest rows`() = runBlocking {
        // 150 episodes, the listener finished up to episode 120 long ago; 250 later plays of another
        // show push those rows out of the latest history rows.
        database.podcastDao().upsert(createPodcastEntity(LONG_SHOW).copy(sourceType = PodcastEntity.SOURCE_RSS, preferredSort = "oldest"))
        database.rssEpisodeDao().upsertAll((1..150).map { rssEpisode(longId(it), publishedDate = it * 1_000L, show = LONG_SHOW) })
        val dao = database.listeningHistoryDao()
        (1..120).forEach { dao.upsert(historyRow(longId(it), completed = true, playedAt = it.toLong(), show = LONG_SHOW)) }
        (1..250).forEach { dao.upsert(historyRow("other-$it", progress = 1_000L, playedAt = 10_000L + it, show = "other-show")) }

        val next = manager.resolveOldestSerialNextEpisodes(database.podcastDao().getSubscribedPodcastsList(), completedIds())

        assertEquals(longId(121), next.getValue(LONG_SHOW).id)
    }

    @Test
    fun `a serial show never offers its finished first episode when its plays are older than the latest rows`() = runBlocking {
        database.podcastDao().upsert(createPodcastEntity(LONG_SHOW).copy(sourceType = PodcastEntity.SOURCE_RSS, preferredSort = "oldest"))
        database.rssEpisodeDao().upsertAll((1..5).map { rssEpisode(longId(it), publishedDate = it * 1_000L, show = LONG_SHOW) })
        val dao = database.listeningHistoryDao()
        dao.upsert(historyRow(longId(1), completed = true, playedAt = 1L, show = LONG_SHOW))
        (1..250).forEach { dao.upsert(historyRow("other-$it", completed = true, playedAt = 10_000L + it, show = "other-show")) }

        val next = manager.resolveOldestSerialNextEpisodes(database.podcastDao().getSubscribedPodcastsList(), completedIds())

        assertEquals(longId(2), next.getValue(LONG_SHOW).id)
    }

    @Test
    fun `a serial show continues from its latest finished play, not an episode sampled long ago`() = runBlocking {
        // The listener tried episode 300 for a minute long ago, then played the show from the start.
        database.podcastDao().upsert(createPodcastEntity(LONG_SHOW).copy(sourceType = PodcastEntity.SOURCE_RSS, preferredSort = "oldest"))
        database.rssEpisodeDao().upsertAll((1..310).map { rssEpisode(longId(it), publishedDate = it * 1_000L, show = LONG_SHOW) })
        val dao = database.listeningHistoryDao()
        dao.upsert(historyRow(longId(300), progress = 60_000L, playedAt = 1L, show = LONG_SHOW))
        (1..50).forEach { dao.upsert(historyRow(longId(it), completed = true, playedAt = 100L + it, show = LONG_SHOW)) }

        val next = manager.resolveOldestSerialNextEpisodes(database.podcastDao().getSubscribedPodcastsList(), completedIds())

        assertEquals(longId(51), next.getValue(LONG_SHOW).id)
    }

    @Test
    fun `a serial show continues after an episode in progress that was played most recently`() = runBlocking {
        database.podcastDao().upsert(createPodcastEntity(LONG_SHOW).copy(sourceType = PodcastEntity.SOURCE_RSS, preferredSort = "oldest"))
        database.rssEpisodeDao().upsertAll((1..40).map { rssEpisode(longId(it), publishedDate = it * 1_000L, show = LONG_SHOW) })
        val dao = database.listeningHistoryDao()
        (1..20).forEach { dao.upsert(historyRow(longId(it), completed = true, playedAt = it.toLong(), show = LONG_SHOW)) }
        dao.upsert(historyRow(longId(30), progress = 60_000L, playedAt = 100L, show = LONG_SHOW))

        val next = manager.resolveOldestSerialNextEpisodes(database.podcastDao().getSubscribedPodcastsList(), completedIds())

        assertEquals(longId(31), next.getValue(LONG_SHOW).id)
    }

    @Test
    fun `a serial show read from the network gets its new episode when the listener is caught up deep in the series`() = runBlocking {
        // 401 episodes from the server's list; the listener finished 1 to 400, and 401 just came out.
        val source = FakeSerialSource(mapOf(NET_SHOW to netEpisodes(NET_SHOW, 401)))
        manager.serialEpisodeSource = source
        finish(NET_SHOW, 1..400)

        val next = manager.resolveOldestSerialNextEpisodes(listOf(serialShow(NET_SHOW)), completedIds())

        assertEquals(netId(NET_SHOW, 401), next.getValue(NET_SHOW).id)
        // Three oldest-first pages ran out before the latest play, then one newest-first page found it.
        assertEquals(listOf("oldest" to 0, "oldest" to 100, "oldest" to 200, "newest" to 0), source.reads(NET_SHOW))
    }

    @Test
    fun `an episode finished while the show's pages are read is not offered`() = runBlocking {
        // Reviewed: completion was read before paging, so an episode finished meanwhile could still be downloaded.
        finish(NET_SHOW, 1..150)
        manager.serialEpisodeSource =
            FakeSerialSource(mapOf(NET_SHOW to netEpisodes(NET_SHOW, 300)), onPage = { _, offset -> if (offset == 100) finish(NET_SHOW, 151..151) })

        val next = manager.resolveOldestSerialNextEpisodes(listOf(serialShow(NET_SHOW)), completedIds())

        assertFalse(NET_SHOW in next)
    }

    @Test
    fun `a serial show read from the network never jumps ahead of a listener partway through`() = runBlocking {
        manager.serialEpisodeSource = FakeSerialSource(mapOf(NET_SHOW to netEpisodes(NET_SHOW, 450)))
        finish(NET_SHOW, 1..330)

        val next = manager.resolveOldestSerialNextEpisodes(listOf(serialShow(NET_SHOW)), completedIds())

        assertFalse(NET_SHOW in next)
    }

    @Test
    fun `auto-download serial shows read no pages, so a show that can be offered keeps its pages`() = runBlocking {
        val autoShows = listOf("net-a1", "net-a2", "net-a3")
        val lists = autoShows.associateWith { netEpisodes(it, 400) } + ("net-z" to netEpisodes("net-z", 400))
        val source = FakeSerialSource(lists)
        manager.serialEpisodeSource = source
        autoShows.forEach { finish(it, 1..250) }
        finish("net-z", 1..150)

        val subs = autoShows.map { serialShow(it).copy(autoDownloadEnabled = true) } + serialShow("net-z")
        val next = manager.resolveOldestSerialNextEpisodes(subs, completedIds())

        assertEquals(setOf("net-z"), next.keys)
        assertEquals(netId("net-z", 151), next.getValue("net-z").id)
        autoShows.forEach { assertTrue(source.reads(it).isEmpty()) }
    }

    @Test
    fun `the shared network pages go to the highest-scored serial show first, not by title`() = runBlocking {
        val shows = listOf("net-a", "net-b", "net-c", "net-d")
        manager.serialEpisodeSource = FakeSerialSource(shows.associateWith { netEpisodes(it, 400) })
        listOf("net-a", "net-b", "net-c").forEach { finish(it, 1..250) }
        // The listener's favourite sorts last by title and needs its second page.
        finish("net-d", 1..150)

        val next =
            manager.resolveOldestSerialNextEpisodes(
                shows.map { serialShow(it) },
                completedIds(),
                podScores = mapOf("net-d" to 10.0, "net-a" to 1.0, "net-b" to 1.0, "net-c" to 1.0),
            )

        assertEquals(netId("net-d", 151), next.getValue("net-d").id)
    }

    @Test
    fun `a serial show reads its own list without feed extras, so a recent extra never jumps ahead`() = runBlocking {
        val extra = createEpisode("extra-new", NET_SHOW, duration = 60).copy(publishedDate = 1_000_000L)
        val source = FakeSerialSource(mapOf(NET_SHOW to netEpisodes(NET_SHOW, 300)), extras = mapOf(NET_SHOW to listOf(extra)))
        manager.serialEpisodeSource = source
        finish(NET_SHOW, 1..100)

        val next = manager.resolveOldestSerialNextEpisodes(listOf(serialShow(NET_SHOW)), completedIds())

        assertEquals(netId(NET_SHOW, 101), next.getValue(NET_SHOW).id)
        assertTrue(source.mergeFlags.isNotEmpty())
        assertTrue(source.mergeFlags.none { it })
    }

    @Test
    fun `a serial show whose first page fails gives no pick, even with feed extras stored`() = runBlocking {
        val extra = createEpisode("extra-new", NET_SHOW, duration = 60).copy(publishedDate = 1_000_000L)
        manager.serialEpisodeSource =
            FakeSerialSource(mapOf(NET_SHOW to netEpisodes(NET_SHOW, 300)), extras = mapOf(NET_SHOW to listOf(extra)), failing = setOf(NET_SHOW))
        finish(NET_SHOW, 1..5)

        val next = manager.resolveOldestSerialNextEpisodes(listOf(serialShow(NET_SHOW)), completedIds())

        assertFalse(NET_SHOW in next)
    }

    /** An oldest-first followed show [id]. */
    private fun serialShow(id: String) = createPodcastEntity(id).copy(title = "Show $id", preferredSort = "oldest")

    /** [count] episodes of the network show [show], oldest first. */
    private fun netEpisodes(show: String, count: Int) = (1..count).map { createEpisode(netId(show, it), show, duration = 60).copy(publishedDate = it * 1_000L) }

    private fun netId(show: String, number: Int) = "$show-e$number"

    /** Records the episodes [numbers] of the network show [show] as finished. */
    private suspend fun finish(show: String, numbers: IntRange) {
        val dao = database.listeningHistoryDao()
        numbers.forEach { dao.upsert(historyRow(netId(show, it), completed = true, playedAt = it.toLong(), show = show)) }
    }

    /** Episode [number] of the long serial show, under an RSS episode ID. */
    private fun longId(number: Int) = "-${10_000 + number}"

    private suspend fun completedIds(): Set<String> =
        database.episodeListenerStore().projectCompletedIds(database.listeningHistoryDao().getCompletedEpisodeIds())

    private fun rssEpisode(id: String, publishedDate: Long, show: String = FEED_SHOW) = cx.aswin.boxlore.core.database.RssEpisodeEntity(
        episodeId = id,
        podcastId = show,
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

    private fun historyRow(
        id: String,
        progress: Long = 0L,
        completed: Boolean = false,
        playedAt: Long,
        show: String = PI_SHOW,
    ) = cx.aswin.boxlore.core.database.ListeningHistoryEntity(
        episodeId = id,
        podcastId = show,
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
        const val LONG_SHOW = "rss:long"
        const val NET_SHOW = "net-long"
    }

    /**
     * A show list from the server, as [PodcastRepository.getEpisodesPaginated] reads it: [extras]
     * are merged into the first page when asked, and a [failing] show reads as an empty page.
     */
    private class FakeSerialSource(
        private val lists: Map<String, List<Episode>>,
        private val extras: Map<String, List<Episode>> = emptyMap(),
        private val failing: Set<String> = emptySet(),
        private val onPage: suspend (feedId: String, offset: Int) -> Unit = { _, _ -> },
    ) : SerialEpisodeSource {
        private val calls = mutableListOf<Triple<String, String, Int>>()
        val mergeFlags = mutableListOf<Boolean>()

        fun reads(feedId: String): List<Pair<String, Int>> = calls.filter { it.first == feedId }.map { it.second to it.third }

        override suspend fun pageSource(feedId: String): ShowPageSource = ShowPageSource.SERVER_LIST

        override suspend fun page(feedId: String, limit: Int, offset: Int, sort: String, mergeSupplements: Boolean,): PodcastRepository.EpisodePage {
            calls += Triple(feedId, sort, offset)
            mergeFlags += mergeSupplements
            onPage(feedId, offset)
            val merged = if (mergeSupplements && offset == 0) extras[feedId].orEmpty() else emptyList()
            if (feedId in failing) return PodcastRepository.EpisodePage(merged, hasMore = false, sourceCount = 0)
            val list = lists.getValue(feedId).let { if (sort == "newest") it.asReversed() else it }
            val slice = list.drop(offset).take(limit)
            val episodes = (slice + merged).sortedBy { if (sort == "newest") -it.publishedDate else it.publishedDate }
            return PodcastRepository.EpisodePage(episodes, hasMore = offset + slice.size < list.size, sourceCount = slice.size)
        }
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

        override fun removeDownload(episodeId: String, isForeground: Boolean, acrossAliases: Boolean,): Job {
            removedDownloads.add(episodeId)
            return CompletableDeferred(Unit)
        }

        override suspend fun awaitDownloadCompletion(episodeId: String, timeoutMs: Long): Boolean =
            completionResultProvider(episodeId)
    }
}
