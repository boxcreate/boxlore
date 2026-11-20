package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistRepositorySelectionTest {
    private fun episode(id: String, date: Long, audio: String = "https://cdn.example/$id.mp3", type: String? = null, duration: Int = 1_800) =
        Episode(id = id, title = id, description = "", audioUrl = audio, publishedDate = date, episodeType = type, duration = duration)

    private fun finished(id: String) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "show",
        episodeTitle = id,
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show",
        progressMs = 0L,
        durationMs = 0L,
        isCompleted = true,
        lastPlayedAt = 1L,
    )

    @Test
    fun `episodes midway through come first as given (most recently played first), then the rest`() {
        val inProgress = listOf(episode("old", 1L), episode("new", 9L))
        val released = listOf(episode("e5", 5L), episode("new", 9L), episode("e4", 4L))

        assertEquals(
            listOf("old", "new", "e5", "e4"),
            PlaylistEpisodeSelection.select(inProgress, released, emptyMap(), emptySet(), 10).map { it.id },
        )
        assertEquals(listOf("old", "new", "e5"), PlaylistEpisodeSelection.select(inProgress, released, emptyMap(), emptySet(), 3).map { it.id })
    }

    @Test
    fun `finished, excluded, trailers and episodes without audio never play`() {
        val released = listOf(
            episode("done", 6L),
            episode("queued", 5L),
            episode("trailer", 4L, type = "trailer", duration = 60),
            episode("long-trailer", 3L, type = "Trailer", duration = 3_600),
            episode("silent", 2L, audio = ""),
            episode("ok", 1L),
        )

        val picked = PlaylistEpisodeSelection.select(emptyList(), released, mapOf("done" to finished("done")), setOf("queued"), 10)

        assertEquals(listOf("long-trailer", "ok"), picked.map { it.id })
    }

    @Test
    fun `archived episodes never play though they are unplayed, started or not`() {
        val inProgress = listOf(episode("started-archived", 9L), episode("started", 8L))
        val released = listOf(episode("trailer-archived", 6L), episode("ok", 5L))
        val history = mapOf(
            "started-archived" to finished("started-archived").copy(isCompleted = false, progressMs = 60_000L, dismissedAt = 20L),
            "trailer-archived" to finished("trailer-archived").copy(isCompleted = false, dismissedAt = 21L),
            // Brought back from the archive: plays again.
            "ok" to finished("ok").copy(isCompleted = false, dismissedAt = -22L),
        )

        val picked = PlaylistEpisodeSelection.select(inProgress, released, history, emptySet(), 10)

        assertEquals(listOf("started", "ok"), picked.map { it.id })
    }
}
