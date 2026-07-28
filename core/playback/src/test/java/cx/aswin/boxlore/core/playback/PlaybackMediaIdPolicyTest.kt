package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackMediaIdPolicyTest {
    @Test
    fun `stripMediaIdPrefixes delegates to QueueMath`() {
        assertEquals("42", PlaybackMediaIdPolicy.stripMediaIdPrefixes("learn:episode:queue:42"))
    }

    @Test
    fun `encodeMediaId adds learn prefix when requested`() {
        assertEquals("learn:ep-1", PlaybackMediaIdPolicy.encodeMediaId("ep-1", useLearnPrefix = true))
        assertEquals("ep-1", PlaybackMediaIdPolicy.encodeMediaId("ep-1", useLearnPrefix = false))
    }

    @Test
    fun `parseEntryPointString prefers entrypoint key`() {
        assertEquals(
            "learn",
            PlaybackMediaIdPolicy.parseEntryPointString(entrypoint = "learn", entryPointLegacy = "generic"),
        )
    }

    @Test
    fun `parseEntryPointString falls back to entry_point key`() {
        assertEquals(
            "home",
            PlaybackMediaIdPolicy.parseEntryPointString(entrypoint = null, entryPointLegacy = "home"),
        )
    }

    @Test
    fun `isLearnEntryPoint detects learn string and enum`() {
        assertTrue(PlaybackMediaIdPolicy.isLearnEntryPoint("learn"))
        assertFalse(PlaybackMediaIdPolicy.isLearnEntryPoint("home"))
        assertTrue(PlaybackMediaIdPolicy.isLearnEntryPoint(PlaybackEntryPoint.LEARN))
        assertFalse(PlaybackMediaIdPolicy.isLearnEntryPoint(PlaybackEntryPoint.GENERIC))
    }

    @Test
    fun `customCacheKey appends briefing audio version`() {
        assertEquals(
            "briefing_in_2026-07-28_602f1072",
            PlaybackMediaIdPolicy.customCacheKey(
                "briefing_in_2026-07-28",
                "https://api.aswin.cx/briefings/audio/in?d=2026-07-28&v=602f1072",
            ),
        )
        assertEquals(
            "ep-1",
            PlaybackMediaIdPolicy.customCacheKey("ep-1", "https://cdn.example/a.mp3?v=x"),
        )
    }
}
