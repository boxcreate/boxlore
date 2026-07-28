package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeMediaCacheKeyTest {
    @Test
    fun `non-briefing episodes keep stable episode id`() {
        assertEquals(
            "ep-42",
            EpisodeMediaCacheKey.of(
                episodeId = "ep-42",
                audioUrl = "https://cdn.example/a.mp3?v=abc",
            ),
        )
    }

    @Test
    fun `briefing cache key appends audio version hash`() {
        assertEquals(
            "briefing_in_2026-07-28_602f1072",
            EpisodeMediaCacheKey.of(
                episodeId = "briefing_in_2026-07-28",
                audioUrl =
                    "https://api.aswin.cx/briefings/audio/in?d=2026-07-28&v=602f1072&exp=1&sig=x",
            ),
        )
    }

    @Test
    fun `briefing without v falls back to episode id`() {
        assertEquals(
            "briefing_in_2026-07-28",
            EpisodeMediaCacheKey.of(
                episodeId = "briefing_in_2026-07-28",
                audioUrl = "https://api.aswin.cx/briefings/audio/in?d=2026-07-28",
            ),
        )
    }

    @Test
    fun `audioVersionQueryParam reads v only`() {
        assertEquals(
            "602f1072",
            EpisodeMediaCacheKey.audioVersionQueryParam(
                "https://api.aswin.cx/briefings/audio/in?d=2026-07-28&v=602f1072&exp=1",
            ),
        )
        assertNull(EpisodeMediaCacheKey.audioVersionQueryParam(null))
        assertNull(EpisodeMediaCacheKey.audioVersionQueryParam("https://example.com/a.mp3"))
    }
}
