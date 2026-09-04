package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.HistoryItem
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Feed-only (negative-id) continuation cases kept out of [SmartQueueEngineTest]
 * so that suite stays under detekt's class-size cap.
 */
class SmartQueueEngineSupplementTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `same-show continuation queues a newer feed-only supplement episode`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] =
            listOf(
                episode(10, publishedDate = 10),
                episode(id = -203, publishedDate = 20),
            )

        val batch =
            engine(sources).getNextEpisodes(
                currentItem(10),
                podcast("pod1", type = "episodic", genre = "News"),
            )

        assertEquals(listOf("-203"), batch.map { it.episode.id.toString() })
        assertTrue(batch.all { it.source == SmartQueueEngine.SOURCE_SAME_PODCAST })
    }

    @Test
    fun `subscription fallback queues a feed-only negative id from candidates`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.subscriptions = listOf(podcast("1258562"))
        sources.episodesByPodcast["1258562"] =
            listOf(episode(id = -9001, podcastId = "1258562", publishedDate = 999))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        val subs = batch.filter { it.source == SmartQueueEngine.SOURCE_SUBSCRIPTION }
        assertTrue(subs.any { it.episode.id == -9001L })
    }

    private open class FakeSources : SmartQueueSources {
        val episodesByPodcast = mutableMapOf<String, List<Episode>>()
        var subscriptions: List<Podcast> = emptyList()

        override suspend fun getEpisodes(podcastId: String): List<Episode> = episodesByPodcast[podcastId] ?: emptyList()

        override suspend fun getQueueCandidates(podcastId: String, limit: Int,): List<Episode> = episodesByPodcast[podcastId]
            .orEmpty()
            .sortedByDescending { it.publishedDate }
            .take(limit)

        override suspend fun getPodcastDetails(podcastId: String): Podcast? = null

        override suspend fun getSubscribedPodcasts(): List<Podcast> = subscriptions

        override suspend fun getCompletedEpisodeIds(): Set<String> = emptySet()

        override suspend fun getRecentlyPlayedPodcastIds(sinceMs: Long): Set<String> = emptySet()

        override suspend fun getResumeCandidates(): List<ListeningHistoryEntity> = emptyList()

        override suspend fun getRecentHistory(limit: Int): List<ListeningHistoryEntity> = emptyList()

        override suspend fun getRegion(): String = "us"

        override suspend fun getInterests(): List<String> = emptyList()

        override suspend fun getHistoryForRecommendations(limit: Int): List<HistoryItem> = emptyList()

        override suspend fun getPersonalizedRecommendations(
            history: List<HistoryItem>,
            interests: List<String>,
            country: String?,
            subscribedPodcastIds: List<String>,
            subscribedGenres: List<String>,
        ): List<Episode> = emptyList()

        override suspend fun getSimilarEpisodes(
            episodeId: String,
            podcastId: String,
            title: String,
            description: String,
            podcastTitle: String,
            country: String?,
        ): List<Episode> = emptyList()

        override suspend fun getTrendingPodcasts(country: String, category: String?,): List<Podcast> = emptyList()
    }

    private fun episode(id: Long, podcastId: String = "pod1", publishedDate: Long = id, podcastGenre: String? = "Comedy",) = Episode(
        id = id.toString(),
        title = "Episode $id",
        description = "",
        audioUrl = "https://audio/$id.mp3",
        imageUrl = "https://img/$id.png",
        podcastImageUrl = "https://img/$podcastId.png",
        podcastTitle = "Podcast $podcastId",
        podcastId = podcastId,
        podcastGenre = podcastGenre,
        publishedDate = publishedDate,
        episodeType = "full",
        duration = 1800,
    )

    private fun podcast(id: String, type: String = "episodic", genre: String = "Comedy",) = Podcast(
        id = id,
        title = "Podcast $id",
        artist = "Artist $id",
        imageUrl = "https://img/$id.png",
        type = type,
        genre = genre,
    )

    private fun currentItem(id: Long) = EpisodeItem(id = id, title = "Episode $id")

    private fun engine(sources: FakeSources) = DefaultSmartQueueEngine(
        sources = sources,
        skipMemory = null,
        nowMs = { now },
        staleRestartEnabled = { true },
    )
}
