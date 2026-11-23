package cx.aswin.boxlore.tv.playback

import cx.aswin.boxlore.tv.model.TvEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TvSmartQueueTest {
    private fun episode(id: String, show: String = "show", day: Long? = 1L, trailer: Boolean = false, audio: String? = "https://example.com/$id.mp3") =
        TvEpisode(id, show, "Episode $id", "Show $show", null, 30, releasedAtMillis = day?.times(DAY), audioUrl = audio, trailer = trailer)

    @Test
    fun `the same show carries on with the episode released next`() {
        val ended = episode("2", day = 2)
        val show = listOf(episode("4", day = 4), episode("1", day = 1), episode("3", day = 3), ended)
        assertEquals("3", TvSmartQueue.next(ended, show, listOf(episode("other", show = "b", day = 9)), played = emptySet())?.id)
    }

    @Test
    fun `played, trailers and silent episodes are skipped in the show`() {
        val ended = episode("1", day = 1)
        val show = listOf(episode("2", day = 2), episode("3", day = 3, trailer = true), episode("4", day = 4, audio = null), episode("5", day = 5))
        assertEquals("5", TvSmartQueue.next(ended, show, emptyList(), played = setOf("2"))?.id)
    }

    @Test
    fun `with nothing newer in the show another followed show plays`() {
        val ended = episode("9", day = 9)
        val followed = listOf(episode("9", day = 9), episode("x", show = "b", day = 8, trailer = true), episode("y", show = "c", day = 7))
        assertEquals("y", TvSmartQueue.next(ended, listOf(episode("1", day = 1), ended), followed, played = emptySet())?.id)
    }

    @Test
    fun `nothing plays when every candidate is used up`() {
        val ended = episode("1", day = null)
        assertNull(TvSmartQueue.next(ended, listOf(episode("2", day = 2)), listOf(episode("3", show = "b")), played = setOf("3")))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
