package cx.aswin.boxlore.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoArtworkFetchLogicTest {
    @Test
    fun acceptsImageContentTypesAndLenientFallbacks() {
        assertTrue(AutoArtworkFetchLogic.isAcceptableImageContentType("image/jpeg"))
        assertTrue(AutoArtworkFetchLogic.isAcceptableImageContentType("image/png; charset=binary"))
        assertTrue(AutoArtworkFetchLogic.isAcceptableImageContentType(null))
        assertTrue(AutoArtworkFetchLogic.isAcceptableImageContentType(""))
        assertTrue(AutoArtworkFetchLogic.isAcceptableImageContentType("application/octet-stream"))
        assertFalse(AutoArtworkFetchLogic.isAcceptableImageContentType("text/html"))
        assertFalse(AutoArtworkFetchLogic.isAcceptableImageContentType("application/json"))
    }

    @Test
    fun detectsCommonImageMagicBytes() {
        assertTrue(AutoArtworkFetchLogic.looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertTrue(
            AutoArtworkFetchLogic.looksLikeImage(
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                ),
            ),
        )
        assertTrue(AutoArtworkFetchLogic.looksLikeImage("GIF89a........".toByteArray()))
        val webp = ByteArray(12)
        "RIFF".toByteArray().copyInto(webp, 0)
        "WEBP".toByteArray().copyInto(webp, 8)
        assertTrue(AutoArtworkFetchLogic.looksLikeImage(webp))
        assertFalse(AutoArtworkFetchLogic.looksLikeImage("<html>".toByteArray()))
        assertFalse(AutoArtworkFetchLogic.looksLikeImage(byteArrayOf(1, 2)))
    }

    @Test
    fun shouldAcceptArtworkEnforcesStatusSizeAndType() {
        assertTrue(
            AutoArtworkFetchLogic.shouldAcceptArtwork(
                responseCode = 200,
                contentType = "image/jpeg",
                contentLength = 1024,
            ),
        )
        assertTrue(
            AutoArtworkFetchLogic.shouldAcceptArtwork(
                responseCode = 200,
                contentType = "application/octet-stream",
                contentLength = -1,
                headerBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            ),
        )
        assertFalse(
            AutoArtworkFetchLogic.shouldAcceptArtwork(
                responseCode = 404,
                contentType = "image/jpeg",
                contentLength = 10,
            ),
        )
        assertFalse(
            AutoArtworkFetchLogic.shouldAcceptArtwork(
                responseCode = 200,
                contentType = "image/jpeg",
                contentLength = AutoArtworkFetchLogic.MAX_ARTWORK_BYTES + 1,
            ),
        )
        assertFalse(
            AutoArtworkFetchLogic.shouldAcceptArtwork(
                responseCode = 200,
                contentType = "text/html",
                contentLength = 10,
            ),
        )
        assertFalse(
            AutoArtworkFetchLogic.shouldAcceptArtwork(
                responseCode = 200,
                contentType = "image/jpeg",
                contentLength = 10,
                headerBytes = "<html>".toByteArray(),
            ),
        )
    }

    @Test
    fun recognizesRedirectStatusCodes() {
        assertTrue(AutoArtworkFetchLogic.isRedirect(301))
        assertTrue(AutoArtworkFetchLogic.isRedirect(302))
        assertTrue(AutoArtworkFetchLogic.isRedirect(307))
        assertFalse(AutoArtworkFetchLogic.isRedirect(200))
        assertFalse(AutoArtworkFetchLogic.isRedirect(404))
    }

    @Test
    fun timeoutConstantsAreReasonableForAutoHosts() {
        assertTrue(AutoArtworkFetchLogic.CONNECT_TIMEOUT_MS >= 5_000)
        assertTrue(AutoArtworkFetchLogic.READ_TIMEOUT_MS >= 8_000)
        assertEquals(5, AutoArtworkFetchLogic.MAX_REDIRECTS)
    }
}
