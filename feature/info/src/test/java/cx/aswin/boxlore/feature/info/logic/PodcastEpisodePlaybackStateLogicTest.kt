package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.InfoListeningProgressItem
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel.EpisodePlaybackState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PodcastEpisodePlaybackStateLogicTest {
    private fun history(
        id: String,
        progressMs: Long,
        durationMs: Long = 3_600_000L,
        completed: Boolean = false,
    ) = InfoListeningProgressItem(episodeId = id, progressMs = progressMs, durationMs = durationMs, isCompleted = completed)

    @Test
    fun `started episodes show their progress to resume and time left`() {
        val states =
            PodcastEpisodePlaybackStateLogic.episodeStates(
                player = PlayerState(),
                history = listOf(history("e1", progressMs = 900_000L), history("e2", progressMs = 3_300_000L)),
                loadedPodcastId = "p1",
            )

        assertEquals(EpisodePlaybackState(isPlaying = false, isResume = true, progress = 0.25f, timeLeft = "45m left"), states["e1"])
        assertEquals("5m left", states["e2"]?.timeLeft)
    }

    @Test
    fun `finished, unstarted and unknown-length episodes have no entry`() {
        val states =
            PodcastEpisodePlaybackStateLogic.episodeStates(
                player = PlayerState(),
                history =
                listOf(
                    history("done", progressMs = 1_000L, completed = true),
                    history("unstarted", progressMs = 0L),
                    history("no-length", progressMs = 1_000L, durationMs = 0L),
                ),
                loadedPodcastId = "p1",
            )

        assertEquals(emptyMap<String, EpisodePlaybackState>(), states)
    }

    @Test
    fun `the episode playing replaces its history entry only when it is this show's`() {
        val playing = TestFixtures.episode(id = "e1", podcastId = "p1")
        val player = PlayerState(isPlaying = true, duration = 4_000_000L, position = 1_000_000L, currentEpisode = playing)
        val history = listOf(history("e1", progressMs = 900_000L))

        val ownShow = PodcastEpisodePlaybackStateLogic.episodeStates(player, history, loadedPodcastId = "p1")
        assertEquals(EpisodePlaybackState(isPlaying = true, isResume = true, progress = 0.25f, timeLeft = "50m left"), ownShow["e1"])

        val otherShow = PodcastEpisodePlaybackStateLogic.episodeStates(player, history, loadedPodcastId = "p2")
        assertEquals(EpisodePlaybackState(isPlaying = false, isResume = true, progress = 0.25f, timeLeft = "45m left"), otherShow["e1"])
    }

    @Test
    fun `the player's show counts when the episode carries another show id`() {
        val playing = TestFixtures.episode(id = "e9", podcastId = "rss:feed")
        val player = PlayerState(isPlaying = false, duration = 0L, currentEpisode = playing, currentPodcast = TestFixtures.podcast(id = "p1"))

        val states = PodcastEpisodePlaybackStateLogic.episodeStates(player, emptyList(), loadedPodcastId = "p1")

        assertEquals(EpisodePlaybackState(isPlaying = false, isResume = true, progress = 0f, timeLeft = null), states["e9"])
    }

    @Test
    fun `time left reads in hours and minutes, and nothing once none is left`() {
        assertEquals("1h 5m left", PodcastEpisodePlaybackStateLogic.timeLeftLabel(3_900L))
        assertEquals("2h 0m left", PodcastEpisodePlaybackStateLogic.timeLeftLabel(7_200L))
        assertEquals("0m left", PodcastEpisodePlaybackStateLogic.timeLeftLabel(59L))
        assertNull(PodcastEpisodePlaybackStateLogic.timeLeftLabel(0L))
        assertNull(PodcastEpisodePlaybackStateLogic.timeLeftLabel(-5L))
    }
}
