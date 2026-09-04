package cx.aswin.boxlore.feature.library

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.subscriptions.groupLatestByDateHeader
import cx.aswin.boxlore.feature.library.subscriptions.scoreLatestIfNeeded
import cx.aswin.boxlore.feature.library.subscriptions.sortLatestDisplayPodcasts
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LatestEpisodesDisplayLogicTest {
    private fun podcast(
        id: String,
        publishedSeconds: Long,
    ): Podcast {
        val episode =
            Episode(
                id = "${id}_ep",
                title = "Latest",
                description = "",
                audioUrl = "https://example.com/a.mp3",
                imageUrl = null,
                publishedDate = publishedSeconds,
                duration = 600,
                podcastId = id,
            )
        return Podcast(
            id = id,
            title = id,
            artist = "Host",
            description = "",
            imageUrl = "",
            feedUrl = "https://example.com/feed",
            latestEpisode = episode,
        )
    }

    @Test
    fun scoreLatestIfNeeded_skipsWhenNotSmart() = runTest {
        var calls = 0
        val scores =
            scoreLatestIfNeeded(
                useSmartRank = false,
                podcasts = listOf(podcast("a", 10L)),
                history = emptyList(),
            ) { _, _ ->
                calls++
                mapOf("a_ep" to 1.0)
            }
        assertTrue(scores.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun scoreLatestIfNeeded_invokesWhenSmart() = runTest {
        val scores =
            scoreLatestIfNeeded(
                useSmartRank = true,
                podcasts = listOf(podcast("a", 10L)),
                history = emptyList(),
            ) { _, _ -> mapOf("a_ep" to 9.0) }
        assertEquals(mapOf("a_ep" to 9.0), scores)
    }

    @Test
    fun sortLatestDisplayPodcasts_smartUsesScores() {
        val older = podcast("old", 100L)
        val newer = podcast("new", 200L)
        val sorted =
            sortLatestDisplayPodcasts(
                podcasts = listOf(older, newer),
                useSmartRank = true,
                episodeScores = mapOf("old_ep" to 5.0, "new_ep" to 1.0),
            )
        assertEquals(listOf("old", "new"), sorted.map { it.id })
    }

    @Test
    fun sortLatestDisplayPodcasts_chronoUsesPublishedDate() {
        val older = podcast("old", 100L)
        val newer = podcast("new", 200L)
        val sorted =
            sortLatestDisplayPodcasts(
                podcasts = listOf(older, newer),
                useSmartRank = false,
                episodeScores = emptyMap(),
            )
        assertEquals(listOf("new", "old"), sorted.map { it.id })
    }

    @Test
    fun groupLatestByDateHeader_emptyWhenSmart() {
        val grouped =
            groupLatestByDateHeader(
                podcasts = listOf(podcast("a", System.currentTimeMillis() / 1000L)),
                useSmartRank = true,
            )
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun groupLatestByDateHeader_chronoGroupsToday() {
        val todaySeconds = System.currentTimeMillis() / 1000L
        val grouped =
            groupLatestByDateHeader(
                podcasts = listOf(podcast("a", todaySeconds), podcast("b", todaySeconds)),
                useSmartRank = false,
            )
        assertEquals(1, grouped.size)
        assertEquals(listOf("a", "b"), grouped.values.single().map { it.id })
    }
}
