package cx.aswin.boxlore.core.designsystem.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A playlist cover is always filled: two or three covers leave no empty quarter or half-shown placeholder. */
class PlaylistCoverTilesTest {
    private fun area(tiles: List<PlaylistCoverTile>) = tiles.sumOf { (it.width * it.height).toDouble() }

    private fun overlaps(a: PlaylistCoverTile, b: PlaylistCoverTile) =
        a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

    @Test
    fun `every count of covers fills the whole tile without overlap`() {
        (1..6).forEach { count ->
            val tiles = playlistCoverTiles(count)
            assertEquals(minOf(count, PlaylistCoverTile.MAX), tiles.size, "tiles for $count covers")
            assertEquals(1.0, area(tiles), 1e-6, "area for $count covers")
            tiles.forEachIndexed { i, a -> tiles.drop(i + 1).forEach { b -> assertEquals(false, overlaps(a, b), "$count covers: $a and $b") } }
        }
        assertEquals(emptyList<PlaylistCoverTile>(), playlistCoverTiles(0))
    }

    @Test
    fun `two covers sit side by side and three put one beside two`() {
        assertEquals(listOf(PlaylistCoverTile(0f, 0f, 0.5f, 1f), PlaylistCoverTile(0.5f, 0f, 0.5f, 1f)), playlistCoverTiles(2))
        assertEquals(PlaylistCoverTile(0f, 0f, 0.5f, 1f), playlistCoverTiles(3).first())
    }
}
