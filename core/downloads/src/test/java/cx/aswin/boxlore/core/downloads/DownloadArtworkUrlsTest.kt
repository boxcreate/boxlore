package cx.aswin.boxlore.core.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DownloadArtworkUrlsTest {
    @Test
    fun `keeps remote urls and existing local files`() {
        assertEquals(
            "https://cdn.example/show.jpg",
            DownloadArtworkUrls.resolve("https://cdn.example/show.jpg"),
        )
        assertEquals(
            "/data/art.png",
            DownloadArtworkUrls.resolve("/data/art.png", fileExists = { it == "/data/art.png" }),
        )
        assertEquals(
            "file:///data/art.png",
            DownloadArtworkUrls.resolve("file:///data/art.png", fileExists = { it == "/data/art.png" }),
        )
    }

    @Test
    fun `drops missing local files and uses fallback`() {
        assertNull(
            DownloadArtworkUrls.resolve(
                stored = "/data/missing.png",
                fileExists = { false },
            ),
        )
        assertEquals(
            "https://cdn.example/show.jpg",
            DownloadArtworkUrls.resolve(
                stored = "/data/missing.png",
                fallback = "https://cdn.example/show.jpg",
                fileExists = { false },
            ),
        )
    }

    @Test
    fun `remoteUrl keeps only http artwork sources`() {
        assertEquals("https://cdn.example/a.jpg", DownloadArtworkUrls.remoteUrl("https://cdn.example/a.jpg"))
        assertEquals("https://cdn.example/a.jpg", DownloadArtworkUrls.remoteUrl("//cdn.example/a.jpg"))
        assertNull(DownloadArtworkUrls.remoteUrl("/data/art.png"))
        assertNull(DownloadArtworkUrls.remoteUrl(" "))
    }
}
