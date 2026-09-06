package cx.aswin.boxlore.feature.info.components

import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastGenreSuggestionsTest {

    @Test
    fun `blank query returns all suggestions in standard catalog order`() {
        val emptyResult = filterGenreSuggestions("")
        val whitespaceResult = filterGenreSuggestions("   ")

        assertEquals(ALL_GENRE_SUGGESTIONS.size, emptyResult.size)
        assertEquals(ALL_GENRE_SUGGESTIONS.size, whitespaceResult.size)
        assertEquals("News", emptyResult.first().name)
    }

    @Test
    fun `exact name search ranks first`() {
        val result = filterGenreSuggestions("Comedy")
        assertFalse(result.isEmpty())
        assertEquals("Comedy", result.first().name)
        assertEquals("comedy", result.first().iconKey)
    }

    @Test
    fun `prefix name search matches correctly`() {
        val result = filterGenreSuggestions("com")
        assertFalse(result.isEmpty())
        assertEquals("Comedy", result.first().name)
    }

    @Test
    fun `movie keyword matches TV and Film`() {
        val movieResult = filterGenreSuggestions("movie")
        assertFalse(movieResult.isEmpty())
        assertEquals("TV & Film", movieResult.first().name)
        assertEquals("movie", movieResult.first().iconKey)

        val filmResult = filterGenreSuggestions("film")
        assertFalse(filmResult.isEmpty())
        assertEquals("TV & Film", filmResult.first().name)
    }

    @Test
    fun `tech keyword matches Technology and Tech`() {
        val result = filterGenreSuggestions("tech")
        val names = result.map { it.name }
        assertTrue(names.contains("Technology"))
        assertTrue(names.contains("Tech"))
    }

    @Test
    fun `crime keyword matches True Crime`() {
        val result = filterGenreSuggestions("crime")
        assertFalse(result.isEmpty())
        assertEquals("True Crime", result.first().name)
        assertEquals("crime", result.first().iconKey)
    }

    @Test
    fun `gaming keyword matches Gaming`() {
        val result = filterGenreSuggestions("gaming")
        assertFalse(result.isEmpty())
        assertEquals("Gaming", result.first().name)
        assertEquals("gaming", result.first().iconKey)
    }

    @Test
    fun `unknown query returns empty list`() {
        val result = filterGenreSuggestions("xyz123nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `all suggestions have matching icons registered in GenreIcons`() {
        for (suggestion in ALL_GENRE_SUGGESTIONS) {
            val icon = GenreIcons.findIcon(suggestion.iconKey)
            assertNotNull(
                icon,
                "Suggestion ${suggestion.name} with iconKey '${suggestion.iconKey}' must resolve in GenreIcons",
            )
        }
    }
}
