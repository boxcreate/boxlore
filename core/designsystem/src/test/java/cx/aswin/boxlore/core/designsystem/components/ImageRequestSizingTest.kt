package cx.aswin.boxlore.core.designsystem.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageRequestSizingTest {
    @Test
    fun `requested pixels are not multiplied by density or quality factors`() {
        assertEquals(400, imageTargetPixels(400))
        assertEquals("https://ichef.bbci.co.uk/images/ic/400x400/cover.jpg", "https://ichef.bbci.co.uk/images/ic/1920x1920/cover.jpg".optimizedImageUrl(400))
        assertTrue("https://example.com/cover.jpg".optimizedImageUrl(400).contains("&w=400&"))
    }

    @Test
    fun `decoder and proxy share safe size bounds`() {
        assertEquals(10, imageTargetPixels(0))
        assertEquals(10, imageTargetPixels(Int.MIN_VALUE))
        assertEquals(2048, imageTargetPixels(Int.MAX_VALUE))
        assertTrue("https://example.com/cover.jpg".optimizedImageUrl(Int.MAX_VALUE).contains("&w=2048&"))
    }

    @Test
    fun `tablet artwork upgrades cover rendered pixels without per-pixel cache churn`() {
        assertEquals(400, layoutImageTargetPixels(400, 0))
        assertEquals(400, layoutImageTargetPixels(400, 320))
        assertEquals(800, layoutImageTargetPixels(400, 700))
        assertEquals(800, layoutImageTargetPixels(400, 701))
        assertEquals(1024, layoutImageTargetPixels(800, 850))
        assertEquals(2048, layoutImageTargetPixels(400, Int.MAX_VALUE))
    }

    @Test
    fun `local covers still bypass network proxy`() {
        assertEquals("content://artwork/1", "content://artwork/1".optimizedImageUrl(400))
    }
}
