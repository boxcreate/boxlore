package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalCatalogOrphanRematchTest {
    private val catalog =
        listOf(
            TestFixtures.episode(
                id = "-8",
                title = "Matched",
                audioUrl = "https://cdn.example.com/ep.mp3",
                publishedDate = 100L,
            ),
        )

    @Test
    fun doesNotRematchWhenOldIdStillResolves() {
        val resolved = catalog.first()
        assertFalse(LocalCatalogOrphanRematch.shouldRematch(resolved))
        assertNull(
            LocalCatalogOrphanRematch.rematch(
                resolved = resolved,
                candidates = catalog,
                guid = "g",
                enclosureUrl = "https://cdn.example.com/ep.mp3",
                title = "Matched",
                publishedDate = 100L,
            ),
        )
    }

    @Test
    fun rematchPrefersEnclosureWhenUnresolved() {
        assertTrue(LocalCatalogOrphanRematch.shouldRematch(null))
        assertEquals(
            "-8",
            LocalCatalogOrphanRematch.rematch(
                resolved = null,
                candidates = catalog,
                guid = null,
                enclosureUrl = "https://cdn.example.com/ep.mp3",
                title = "Other",
                publishedDate = 1L,
            )?.id,
        )
    }

    @Test
    fun rematchFallsBackToTitleAndDate() {
        assertEquals(
            "-8",
            LocalCatalogOrphanRematch.rematch(
                resolved = null,
                candidates = catalog,
                guid = null,
                enclosureUrl = "",
                title = "Matched",
                publishedDate = 100L,
            )?.id,
        )
    }
}
