package cx.aswin.boxlore.core.rss

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalEpisodeCatalogRepositoryTest {
    @Test
    fun resolveHttpsPrefersPrimaryThenFallback() {
        assertEquals(
            "https://a.example/feed.xml",
            LocalEpisodeCatalogRepository.resolveHttps(
                "https://a.example/feed.xml",
                "https://b.example/feed.xml",
            ),
        )
        assertEquals(
            "https://b.example/feed.xml",
            LocalEpisodeCatalogRepository.resolveHttps("", "https://b.example/feed.xml"),
        )
        assertNull(LocalEpisodeCatalogRepository.resolveHttps("http://insecure.example/f.xml", null))
    }

    @Test
    fun stubFeedIsNotReady() {
        val stub = LocalEpisodeCatalogRepository.stubFeed("100")
        assertEquals(true, stub.needsFullBackfill)
        assertEquals(false, stub.ready)
        assertEquals(false, LocalCatalogReadyLogic.isReady(stub))
    }
}
