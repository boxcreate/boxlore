package cx.aswin.boxlore.core.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastScoringTest {
    private val nowMs = 1_700_000_000_000L

    @Test
    fun `just subscribed peak recency outranks an idle older sub`() {
        val scores =
            PodcastScoring.calculateScores(
                podcasts =
                listOf(
                    scorable("fresh", nowMs),
                    scorable("idle", nowMs - 200L * 24 * 3_600_000L),
                ),
                allHistory = emptyList(),
                nowMs = nowMs,
            )
        assertEquals(PodcastScoring.SUBSCRIBE_RECENCY_PEAK, scores.getValue("fresh"), 1e-9)
        assertTrue(scores.getValue("fresh") > scores.getValue("idle"))
    }

    @Test
    fun `recency is still material three days after subscribe`() {
        val scores =
            PodcastScoring.calculateScores(
                podcasts = listOf(scorable("recent", nowMs - 72L * 3_600_000L)),
                allHistory = emptyList(),
                nowMs = nowMs,
            )
        assertEquals(150.0, scores.getValue("recent"), 1e-9)
    }

    @Test
    fun `missing subscribedAt adds no recency`() {
        val scores =
            PodcastScoring.calculateScores(
                podcasts = listOf(scorable("none", 0L)),
                allHistory = emptyList(),
                nowMs = nowMs,
            )
        assertEquals(0.0, scores.getValue("none"), 1e-9)
    }

    @Test
    fun `caller can exclude subscription recency without losing listening signals`() {
        val scores =
            PodcastScoring.calculateScores(
                podcasts = listOf(scorable("fresh", nowMs)),
                allHistory =
                listOf(
                    ListeningHistoryEntity(
                        episodeId = "episode",
                        podcastId = "fresh",
                        episodeTitle = "Episode",
                        episodeImageUrl = null,
                        podcastImageUrl = null,
                        episodeAudioUrl = null,
                        podcastName = "Fresh",
                        progressMs = 1_000L,
                        durationMs = 10_000L,
                        isCompleted = false,
                        lastPlayedAt = nowMs,
                    ),
                ),
                includeSubscriptionRecency = false,
                nowMs = nowMs,
            )

        assertEquals(262.0, scores.getValue("fresh"), 1e-9)
    }

    @Test
    fun `a show marked played all at once ranks below the shows really listened to`() {
        // Regression: 300 rows of one Mark all as played scored 12 points each as plays.
        val listened = listOf("daily", "weekly", "news").flatMap { show ->
            (0 until 7).map { row("$show-$it", show, nowMs - (it + 1) * 3_600_000L, progressMs = 600_000L) }
        }
        val marked = (0 until 300).map { row("old-$it", "back-catalogue", nowMs, completed = true).copy(isBulkCompletion = true) }
        val oneMarked = row("manual", "back-catalogue", nowMs, completed = true).copy(isManualCompletion = true)
        val shows = listOf("back-catalogue", "daily", "weekly", "news").map { scorable(it, 0L) }

        val scores = PodcastScoring.calculateScores(podcasts = shows, allHistory = marked + oneMarked + listened, nowMs = nowMs)

        assertEquals(0.0, scores.getValue("back-catalogue"), 1e-9)
        listOf("daily", "weekly", "news").forEach { assertTrue(scores.getValue(it) > scores.getValue("back-catalogue")) }
        val mostListened = shows.map { cx.aswin.boxlore.core.model.Podcast(id = it.id, title = it.id, artist = "", imageUrl = "") }
            .sortedByListening(marked + oneMarked + listened)
        assertEquals("back-catalogue", mostListened.last().id)
    }

    @Test
    fun `a latest episode marked played gets no freshness boost`() {
        val latest = cx.aswin.boxlore.core.model.Episode(id = "new", title = "New", description = "", audioUrl = "", publishedDate = nowMs / 1000L)
        val show = scorable("show", 1L).copy(latestEpisode = latest)

        val unplayed = PodcastScoring.calculateScores(listOf(show), emptyList(), includeSubscriptionRecency = false, nowMs = nowMs)
        val marked = PodcastScoring.calculateScores(
            listOf(show),
            listOf(row("new", "show", nowMs, completed = true).copy(isBulkCompletion = true)),
            includeSubscriptionRecency = false,
            nowMs = nowMs,
        )

        assertTrue(unplayed.getValue("show") > 0.0)
        assertEquals(0.0, marked.getValue("show"), 1e-9)
    }

    private fun row(id: String, show: String, playedAt: Long, progressMs: Long = 0L, completed: Boolean = false,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = show,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = show,
        progressMs = progressMs,
        durationMs = 3_600_000L,
        isCompleted = completed,
        lastPlayedAt = playedAt,
    )

    private fun scorable(id: String, subscribedAt: Long,) = ScorablePodcast(
        id = id,
        subscribedAt = subscribedAt,
        latestEpisode = null,
        notificationsEnabled = false,
        autoDownloadEnabled = false,
    )
}
