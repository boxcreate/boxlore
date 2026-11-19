package cx.aswin.boxlore.feature.library.logic

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.subscriptions.latestPlaybackEpisodes
import cx.aswin.boxlore.feature.library.subscriptions.visibleLatestPodcasts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Regression: Subscriptions and New Episodes read played state from the newest 300 history rows,
 * so after one "Mark all as played" of 300+ episodes, latest episodes finished earlier showed as
 * unplayed (Hide played brought them back, Play all queued them) and older started ones lost their
 * progress.
 */
class LibraryEpisodeStatusLogicTest {
    private val shows = listOf(show("finished-show", "finished"), show("started-show", "started"), show("new-show", "new"))

    @Test
    fun latestEpisodesTakeTheirStateFromEveryCompletedAndStartedEpisode() {
        // No row of the finished episode is in reach; only the completed IDs know it.
        val statuses = LibraryEpisodeStatusLogic.withLatestEpisodeStatus(
            podcasts = shows,
            inProgressById = mapOf("started" to row("started", progressMs = 900_000L)),
            completedIds = setOf("finished", "back-catalogue-1"),
        ).associateBy { it.id }

        assertEquals(EpisodeStatus.COMPLETED, statuses.getValue("finished-show").episodeStatus)
        assertEquals(1f, statuses.getValue("finished-show").resumeProgress)
        assertEquals(EpisodeStatus.IN_PROGRESS, statuses.getValue("started-show").episodeStatus)
        assertEquals(0.25f, statuses.getValue("started-show").resumeProgress)
        assertEquals(EpisodeStatus.UNPLAYED, statuses.getValue("new-show").episodeStatus)
        assertNull(statuses.getValue("new-show").resumeProgress)
    }

    @Test
    fun hidePlayedAndPlayAllLeaveOutAnEpisodeFinishedBeforeAMarkAll() {
        val statuses = LibraryEpisodeStatusLogic.withLatestEpisodeStatus(shows, emptyMap(), completedIds = setOf("finished"))

        assertEquals(listOf("started-show", "new-show"), visibleLatestPodcasts(statuses, hideCompleted = true, pendingPlayedIds = emptySet()).map { it.id })
        assertEquals(listOf("started", "new"), latestPlaybackEpisodes(statuses).map { it.id })
    }

    @Test
    fun aShowWithoutALatestEpisodeIsLeftAsItIs() {
        val bare = Podcast(id = "bare", title = "Bare", artist = "", imageUrl = "")

        assertEquals(listOf(bare), LibraryEpisodeStatusLogic.withLatestEpisodeStatus(listOf(bare), emptyMap(), setOf("x")))
    }

    private fun show(id: String, latest: String) = Podcast(
        id = id,
        title = id,
        artist = "",
        imageUrl = "",
        latestEpisode = Episode(id = latest, title = latest, description = "", audioUrl = "https://example.com/$latest.mp3"),
    )

    private fun row(id: String, progressMs: Long) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "show",
        episodeTitle = id,
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show",
        progressMs = progressMs,
        durationMs = 3_600_000L,
        isCompleted = false,
        lastPlayedAt = 1L,
    )
}
