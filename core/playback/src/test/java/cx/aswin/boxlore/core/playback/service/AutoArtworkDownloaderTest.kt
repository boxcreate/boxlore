package cx.aswin.boxlore.core.playback.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoArtworkDownloaderTest {
    @Test
    fun parseHttpsUrlUpgradesHttpAndRejectsJunk() {
        val https = AutoArtworkDownloader.parseHttpsUrl("https://cdn.example/art.jpg")!!
        assertEquals("https", https.protocol)
        assertEquals("cdn.example", https.host)

        val upgraded = AutoArtworkDownloader.parseHttpsUrl("http://cdn.example/art.jpg")!!
        assertEquals("https", upgraded.protocol)
        assertEquals("cdn.example", upgraded.host)

        assertNull(AutoArtworkDownloader.parseHttpsUrl("not a url"))
        assertNull(AutoArtworkDownloader.parseHttpsUrl("ftp://cdn.example/x.jpg"))
    }

    @Test
    fun isPublicHttpsUrlRejectsNonHttpsAndNon443WithoutDns() {
        val http = URI("http://example.com/cover.jpg").toURL()
        assertFalse(AutoArtworkDownloader.isPublicHttpsUrl(http))

        val weirdPort = URI("https://example.com:8443/cover.jpg").toURL()
        assertFalse(AutoArtworkDownloader.isPublicHttpsUrl(weirdPort))
    }

    @Test
    fun isPublicAddressClassifiesLocalRangesWithoutDns() {
        val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        assertFalse(AutoArtworkDownloader.isPublicAddress(loopback))

        val siteLocal = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 1))
        assertFalse(AutoArtworkDownloader.isPublicAddress(siteLocal))

        val linkLocal = InetAddress.getByAddress(byteArrayOf(169.toByte(), 254.toByte(), 1, 1))
        assertFalse(AutoArtworkDownloader.isPublicAddress(linkLocal))

        // 8.8.8.8 is a well-known public address used only as a classifier fixture.
        val publicV4 = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
        assertTrue(AutoArtworkDownloader.isPublicAddress(publicV4))
    }
}
