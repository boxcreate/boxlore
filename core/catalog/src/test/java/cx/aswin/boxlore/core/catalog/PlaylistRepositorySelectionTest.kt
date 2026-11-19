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
}
