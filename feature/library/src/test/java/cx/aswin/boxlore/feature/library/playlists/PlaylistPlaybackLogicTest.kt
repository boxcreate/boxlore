package cx.aswin.boxlore.feature.library.playlists

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaylistEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaylistPlaybackLogicTest {
    private fun row(item: Long, id: String = "e$item") = PlaylistEpisode(item, "p", item.toInt(), 0L, Episode(id, "Episode $id", "", "https://cdn.example/$id.mp3"))

    private val rows = listOf(row(1), row(2), row(3), row(4))

    private fun ids(episodes: List<Episode>) = episodes.map { it.id }

    @Test
    fun `play starts at the first unplayed episode and skips played ones after it`() {
        assertEquals(listOf("e2", "e4"), ids(PlaylistPlaybackLogic.playbackEpisodes(rows, setOf("e1", "e3"), limit = 50)))
    }

    @Test
    fun `a tapped episode plays even when played, then the unplayed ones after it`() {
        assertEquals(listOf("e3", "e4"), ids(PlaylistPlaybackLogic.playbackEpisodes(rows, setOf("e3"), startItemId = 3, limit = 50)))
    }

    @Test
    fun `when everything is played the whole playlist replays from the top`() {
        assertEquals(listOf("e1", "e2", "e3", "e4"), ids(PlaylistPlaybackLogic.playbackEpisodes(rows, rows.map { it.episode.id }.toSet(), limit = 50)))
    }

    @Test
    fun `a tapped played episode with only played ones after it replays from there`() {
        assertEquals(listOf("e3", "e4"), ids(PlaylistPlaybackLogic.playbackEpisodes(rows, setOf("e3", "e4"), startItemId = 3, limit = 50)))
    }

    @Test
    fun `a tapped played episode still skips played ones when unplayed ones follow`() {
        val five = rows + row(5)
        assertEquals(listOf("e2", "e5"), ids(PlaylistPlaybackLogic.playbackEpisodes(five, setOf("e2", "e3", "e4"), startItemId = 2, limit = 50)))
    }

    @Test
    fun `the queue copy is capped and holds each episode once`() {
        val twice = rows + row(5, "e1")
        assertEquals(listOf("e1", "e2"), ids(PlaylistPlaybackLogic.playbackEpisodes(twice, emptySet(), limit = 2)))
        assertEquals(listOf("e1", "e2", "e3", "e4"), ids(PlaylistPlaybackLogic.playbackEpisodes(twice, emptySet(), limit = 50)))
        assertEquals(emptyList<String>(), ids(PlaylistPlaybackLogic.playbackEpisodes(emptyList(), emptySet(), limit = 50)))
    }

    @Test
    fun `a pending order wins, and rows it doesn't name keep their order after it`() {
        assertEquals(listOf(3L, 1L, 2L, 4L), PlaylistPlaybackLogic.applyOrder(rows, listOf(3L, 1L)).map { it.itemId })
        assertEquals(rows, PlaylistPlaybackLogic.applyOrder(rows, null))
    }

    @Test
    fun `move and nudge`() {
        val order = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(2L, 3L, 1L, 4L), PlaylistPlaybackLogic.move(order, 1L, 3L))
        assertEquals(listOf(1L, 4L, 2L, 3L), PlaylistPlaybackLogic.move(order, 4L, 2L))
        assertEquals(order, PlaylistPlaybackLogic.move(order, 9L, 2L))
        assertEquals(listOf(2L, 1L, 3L, 4L), PlaylistPlaybackLogic.nudge(order, 1, -1))
        assertEquals(order, PlaylistPlaybackLogic.nudge(order, 3, 1))
    }
}
