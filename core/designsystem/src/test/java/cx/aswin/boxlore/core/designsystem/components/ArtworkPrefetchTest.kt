package cx.aswin.boxlore.core.designsystem.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Deck artwork is prefetched with the request OptimizedImage makes, so the card hits the cache. */
class ArtworkPrefetchTest {
    private val optimize: (String, Int) -> String = { url, width -> "proxy:$width:$url" }

    @Test
    fun `prefetches the first route each artwork would load`() {
        val routes = ArtworkPrefetch.prefetchRoutes(
            listOf("https://a/1.jpg", null, " ", "https://a/2.jpg", "https://a/1.jpg"),
            600,
            ArtworkLoadPolicy(),
            optimize,
        )
        assertEquals(listOf("proxy:600:https://a/1.jpg", "proxy:600:https://a/2.jpg"), routes)
        val expected = ArtworkAttempts(listOf("https://a/1.jpg"), 600, ArtworkLoadPolicy(), optimize).next()?.url
        assertEquals(expected, routes.first())
    }

    @Test
    fun `memory key matches the loaded image key`() {
        assertEquals("proxy:600:https://a/1.jpg-w600", ArtworkPrefetch.memoryKey("proxy:600:https://a/1.jpg", 600))
    }
}
