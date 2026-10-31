package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Feeds sometimes tag full-length episodes as trailers; only short ones count as trailers. */
class EpisodeTrailerTest {
    private fun episode(type: String?, duration: Int) = Episode(id = "1", title = "Episode", description = "", audioUrl = "https://a/1.mp3", duration = duration, episodeType = type)

    @Test
    fun `a short trailer is a trailer`() {
        assertTrue(episode("trailer", 120).isTrailer)
        assertTrue(episode("Trailer", TRAILER_MAX_DURATION_SECONDS).isTrailer)
        assertEquals("trailer", episode("trailer", 120).effectiveEpisodeType)
    }

    @Test
    fun `a full-length episode the feed tags as a trailer is a full episode`() {
        // Conspiracy Theories tags "The Possessed Nuns of Loudun" (54 min) as a trailer.
        val mislabelled = episode("trailer", 3264)
        assertFalse(mislabelled.isTrailer)
        assertEquals("full", mislabelled.effectiveEpisodeType)
        assertFalse(episode("trailer", TRAILER_MAX_DURATION_SECONDS + 1).isTrailer)
    }

    @Test
    fun `with no known duration the feed's tag stands`() {
        assertTrue(episode("trailer", 0).isTrailer)
    }

    @Test
    fun `other types are untouched`() {
        assertFalse(episode("full", 60).isTrailer)
        assertFalse(episode(null, 60).isTrailer)
        assertEquals("bonus", episode("bonus", 3600).effectiveEpisodeType)
        assertEquals(null, episode(null, 3600).effectiveEpisodeType)
    }
}
