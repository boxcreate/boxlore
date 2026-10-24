package cx.aswin.boxlore.core.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** `Episode.recipeId` and `Episode.catalogKey` are optional and don't change existing JSON. */
class EpisodeIdentityFieldsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val episode = Episode(id = "-42", title = "t", description = "d", audioUrl = "https://a.example/e.mp3")

    @Test
    fun `episodes without identity fields default to null`() {
        assertNull(episode.recipeId)
        assertNull(episode.catalogKey)
    }

    @Test
    fun `json written before the fields existed still decodes`() {
        val decoded = json.decodeFromString<Episode>("""{"id":"7","title":"t","description":"d","audioUrl":"u"}""")

        assertNull(decoded.recipeId)
        assertNull(decoded.catalogKey)
    }

    @Test
    fun `unset identity fields are not written`() {
        val encoded = json.encodeToString(Episode.serializer(), episode)

        assertFalse("recipeId" in encoded)
        assertFalse("catalogKey" in encoded)
    }

    @Test
    fun `identity fields round trip`() {
        val withIds = episode.copy(recipeId = 4_503_599_627_370_497L, catalogKey = "guid-1")

        assertEquals(withIds, json.decodeFromString<Episode>(json.encodeToString(Episode.serializer(), withIds)))
    }
}
