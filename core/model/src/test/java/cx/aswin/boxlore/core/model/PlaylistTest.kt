package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `a playlist is full at the limit`() {
        assertFalse(Playlist("p", "P", 0L, 0L, PLAYLIST_EPISODE_LIMIT - 1).isFull)
        assertTrue(Playlist("p", "P", 0L, 0L, PLAYLIST_EPISODE_LIMIT).isFull)
    }
}
