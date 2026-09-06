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
    fun `sci-fi and scifi keywords match Fiction`() {
        val scifiResult = filterGenreSuggestions("sci-fi")
        assertFalse(scifiResult.isEmpty())
        assertEquals("Fiction", scifiResult.first().name)
        assertEquals("book", scifiResult.first().iconKey)

        val spaceResult = filterGenreSuggestions("sci fi")
        assertFalse(spaceResult.isEmpty())
        assertEquals("Fiction", spaceResult.first().name)

        val wordResult = filterGenreSuggestions("scifi")
        assertFalse(wordResult.isEmpty())
        assertEquals("Fiction", wordResult.first().name)
    }

    @Test
    fun `tv and film query matches TV & Film via normalization`() {
        val result = filterGenreSuggestions("tv and film")
        assertFalse(result.isEmpty())
        assertEquals("TV & Film", result.first().name)
        assertEquals("movie", result.first().iconKey)
    }

    @Test
    fun `hyphenated true-crime query matches True Crime`() {
        val result = filterGenreSuggestions("true-crime")
        assertFalse(result.isEmpty())
        assertEquals("True Crime", result.first().name)
        assertEquals("crime", result.first().iconKey)
    }

    @Test
    fun `society and culture query matches Society & Culture`() {
        val result = filterGenreSuggestions("society and culture")
        assertFalse(result.isEmpty())
        assertEquals("Society & Culture", result.first().name)
    }

    @Test
    fun `kids and family query matches Kids & Family`() {
        val result = filterGenreSuggestions("kids and family")
        assertFalse(result.isEmpty())
        assertEquals("Kids & Family", result.first().name)
    }

    @Test
    fun `religion and spirituality query matches Religion & Spirituality`() {
        val result = filterGenreSuggestions("religion and spirituality")
        assertFalse(result.isEmpty())
        assertEquals("Religion & Spirituality", result.first().name)
    }

    @Test
    fun `subscription genre catalog tags Religion, Family, Govt exist in ALL_GENRE_SUGGESTIONS`() {
        val names = ALL_GENRE_SUGGESTIONS.map { it.name }
        assertTrue(names.contains("Religion"), "ALL_GENRE_SUGGESTIONS must include Religion")
        assertTrue(names.contains("Family"), "ALL_GENRE_SUGGESTIONS must include Family")
        assertTrue(names.contains("Govt"), "ALL_GENRE_SUGGESTIONS must include Govt")
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

    @Test
    fun `enhanced keywords match appropriate genres`() {
        assertEquals("Philosophy", filterGenreSuggestions("stoicism").first().name)
        assertEquals("Finance", filterGenreSuggestions("bitcoin").first().name)
        assertEquals("Technology", filterGenreSuggestions("artificial intelligence").first().name)
        assertEquals("True Crime", filterGenreSuggestions("serial killer").first().name)
        assertEquals("Sports", filterGenreSuggestions("formula 1").first().name)
        assertEquals("Science", filterGenreSuggestions("astronomy").first().name)
        assertEquals("Automotive", filterGenreSuggestions("tesla").first().name)
        assertEquals("Psychology", filterGenreSuggestions("adhd").first().name)
        assertEquals("Books", filterGenreSuggestions("book club").first().name)
        assertEquals("Ideas", filterGenreSuggestions("innovation").first().name)
    }

    @Test
    fun `multi-token query matches across corpus`() {
        val result = filterGenreSuggestions("python code")
        assertFalse(result.isEmpty())
        assertEquals("Coding", result.first().name)
    }

    @Test
    fun `findSuggestedIcons returns empty when query is blank`() {
        assertTrue(findSuggestedIcons("").isEmpty())
        assertTrue(findSuggestedIcons("   ").isEmpty())
    }

    @Test
    fun `findSuggestedIcons returns relevant icons for query matching suggestions`() {
        val techIcons = findSuggestedIcons("tech")
        assertFalse(techIcons.isEmpty())
        assertTrue(techIcons.any { it.key == "tech" })

        val comedyIcons = findSuggestedIcons("standup comedy")
        assertFalse(comedyIcons.isEmpty())
        assertTrue(comedyIcons.any { it.key == "comedy" })
    }

    @Test
    fun `findSuggestedIcons matches direct icon labels`() {
        val fireIcons = findSuggestedIcons("fire")
        assertFalse(fireIcons.isEmpty())
        assertTrue(fireIcons.any { it.key == "fire" })

        val starIcons = findSuggestedIcons("star")
        assertFalse(starIcons.isEmpty())
        assertTrue(starIcons.any { it.key == "star" })
    }
}
