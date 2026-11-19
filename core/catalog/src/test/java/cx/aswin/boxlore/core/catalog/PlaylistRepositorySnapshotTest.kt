package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Transcript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The episode copy a [PlaylistRepository] row keeps: the queue's fields and JSON shapes. */
class PlaylistRepositorySnapshotTest {
    private val episode = Episode(
        id = "rss:42",
        title = "Pilot",
        description = "",
        audioUrl = "https://cdn.example/42.mp3",
        podcastId = "rss:show",
        podcastTitle = "Show",
        persons = listOf(Person(name = "Host", role = "host", img = "https://img.example/h.jpg")),
        transcripts = listOf(Transcript(url = "https://t.example/42.vtt", type = "text/vtt")),
        seasonNumber = 1,
        episodeNumber = 2,
        episodeType = "full",
    )

    @Test
    fun `people and transcripts are stored as the queue stores them`() {
        val row = PlaylistEpisodeSnapshot.toEntity("p", episode, addedAt = 9L, position = 3)
        assertEquals("""[{"name":"Host","role":"host","img":"https://img.example/h.jpg"}]""", row.personsJson)
        assertEquals("""[{"url":"https://t.example/42.vtt","type":"text/vtt"}]""", row.transcriptsJson)
        assertNull(row.description)
        assertEquals(3, row.position)
        assertEquals(episode, PlaylistEpisodeSnapshot.toEpisode(row))
    }

    @Test
    fun `a row written by the queue's encoder reads back, and a broken one reads as none`() {
        val row = PlaylistEpisodeSnapshot.toEntity("p", episode, addedAt = 0L).copy(
            personsJson = """[{"name":"Guest","role":"guest","href":"https://x.example"}]""",
            transcriptsJson = "not json",
        )
        val read = PlaylistEpisodeSnapshot.toEpisode(row)
        assertEquals(listOf(Person(name = "Guest", role = "guest", href = "https://x.example")), read.persons)
        assertNull(read.transcripts)
    }
}
