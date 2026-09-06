package cx.aswin.boxlore.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenreIconsTest {

    @Test
    fun `all icons list contains required expressives`() {
        val keys = GenreIcons.all.map { it.key }
        val required = listOf(
            "mic", "headphones", "music", "movie", "gaming", "code", "tech",
            "bulb", "star", "fire", "science", "book", "health", "finance",
        )
        for (key in required) {
            assertNotNull(GenreIcons.findIcon(key), "Missing icon for key: $key")
            org.junit.jupiter.api.Assertions.assertTrue(keys.contains(key), "all list must contain $key")
        }
    }

    @Test
    fun `findIcon returns expected vector for key`() {
        assertEquals(Icons.Rounded.Mic, GenreIcons.findIcon("mic"))
        assertEquals(Icons.Rounded.Code, GenreIcons.findIcon("code"))
        assertEquals(Icons.Rounded.Code, GenreIcons.findIcon("CODE"))
        assertNull(GenreIcons.findIcon("non_existent_key_12345"))
        assertNull(GenreIcons.findIcon(null))
        assertNull(GenreIcons.findIcon(""))
    }

    @Test
    fun `defaultGenreIcon resolves common podcast genres`() {
        assertEquals(Icons.Rounded.Newspaper, GenreIcons.defaultGenreIcon("News"))
        assertEquals(Icons.Rounded.MusicNote, GenreIcons.defaultGenreIcon("Music"))
        assertEquals(GenreIcons.findIcon("art"), GenreIcons.defaultGenreIcon("Arts"))
        assertEquals(GenreIcons.findIcon("work"), GenreIcons.defaultGenreIcon("Startups"))
        assertEquals(Icons.Rounded.Category, GenreIcons.defaultGenreIcon("Unknown Genre"))
        assertEquals(Icons.Rounded.Category, GenreIcons.defaultGenreIcon(null))
    }

    @Test
    fun `iconOrFallback prefers explicit icon key over fallback genre`() {
        assertEquals(Icons.Rounded.Code, GenreIcons.iconOrFallback("code", "News"))
        assertEquals(Icons.Rounded.Newspaper, GenreIcons.iconOrFallback(null, "News"))
        assertEquals(Icons.Rounded.Category, GenreIcons.iconOrFallback(null, null))
    }

    @Test
    fun `folderIconOrFallback returns explicit icon or falls back to folder vector`() {
        assertEquals(Icons.Rounded.Code, GenreIcons.folderIconOrFallback("code"))
        assertEquals(Icons.Rounded.Folder, GenreIcons.folderIconOrFallback(null))
        assertEquals(Icons.Rounded.Folder, GenreIcons.folderIconOrFallback(""))
        assertEquals(Icons.Rounded.Folder, GenreIcons.findIcon("folder"))
    }

    @Test
    fun `suggestIcons returns matching icons for query`() {
        assertTrue(GenreIcons.suggestIcons("").isEmpty())
        val techMatches = GenreIcons.suggestIcons("tech")
        assertTrue(techMatches.any { it.key == "tech" })
        val musicMatches = GenreIcons.suggestIcons("music")
        assertTrue(musicMatches.any { it.key == "music" })
    }
}
