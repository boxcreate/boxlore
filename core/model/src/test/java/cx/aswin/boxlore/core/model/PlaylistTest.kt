package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistTest {
    @Test
    fun `names are trimmed, folded and capped`() {
        assertEquals("Road trip", normalizePlaylistName("  Road \n  trip  "))
        assertEquals("", normalizePlaylistName("   "))
        assertEquals(PLAYLIST_NAME_MAX_LENGTH, normalizePlaylistName("a".repeat(200)).length)
    }

    @Test
    fun `mix reads its stored value and defaults to taking turns`() {
        assertEquals(PlaylistMix.TAKE_TURNS, PlaylistMix.fromStored("take_turns"))
        assertEquals(PlaylistMix.NEWEST_FIRST, PlaylistMix.fromStored("newest_first"))
        assertEquals(PlaylistMix.ONE_SHOW_AT_A_TIME, PlaylistMix.fromStored("one_show"))
        assertEquals(PlaylistMix.TAKE_TURNS, PlaylistMix.fromStored(null))
        assertEquals(PlaylistMix.TAKE_TURNS, PlaylistMix.fromStored("oldest"))
    }

    @Test
    fun `stored mix values never change`() {
        assertEquals(listOf("take_turns", "newest_first", "one_show"), PlaylistMix.entries.map { it.stored })
    }

    @Test
    fun `a new playlist takes turns, three episodes per show, with no icon`() {
        val playlist = Playlist("p", "Mix", 0L, 0L, showCount = 0)
        assertEquals(PlaylistMix.TAKE_TURNS, playlist.mix)
        assertEquals(3, playlist.perShowLimit)
        assertNull(playlist.iconKey)
        assertEquals(listOf(1, 2, 3, 5, null), PLAYLIST_PER_SHOW_CHOICES)
        assertTrue(PLAYLIST_DEFAULT_PER_SHOW_LIMIT in PLAYLIST_PER_SHOW_CHOICES)
    }

    @Test
    fun `a per-show limit of zero or less is no limit`() {
        assertEquals(2, normalizePlaylistPerShowLimit(2))
        assertNull(normalizePlaylistPerShowLimit(0))
        assertNull(normalizePlaylistPerShowLimit(-1))
        assertNull(normalizePlaylistPerShowLimit(null))
    }

    @Test
    fun `a show plays only while followed and not hidden`() {
        val show = PlaylistShow("p", "s", 0, 0L, "Show", null, isFollowed = true)
        assertTrue(show.isPlayable)
        assertFalse(show.copy(isFollowed = false).isPlayable)
        assertFalse(show.copy(isHidden = true).isPlayable)
    }
}
