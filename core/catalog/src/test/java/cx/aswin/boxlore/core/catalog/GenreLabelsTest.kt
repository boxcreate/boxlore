package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.PodcastGenres
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Genre tags show the translated genre name, never the "Podcast" placeholder. */
class GenreLabelsTest {
    @Test
    fun `blank and placeholder genres have no label`() {
        listOf(null, "", "  ", "Podcast", "podcasts ").forEach { assertNull("'$it'", genreLabelRes(it)) }
    }

    @Test
    fun `genres and their synonyms use the shared genre names`() {
        assertEquals(R.string.auto_genre_society, genreLabelRes("Society & Culture"))
        assertEquals(R.string.auto_genre_society, genreLabelRes("culture"))
        assertEquals(R.string.auto_genre_technology, genreLabelRes("Technology"))
        assertEquals(R.string.auto_genre_tv_film, genreLabelRes("Movies"))
        assertNull(genreLabelRes("Underwater basket weaving"))
    }

    @Test
    fun `every canonical genre has a name in all five languages`() {
        val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val missing = PodcastGenres.all.filter { genreLabelRes(it) == null }
        assertTrue("Genres without a label: $missing", missing.isEmpty())
        val used = PodcastGenres.all.map { names.getValue(genreLabelRes(it)!!) }.toSet()
        LOCALES.forEach { folder ->
            val defined = stringNames(File("src/main/res/$folder/strings.xml"))
            assertTrue("$folder lacks ${used - defined}", defined.containsAll(used))
        }
    }

    private fun stringNames(file: File): Set<String> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }

    private companion object {
        val LOCALES = listOf("values", "values-de", "values-es", "values-fr", "values-pt-rBR")
    }
}
