package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SameShowContinuationLogicTest {
    private fun testEpisode(
        id: String,
        contextSourceId: String? = null,
        podcastId: String = "pod-1",
        podcastTitle: String = "Test Show",
        publishedDate: Long = 1000L,
        episodeType: String? = "full",
        audioUrl: String = "https://example.com/audio/$id.mp3",
    ): Episode = Episode(
        id = id,
        title = "Episode $id",
        description = "Description $id",
        audioUrl = audioUrl,
        podcastId = podcastId,
        podcastTitle = podcastTitle,
        publishedDate = publishedDate,
        episodeType = episodeType,
        contextSourceId = contextSourceId,
    )

    private fun testPodcast(
        id: String = "pod-1",
        type: String = "episodic",
        preferredSort: String? = null,
        genre: String = "Technology",
    ): Podcast = Podcast(
        id = id,
        title = "Test Show",
        artist = "Test Artist",
        imageUrl = "https://example.com/art.jpg",
        type = type,
        preferredSort = preferredSort,
        genre = genre,
    )

    @Test
    fun `canShowBanner returns false when episode is null`() {
        assertFalse(SameShowContinuationLogic.canShowBanner(null, sameShowQueueOnly = false))
    }

    @Test
    fun `canShowBanner returns false when sameShowQueueOnly is enabled`() {
        val ep = testEpisode("ep-1", contextSourceId = "recommendations")
        assertFalse(SameShowContinuationLogic.canShowBanner(ep, sameShowQueueOnly = true))
    }

    @Test
    fun `canShowBanner returns false for curiosity items`() {
        val ep = testEpisode("learn:123", contextSourceId = "recommendations")
        assertFalse(SameShowContinuationLogic.canShowBanner(ep, sameShowQueueOnly = false))
    }

    @Test
    fun `canShowBanner returns false for briefing episodes`() {
        val ep1 = testEpisode("briefing_2026_09_04", contextSourceId = "recommendations")
        assertFalse(SameShowContinuationLogic.canShowBanner(ep1, sameShowQueueOnly = false))

        val ep2 = testEpisode("ep-2", contextSourceId = "recommendations", podcastId = "briefing_global")
        assertFalse(SameShowContinuationLogic.canShowBanner(ep2, sameShowQueueOnly = false))
    }

    @Test
    fun `canShowBanner returns false when contextSourceId is null`() {
        // When contextSourceId is null, Tier 0 was allowed naturally, not skipped
        val ep = testEpisode("ep-1", contextSourceId = null)
        assertFalse(SameShowContinuationLogic.canShowBanner(ep, sameShowQueueOnly = false))
    }

    @Test
    fun `canShowBanner returns false when contextSourceId is in SHOW_BINGE_SOURCES`() {
        SmartQueueEngine.SHOW_BINGE_SOURCES.forEach { source ->
            val ep = testEpisode("ep-1", contextSourceId = source)
            assertFalse(
                SameShowContinuationLogic.canShowBanner(ep, sameShowQueueOnly = false),
                "Expected banner hidden for binge source $source",
            )
        }
    }

    @Test
    fun `canShowBanner returns true for discovery and recommendation sources`() {
        val discoverySources = listOf("recommendations", "home_for_you", "similar_episode", "trending", "feed")
        discoverySources.forEach { source ->
            val ep = testEpisode("ep-1", contextSourceId = source)
            assertTrue(
                SameShowContinuationLogic.canShowBanner(ep, sameShowQueueOnly = false),
                "Expected banner allowed for recommendation source $source",
            )
        }
    }

    @Test
    fun `computeCandidates returns forward chronological episodes for serial podcast`() {
        val episodes =
            (1..6).map { i ->
                testEpisode("ep-$i", publishedDate = i * 1000L)
            }
        val current = episodes[1] // ep-2
        val podcast = testPodcast(type = "serial")

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = current,
                podcast = podcast,
            )

        assertEquals(4, candidates.size)
        assertEquals(listOf("ep-3", "ep-4", "ep-5", "ep-6"), candidates.map { it.id })
    }

    @Test
    fun `computeCandidates returns empty list for final episode of serial podcast`() {
        val episodes =
            (1..5).map { i ->
                testEpisode("ep-$i", publishedDate = i * 1000L)
            }
        val current = episodes.last() // ep-5
        val podcast = testPodcast(type = "serial")

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = current,
                podcast = podcast,
            )

        assertTrue(candidates.isEmpty(), "Banner must NOT be shown when user is on final serial episode")
    }

    @Test
    fun `computeCandidates returns newer episodes for episodic podcast`() {
        val episodes =
            (1..6).map { i ->
                testEpisode("ep-$i", publishedDate = i * 1000L)
            }
        val current = episodes[2] // ep-3 (pubDate = 3000L)
        val podcast = testPodcast(type = "episodic", preferredSort = "newest")

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = current,
                podcast = podcast,
            )

        // Newer episodes in forward chronological order: ep-4, ep-5, ep-6
        assertEquals(listOf("ep-4", "ep-5", "ep-6"), candidates.map { it.id })
    }

    @Test
    fun `computeCandidates returns empty list for latest release of episodic podcast`() {
        val episodes =
            (1..5).map { i ->
                testEpisode("ep-$i", publishedDate = i * 1000L)
            }
        val current = episodes.maxByOrNull { it.publishedDate }!! // ep-5 (latest)
        val podcast = testPodcast(type = "episodic", preferredSort = "newest")

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = current,
                podcast = podcast,
            )

        assertTrue(candidates.isEmpty(), "Banner must NOT be shown when user is on latest release")
    }

    @Test
    fun `computeCandidates caps at MAX_CONTINUATION_OFFER`() {
        val episodes =
            (1..10).map { i ->
                testEpisode("ep-$i", publishedDate = i * 1000L)
            }
        val current = episodes.first()
        val podcast = testPodcast(type = "serial")

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = current,
                podcast = podcast,
            )

        assertEquals(SameShowContinuationState.MAX_CONTINUATION_OFFER, candidates.size)
        assertEquals(listOf("ep-2", "ep-3", "ep-4", "ep-5", "ep-6"), candidates.map { it.id })
    }

    @Test
    fun `computeCandidates excludes active queue items`() {
        val episodes =
            (1..6).map { i ->
                testEpisode("ep-$i", publishedDate = i * 1000L)
            }
        val current = episodes[0] // ep-1
        val podcast = testPodcast(type = "serial")

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = current,
                podcast = podcast,
                excludeEpisodeIds = setOf("ep-2", "ep-4"),
            )

        assertEquals(listOf("ep-3", "ep-5", "ep-6"), candidates.map { it.id })
    }

    @Test
    fun `computeCandidates filters out trailers and blank audio URLs`() {
        val ep1 = testEpisode("ep-1", publishedDate = 1000L)
        val ep2 = testEpisode("ep-2", publishedDate = 2000L, episodeType = "trailer")
        val ep3 = testEpisode("ep-3", publishedDate = 3000L, audioUrl = "   ")
        val ep4 = testEpisode("ep-4", publishedDate = 4000L, episodeType = "full")
        val episodes = listOf(ep1, ep2, ep3, ep4)

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = ep1,
                podcast = testPodcast(type = "serial"),
            )

        assertEquals(listOf("ep-4"), candidates.map { it.id })
    }

    @Test
    fun `computeCandidates deduplicates episodes with identical ids`() {
        val ep1 = testEpisode("ep-1", publishedDate = 1000L)
        val ep2a = testEpisode("ep-2", publishedDate = 2000L)
        val ep2b = testEpisode("ep-2", publishedDate = 2000L)
        val ep3 = testEpisode("ep-3", publishedDate = 3000L)
        val episodes = listOf(ep1, ep2a, ep2b, ep3)

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = ep1,
                podcast = testPodcast(type = "serial"),
            )

        assertEquals(listOf("ep-2", "ep-3"), candidates.map { it.id })
    }

    @Test
    fun `computeCandidates falls back to publishedDate when currentEpisode id is not in allEpisodes for serial show`() {
        val ep1 = testEpisode("ep-1-unknown-id", publishedDate = 1500L)
        val episodes =
            listOf(
                testEpisode("ep-0", publishedDate = 1000L),
                testEpisode("ep-2", publishedDate = 2000L),
                testEpisode("ep-3", publishedDate = 3000L),
            )

        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = episodes,
                currentEpisode = ep1,
                podcast = testPodcast(type = "serial"),
            )

        assertEquals(listOf("ep-2", "ep-3"), candidates.map { it.id })
    }
}
