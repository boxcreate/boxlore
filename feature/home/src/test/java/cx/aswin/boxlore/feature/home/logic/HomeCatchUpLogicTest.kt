package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.withInProgress
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import cx.aswin.boxlore.feature.home.toHomeListeningHistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeCatchUpLogicTest {
    @Test
    fun `resolve fresh episode uses serial map for oldest sort`() {
        val serial = TestFixtures.episode(id = "serial-ep")
        val latest = TestFixtures.episode(id = "latest-ep")
        val pod =
            TestFixtures.podcast(id = "pod-1").copy(
                preferredSort = "oldest",
                latestEpisode = latest,
            )

        assertEquals("serial-ep", HomeCatchUpLogic.resolveFreshEpisode(pod, mapOf("pod-1" to serial))?.id)
        assertEquals("latest-ep", HomeCatchUpLogic.resolveFreshEpisode(pod.copy(preferredSort = "newest"), mapOf("pod-1" to serial))?.id)
        assertNull(HomeCatchUpLogic.resolveFreshEpisode(pod.copy(latestEpisode = null), emptyMap()))
    }

    @Test
    fun `build catch up buckets splits unplayed in progress and completed`() {
        val unplayedEp = TestFixtures.episode(id = "ep-u", podcastId = "u")
        val progressEp = TestFixtures.episode(id = "ep-p", podcastId = "p")
        val doneEp = TestFixtures.episode(id = "ep-d", podcastId = "d")
        val subs =
            listOf(
                TestFixtures.podcast(id = "u", title = "U").copy(latestEpisode = unplayedEp),
                TestFixtures.podcast(id = "p", title = "P").copy(latestEpisode = progressEp),
                TestFixtures.podcast(id = "d", title = "D").copy(latestEpisode = doneEp),
            )
        val history =
            listOf(
                history(episodeId = "ep-p", podcastId = "p", progressMs = 10_000L, durationMs = 100_000L, completed = false),
                history(episodeId = "ep-d", podcastId = "d", progressMs = 100_000L, durationMs = 100_000L, completed = true),
            )

        val buckets = HomeCatchUpLogic.buildCatchUpBuckets(subs, history, resolvedSerial = emptyMap())

        assertEquals(listOf("u"), buckets.unplayed.map { it.id })
        assertEquals(EpisodeStatus.UNPLAYED, buckets.unplayed.single().episodeStatus)
        assertEquals(listOf("p"), buckets.inProgress.map { it.first.id })
        assertEquals(
            0.1f,
            buckets.inProgress
                .single()
                .first.resumeProgress,
        )
        assertEquals(listOf("d"), buckets.completed.map { it.first.id })
        assertEquals(
            EpisodeStatus.COMPLETED,
            buckets.completed
                .single()
                .first.episodeStatus,
        )
    }

    @Test
    fun `a latest episode finished outside recent history still reads as finished`() {
        // Regression: after one Mark all of 300+ episodes no row of an episode finished earlier was
        // left in recent history, so Home offered it again as new.
        val finished = TestFixtures.episode(id = "ep-done", podcastId = "d")
        val fresh = TestFixtures.episode(id = "ep-new", podcastId = "u")
        val subs =
            listOf(
                TestFixtures.podcast(id = "d", title = "D").copy(latestEpisode = finished),
                TestFixtures.podcast(id = "u", title = "U").copy(latestEpisode = fresh),
            )

        val buckets = HomeCatchUpLogic.buildCatchUpBuckets(subs, emptyList(), resolvedSerial = emptyMap(), completedEpisodeIds = setOf("ep-done"))

        assertEquals(listOf("u"), buckets.unplayed.map { it.id })
        assertEquals(listOf("d"), buckets.completed.map { it.first.id })
        assertEquals(EpisodeStatus.COMPLETED, buckets.completed.single().first.episodeStatus)
    }

    @Test
    fun `a latest episode started behind recent history still reads as in progress`() {
        // Regression: recent history is bounded, so a started episode behind it read as new. Home
        // adds every started episode to its status history (recent history withInProgress).
        val started = TestFixtures.episode(id = "ep-started", podcastId = "s")
        val subs = listOf(TestFixtures.podcast(id = "s", title = "S").copy(latestEpisode = started))
        val row = ListeningHistoryEntity(
            episodeId = "ep-started",
            podcastId = "s",
            episodeTitle = "Started",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = null,
            podcastName = "S",
            progressMs = 900_000L,
            durationMs = 3_600_000L,
            isCompleted = false,
            lastPlayedAt = 5L,
        )
        val statusHistory = emptyList<ListeningHistoryEntity>().withInProgress(listOf(row)).map { it.toHomeListeningHistoryItem() }

        val buckets = HomeCatchUpLogic.buildCatchUpBuckets(subs, statusHistory, resolvedSerial = emptyMap())

        assertEquals(listOf("s"), buckets.inProgress.map { it.first.id })
        assertEquals(0.25f, buckets.inProgress.single().first.resumeProgress)
    }

    @Test
    fun `a switched show's latest episode played under its old id reads as played`() {
        // The feed show lists the episode as "-1"; history keeps it under the old show's ID "pi-1".
        val latest = TestFixtures.episode(id = "-1", podcastId = "rss:feed")
        val subs = listOf(TestFixtures.podcast(id = "rss:feed", title = "Feed").copy(latestEpisode = latest))
        val history = listOf(history(episodeId = "pi-1", podcastId = "pi-show", progressMs = 100_000L, durationMs = 100_000L, completed = true))
        val aliases = mapOf("pi-1" to setOf("pi-1", "-1"), "-1" to setOf("pi-1", "-1"))

        val lookup = HomeCatchUpLogic.underEveryId(history, aliases)
        val buckets = HomeCatchUpLogic.buildCatchUpBuckets(subs, lookup, resolvedSerial = emptyMap())

        assertEquals(listOf("pi-1", "-1"), lookup.map { it.episodeId })
        assertEquals(listOf("rss:feed"), buckets.completed.map { it.first.id })
        assertEquals(emptyList<String>(), buckets.unplayed.map { it.id })
        // Without aliases the list is the same list.
        assertEquals(history, HomeCatchUpLogic.underEveryId(history, emptyMap()))
    }

    private fun history(
        episodeId: String,
        podcastId: String,
        progressMs: Long,
        durationMs: Long,
        completed: Boolean,
    ) = HomeListeningHistoryItem(
        episodeId = episodeId,
        podcastId = podcastId,
        podcastName = "Show",
        podcastImageUrl = null,
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = completed,
        isLiked = false,
        lastPlayedAt = 1L,
    )

    @Test
    fun `an archived fresh episode is in no catch up bucket, played or not`() {
        val trailer = TestFixtures.episode(id = "ep-trailer", podcastId = "t")
        val started = TestFixtures.episode(id = "ep-started", podcastId = "s")
        val fresh = TestFixtures.episode(id = "ep-fresh", podcastId = "f")
        val subs =
            listOf(
                TestFixtures.podcast(id = "t", title = "T").copy(latestEpisode = trailer),
                TestFixtures.podcast(id = "s", title = "S").copy(latestEpisode = started),
                TestFixtures.podcast(id = "f", title = "F").copy(latestEpisode = fresh),
            )
        val history = listOf(history(episodeId = "ep-started", podcastId = "s", progressMs = 10_000L, durationMs = 100_000L, completed = false))

        val buckets = HomeCatchUpLogic.buildCatchUpBuckets(subs, history, resolvedSerial = emptyMap(), dismissedEpisodeIds = setOf("ep-trailer", "ep-started"))

        assertEquals(listOf("f"), buckets.unplayed.map { it.id })
        assertEquals(emptyList<String>(), buckets.inProgress.map { it.first.id })
        assertEquals(emptyList<String>(), buckets.completed.map { it.first.id })
    }
}
