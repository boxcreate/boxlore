package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaylistMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun show(name: String, vararg dates: Long) = dates.map { episode("$name$it", it) }

    private fun ids(episodes: List<Episode>) = episodes.map { it.id }

    private fun arrange(
        mix: PlaylistMix,
        shows: List<List<Episode>>,
        perShowLimit: Int? = null,
        windowStart: Long? = null,
        inProgress: List<Episode> = emptyList(),
        history: Map<String, ListeningHistoryEntity> = emptyMap(),
        excluded: Set<String> = emptySet(),
    ) = ids(PlaylistEpisodeSelection.arrange(mix, perShowLimit, windowStart, inProgress, shows, history, excluded))

    @Test
    fun `taking turns plays every show's first, then every show's second, skipping shows that ran out`() {
        val perShow = listOf(show("a", 3L, 2L, 1L), show("b", 9L), show("c", 8L, 7L))

        assertEquals(listOf("a3", "b9", "c8", "a2", "c7", "a1"), ids(PlaylistEpisodeSelection.takeTurns(perShow)))
        assertEquals(emptyList<String>(), ids(PlaylistEpisodeSelection.takeTurns(emptyList())))
    }

    @Test
    fun `taking turns starts each show at its newest, at most the limit per show, in playlist show order`() {
        // Stored in any order: each show is put newest first before the turns.
        val shows = listOf(show("a", 1L, 4L, 2L, 3L), show("b", 9L, 8L), show("c"))

        assertEquals(listOf("a4", "b9", "a3", "b8", "a2"), arrange(PlaylistMix.TAKE_TURNS, shows, perShowLimit = 3))
        assertEquals(listOf("a4", "b9"), arrange(PlaylistMix.TAKE_TURNS, shows, perShowLimit = 1))
        assertEquals(listOf("a4", "b9", "a3", "b8", "a2", "a1"), arrange(PlaylistMix.TAKE_TURNS, shows, perShowLimit = null))
        assertEquals("0 is no limit", listOf("a4", "b9", "a3", "b8", "a2", "a1"), arrange(PlaylistMix.TAKE_TURNS, shows, perShowLimit = 0))
    }

    @Test
    fun `taking turns reaches back only to the window start, and a show with nothing new sits out`() {
        val day = 86_400L
        val nowMs = 100 * day * 1_000L
        val start = PlaylistEpisodeSelection.windowStart(PlaylistMix.TAKE_TURNS, nowMs)!!
        assertEquals(70 * day, start)
        val shows = listOf(show("a", 99 * day, 71 * day, 70 * day, 69 * day), show("b", 40 * day), show("c", 98 * day))

        assertEquals(
            listOf("a${99 * day}", "c${98 * day}", "a${71 * day}", "a${70 * day}"),
            arrange(PlaylistMix.TAKE_TURNS, shows, perShowLimit = 5, windowStart = start),
        )
    }

    @Test
    fun `only taking turns has a window`() {
        assertNull(PlaylistEpisodeSelection.windowStart(PlaylistMix.NEWEST_FIRST, 10_000_000_000L))
        assertNull(PlaylistEpisodeSelection.windowStart(PlaylistMix.ONE_SHOW_AT_A_TIME, 10_000_000_000L))
    }

    @Test
    fun `newest first merges every show by release date, each capped at its newest`() {
        val shows = listOf(show("a", 10L, 30L, 50L, 70L), show("b", 20L, 40L), show("c", 60L))

        assertEquals(listOf("a70", "c60", "a50", "b40", "a30", "b20", "a10"), arrange(PlaylistMix.NEWEST_FIRST, shows))
        assertEquals(listOf("a70", "c60", "a50", "b40", "b20"), arrange(PlaylistMix.NEWEST_FIRST, shows, perShowLimit = 2))
    }

    @Test
    fun `newest first breaks a tie on release date by episode ID, highest first, as the pages do`() {
        val shows = listOf(listOf(episode("a1", 5L)), listOf(episode("b1", 5L)), listOf(episode("a2", 5L)))

        assertEquals(listOf("b1", "a2", "a1"), arrange(PlaylistMix.NEWEST_FIRST, shows))
    }

    @Test
    fun `one show at a time plays each show oldest first, show after show`() {
        val shows = listOf(show("a", 50L, 10L, 30L), show("b", 40L, 20L), show("c", 5L))

        assertEquals(listOf("a10", "a30", "a50", "b20", "b40", "c5"), arrange(PlaylistMix.ONE_SHOW_AT_A_TIME, shows))
        assertEquals(listOf("a10", "a30", "b20", "b40", "c5"), arrange(PlaylistMix.ONE_SHOW_AT_A_TIME, shows, perShowLimit = 2))
    }

    @Test
    fun `episodes midway through come first in every mix and aren't picked again from their show`() {
        val shows = listOf(show("a", 3L, 2L, 1L), show("b", 9L))
        val inProgress = listOf(episode("a2", 2L), episode("old", 0L))

        assertEquals(listOf("a2", "old", "a3", "b9", "a1"), arrange(PlaylistMix.TAKE_TURNS, shows, perShowLimit = 2, inProgress = inProgress))
        assertEquals(listOf("a2", "old", "b9", "a3", "a1"), arrange(PlaylistMix.NEWEST_FIRST, shows, perShowLimit = 2, inProgress = inProgress))
        assertEquals(listOf("a2", "old", "a1", "a3", "b9"), arrange(PlaylistMix.ONE_SHOW_AT_A_TIME, shows, perShowLimit = 2, inProgress = inProgress))
    }

    @Test
    fun `episodes that don't play are skipped in every mix and leave room under the limit`() {
        val shows = listOf(
            listOf(
                episode("done", 9L),
                episode("queued", 8L),
                episode("archived", 7L),
                episode("trailer", 6L, type = "trailer", duration = 60),
                episode("silent", 5L, audio = ""),
                episode("a4", 4L),
                episode("a3", 3L),
            ),
            show("b", 2L),
        )
        val history = mapOf(
            "done" to finished("done"),
            "archived" to finished("archived").copy(isCompleted = false, dismissedAt = 20L),
        )

        val expected = mapOf(
            PlaylistMix.TAKE_TURNS to listOf("a4", "b2"),
            PlaylistMix.NEWEST_FIRST to listOf("a4", "b2"),
            PlaylistMix.ONE_SHOW_AT_A_TIME to listOf("a3", "b2"),
        )
        for ((mix, ids) in expected) {
            assertEquals(mix.name, ids, arrange(mix, shows, perShowLimit = 1, history = history, excluded = setOf("queued")))
        }
    }
}
