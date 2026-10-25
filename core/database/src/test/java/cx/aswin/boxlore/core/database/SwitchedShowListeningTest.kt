package cx.aswin.boxlore.core.database

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.listenerShowIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Per-show reads of a show the listener switched from its Podcast Index listing to its RSS feed
 * (docs/EPISODE_IDS.md §5): the switch keeps every row under the Podcast Index show, so the RSS
 * show counts those rows as its own, and an episode is found under any of its stored IDs.
 */
class SwitchedShowListeningTest {
    private val nowMs = 1_700_000_000_000L

    @Test
    fun aSwitchedRssShowReadsTheRowsOfThePodcastIndexShowItReplaced() {
        assertEquals(setOf(RSS, PI), rssShow().listenerShowIds)
        assertEquals(setOf(RSS, PI), rssEntity().listenerShowIds)
        // A Podcast Index show, and an RSS show that replaced nothing, read their own rows only.
        assertEquals(setOf(PI), Podcast(id = PI, title = "", artist = "", imageUrl = "", linkedPodcastIndexId = "1").listenerShowIds)
        assertEquals(setOf(RSS), rssShow().copy(linkedPodcastIndexId = null).listenerShowIds)
        assertEquals(setOf(RSS), rssShow().copy(linkedPodcastIndexId = " ").listenerShowIds)
    }

    @Test
    fun aSwitchedShowsScoreCountsItsOldShowsListeningOnce() {
        val history = listOf(row("1", PI, playedAt = nowMs - 1_000L, liked = true), row("2", PI, playedAt = nowMs - 2_000L), row("3", RSS, playedAt = nowMs))
        val switched = rssEntity().toScorable()
        val unlinked = switched.copy(listenerShowIds = setOf(RSS))

        val scores = PodcastScoring.calculateScores(listOf(switched), history, includeSubscriptionRecency = false, nowMs = nowMs)
        val ownOnly = PodcastScoring.calculateScores(listOf(unlinked), history, includeSubscriptionRecency = false, nowMs = nowMs)

        // Three plays and a like instead of one play.
        assertEquals(ownOnly.getValue(RSS) + 2 * 12.0 + 25.0, scores.getValue(RSS), 1e-9)
        // When the listener follows the Podcast Index show as well, its rows count only for it.
        val both = PodcastScoring.calculateScores(listOf(switched, scorable(PI)), history, includeSubscriptionRecency = false, nowMs = nowMs)
        assertEquals(ownOnly.getValue(RSS), both.getValue(RSS), 1e-9)
    }

    @Test
    fun mostListenedCountsTheOldShowsRowsForTheSwitchedShow() {
        val other = Podcast(id = "555", title = "", artist = "", imageUrl = "")
        val history = listOf(row("1", PI), row("2", PI), row("3", "555"), row("4", "555"))
        // Two rows each, but the switched show also has one of its own.
        val sorted = listOf(other, rssShow()).sortedByListening(history + row("5", RSS))
        assertEquals(listOf(RSS, "555"), sorted.map { it.id })
        // Ties keep their order.
        assertEquals(listOf("555", RSS), listOf(other, rssShow()).sortedByListening(history).map { it.id })
    }

    @Test
    fun anIdsOtherStoredIdsComeFromTheSharedAliasesAlone() {
        val shared = listOf(alias("A", "k1"), alias("B", "k1"), alias("C", "k2"), alias("D", "k2"))

        assertEquals(setOf("A", "B", "X"), EpisodeListenerProjection.withSharedAliases(setOf("A", "X"), shared))
        assertEquals(setOf("C", "D", "B", "A"), EpisodeListenerProjection.withSharedAliases(setOf("C", "B"), shared))
        assertEquals(setOf("X"), EpisodeListenerProjection.withSharedAliases(setOf("X"), shared))
        assertTrue(EpisodeListenerProjection.withSharedAliases(emptySet(), shared).isEmpty())
        // The same key of another show is another episode.
        assertEquals(setOf("A", "B"), EpisodeListenerProjection.withSharedAliases(setOf("A"), shared + alias("E", "k1").copy(podcastId = "other") + alias("F", "k1").copy(podcastId = "other")))
    }

    private fun rssShow() = Podcast(id = RSS, title = "Feed", artist = "", imageUrl = "", sourceType = Podcast.SOURCE_RSS, linkedPodcastIndexId = PI)

    private fun rssEntity() = PodcastEntity(
        podcastId = RSS,
        title = "Feed",
        author = "",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        subscribedAt = nowMs - 10_000L,
        sourceType = PodcastEntity.SOURCE_RSS,
        linkedPodcastIndexId = PI,
        latestEpisode = Episode(id = "3", title = "Latest", description = "", audioUrl = "", publishedDate = 0L),
    )

    private fun scorable(id: String) = ScorablePodcast(id, subscribedAt = 0L, latestEpisode = null, notificationsEnabled = false, autoDownloadEnabled = false)

    private fun alias(id: String, key: String) = EpisodeAliasEntity(id, PI, key, null, EpisodeAliasState.TRUSTED, false, EpisodeAliasProvenance.UPGRADE_CATALOG, 1L)

    private fun row(id: String, show: String, playedAt: Long = nowMs, liked: Boolean = false) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = show,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show",
        progressMs = 1_000L,
        durationMs = 10_000L,
        isCompleted = false,
        isLiked = liked,
        lastPlayedAt = playedAt,
    )

    private companion object {
        const val PI = "920666"
        const val RSS = "rss:feed"
    }
}
