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
    fun `order reads its stored value and defaults to newest`() {
        assertEquals(PlaylistOrder.OLDEST, PlaylistOrder.fromStored("oldest"))
        assertEquals(PlaylistOrder.NEWEST, PlaylistOrder.fromStored("newest"))
        assertEquals(PlaylistOrder.NEWEST, PlaylistOrder.fromStored(null))
        assertEquals(PlaylistOrder.NEWEST, PlaylistOrder.fromStored("sideways"))
    }

    @Test
    fun `a show plays only while followed and not hidden`() {
        val show = PlaylistShow("p", "s", 0, 0L, "Show", null, isFollowed = true)
        assertTrue(show.isPlayable)
        assertFalse(show.copy(isFollowed = false).isPlayable)
        assertFalse(show.copy(isHidden = true).isPlayable)
    }
}
