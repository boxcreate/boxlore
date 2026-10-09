package cx.aswin.boxlore.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkDetailRouteTest {
    @Test fun `both episode routes and podcast details retain their own artwork palette clock`() {
        listOf("podcast/{podcastId}?entryPoint={entryPoint}", "episode/{episodeId}?t={t}", "episode/{episodeId}/{episodeTitle}").forEach {
            assertTrue(isArtworkDetailRoute(it))
        }
        listOf(null, "home", "explore", "settings", "library/downloads/show", "briefing", "learn/history").forEach {
            assertFalse(isArtworkDetailRoute(it))
        }
    }
}
