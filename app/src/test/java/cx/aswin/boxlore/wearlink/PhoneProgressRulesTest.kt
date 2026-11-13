package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlaybackProgressNow
import cx.aswin.boxlore.core.wearlink.WearLinkEpisode
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneProgressRulesTest {
    private val episode = WearLinkEpisode("e", "1", "E", durationMs = 0L)

    @Test
    fun `the player's own position and length are sent, not the state's stale ones`() {
        // With the screen off the state still says 6 minutes although the player is at 36.
        val live = PlaybackProgressNow("e", positionMs = 2_160_000L, durationMs = 6_240_000L)

        val state = PhoneProgressRules.state(episode, isPlaying = false, statePositionMs = 360_000L, speed = 1f, live = live)

        assertEquals(2_160_000L, state.positionMs)
        assertEquals(6_240_000L, state.episode?.durationMs)
    }

    @Test
    fun `an episode the mini player shows but the player has not loaded keeps its own numbers`() {
        val shown = episode.copy(durationMs = 2_400_000L)
        val live = PlaybackProgressNow("another", positionMs = 4_968_000L, durationMs = 6_240_000L)

        val state = PhoneProgressRules.state(shown, isPlaying = false, statePositionMs = 340_000L, speed = 1f, live = live)

        assertEquals(340_000L, state.positionMs)
        assertEquals(2_400_000L, state.episode?.durationMs)
    }

    @Test
    fun `a length the player does not know yet leaves the feed's length in place`() {
        val shown = episode.copy(durationMs = 2_400_000L)
        val live = PlaybackProgressNow("e", positionMs = 5_000L, durationMs = 0L)

        val state = PhoneProgressRules.state(shown, isPlaying = true, statePositionMs = 0L, speed = 1.5f, live = live)

        assertEquals(5_000L, state.positionMs)
        assertEquals(2_400_000L, state.episode?.durationMs)
        assertEquals(1.5f, state.speed)
    }

    private fun queued(id: String) = Episode(id = id, title = id, description = "", audioUrl = "https://example.com/$id.mp3")

    @Test
    fun `up next is what the queue holds after the current episode`() {
        val queue = listOf(queued("a"), queued("b"), queued("c"))

        assertEquals(listOf("b", "c"), PhoneProgressRules.upNext(queue, currentId = "a").map { it.id })
        assertEquals(emptyList<String>(), PhoneProgressRules.upNext(queue, currentId = "c").map { it.id })
    }

    @Test
    fun `a queue that does not hold the current episode all comes after it`() {
        val queue = listOf(queued("a"), queued("b"))

        assertEquals(listOf("a", "b"), PhoneProgressRules.upNext(queue, currentId = "handed-over").map { it.id })
        assertEquals(listOf("a", "b"), PhoneProgressRules.upNext(queue, currentId = null).map { it.id })
    }

    @Test
    fun `without a loaded player the state is passed on as it is`() {
        val state = PhoneProgressRules.state(episode, isPlaying = false, statePositionMs = 12_000L, speed = 1f, live = null)

        assertEquals(12_000L, state.positionMs)
        assertEquals(episode, state.episode)
    }
}
