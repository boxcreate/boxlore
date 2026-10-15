package cx.aswin.boxlore.navigation

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test fun `going back to a regular page shows its own background at once, even mid-transition`() {
        val base = Color(0xFFFDF8FD)
        val departing = Color(0xFF3A1020)
        // No palette store at all still falls back to the base for regular pages.
        assertEquals(base, artworkNavigationBackdrop(null, "home", transitioning = true, isDetail = false, base = base, initial = departing))
        assertEquals(base, artworkNavigationBackdrop(null, "home", transitioning = false, isDetail = false, base = base, initial = departing))
        // Detail destinations keep the departing colour under them while the pages slide.
        assertEquals(departing, artworkNavigationBackdrop(null, "episode", transitioning = true, isDetail = true, base = base, initial = departing))
    }
}
