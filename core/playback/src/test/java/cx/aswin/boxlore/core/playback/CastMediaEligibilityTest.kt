package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastMediaEligibilityTest {
    @Test
    fun acceptsPublicHttpStreams() {
        assertTrue(CastMediaEligibility.isCastable("https://cdn.example.com/episode.mp3"))
        assertTrue(CastMediaEligibility.isCastable("http://cdn.example.com/live"))
    }

    @Test
    fun rejectsDeviceLocalAndMalformedSources() {
        assertFalse(CastMediaEligibility.isCastable("content://downloads/episode"))
        assertFalse(CastMediaEligibility.isCastable("file:///data/user/0/episode.mp3"))
        assertFalse(CastMediaEligibility.isCastable("http://localhost:8080/episode.mp3"))
        assertFalse(CastMediaEligibility.isCastable("http://192.168.1.4/episode.mp3"))
        assertFalse(CastMediaEligibility.isCastable("http://172.20.0.2/episode.mp3"))
        assertFalse(CastMediaEligibility.isCastable("http://[::1]/episode.mp3"))
        assertFalse(CastMediaEligibility.isCastable("http://[fe80::1]/episode.mp3"))
        assertFalse(CastMediaEligibility.isCastable("http://[fc00::1]/episode.mp3"))
        assertFalse(CastMediaEligibility.isCastable("http://2130706433/episode.mp3"))
        assertFalse(CastMediaEligibility.isCastable("not a uri"))
        assertFalse(CastMediaEligibility.isCastable(null))
    }

    @Test
    fun rejectsMissingCastQueueTitlesInsteadOfInventingPlaceholderEpisodes() {
        assertEquals("A real episode", CastMediaMetadata.queueTitle("  A real episode  "))
        assertNull(CastMediaMetadata.queueTitle(""))
        assertNull(CastMediaMetadata.queueTitle("   "))
        assertNull(CastMediaMetadata.queueTitle(null))
    }
}
