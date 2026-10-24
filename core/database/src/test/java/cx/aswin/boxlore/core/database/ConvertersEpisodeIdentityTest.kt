package cx.aswin.boxlore.core.database

import cx.aswin.boxlore.core.model.Episode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Room stores embedded [Episode] values (queue, history and other rows) through [Converters], which
 * uses Gson. Rows written before `recipeId` and `catalogKey` existed must still read back unchanged,
 * and an episode without them must be written as the same bytes an older build reads.
 *
 * Phase P4, plan section "Identity at every boundary".
 */
class ConvertersEpisodeIdentityTest {
    private val converters = Converters()

    @Test
    fun `a stored episode without identity fields reads back unchanged`() {
        val decoded = requireNotNull(converters.toEpisode(LEGACY_JSON))

        assertNull(decoded.recipeId)
        assertNull(decoded.catalogKey)
        assertEquals(
            Episode(
                id = "-42",
                title = "Pilot",
                description = "First one",
                audioUrl = "https://a.example/e.mp3",
                imageUrl = "https://a.example/e.jpg",
                podcastTitle = "Show",
                podcastId = "rss:ab12",
                duration = 1800,
                publishedDate = 1_700_000_000L,
                seasonNumber = 1,
                episodeNumber = 2,
                episodeType = "full",
            ),
            decoded,
        )
    }

    @Test
    fun `an episode without identity fields is written without their keys`() {
        val legacy = requireNotNull(converters.toEpisode(LEGACY_JSON))

        val encoded = requireNotNull(converters.fromEpisode(legacy))

        assertFalse("recipeId" in encoded, encoded)
        assertFalse("catalogKey" in encoded, encoded)
        assertEquals(legacy, converters.toEpisode(encoded))
    }

    @Test
    fun `identity fields round trip without losing precision`() {
        val episode =
            requireNotNull(converters.toEpisode(LEGACY_JSON))
                .copy(recipeId = 4_503_599_627_370_497L, catalogKey = "00123")

        val decoded = requireNotNull(converters.toEpisode(converters.fromEpisode(episode)))

        assertEquals(4_503_599_627_370_497L, decoded.recipeId)
        assertEquals("00123", decoded.catalogKey)
        assertEquals(episode, decoded)
    }

    private companion object {
        /** An `Episode` row as builds before P4 wrote it. */
        const val LEGACY_JSON =
            """{"id":"-42","title":"Pilot","description":"First one","audioUrl":"https://a.example/e.mp3",""" +
                """"imageUrl":"https://a.example/e.jpg","podcastTitle":"Show","podcastId":"rss:ab12",""" +
                """"duration":1800,"publishedDate":1700000000,"seasonNumber":1,"episodeNumber":2,"episodeType":"full"}"""
    }
}
